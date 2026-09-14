package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nonlinear, shared-usage evaluator and bounded multi-batch planner. */
public final class DecayPlanner {
    private DecayPlanner() {}

    public static PlanningResult plan(ScrollPlanningRequest request) {
        long deadline = System.nanoTime() + request.budget().nanos();
        EvaluatedPlan best = emptyPlan(request);
        try {
            check(request, deadline);
            if (request.desiredCrafts() == 0) {
                EvaluatedPlan empty = evaluate(List.of(), request.recipe(), request.materials(),
                    request.currentUsage(), request.materialBudget());
                return new PlanningResult(PlanningStatus.COMPLETE, empty, List.of());
            }
            List<Material> allowed = request.materials().stream()
                .filter(m -> !request.recipe().mainMaterial().equals(m.name()))
                .filter(m -> !request.materialBudget().containsKey(m.name()) || request.materialBudget().get(m.name()) > 0)
                .toList();
            List<CraftPlan> candidates = ArcaneSolver.findCraftPlans(
                request.recipe(), allowed, ArcaneSolver.DEFAULT_IMPURITY_LIMIT, 64,
                () -> request.cancellation().getAsBoolean() || System.nanoTime() >= deadline);
            check(request, deadline);
            if (candidates.isEmpty()) return new PlanningResult(
                PlanningStatus.NO_FEASIBLE_PLAN, emptyPlan(request), List.of());

            List<RankedPlan> ranked = new ArrayList<>();
            for (CraftPlan candidate : candidates) {
                check(request, deadline);
                int capacity = maximumFeasibleRepeat(candidate, request, request.currentUsage(), deadline);
                if (capacity > 0) ranked.add(new RankedPlan(candidate, capacity, finalUsage(candidate, capacity, request)));
            }
            ranked.sort(Comparator.comparingInt(RankedPlan::capacity).reversed()
                .thenComparing((a, b) -> Integer.compare(b.finalUsage(), a.finalUsage()))
                .thenComparing(r -> r.plan().id()));
            Map<String, Integer> usage = new LinkedHashMap<>(request.currentUsage());
            List<RotationBatch> batches = new ArrayList<>();
            int remaining = request.desiredCrafts();
            for (RankedPlan rankedPlan : ranked) {
                check(request, deadline);
                if (remaining == 0) break;
                int capacity = maximumFeasibleRepeat(rankedPlan.plan(), request, usage, deadline);
                int crafts = Math.min(remaining, capacity);
                if (crafts <= 0) continue;
                batches.add(new RotationBatch(rankedPlan.plan(), crafts));
                EvaluatedBatch evaluated = evaluateBatch(rankedPlan.plan(), crafts, request.recipe(),
                    request.materials(), usage);
                usage = new LinkedHashMap<>(evaluated.afterUsage());
                remaining -= crafts;
            }
            best = evaluateInternal(batches, request.recipe(), request.materials(),
                request.currentUsage(), request.materialBudget(), request.desiredCrafts());
            PlanningStatus status = best.complete() ? PlanningStatus.COMPLETE : PlanningStatus.PARTIAL;
            return new PlanningResult(status, best, candidates);
        } catch (ArcaneSolver.CalculationCancelledException ex) {
            if (System.nanoTime() >= deadline) {
                return new PlanningResult(PlanningStatus.TIMED_OUT, best, List.of());
            }
            throw new PlanningCancelledException();
        } catch (PlanningCancelledException ex) {
            throw ex;
        } catch (PlanningTimeoutException ex) {
            return new PlanningResult(PlanningStatus.TIMED_OUT, best, List.of());
        }
    }

    /** Evaluates a manually edited schedule; this never invokes the solver. */
    public static EvaluatedPlan evaluate(List<RotationBatch> batches, ScrollRecipe recipe,
                                         List<Material> materials, Map<String, Integer> initialUsage) {
        return evaluate(batches, recipe, materials, initialUsage, Map.of());
    }

    public static EvaluatedPlan evaluate(List<RotationBatch> batches, ScrollRecipe recipe,
                                         List<Material> materials, Map<String, Integer> initialUsage,
                                         Map<String, Integer> budget) {
        int planned = batches == null ? 0 : batches.stream()
            .filter(batch -> batch != null && batch.crafts() > 0)
            .mapToInt(RotationBatch::crafts).sum();
        return evaluateInternal(batches, recipe, materials, initialUsage, budget, planned);
    }

    private static EvaluatedPlan evaluateInternal(List<RotationBatch> batches, ScrollRecipe recipe,
                                         List<Material> materials, Map<String, Integer> initialUsage,
                                         Map<String, Integer> budget, int desiredCrafts) {
        if (recipe == null || materials == null) throw new IllegalArgumentException("评估输入不能为空");
        Map<String, Material> byName = new LinkedHashMap<>();
        materials.forEach(m -> byName.put(m.name(), m));
        Map<String, Integer> before = immutableUsage(initialUsage);
        Map<String, Integer> usage = new LinkedHashMap<>(before);
        List<EvaluatedBatch> result = new ArrayList<>();
        for (RotationBatch batch : batches == null ? List.<RotationBatch>of() : batches) {
            if (batch == null || batch.plan() == null || batch.crafts() <= 0) continue;
            EvaluatedBatch evaluated = evaluateBatch(batch.plan(), batch.crafts(), recipe, materials, usage);
            result.add(evaluated);
            usage = new LinkedHashMap<>(evaluated.afterUsage());
        }
        ElementAmounts theoretical = ElementAmounts.zero();
        Map<Element, BigDecimal> effective = zeroElements();
        int impurity = 0, excess = 0;
        Map<String, Integer> extra = new LinkedHashMap<>();
        BigDecimal efficiency = BigDecimal.ZERO;
        for (EvaluatedBatch batch : result) {
            theoretical = theoretical.plus(batch.theoreticalElements());
            addElements(effective, batch.effectiveElements());
            impurity += batch.impurity();
            excess += batch.excess();
            merge(extra, batch.extraMaterials());
            efficiency = efficiency.add(batch.efficiency(), MaterialDecay.MATH_CONTEXT);
        }
        int planned = result.stream().mapToInt(EvaluatedBatch::crafts).sum();
        boolean feasible = result.stream().allMatch(EvaluatedBatch::feasible) && withinBudget(before, usage, budget);
        PlanningStatus status = feasible ? PlanningStatus.COMPLETE : PlanningStatus.PARTIAL;
        return new EvaluatedPlan(desiredCrafts, planned, result, before, usage, theoretical, effective,
            feasible, impurity, excess, extra, efficiency, status);
    }

    /** Legacy API retained for existing controller code. Main-item contribution remains excluded. */
    public static Map<String, double[]> simulate(List<RotationBatch> batches,
                                                   List<Material> materials,
                                                   Map<String, Integer> initialUsage,
                                                   boolean includeMain,
                                                   String mainMaterial) {
        Map<String, Material> byName = new LinkedHashMap<>();
        materials.forEach(m -> byName.put(m.name(), m));
        Map<String, Integer> usage = new LinkedHashMap<>(initialUsage == null ? Map.of() : initialUsage);
        Map<String, double[]> result = new LinkedHashMap<>();
        for (RotationBatch batch : batches == null ? List.<RotationBatch>of() : batches) {
            if (batch == null || batch.plan() == null || batch.crafts() <= 0) continue;
            Map<String, Integer> all = new LinkedHashMap<>(batch.plan().materials());
            // Historical semantics count the main item for the input cap, but its
            // element row is not part of the imported material table.
            for (Map.Entry<String, Integer> entry : all.entrySet()) {
                Material material = byName.get(entry.getKey());
                if (material == null) continue;
                int before = usage.getOrDefault(entry.getKey(), 0);
                int amount = entry.getValue() * batch.crafts();
                double[] values = result.computeIfAbsent(entry.getKey(), ignored -> new double[Element.values().length]);
                for (Element e : Element.values()) values[e.ordinal()] +=
                    MaterialDecay.incremental(material.elements().get(e), before, amount);
                usage.put(entry.getKey(), before + amount);
            }
        }
        return Map.copyOf(result);
    }

    private static EvaluatedBatch evaluateBatch(CraftPlan plan, int crafts, ScrollRecipe recipe,
                                                List<Material> materials, Map<String, Integer> before) {
        Map<String, Material> byName = new LinkedHashMap<>();
        materials.forEach(m -> byName.put(m.name(), m));
        Map<String, Integer> start = immutableUsage(before);
        Map<String, Integer> after = new LinkedHashMap<>(start);
        Map<Element, BigDecimal> effective = zeroElements();
        ElementAmounts theoretical = ElementAmounts.zero();
        Map<String, Integer> extra = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : plan.materials().entrySet()) {
            int amount = Math.multiplyExact(entry.getValue(), crafts);
            Material material = byName.get(entry.getKey());
            if (material == null) {
                extra.merge(entry.getKey(), amount, Integer::sum);
                continue;
            }
            int used = after.getOrDefault(entry.getKey(), 0);
            for (Element e : Element.values()) effective.put(e, effective.get(e).add(
                MaterialDecay.incremental(BigDecimal.valueOf(material.elements().get(e)), used, amount),
                MaterialDecay.MATH_CONTEXT));
            theoretical = theoretical.plus(scale(material.elements(), amount));
            after.put(entry.getKey(), Math.addExact(used, amount));
        }
        int impurity = 0, excess = 0;
        for (Element e : Element.values()) {
            BigDecimal value = effective.get(e);
            if (recipe.required().get(e) == 0) impurity += value.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).intValue();
            BigDecimal required = BigDecimal.valueOf((long) recipe.required().get(e) * crafts);
            if (value.compareTo(required) > 0) excess += value.subtract(required).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        boolean feasible = extra.isEmpty()
            && batchInputCount(plan, crafts) <= ArcaneSolver.MAX_BATCH_INPUTS
            && meetsRecipe(effective, recipe, crafts)
            && impurity < ArcaneSolver.DEFAULT_IMPURITY_LIMIT * crafts;
        BigDecimal requiredTotal = BigDecimal.valueOf((long) recipe.required().total() * crafts);
        BigDecimal efficiency = requiredTotal.signum() == 0 ? BigDecimal.ZERO :
            effective.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(requiredTotal, MaterialDecay.MATH_CONTEXT);
        return new EvaluatedBatch(plan, crafts, theoretical, effective, start, after, feasible,
            impurity, excess, extra, efficiency);
    }

    private static int maximumFeasibleRepeat(CraftPlan plan, ScrollPlanningRequest request,
                                             Map<String, Integer> usage, long deadline) {
        int cap = ArcaneSolver.maxCraftsPerBatch(plan, 64), best = 0;
        for (int n = 1; n <= cap; n++) {
            check(request, deadline);
            EvaluatedBatch batch = evaluateBatch(plan, n, request.recipe(), request.materials(), usage);
            if (batch.feasible() && meetsRecipe(batch.effectiveElements(), request.recipe(), n)
                && withinBudget(usage, batch.afterUsage(), request.materialBudget())) best = n;
            else break;
        }
        return best;
    }
    private static boolean meetsRecipe(Map<Element, BigDecimal> effective, ScrollRecipe recipe, int crafts) {
        for (Element e : Element.values()) if (effective.get(e).compareTo(
            BigDecimal.valueOf((long) recipe.required().get(e) * crafts)) < 0) return false;
        return true;
    }
    private static int finalUsage(CraftPlan plan, int crafts, ScrollPlanningRequest request) {
        int maximum = 0;
        for (Map.Entry<String, Integer> e : plan.materials().entrySet()) maximum = Math.max(maximum,
            request.currentUsage().getOrDefault(e.getKey(), 0) + e.getValue() * crafts);
        return maximum;
    }
    private static EvaluatedPlan emptyPlan(ScrollPlanningRequest request) {
        return new EvaluatedPlan(request.desiredCrafts(), 0, List.of(), request.currentUsage(),
            request.currentUsage(), ElementAmounts.zero(), zeroElements(), false, 0, 0, Map.of(),
            BigDecimal.ZERO, PlanningStatus.NO_FEASIBLE_PLAN);
    }
    private static int batchInputCount(CraftPlan plan, int crafts) {
        long total = ((long) plan.materialTotal() + 1L) * crafts;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
    private static ElementAmounts scale(ElementAmounts values, int factor) {
        int[] result = values.toArray();
        for (int i = 0; i < result.length; i++) result[i] = Math.multiplyExact(result[i], factor);
        return new ElementAmounts(result);
    }
    private static Map<Element, BigDecimal> zeroElements() {
        Map<Element, BigDecimal> result = new LinkedHashMap<>();
        for (Element e : Element.values()) result.put(e, BigDecimal.ZERO);
        return result;
    }
    private static void addElements(Map<Element, BigDecimal> target, Map<Element, BigDecimal> source) {
        source.forEach((e, value) -> target.put(e, target.get(e).add(value, MaterialDecay.MATH_CONTEXT)));
    }
    private static void merge(Map<String, Integer> target, Map<String, Integer> source) {
        source.forEach((name, value) -> target.merge(name, value, Math::addExact));
    }
    private static Map<String, Integer> immutableUsage(Map<String, Integer> values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (values != null) values.forEach((name, value) -> {
            if (name == null || name.isBlank() || value == null || value < 0) throw new IllegalArgumentException("用量无效");
            result.put(name, value);
        });
        return Map.copyOf(result);
    }
    private static boolean withinBudget(Map<String, Integer> before, Map<String, Integer> after,
                                        Map<String, Integer> budget) {
        for (Map.Entry<String, Integer> entry : budget.entrySet()) {
            int used = after.getOrDefault(entry.getKey(), 0) - before.getOrDefault(entry.getKey(), 0);
            if (used > entry.getValue()) return false;
        }
        return true;
    }
    private static void check(ScrollPlanningRequest request, long deadline) {
        if (request.cancellation().getAsBoolean() || Thread.currentThread().isInterrupted()) throw new PlanningCancelledException();
        if (System.nanoTime() >= deadline) throw new PlanningTimeoutException();
    }
    private record RankedPlan(CraftPlan plan, int capacity, int finalUsage) {}
    public static final class PlanningCancelledException extends RuntimeException {}
    private static final class PlanningTimeoutException extends RuntimeException {}
}
