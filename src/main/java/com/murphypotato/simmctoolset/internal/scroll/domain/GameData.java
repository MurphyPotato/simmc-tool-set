package com.murphypotato.simmctoolset.internal.scroll.domain;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Collator;
import java.util.Locale;

public record GameData(List<ScrollRecipe> recipes, List<Material> materials, Map<String, Integer> nameSortRanks) {
    public static final String RESOURCE = "/assets/simmc_tool_set/scroll-data/game-data-v1.1.1.json";
    public static final String SCHEMA = "simmc-arcane-scroll-calculator:v1.1.1/game-data";

    public GameData {
        recipes = List.copyOf(recipes);
        materials = List.copyOf(materials);
        nameSortRanks = Map.copyOf(nameSortRanks);
        validate(recipes, materials);
    }

    public static GameData load() {
        try (InputStream stream = GameData.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("缺少内嵌数据：" + RESOURCE);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!SCHEMA.equals(root.get("schema").getAsString())) throw new IllegalArgumentException("游戏数据 schema 不匹配");
            validateElements(root.getAsJsonArray("elements"));
            Map<String, Integer> nameSortRanks = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("nameSortRanks").entrySet()) {
                nameSortRanks.put(entry.getKey(), entry.getValue().getAsInt());
            }
            List<ScrollRecipe> recipes = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("recipes")) {
                JsonObject recipe = element.getAsJsonObject();
                recipes.add(new ScrollRecipe(
                    recipe.get("name").getAsString(),
                    recipe.get("mainMaterial").getAsString(),
                    amounts(recipe.getAsJsonObject("required"))
                ));
            }
            List<Material> materials = new ArrayList<>();
            for (JsonElement element : root.getAsJsonArray("materials")) {
                JsonObject material = element.getAsJsonObject();
                materials.add(new Material(
                    material.get("name").getAsString(),
                    amounts(material.getAsJsonObject("elements")),
                    material.get("sortRank").getAsInt()
                ));
            }
            return new GameData(recipes, materials, nameSortRanks);
        } catch (IOException error) {
            throw new IllegalStateException("读取内嵌数据失败", error);
        }
    }

    public ScrollRecipe recipe(String name) {
        return recipes.stream().filter(recipe -> recipe.name().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("未知卷轴：" + name));
    }

    public Map<String, Integer> materialSortRanks() {
        return nameSortRanks;
    }

    public int compareNames(String left, String right) {
        Integer leftRank = nameSortRanks.get(left);
        Integer rightRank = nameSortRanks.get(right);
        if (leftRank != null && rightRank != null) return Integer.compare(leftRank, rightRank);
        return Collator.getInstance(Locale.CHINA).compare(left, right);
    }

    private static ElementAmounts amounts(JsonObject object) {
        int[] values = new int[Element.values().length];
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            values[Element.fromLabel(entry.getKey()).ordinal()] = entry.getValue().getAsInt();
        }
        return new ElementAmounts(values);
    }

    private static void validateElements(JsonArray labels) {
        if (labels.size() != Element.values().length) throw new IllegalArgumentException("元素表数量不正确");
        for (int index = 0; index < labels.size(); index++) {
            if (!Element.values()[index].label().equals(labels.get(index).getAsString())) {
                throw new IllegalArgumentException("元素表顺序不正确");
            }
        }
    }

    private static void validate(List<ScrollRecipe> recipes, List<Material> materials) {
        if (recipes.isEmpty() || materials.isEmpty()) throw new IllegalArgumentException("游戏数据不能为空");
        Set<String> recipeNames = new HashSet<>();
        for (ScrollRecipe recipe : recipes) {
            if (!recipeNames.add(recipe.name())) throw new IllegalArgumentException("卷轴名称重复：" + recipe.name());
        }
        Set<String> materialNames = new HashSet<>();
        Set<Integer> sortRanks = new HashSet<>();
        for (Material material : materials) {
            if (!materialNames.add(material.name())) throw new IllegalArgumentException("材料名称重复：" + material.name());
            if (!sortRanks.add(material.sortRank())) throw new IllegalArgumentException("材料排序键重复：" + material.sortRank());
        }
    }
}
