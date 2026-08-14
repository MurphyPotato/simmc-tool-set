package com.murphypotato.simmctoolset.internal.scroll.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public final class ArcaneSolver {
    public static final int DEFAULT_IMPURITY_LIMIT = 8;
    public static final int DEFAULT_MAX_PLANS = 10;
    public static final int DEFAULT_REPEAT_THRESHOLD = 64;
    private static final int STATE_VARIANTS = 3;
    private static final int TARGET_EXCESS_CAP = 24;
    private static final int BEAM_WIDTH = 1400;

    private ArcaneSolver() {
    }

    public static List<CraftPlan> findCraftPlans(ScrollRecipe recipe, List<Material> materials) {
        return findCraftPlans(recipe, materials, DEFAULT_IMPURITY_LIMIT, DEFAULT_MAX_PLANS, () -> false);
    }

    public static List<CraftPlan> findCraftPlans(
        ScrollRecipe recipe,
        List<Material> materialList,
        int impurityLimit,
        int maxPlans,
        BooleanSupplier cancelled
    ) {
        if (impurityLimit <= 0 || maxPlans <= 0) return List.of();
        List<Element> targetElements = Arrays.stream(Element.values())
            .filter(element -> recipe.required().get(element) > 0).toList();
        List<Element> nonTargetElements = Arrays.stream(Element.values())
            .filter(element -> recipe.required().get(element) <= 0).toList();
        int[] caps = new int[Element.values().length];
        for (Element element : Element.values()) {
            caps[element.ordinal()] = recipe.required().get(element) > 0
                ? recipe.required().get(element) + TARGET_EXCESS_CAP
                : impurityLimit - 1;
        }

        List<Candidate> candidates = buildCandidates(materialList, targetElements, nonTargetElements, impurityLimit);
        Draft initial = new Draft(new int[candidates.size()], new int[Element.values().length], 0, 0, 0);
        List<Draft> frontier = new ArrayList<>(List.of(initial));
        Map<String, List<Draft>> seen = new HashMap<>();
        Map<String, CraftPlan> finals = new LinkedHashMap<>();
        int requiredTotal = Arrays.stream(Element.values()).mapToInt(recipe.required()::get).sum();
        int maxDepth = Math.min(90, Math.max(24, requiredTotal));
        addDraft(seen, stateKey(initial, targetElements, recipe), initial, recipe, candidates);

        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            checkCancelled(cancelled);
            List<Draft> next = new ArrayList<>();
            for (Draft draft : frontier) {
                checkCancelled(cancelled);
                for (int candidateIndex = draft.lastIndex; candidateIndex < candidates.size(); candidateIndex++) {
                    Candidate candidate = candidates.get(candidateIndex);
                    Draft added = addOneMaterial(
                        draft, candidate, candidateIndex, caps, targetElements, nonTargetElements, impurityLimit
                    );
                    if (added == null) continue;
                    if (!addDraft(seen, stateKey(added, targetElements, recipe), added, recipe, candidates)) continue;
                    if (isSatisfied(recipe, added.supplied)) {
                        CraftPlan plan = toCraftPlan(added, recipe, candidates);
                        finals.put(plan.id(), plan);
                    } else {
                        next.add(added);
                    }
                }
            }
            next.sort((left, right) -> compareDrafts(left, right, recipe, candidates));
            frontier = new ArrayList<>(next.subList(0, Math.min(BEAM_WIDTH, next.size())));

            List<CraftPlan> sortedFinals = finals.values().stream().sorted(PLAN_COMPARATOR).toList();
            if (sortedFinals.size() >= maxPlans && depth + 1 >= sortedFinals.get(maxPlans - 1).materialTotal() + 4) {
                return List.copyOf(sortedFinals.subList(0, maxPlans));
            }
        }
        return finals.values().stream().sorted(PLAN_COMPARATOR).limit(maxPlans).toList();
    }

    public static List<RotationBatch> makeRotationSchedule(List<CraftPlan> plans, int quantity, int repeatThreshold) {
        if (quantity <= 0 || plans.isEmpty() || repeatThreshold <= 0) return List.of();
        int usableCount = Math.min(plans.size(), Math.max(1, (quantity + repeatThreshold - 1) / repeatThreshold));
        List<CraftPlan> usable = plans.subList(0, usableCount);
        List<RotationBatch> batches = new ArrayList<>();
        int remaining = quantity;
        int index = 0;
        while (remaining > 0) {
            int crafts = Math.min(repeatThreshold, remaining);
            batches.add(new RotationBatch(usable.get(index % usable.size()), crafts));
            remaining -= crafts;
            index++;
        }
        return List.copyOf(batches);
    }

    public static Map<String, Integer> scalePlanMaterials(
        CraftPlan plan,
        int quantity,
        boolean includeMainMaterial,
        String mainMaterial
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        plan.materials().forEach((name, count) -> result.put(name, count * quantity));
        if (includeMainMaterial) result.merge(mainMaterial, quantity, Integer::sum);
        return result;
    }

    private static List<Candidate> buildCandidates(
        List<Material> materialList,
        List<Element> targetElements,
        List<Element> nonTargetElements,
        int impurityLimit
    ) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Integer> vectors = new HashMap<>();
        for (int index = 0; index < materialList.size(); index++) {
            Material material = materialList.get(index);
            int targetValue = targetElements.stream().mapToInt(material.elements()::get).sum();
            int impurity = nonTargetElements.stream().mapToInt(material.elements()::get).sum();
            if (targetValue <= 0 || impurity >= impurityLimit) continue;
            String vector = Arrays.stream(Element.values())
                .mapToInt(material.elements()::get).mapToObj(Integer::toString).collect(Collectors.joining(","));
            int count = vectors.getOrDefault(vector, 0);
            vectors.put(vector, count + 1);
            if (count >= 2) continue;
            candidates.add(new Candidate(index, material, impurity, targetValue));
        }
        candidates.sort((left, right) -> {
            double leftRatio = left.targetValue / (double) Math.max(1, left.impurity);
            double rightRatio = right.targetValue / (double) Math.max(1, right.impurity);
            int ratio = Double.compare(rightRatio, leftRatio);
            if (ratio != 0) return ratio;
            int impurity = Integer.compare(left.impurity, right.impurity);
            if (impurity != 0) return impurity;
            return Integer.compare(left.material.sortRank(), right.material.sortRank());
        });

        List<Candidate> useful = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            boolean keep = index < 14 || (candidate.impurity == 0 && index < 22);
            if (!keep) {
                for (Element element : targetElements) {
                    int found = 0;
                    for (Candidate item : candidates) {
                        if (item.material.elements().get(element) <= 0) continue;
                        if (item == candidate) keep = true;
                        if (++found >= 6) break;
                    }
                    if (keep) break;
                }
            }
            if (keep) useful.add(candidate);
            if (useful.size() >= 20) break;
        }
        return List.copyOf(useful);
    }

    private static boolean addDraft(
        Map<String, List<Draft>> seen,
        String key,
        Draft draft,
        ScrollRecipe recipe,
        List<Candidate> candidates
    ) {
        List<Draft> bucket = seen.computeIfAbsent(key, ignored -> new ArrayList<>());
        String signature = countSignature(draft.counts);
        if (bucket.stream().anyMatch(item -> countSignature(item.counts).equals(signature))) return false;
        bucket.add(draft);
        bucket.sort((left, right) -> compareDrafts(left, right, recipe, candidates));
        if (bucket.size() > STATE_VARIANTS) bucket.subList(STATE_VARIANTS, bucket.size()).clear();
        return bucket.contains(draft);
    }

    private static String stateKey(Draft draft, List<Element> targetElements, ScrollRecipe recipe) {
        String targets = targetElements.stream()
            .map(element -> Integer.toString(Math.min(recipe.required().get(element), draft.supplied[element.ordinal()])))
            .collect(Collectors.joining(","));
        return draft.lastIndex + "|" + targets + "|i" + draft.impurityTotal;
    }

    private static Draft addOneMaterial(
        Draft draft,
        Candidate candidate,
        int candidateIndex,
        int[] caps,
        List<Element> targetElements,
        List<Element> nonTargetElements,
        int impurityLimit
    ) {
        int[] supplied = draft.supplied.clone();
        for (Element element : Element.values()) supplied[element.ordinal()] += candidate.material.elements().get(element);
        if (targetElements.stream().anyMatch(element -> supplied[element.ordinal()] > caps[element.ordinal()])) return null;
        int impurityTotal = nonTargetElements.stream().mapToInt(element -> supplied[element.ordinal()]).sum();
        if (impurityTotal >= impurityLimit) return null;
        int[] counts = draft.counts.clone();
        counts[candidateIndex]++;
        return new Draft(counts, supplied, draft.materialTotal + 1, impurityTotal, candidateIndex);
    }

    private static boolean isSatisfied(ScrollRecipe recipe, int[] supplied) {
        return Arrays.stream(Element.values())
            .allMatch(element -> supplied[element.ordinal()] >= recipe.required().get(element));
    }

    private static CraftPlan toCraftPlan(Draft draft, ScrollRecipe recipe, List<Candidate> candidates) {
        Map<String, Integer> materials = new LinkedHashMap<>();
        for (int index = 0; index < draft.counts.length; index++) {
            if (draft.counts[index] > 0) materials.put(candidates.get(index).material.name(), draft.counts[index]);
        }
        int targetExcessTotal = Arrays.stream(Element.values())
            .mapToInt(element -> Math.max(0, draft.supplied[element.ordinal()] - recipe.required().get(element))).sum();
        int maxRepeat = materials.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int distinctMaterials = materials.size();
        long score = draft.materialTotal * 100000L
            + maxRepeat * 1800L
            + draft.impurityTotal * 700L
            + targetExcessTotal * 120L
            - distinctMaterials * 40L;
        return new CraftPlan(
            countSignature(draft.counts), materials, new ElementAmounts(draft.supplied), draft.impurityTotal,
            targetExcessTotal, draft.materialTotal, maxRepeat, distinctMaterials, score
        );
    }

    private static int compareDrafts(Draft left, Draft right, ScrollRecipe recipe, List<Candidate> candidates) {
        int rank = Long.compare(draftRank(left, recipe, candidates), draftRank(right, recipe, candidates));
        return rank != 0 ? rank : countSignature(left.counts).compareTo(countSignature(right.counts));
    }

    private static long draftRank(Draft draft, ScrollRecipe recipe, List<Candidate> candidates) {
        int shortage = Arrays.stream(Element.values())
            .mapToInt(element -> Math.max(0, recipe.required().get(element) - draft.supplied[element.ordinal()])).sum();
        int maxRepeat = Arrays.stream(draft.counts).filter(count -> count > 0).max().orElse(0);
        int distinct = (int) Arrays.stream(draft.counts).filter(count -> count > 0).count();
        int targetExcess = Arrays.stream(Element.values())
            .mapToInt(element -> Math.max(0, draft.supplied[element.ordinal()] - recipe.required().get(element))).sum();
        int usefulTargetValue = 0;
        for (int index = 0; index < draft.counts.length; index++) {
            usefulTargetValue += draft.counts[index] * candidates.get(index).targetValue;
        }
        return shortage * 5000L
            + draft.materialTotal * 800L
            + maxRepeat * 120L
            + draft.impurityTotal * 70L
            + targetExcess * 20L
            - distinct * 15L
            - usefulTargetValue;
    }

    private static String countSignature(int[] counts) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0) parts.add(index + ":" + counts[index]);
        }
        return String.join("|", parts);
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new CalculationCancelledException();
    }

    private static final Comparator<CraftPlan> PLAN_COMPARATOR = Comparator
        .comparingLong(CraftPlan::score)
        .thenComparing(CraftPlan::id);

    private record Candidate(int index, Material material, int impurity, int targetValue) {
    }

    private record Draft(int[] counts, int[] supplied, int materialTotal, int impurityTotal, int lastIndex) {
    }

    public static final class CalculationCancelledException extends RuntimeException {
    }
}
