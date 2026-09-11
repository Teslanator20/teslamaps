package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Heart of the Mountain / Forest state, ported from skyblock-pv's SkillTree. The tier comes from
 * the tree's own xp table, never from the core node, and the nodes come from the loadout slot the
 * player actually has selected.
 */
public class SkillTreeInfo {

    public enum Type {
        MINING("mining", "mountain", "core_of_the_mountain",
                new long[]{0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 557_000, 847_000, 1_247_000}),
        FORAGING("foraging", "forest", "center_of_the_forest",
                new long[]{0, 3_000, 12_000, 37_000, 97_000, 197_000, 347_000, 547_000});

        public final String skill;
        public final String tree;
        public final String coreNode;
        /** Cumulative xp needed for level index + 1. */
        public final long[] levelToXp;

        Type(String skill, String tree, String coreNode, long[] levelToXp) {
            this.skill = skill;
            this.tree = tree;
            this.coreNode = coreNode;
            this.levelToXp = levelToXp;
        }
    }

    public final Type type;
    public final int slot;
    public final long xp;
    public final int level;
    public final int maxLevel;
    public final long xpIntoLevel;
    public final long xpForLevel;
    public final int tokensSpent;
    public final Map<String, Integer> nodes = new HashMap<>();
    public final Set<String> disabled = new HashSet<>();

    public SkillTreeInfo(JsonObject member, Type type) {
        this.type = type;
        JsonObject tree = object(member, "skill_tree");

        int selected = asInt(get(object(tree, "selected_skill_tree_slot"), type.skill));
        this.slot = selected <= 0 ? 1 : selected;
        String suffix = slot > 1 ? "_" + slot : "";

        JsonObject slotNodes = object(object(tree, "nodes"), type.skill + suffix);
        if (slotNodes != null) {
            for (Map.Entry<String, JsonElement> entry : slotNodes.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("toggle_")) {
                    if (entry.getValue().isJsonPrimitive() && !entry.getValue().getAsBoolean()) {
                        disabled.add(key.substring("toggle_".length()));
                    }
                } else {
                    nodes.put(key, asInt(entry.getValue()));
                }
            }
        }

        this.xp = asLong(get(object(tree, "experience"), type.skill));
        this.tokensSpent = asInt(get(object(tree, "tokens_spent"), type.tree + suffix));

        this.maxLevel = type.levelToXp.length;
        int found = 1;
        for (int i = 0; i < type.levelToXp.length; i++) {
            if (xp >= type.levelToXp[i]) found = i + 1;
        }
        this.level = found;

        long base = type.levelToXp[level - 1];
        this.xpIntoLevel = xp - base;
        this.xpForLevel = level >= maxLevel ? 0 : type.levelToXp[level] - base;
    }

    public boolean isMaxed() {
        return level >= maxLevel;
    }

    public double progress() {
        if (isMaxed() || xpForLevel <= 0) return 1;
        return Math.max(0, Math.min(1, (double) xpIntoLevel / xpForLevel));
    }

    /** Spent currency of this loadout, e.g. whispers.forest.2.spent or powder_spent_mithril_2. */
    public long spent(JsonObject core, String currency) {
        if (core == null) return 0;
        if (type == Type.FORAGING) {
            JsonObject whispers = object(object(core, "whispers"), currency);
            return asLong(get(object(whispers, String.valueOf(slot)), "spent"));
        }
        String key = "powder_spent_" + currency + (slot > 1 ? "_" + slot : "");
        return asLong(get(core, key));
    }

    public long total(JsonObject core, String currency) {
        if (core == null) return 0;
        if (type == Type.FORAGING) {
            return asLong(get(object(object(core, "whispers"), currency), "total"));
        }
        return asLong(get(core, "powder_" + currency));
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent != null && parent.has(key) && parent.get(key).isJsonObject()
                ? parent.getAsJsonObject(key) : null;
    }

    private static JsonElement get(JsonObject parent, String key) {
        return parent != null && parent.has(key) ? parent.get(key) : null;
    }

    private static int asInt(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0;
        return element.getAsInt();
    }

    private static long asLong(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return 0;
        return element.getAsLong();
    }
}
