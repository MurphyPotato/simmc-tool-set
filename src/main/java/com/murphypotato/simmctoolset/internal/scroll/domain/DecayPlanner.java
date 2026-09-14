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
                .filter(m -> !request.excludedMaterials().contains(m.name()))
                .filter(m -> !request.materialBudget().containsKey(m.name()) || request.materialBudget().get(m.name()) > 0)
                .toList();
            List<CraftPlan> fallback = fallbackCandidates(allowed, request.recipe());
            for (CraftPlan candidate : fallback) {
                int capacity = maximumFeasibleRepeat(candidate, request, request.currentUsage(), deadline);
                if (capacity > 0) best = evaluateInternal(
                    List.of(new RotationBatch(candidate, Math.min(capacity, request.desiredCrafts()))), request.recipe(), request.materials(),
                    request.currentUsage(), request.materialBudget(), request.desiredCrafts());
            }
            List<CraftPlan> candidates = ArcaneSolver.findCraftPlans(
                request.recipe(), allowed, ArcaneSolver.DEFAULT_IMPURITY_LIMIT, 64,
                () -> {
                    if (System.nanoTime() >= deadline) throw new PlanningTimeoutException();
                    return request.cancellation().getAsBoolean();
                });
            check(request, deadline);
            candidates = expandCandidates(mergeCandidates(fallback, candidates), allowed, request, deadline);
            if (candidates.isEmpty()) return new PlanningResult(
                PlanningStatus.NO_FEASIBLE_PLAN, emptyPlan(request), List.of());

            List<RankedPlan> ranked = new ArrayList<>();
            for (CraftPlan candidate : candidates) {
                check(request, deadline);
                int capacity = maximumFeasibleRepeat(candidate, request, request.currentUsage(), deadline);
                if (capacity > 0) {
                    ranked.add(new RankedPlan(candidate, capacity, finalUsage(candidate, capacity, request)));
                    best = evaluateInternal(List.of(new RotationBatch(candidate, Math.min(capacity, request.desiredCrafts()))), request.recipe(),
                        request.materials(), request.currentUsage(), request.materialBudget(), request.desiredCrafts());
                }
            }
            ranked.sort(Comparator.comparingInt(RankedPlan::capacity).reversed()
                .thenComparing((a, b) -> Integer.compare(b.finalUsage(), a.finalUsage()))
                .thenComparing(r -> r.plan().id()));
            Map<String, Integer> usage = new LinkedHashMap<>(request.currentUsage());
            List<RotationBatch> batches = new ArrayList<>();
            int remaining = request.desiredCrafts();
            while (remaining > 0 && !ranked.isEmpty()) {
                check(request, deadline);
                RankedPlan rankedPlan = choosePlan(ranked, request, usage, deadline);
                int capacity = maximumFeasibleRepeat(rankedPlan.plan(), request, usage, deadline);
                if (capacity <= 0) {
                    ranked.remove(rankedPlan);
                    continue;
                }
                int crafts = Math.min(remaining, capacity);
                batches.add(new RotationBatch(rankedPlan.plan(), crafts));
                EvaluatedBatch evaluated = evaluateBatch(rankedPlan.plan(), crafts, request.recipe(),
                    request.materials(), usage);
                usage = new LinkedHashMap<>(evaluated.afterUsage());
                remaining -= crafts;
                best = evaluateInternal(batches, request.recipe(), request.materials(),
                    request.currentUsage(), request.materialBudget(), request.desiredCrafts());
            }
            List<RotationBatch> ordered = orderByFinalPortfolio(batches, request.currentUsage());
            EvaluatedPlan orderedPlan = evaluateInternal(ordered, request.recipe(), request.materials(),
                request.currentUsage(), request.materialBudget(), request.desiredCrafts());
            best = orderedPlan.feasible() ? orderedPlan : best;
            PlanningStatus status = best.complete() ? PlanningStatus.COMPLETE : PlanningStatus.PARTIAL;
            return new PlanningResult(status, best, candidates);
        } catch (ArcaneSolver.CalculationCancelledException ex) {
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
            validatePlan(batch.plan());
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
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("材料向量无效");
            }
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
        BigDecimal impurityExact = BigDecimal.ZERO, excessExact = BigDecimal.ZERO;
        for (Element e : Element.values()) {
            BigDecimal value = effective.get(e);
            BigDecimal required = BigDecimal.valueOf((long) recipe.required().get(e) * crafts);
            if (recipe.required().get(e) == 0) impurityExact = impurityExact.add(value);
            if (value.compareTo(required) > 0) excessExact = excessExact.add(value.subtract(required));
        }
        int impurity = impurityExact.setScale(0, RoundingMode.HALF_UP).intValue();
        int excess = excessExact.setScale(0, RoundingMode.HALF_UP).intValue();
        boolean feasible = extra.isEmpty()
            && actualInputCount(plan, crafts) <= ArcaneSolver.MAX_BATCH_INPUTS
            && meetsRecipe(effective, recipe, crafts)
            && impurityExact.compareTo(BigDecimal.valueOf((long) ArcaneSolver.DEFAULT_IMPURITY_LIMIT * crafts)) < 0
            && withinTargetExcess(effective, recipe, crafts);
        BigDecimal requiredTotal = BigDecimal.valueOf((long) recipe.required().total() * crafts);
        BigDecimal efficiency = requiredTotal.signum() == 0 ? BigDecimal.ZERO :
            effective.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(requiredTotal, MaterialDecay.MATH_CONTEXT);
        return new EvaluatedBatch(plan, crafts, theoretical, effective, start, after, feasible,
            impurity, excess, extra, efficiency);
    }

    private static int maximumFeasibleRepeat(CraftPlan plan, ScrollPlanningRequest request,
                                             Map<String, Integer> usage, long deadline) {
        int cap = Math.min(320, actualInputCount(plan, 1) == 0 ? 0
            : ArcaneSolver.MAX_BATCH_INPUTS / actualInputCount(plan, 1)), best = 0;
        for (int n = 1; n <= cap; n++) {
            check(request, deadline);
            EvaluatedBatch batch = evaluateBatch(plan, n, request.recipe(), request.materials(), usage);
            if (batch.feasible() && meetsRecipe(batch.effectiveElements(), request.recipe(), n)
                && withinBudget(usage, batch.afterUsage(), request.materialBudget())) best = n;
            else break;
        }
        return best;
    }
    private static RankedPlan choosePlan(List<RankedPlan> ranked, ScrollPlanningRequest request,
                                         Map<String, Integer> usage, long deadline) {
        RankedPlan best = ranked.getFirst();
        int bestCapacity = maximumFeasibleRepeat(best.plan(), request, usage, deadline);
        for (RankedPlan candidate : ranked) {
            check(request, deadline);
            int capacity = maximumFeasibleRepeat(candidate.plan(), request, usage, deadline);
            if (capacity > bestCapacity || (capacity == bestCapacity
                && candidate.plan().materialTotal() < best.plan().materialTotal())) {
                best = candidate;
                bestCapacity = capacity;
            }
        }
        return best;
    }
    private static List<CraftPlan> expandCandidates(List<CraftPlan> seeds, List<Material> materials,
                                                     ScrollPlanningRequest request, long deadline) {
        Map<String, CraftPlan> unique = new LinkedHashMap<>();
        for (CraftPlan seed : seeds) unique.put(seed.id(), seed);
        int width = request.budget().beamWidth();
        List<CraftPlan> frontier = new ArrayList<>(seeds);
        for (int depth = 0; depth < 2 && unique.size() < width; depth++) {
            List<CraftPlan> next = new ArrayList<>();
            for (CraftPlan seed : frontier) {
                check(request, deadline);
                for (Material material : materials) {
                    if (request.excludedMaterials().contains(material.name())) continue;
                    CraftPlan expanded = augment(seed, material, request.recipe());
                    if (expanded != null && unique.putIfAbsent(expanded.id(), expanded) == null) next.add(expanded);
                    if (unique.size() >= width) break;
                }
            }
            frontier = next;
            if (frontier.isEmpty()) break;
        }
        return List.copyOf(unique.values());
    }
    private static List<CraftPlan> mergeCandidates(List<CraftPlan> first, List<CraftPlan> second) {
        Map<String, CraftPlan> merged = new LinkedHashMap<>();
        first.forEach(plan -> merged.put(plan.id(), plan));
        second.forEach(plan -> merged.putIfAbsent(plan.id(), plan));
        return List.copyOf(merged.values());
    }
    private static List<CraftPlan> fallbackCandidates(List<Material> materials, ScrollRecipe recipe) {
        List<CraftPlan> result = new ArrayList<>();
        for (Material material : materials) {
            int impurity = 0;
            for (Element e : Element.values()) if (recipe.required().get(e) == 0) impurity += material.elements().get(e);
            boolean target = true;
            for (Element e : Element.values()) {
                int value = material.elements().get(e), required = recipe.required().get(e);
                if (value < required || value > required + 24) target = false;
            }
            if (!target) continue;
            result.add(new CraftPlan("single:" + material.name(), Map.of(material.name(), 1),
                material.elements(), impurity, 0, 1, 1, 1, 100000L + impurity * 700L));
        }
        return result;
    }
    private static CraftPlan augment(CraftPlan seed, Material material, ScrollRecipe recipe) {
        Map<String, Integer> map = new LinkedHashMap<>(seed.materials());
        map.merge(material.name(), 1, Math::addExact);
        ElementAmounts supplied = seed.supplied().plus(material.elements());
        for (Element e : Element.values()) {
            int required = recipe.required().get(e);
            if (recipe.required().get(e) > 0 && supplied.get(e) > required + 24) return null;
        }
        int impurity = 0;
        for (Element e : Element.values()) if (recipe.required().get(e) == 0) impurity += supplied.get(e);
        // Impurity is evaluated after nonlinear decay at the evolving usage;
        // theoretical impurity alone is not a rejection criterion.
        int total = map.values().stream().mapToInt(Integer::intValue).sum();
        int maxRepeat = map.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long score = total * 100000L + impurity * 700L;
        return new CraftPlan(seed.id() + "+" + material.name(), map, supplied, impurity, 0,
            total, maxRepeat, map.size(), score);
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
    private static List<RotationBatch> orderByFinalPortfolio(List<RotationBatch> batches,
                                                              Map<String, Integer> initialUsage) {
        Map<String, Integer> finalUsage = new LinkedHashMap<>(initialUsage);
        for (RotationBatch batch : batches) {
            for (Map.Entry<String, Integer> entry : batch.plan().materials().entrySet()) {
                finalUsage.merge(entry.getKey(), entry.getValue() * batch.crafts(), Math::addExact);
            }
        }
        return batches.stream().sorted(Comparator.comparingInt(
            (RotationBatch batch) -> batch.plan().materials().keySet().stream()
                .mapToInt(name -> finalUsage.getOrDefault(name, 0)).max().orElse(0)).reversed()).toList();
    }
    private static EvaluatedPlan emptyPlan(ScrollPlanningRequest request) {
        return new EvaluatedPlan(request.desiredCrafts(), 0, List.of(), request.currentUsage(),
            request.currentUsage(), ElementAmounts.zero(), zeroElements(), false, 0, 0, Map.of(),
            BigDecimal.ZERO, PlanningStatus.NO_FEASIBLE_PLAN);
    }
    private static int actualInputCount(CraftPlan plan, int crafts) {
        long perCraft = 1L;
        for (Integer count : plan.materials().values()) {
            if (count == null || count <= 0) return Integer.MAX_VALUE;
            perCraft = Math.addExact(perCraft, count.longValue());
        }
        if (perCraft <= 0L) return Integer.MAX_VALUE;
        long total = perCraft * crafts;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
    private static void validatePlan(CraftPlan plan) {
        actualInputCount(plan, 1);
        plan.materials().forEach((name, count) -> {
            if (name == null || name.isBlank() || count == null || count <= 0) {
                throw new IllegalArgumentException("材料向量无效");
            }
        });
    }
    private static boolean withinTargetExcess(Map<Element, BigDecimal> effective,
                                              ScrollRecipe recipe, int crafts) {
        for (Element e : Element.values()) {
            BigDecimal upper = BigDecimal.valueOf((long) recipe.required().get(e) * crafts + 24L * crafts);
            if (effective.get(e).compareTo(upper) > 0) return false;
        }
        return true;
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
