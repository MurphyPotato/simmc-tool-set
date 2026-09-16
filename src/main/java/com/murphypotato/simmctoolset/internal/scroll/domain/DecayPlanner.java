package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Nonlinear, shared-usage evaluator and bounded multi-batch planner. */
public final class DecayPlanner {
    private DecayPlanner() {}

    public static PlanningResult plan(ScrollPlanningRequest request) {
        return plan(request, System::nanoTime);
    }

    static PlanningResult plan(ScrollPlanningRequest request, java.util.function.LongSupplier clock) {
        Deadline deadline = new Deadline(clock, clock.getAsLong(), request.budget().nanos());
        SearchProgress progress = new SearchProgress(emptyPlan(request));
        try {
            check(request, deadline);
            if (request.desiredCrafts() == 0) {
                EvaluatedPlan empty = evaluate(List.of(), request.recipe(), request.materials(),
                    request.currentUsage(), request.materialBudget());
                return new PlanningResult(PlanningStatus.COMPLETE, empty, List.of());
            }
            Map<String, Integer> usage = new LinkedHashMap<>(request.currentUsage());
            List<RotationBatch> batches = new ArrayList<>();
            int remaining = request.desiredCrafts();
            while (remaining > 0) {
                check(request, deadline);
                Map<String, Integer> available = new LinkedHashMap<>();
                for (var entry : request.materialBudget().entrySet()) {
                    int used = usage.getOrDefault(entry.getKey(), 0)
                        - request.currentUsage().getOrDefault(entry.getKey(), 0);
                    available.put(entry.getKey(), Math.max(0, entry.getValue() - used));
                }
                ScrollPlanningRequest stage = new ScrollPlanningRequest(request.recipe(), request.materials(),
                    remaining, usage, available, request.excludedMaterials(), request.budget(), request.cancellation());
                IncrementalCandidateSearch.Outcome outcome = IncrementalCandidateSearch.search(stage,
                    () -> check(request, deadline), candidate -> {
                        progress.discovered.add(candidate);
                        List<RotationBatch> tentative = new ArrayList<>(batches);
                        tentative.add(new RotationBatch(candidate, 1));
                        progress.best = betterPlan(progress.best, evaluateInternal(tentative, request.recipe(),
                            request.materials(), request.currentUsage(), request.materialBudget(),
                            request.desiredCrafts(), request.excludedMaterials()));
                    }, candidate -> {
                        // Closest candidate is diagnostic only, never an executable
                        // batch or a contribution to temporary/recorded M.
                        if (batches.isEmpty()) progress.closest = candidate;
                    });
                if (outcome.candidates().isEmpty()) {
                    progress.explanation = outcome.explanation();
                    PlanningStatus status = !batches.isEmpty() ? PlanningStatus.PARTIAL
                        : outcome.exhaustive() ? PlanningStatus.NO_FEASIBLE_PLAN : PlanningStatus.SEARCH_LIMIT_REACHED;
                    return progress.result(status);
                }
                CraftPlan selected = null;
                int selectedCrafts = 0;
                EvaluatedPlan selectedPortfolio = null;
                for (CraftPlan candidate : outcome.candidates()) {
                    check(request, deadline);
                    int crafts = maximumFeasibleRepeat(candidate, stage, usage, deadline);
                    if (crafts <= 0) continue;
                    List<RotationBatch> tentative = new ArrayList<>(batches);
                    tentative.add(new RotationBatch(candidate, crafts));
                    EvaluatedPlan evaluated = evaluateInternal(tentative, request.recipe(), request.materials(),
                        request.currentUsage(), request.materialBudget(), request.desiredCrafts(), request.excludedMaterials());
                    progress.best = betterPlan(progress.best, evaluated);
                    if (selectedPortfolio == null || betterPlan(selectedPortfolio, evaluated) == evaluated) {
                        selected = candidate;
                        selectedCrafts = crafts;
                        selectedPortfolio = evaluated;
                    }
                }
                if (selected == null) return progress.result(PlanningStatus.SEARCH_LIMIT_REACHED);
                batches.add(new RotationBatch(selected, selectedCrafts));
                usage = new LinkedHashMap<>(selectedPortfolio.afterUsage());
                remaining -= selectedCrafts;
            }
            check(request, deadline);
            List<RotationBatch> ordered = orderByFinalPortfolio(batches, request.currentUsage());
            EvaluatedPlan orderedPlan = evaluateInternal(ordered, request.recipe(), request.materials(),
                request.currentUsage(), request.materialBudget(), request.desiredCrafts(),
                request.excludedMaterials());
            // Never sacrifice feasibility just to reorder the visible batches.
            if (orderedPlan.complete()) progress.best = orderedPlan;
            return progress.result(progress.best.complete() ? PlanningStatus.COMPLETE : PlanningStatus.PARTIAL);
        } catch (PlanningCancelledException ex) {
            throw ex;
        } catch (PlanningTimeoutException ex) {
            return progress.result(PlanningStatus.TIMED_OUT);
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
        return evaluate(batches, recipe, materials, initialUsage, budget, Set.of());
    }

    /** Evaluates a manually edited schedule while enforcing requested exclusions. */
    public static EvaluatedPlan evaluate(List<RotationBatch> batches, ScrollRecipe recipe,
                                         List<Material> materials, Map<String, Integer> initialUsage,
                                         Map<String, Integer> budget, Set<String> excludedMaterials) {
        int planned = batches == null ? 0 : batches.stream()
            .filter(batch -> batch != null && batch.crafts() > 0)
            .mapToInt(RotationBatch::crafts).sum();
        return evaluateInternal(batches, recipe, materials, initialUsage, budget, planned,
            excludedMaterials == null ? Set.of() : excludedMaterials);
    }

    private static EvaluatedPlan evaluateInternal(List<RotationBatch> batches, ScrollRecipe recipe,
                                         List<Material> materials, Map<String, Integer> initialUsage,
                                         Map<String, Integer> budget, int desiredCrafts,
                                         Set<String> excludedMaterials) {
        if (recipe == null || materials == null) throw new IllegalArgumentException("评估输入不能为空");
        Map<String, Material> byName = new LinkedHashMap<>();
        materials.forEach(m -> byName.put(m.name(), m));
        Map<String, Integer> before = immutableUsage(initialUsage);
        Map<String, Integer> usage = new LinkedHashMap<>(before);
        List<EvaluatedBatch> result = new ArrayList<>();
        for (RotationBatch batch : batches == null ? List.<RotationBatch>of() : batches) {
            if (batch == null || batch.plan() == null || batch.crafts() <= 0) continue;
            validatePlan(batch.plan());
            EvaluatedBatch evaluated = evaluateBatch(batch.plan(), batch.crafts(), recipe, materials, usage,
                excludedMaterials);
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
                                                List<Material> materials, Map<String, Integer> before,
                                                Set<String> excludedMaterials) {
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
            if (excludedMaterials.contains(entry.getKey())) {
                extra.merge(entry.getKey(), amount, Integer::sum);
                continue;
            }
            Material material = byName.get(entry.getKey());
            if (material == null) {
                extra.merge(entry.getKey(), amount, Integer::sum);
                continue;
            }
            int used = after.getOrDefault(entry.getKey(), 0);
            BigDecimal factor = MaterialDecay.incremental(BigDecimal.ONE, used, amount);
            for (Element e : Element.values()) effective.put(e, effective.get(e).add(
                factor.multiply(BigDecimal.valueOf(material.elements().get(e)), MaterialDecay.MATH_CONTEXT),
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
                                             Map<String, Integer> usage, Deadline deadline) {
        int perCraftInputs = actualInputCount(plan, 1);
        int cap = perCraftInputs <= 0 || perCraftInputs > ArcaneSolver.MAX_BATCH_INPUTS ? 0
            : ArcaneSolver.MAX_BATCH_INPUTS / perCraftInputs;
        cap = Math.min(cap, request.desiredCrafts());
        if (cap == 0) return 0;
        EvaluatedBatch first = evaluateBatch(plan, 1, request.recipe(), request.materials(), usage,
            request.excludedMaterials());
        if (!first.feasible() || !withinBudget(usage, first.afterUsage(), request.materialBudget())) return 0;
        // For a fixed nonnegative material vector c, each element per craft is
        // sum(a*c*(1.0618608 - .0054307*U - .00271535*c*n)).
        // It decreases linearly in n. Starting with feasible n=1, impurity and
        // excess upper bounds stay satisfied; target lower bounds/input budgets
        // give a feasible prefix. Binary search therefore cannot skip a hole.
        int best = 1;
        while (best < cap) {
            check(request, deadline);
            int n = best + (cap - best + 1) / 2;
            EvaluatedBatch batch = evaluateBatch(plan, n, request.recipe(), request.materials(), usage,
                request.excludedMaterials());
            if (batch.feasible()
                && withinBudget(usage, batch.afterUsage(), request.materialBudget())) best = n;
            else cap = n - 1;
        }
        return best;
    }
    private static boolean meetsRecipe(Map<Element, BigDecimal> effective, ScrollRecipe recipe, int crafts) {
        for (Element e : Element.values()) if (effective.get(e).compareTo(
            BigDecimal.valueOf((long) recipe.required().get(e) * crafts)) < 0) return false;
        return true;
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
    private static EvaluatedPlan betterPlan(EvaluatedPlan current, EvaluatedPlan candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        if (candidate.feasible() != current.feasible()) return candidate.feasible() ? candidate : current;
        if (candidate.complete() != current.complete()) return candidate.complete() ? candidate : current;
        if (candidate.plannedCrafts() != current.plannedCrafts()) {
            return candidate.plannedCrafts() > current.plannedCrafts() ? candidate : current;
        }
        int candidateM = involvedMaximumUsage(candidate), currentM = involvedMaximumUsage(current);
        if (candidateM != currentM) {
            return candidateM < currentM ? candidate : current;
        }
        long candidateInputs = materialInputs(candidate), currentInputs = materialInputs(current);
        if (candidateInputs != currentInputs) return candidateInputs < currentInputs ? candidate : current;
        if (candidate.impurity() != current.impurity()) return candidate.impurity() < current.impurity() ? candidate : current;
        return candidate.excess() < current.excess() ? candidate : current;
    }
    private static int involvedMaximumUsage(EvaluatedPlan plan) {
        return plan.batches().stream().flatMap(batch -> batch.plan().materials().keySet().stream())
            .mapToInt(name -> plan.afterUsage().getOrDefault(name, 0)).max().orElse(0);
    }
    private static long materialInputs(EvaluatedPlan plan) {
        return plan.batches().stream().mapToLong(batch -> (long) batch.plan().materialTotal() * batch.crafts()).sum();
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
    private static void check(ScrollPlanningRequest request, Deadline deadline) {
        if (request.cancellation().getAsBoolean() || Thread.currentThread().isInterrupted()) throw new PlanningCancelledException();
        if (deadline.clock().getAsLong() - deadline.started() >= deadline.budget()) throw new PlanningTimeoutException();
    }
    private record Deadline(java.util.function.LongSupplier clock, long started, long budget) {}
    private static final class SearchProgress {
        private EvaluatedPlan best;
        private CraftPlan closest;
        private String explanation = "";
        private final List<CraftPlan> discovered = new ArrayList<>();
        private SearchProgress(EvaluatedPlan best) { this.best = best; }
        private PlanningResult result(PlanningStatus status) {
            return new PlanningResult(status, best, discovered, closest, explanation);
        }
    }
    public static final class PlanningCancelledException extends RuntimeException {}
    private static final class PlanningTimeoutException extends RuntimeException {}
}
