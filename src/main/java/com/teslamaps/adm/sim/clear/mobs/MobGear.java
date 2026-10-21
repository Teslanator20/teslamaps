// Ported from ADM's p3sim/clear/mobs/MobGear.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.SimItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MobGear {
    private MobGear() {}

    private static final class Piece {
        final EquipmentSlot slot;
        final Item item;
        final String name;
        final Integer dye;
        final String head;
        final boolean glint;

        Piece(EquipmentSlot slot, Item item, String name, Integer dye, String head, boolean glint) {
            this.slot = slot; this.item = item; this.name = name; this.dye = dye; this.head = head; this.glint = glint;
        }

        Piece(EquipmentSlot slot, Item item, String name) { this(slot, item, name, null, null, false); }
    }

    private static List<Piece> leather(String prefix, int rgb, boolean glint, int chestRgb, int legsRgb, int feetRgb) {
        return List.of(
                new Piece(EquipmentSlot.HEAD, Items.LEATHER_HELMET, prefix + " Helmet", rgb, null, glint),
                new Piece(EquipmentSlot.CHEST, Items.LEATHER_CHESTPLATE, prefix + " Chestplate", chestRgb, null, glint),
                new Piece(EquipmentSlot.LEGS, Items.LEATHER_LEGGINGS, prefix + " Leggings", legsRgb, null, glint),
                new Piece(EquipmentSlot.FEET, Items.LEATHER_BOOTS, prefix + " Boots", feetRgb, null, glint)
        );
    }

    private static List<Piece> leather(String prefix, int rgb, boolean glint, int chestRgb, int legsRgb) { return leather(prefix, rgb, glint, chestRgb, legsRgb, rgb); }

    private static List<Piece> leather(String prefix, int rgb, boolean glint, int chestRgb) { return leather(prefix, rgb, glint, chestRgb, chestRgb, rgb); }

    private static List<Piece> leather(String prefix, int rgb, boolean glint) { return leather(prefix, rgb, glint, rgb, rgb, rgb); }

    private static List<Piece> leather(String prefix, int rgb) { return leather(prefix, rgb, false); }

    private static List<Piece> gold(String prefix) {
        return List.of(
                new Piece(EquipmentSlot.HEAD, Items.GOLDEN_HELMET, prefix + " Helmet"), new Piece(EquipmentSlot.CHEST, Items.GOLDEN_CHESTPLATE, prefix + " Chestplate"),
                new Piece(EquipmentSlot.LEGS, Items.GOLDEN_LEGGINGS, prefix + " Leggings"), new Piece(EquipmentSlot.FEET, Items.GOLDEN_BOOTS, prefix + " Boots")
        );
    }

    private static Piece hand(Item item, String name, boolean glint) { return new Piece(EquipmentSlot.MAINHAND, item, name, null, null, glint); }

    private static Piece hand(Item item, String name) { return hand(item, name, false); }

    private static Piece hand(Item item) { return hand(item, null, false); }

    @SafeVarargs
    private static List<Piece> cat(List<Piece>... parts) {
        List<Piece> out = new ArrayList<>();
        for (List<Piece> p : parts) out.addAll(p);
        return out;
    }

    private static List<Piece> drop1(List<Piece> l) { return l.subList(1, l.size()); }

    private static final Map<String, List<Piece>> GEAR = new LinkedHashMap<>();

    static {
        GEAR.put("zombie_soldier", cat(leather("Zombie Soldier", 0xd07f00), List.of(hand(Items.IRON_SWORD, "Zombie Soldier Cutlass"))));
        GEAR.put("skeleton_master", cat(leather("Skeleton Master", 0xff6b0b), List.of(hand(Items.BOW))));
        GEAR.put("skeleton_soldier", cat(leather("Skeleton Soldier", 0xffbc0b), List.of(hand(Items.BOW))));
        GEAR.put("super_archer", cat(leather("Bouncy", 0xadff2f), List.of(hand(Items.BOW))));
        GEAR.put("super_tank_zombie", cat(leather("Super Heavy", 0xe6e6e6, false, 0x5a6464), List.of(hand(Items.STONE_SWORD, "Earthen Blade"))));
        GEAR.put("zombie_knight", gold("Zombie Knight"));
        GEAR.put("zombie_commander", cat(leather("Zombie Commander", 0xd51230), List.of(hand(Items.FISHING_ROD, "Zombie Commander Whip"))));
        GEAR.put("zombie_lord", cat(List.of(new Piece(EquipmentSlot.HEAD, Items.GOLDEN_HELMET, "Zombie Lord Helmet")), drop1(leather("Zombie Lord", 0x9b01c1)), List.of(hand(Items.DIAMOND_SWORD))));
        GEAR.put("skeleton_lord", cat(List.of(new Piece(EquipmentSlot.HEAD, Items.GOLDEN_HELMET, "Skeleton Lord Helmet")), drop1(leather("Skeleton Lord", 0x268105)), List.of(hand(Items.BOW))));
        GEAR.put("sniper", List.of(new Piece(EquipmentSlot.HEAD, Items.PLAYER_HEAD, "Sniper Helmet", null, "b18c071f080dba50a62a6263ff724edc157ce4fb4883cceff2491d5bbde830c1", false), hand(Items.BOW)));
        GEAR.put("crypt_dreadlord", List.of(hand(Items.IRON_SWORD)));
        GEAR.put("crypt_souleater", List.of(hand(Items.BOW)));
        GEAR.put("crypt_undead", List.of(hand(Items.BONE)));
        GEAR.put("withermancer", List.of(hand(Items.STONE_SWORD)));
        GEAR.put("angry_archaeologist", List.of(
                new Piece(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, "Perfect Helmet - Tier XII", null, null, true), new Piece(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, "Perfect Chestplate - Tier XII", null, null, true),
                new Piece(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, "Perfect Leggings - Tier XII", null, null, true), new Piece(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, "Perfect Boots - Tier XII", null, null, true),
                hand(Items.DIAMOND_SWORD, null, true)
        ));
        GEAR.put("frozen_adventurer", cat(List.of(new Piece(EquipmentSlot.HEAD, Items.PLAYER_HEAD, "Frozen Blaze Helmet", null, "70310b55eed958fee8ffaaf718467167deb1a7c9046d67b25887b596925636bb", false)),
                drop1(leather("Frozen Blaze", 0xa0daef, true)), List.of(hand(Items.STICK, "Ice Spray Wand", true))));
        GEAR.put("king_midas", cat(gold("Golden"), List.of(hand(Items.GOLDEN_SWORD))));
    }

    public static class LostSet {
        public final String word;
        public final String helmet;
        public final int rgb;
        public final int feetRgb;
        public final String skin;
        public final int weight;

        public LostSet(String word, String helmet, int rgb, int feetRgb, String skin, int weight) {
            this.word = word; this.helmet = helmet; this.rgb = rgb; this.feetRgb = feetRgb; this.skin = skin; this.weight = weight;
        }
    }

    public static final List<LostSet> LOST_SETS = List.of(
            new LostSet("Holy", "99cc8ba26793218f6f6491947a1837fd52878701f33f76bbe2883a45aba8ab8f", 0x47d147, 0x47d147, "3a26cf66af42a48f6be52a0a48a8eb598f3a23639b7a5a8ee171c13999ee94c9", 277),
            new LostSet("Young", "a0e81ed07dfb0244d56f4d5f2b37553ec026fb4796f0fb0c57a8eb264983e1e0", 0xdde4f0, 0xdde4f0, "36a0383a527803d99b662ad18bcac78c119250ebbfb14475b4eb4d4a662796b4", 159),
            new LostSet("Unstable", "605fc4e2b68c45059afef3999e9a95aa3d8943597a7894e569397d6cf5d1672a", 0xb212e3, 0xb212e3, "e381a1875efeb2a260d357984c3e75a7dfbc7a22b582bfe9990421cbb445acc", 119),
            new LostSet("Superior", "1e0329f42192ee7c1a0c704f82dbbbe7c00fbfa3f020c1067a208630b991b988", 0xf2df11, 0xf25d18, "36a0383a527803d99b662ad18bcac78c119250ebbfb14475b4eb4d4a662796b4", 40)
    );

    public static LostSet lostSet(UUID uuid) {
        int total = 0;
        for (LostSet s : LOST_SETS) total += s.weight;
        double pick = Math.floorMod(uuid.getLeastSignificantBits() ^ uuid.getMostSignificantBits(), 1_000_003L) / 1_000_003.0 * total;
        for (LostSet s : LOST_SETS) { pick -= s.weight; if (pick < 0) return s; }
        return LOST_SETS.get(LOST_SETS.size() - 1);
    }

    private static List<Piece> lostGear(UUID uuid) {
        LostSet s = lostSet(uuid);
        return cat(List.of(new Piece(EquipmentSlot.HEAD, Items.PLAYER_HEAD, s.word + " Dragon Helmet", null, s.helmet, false)),
                drop1(leather(s.word + " Dragon", s.rgb, true, s.rgb, s.rgb, s.feetRgb)), List.of(hand(Items.DIAMOND_SWORD, "Aspect of the Dragons", true)));
    }

    public static boolean has(String archId) { return archId.equals("lost_adventurer") || GEAR.containsKey(archId); }

    public static void dress(LivingEntity body, String archId, UUID uuid) {
        List<Piece> pieces;
        switch (archId) {
            case "lost_adventurer" -> pieces = lostGear(uuid);
            case "crypt_lurker" -> pieces = List.of(hand(Math.floorMod(uuid.getLeastSignificantBits(), 100L) < 28 ? Items.ROTTEN_FLESH : Items.BONE));
            default -> {
                pieces = GEAR.get(archId);
                if (pieces == null) return;
            }
        }
        for (Piece p : pieces) body.setItemSlot(p.slot, stack(p));
    }

    private static ItemStack stack(Piece p) {
        ItemStack s = p.head != null ? SimItems.head(texValue(p.head), p.name != null ? p.name : "Player Head") : new ItemStack(p.item);
        if (p.head == null && p.name != null) s.set(DataComponents.CUSTOM_NAME, Component.literal(p.name).withStyle(it -> it.withItalic(false)));
        if (p.dye != null) s.set(DataComponents.DYED_COLOR, new DyedItemColor(p.dye));
        if (p.glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private static String texValue(String hash) {
        return Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}").getBytes(StandardCharsets.UTF_8));
    }
}
