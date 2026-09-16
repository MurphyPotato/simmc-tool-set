package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.*;
import java.util.function.Consumer;

/**
 * Request-local, deficit-directed search. Eight-decimal fixed point is exact for
 * the supplied polynomial at integer M; no rounding is used for feasibility.
 * Counts remain per material, even for materials sharing an elemental vector.
 */
final class IncrementalCandidateSearch {
    static final long SCALE = 100_000_000L;
    private static final int ELEMENTS = Element.values().length;

    record Outcome(List<CraftPlan> candidates, boolean exhaustive, String explanation) {
        Outcome(List<CraftPlan> candidates, boolean exhaustive) { this(candidates, exhaustive, ""); }
    }

    static Outcome search(ScrollPlanningRequest request, Runnable checkpoint,
                          Consumer<CraftPlan> found, Consumer<CraftPlan> closest) {
        checkpoint.run();
        List<Material> materials = request.materials().stream()
            .filter(m -> !m.name().equals(request.recipe().mainMaterial()))
            .filter(m -> !request.excludedMaterials().contains(m.name()))
            .filter(m -> request.materialBudget().getOrDefault(m.name(), Integer.MAX_VALUE) > 0)
            .filter(m -> Arrays.stream(Element.values()).anyMatch(e ->
                request.recipe().required().get(e) > 0 && m.elements().get(e) > 0))
            .sorted(Comparator.comparing(Material::name)).toList();
        long[] required = new long[ELEMENTS];
        long[] upper = new long[ELEMENTS];
        int[] requirements = request.recipe().required().toArray();
        for (int e = 0; e < ELEMENTS; e++) {
            required[e] = requirements[e] * SCALE;
            upper[e] = Math.addExact(required[e], 24 * SCALE);
        }
        // Exact optimistic ratio bound: decay scales all elements of one
        // material equally. If every permitted source of target e has
        // e / impurity <= required[e] / 8, impurity < 8 cannot meet that target.
        for (int e = 0; e < ELEMENTS; e++) {
            if (requirements[e] == 0) continue;
            boolean blocked = true;
            for (Material material : materials) {
                checkpoint.run();
                int[] values = material.elements().toArray();
                long impurity = 0;
                for (int n = 0; n < ELEMENTS; n++) if (requirements[n] == 0) impurity += values[n];
                var possible = java.math.BigDecimal.valueOf(values[e]).multiply(java.math.BigDecimal.valueOf(8));
                var needed = java.math.BigDecimal.valueOf(requirements[e]).multiply(java.math.BigDecimal.valueOf(impurity));
                if (possible.compareTo(needed) > 0) { blocked = false; break; }
            }
            if (blocked) return new Outcome(List.of(), true,
                "当前材料表的元素/杂质比例上界无法同时满足 "
                    + Element.values()[e].label() + requirements[e]
                    + " 和总杂质 < 8（非负元素模型）。");
        }
        int size = materials.size();
        int[] initial = new int[size], limits = new int[size];
        long[][][] increments = new long[size][][];
        List<List<Integer>> byElement = new ArrayList<>();
        for (int e = 0; e < ELEMENTS; e++) byElement.add(new ArrayList<>());
        boolean exhaustive = true;
        for (int m = 0; m < size; m++) {
            checkpoint.run();
            Material material = materials.get(m);
            initial[m] = request.currentUsage().getOrDefault(material.name(), 0);
            // Beyond this point marginal gains are negative. Automatic search
            // does not exploit negative elements to cancel impurities.
            limits[m] = Math.min(319, Math.min(request.materialBudget().getOrDefault(material.name(), 319),
                Math.max(0, 196 - initial[m])));
            if (limits[m] < Math.min(319, request.materialBudget().getOrDefault(material.name(), 319))) exhaustive = false;
            increments[m] = new long[limits[m]][ELEMENTS];
            int[] unit = material.elements().toArray();
            for (int count = 0; count < limits[m]; count++) {
                long factor = 105_914_545L - 543_070L * (initial[m] + (long) count);
                for (int e = 0; e < ELEMENTS; e++) {
                    increments[m][count][e] = Math.multiplyExact(factor, unit[e]);
                }
            }
            for (int e = 0; e < ELEMENTS; e++) {
                if (required[e] > 0 && limits[m] > 0 && increments[m][0][e] > 0) byElement.get(e).add(m);
            }
        }
        for (int e = 0; e < ELEMENTS; e++) {
            if (required[e] > 0 && byElement.get(e).isEmpty()) return new Outcome(List.of(), exhaustive);
        }
        int width = request.budget().beamWidth();
        int candidateLimit = switch (request.budget()) {
            case FAST -> 12;
            case BALANCED -> 32;
            case EXTREME -> 64;
        };
        Comparator<State> order = Comparator.comparingLong((State s) -> s.shortage)
            .thenComparingInt(s -> s.maximumM).thenComparingLong(s -> s.impurity)
            .thenComparing(s -> s.key);
        List<State> frontier = List.of(new State(new int[size], new long[ELEMENTS], 0, 0,
            Long.MAX_VALUE, ""));
        List<CraftPlan> finals = new ArrayList<>();
        List<State> finalStates = new ArrayList<>();
        long closestShortage = Long.MAX_VALUE;
        for (int depth = 1; depth <= 319 && !frontier.isEmpty(); depth++) {
            checkpoint.run();
            PriorityQueue<State> next = new PriorityQueue<>(width, order.reversed());
            Map<String, List<State>> profiles = new HashMap<>();
            Set<String> seen = new HashSet<>();
            for (State state : frontier) {
                checkpoint.run();
                // Focus on the largest proportional deficit first, but retain
                // other missing-element branches (focus-only pruning is unsafe).
                List<Integer> targets = new ArrayList<>();
                for (int e = 0; e < ELEMENTS; e++) if (state.elements[e] < required[e]) targets.add(e);
                targets.sort(Comparator.comparingDouble((Integer e) ->
                    (required[e] - state.elements[e]) / (double) required[e]).reversed());
                Set<Integer> expansion = new LinkedHashSet<>();
                for (int e : targets) expansion.addAll(byElement.get(e));
                for (int m : expansion) {
                    checkpoint.run();
                    if (state.counts[m] >= limits[m]) continue;
                    int[] counts = state.counts.clone();
                    long[] elements = state.elements.clone();
                    long[] increment = increments[m][counts[m]++];
                    long impurity = 0, shortage = 0;
                    boolean valid = true;
                    for (int e = 0; e < ELEMENTS; e++) {
                        elements[e] = Math.addExact(elements[e], increment[e]);
                        if (required[e] == 0) impurity = Math.addExact(impurity, elements[e]);
                        else {
                            if (elements[e] > upper[e]) valid = false;
                            shortage += Math.max(0, required[e] - elements[e]);
                        }
                    }
                    if (!valid || impurity >= 8 * SCALE) continue;
                    String key = key(counts);
                    if (!seen.add(key)) continue;
                    int maximumM = Math.max(state.maximumM, initial[m] + counts[m]);
                    State added = new State(counts, elements, maximumM, impurity, shortage, key);
                    if (shortage == 0) {
                        CraftPlan plan = toPlan(added, materials);
                        finals.add(plan);
                        finalStates.add(added);
                        found.accept(plan); // Publish before any further checkpoint.
                        if (finals.size() >= candidateLimit) return finish(finals, finalStates,
                            materials, initial, limits, increments, required, upper, request.budget(), checkpoint, found);
                        continue; // An already feasible vector needs no extra inputs.
                    }
                    if (shortage < closestShortage) {
                        closestShortage = shortage;
                        closest.accept(toPlan(added, materials));
                    }
                    if (!canReach(added, required, increments, limits, byElement, 319 - depth)) continue;
                    // Equal elemental supplies are NOT interchangeable material
                    // identities. Keep bounded distinct count vectors per profile
                    // so dozens of equivalent fresh items cannot crowd out all
                    // complementary element combinations. This is heuristic
                    // pruning and therefore never a proof of infeasibility.
                    String profile = Arrays.toString(elements);
                    List<State> variants = profiles.computeIfAbsent(profile, ignored -> new ArrayList<>());
                    int variantLimit = request.budget() == SearchBudget.FAST ? 2 : 4;
                    if (variants.size() >= variantLimit) {
                        exhaustive = false;
                        State worst = variants.stream().max(order).orElseThrow();
                        if (order.compare(added, worst) >= 0) continue;
                        next.remove(worst);
                        variants.remove(worst);
                    }
                    if (next.size() >= width) {
                        exhaustive = false;
                        if (order.compare(added, next.peek()) >= 0) continue;
                        State removed = next.poll();
                        profiles.get(Arrays.toString(removed.elements)).remove(removed);
                    }
                    next.add(added);
                    variants.add(added);
                }
            }
            frontier = new ArrayList<>(next);
            frontier.sort(order); // Cached scalar ranks; no formula calls in sort.
            if (!finals.isEmpty()) {
                // Complete the current depth for alternatives, then reserve
                // the remaining budget for batch planning at evolving M.
                return finish(finals, finalStates, materials, initial, limits, increments,
                    required, upper, request.budget(), checkpoint, found);
            }
        }
        return new Outcome(List.copyOf(finals), exhaustive);
    }

    private static Outcome finish(List<CraftPlan> plans, List<State> states, List<Material> materials,
                                  int[] initial, int[] limits, long[][][] increments,
                                  long[] required, long[] upper, SearchBudget budget,
                                  Runnable checkpoint, Consumer<CraftPlan> found) {
        if (budget != SearchBudget.EXTREME) return new Outcome(List.copyOf(plans), false);
        // A bounded local swap pass explores alternative low-M ingredients
        // without reopening the entire count-combination tree.
        Set<String> seen = new HashSet<>();
        plans.forEach(plan -> seen.add(plan.id()));
        int cap = plans.size() + 32;
        for (State state : states.subList(0, Math.min(8, states.size()))) {
            for (int removed = 0; removed < materials.size(); removed++) {
                if (state.counts[removed] == 0) continue;
                for (int added = 0; added < materials.size(); added++) {
                    checkpoint.run();
                    if (added == removed || state.counts[added] >= limits[added]) continue;
                    int[] counts = state.counts.clone();
                    long[] subtract = increments[removed][--counts[removed]];
                    long[] add = increments[added][counts[added]++];
                    long[] elements = state.elements.clone();
                    boolean valid = true;
                    long impurity = 0;
                    for (int e = 0; e < ELEMENTS; e++) {
                        elements[e] = Math.addExact(elements[e] - subtract[e], add[e]);
                        if (required[e] == 0) impurity += elements[e];
                        else if (elements[e] < required[e] || elements[e] > upper[e]) valid = false;
                    }
                    if (!valid || impurity >= 8 * SCALE) continue;
                    String key = key(counts);
                    if (!seen.add(key)) continue;
                    int maximumM = 0;
                    for (int m = 0; m < counts.length; m++) if (counts[m] > 0) {
                        maximumM = Math.max(maximumM, initial[m] + counts[m]);
                    }
                    CraftPlan plan = toPlan(new State(counts, elements, maximumM, impurity, 0, key), materials);
                    plans.add(plan);
                    found.accept(plan);
                    if (plans.size() >= cap) return new Outcome(List.copyOf(plans), false);
                }
            }
        }
        return new Outcome(List.copyOf(plans), false);
    }

    private static boolean canReach(State state, long[] required, long[][][] increments,
                                    int[] limits, List<List<Integer>> byElement, int slots) {
        for (int e = 0; e < ELEMENTS; e++) {
            long missing = required[e] - state.elements[e];
            if (missing <= 0) continue;
            long largest = 0;
            for (int m : byElement.get(e)) if (state.counts[m] < limits[m]) {
                largest = Math.max(largest, increments[m][state.counts[m]][e]);
            }
            // Optimistic upper bound: pretend every remaining slot supplies
            // the best current marginal gain, ignoring impurities/capacity.
            if (largest == 0 || (missing - 1) / largest >= slots) return false;
        }
        return true;
    }

    private static CraftPlan toPlan(State state, List<Material> materials) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ElementAmounts supplied = ElementAmounts.zero();
        int total = 0, maximum = 0;
        for (int m = 0; m < materials.size(); m++) {
            int count = state.counts[m];
            if (count == 0) continue;
            Material material = materials.get(m);
            counts.put(material.name(), count);
            int[] scaled = material.elements().toArray();
            for (int e = 0; e < ELEMENTS; e++) scaled[e] = Math.multiplyExact(scaled[e], count);
            supplied = supplied.plus(new ElementAmounts(scaled));
            total += count;
            maximum = Math.max(maximum, count);
        }
        return new CraftPlan(state.key, counts, supplied, (int) (state.impurity / SCALE), 0,
            total, maximum, counts.size(), total * 100000L + state.maximumM * 100L);
    }

    private static String key(int[] counts) {
        StringBuilder key = new StringBuilder();
        for (int m = 0; m < counts.length; m++) if (counts[m] > 0) key.append(m).append(':').append(counts[m]).append(';');
        return key.toString();
    }

    private record State(int[] counts, long[] elements, int maximumM, long impurity, long shortage, String key) {}
}
