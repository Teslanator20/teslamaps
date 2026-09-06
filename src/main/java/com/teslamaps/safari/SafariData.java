package com.teslamaps.safari;

import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

// The 37 Critter Safari critters and the four biomes they live in.
public class SafariData {

    public enum Biome {
        CAVERN("Cavern", "§6", "hypixel:cavern"),
        FOREST("Forest", "§2", "hypixel:forest"),
        HAUNTED("Haunted", "§5", "hypixel:haunted"),
        ICY("Icy", "§b", "hypixel:icy");

        public final String label;
        public final String color;
        private final String id;

        Biome(String label, String color, String id) {
            this.label = label;
            this.color = color;
            this.id = id;
        }
    }

    public enum Rarity {
        COMMON(0xFFFFFF), UNCOMMON(0x55FF55), RARE(0x5555FF), EPIC(0xAA00AA), LEGENDARY(0xFFAA00);

        public final int color;

        Rarity(int color) { this.color = color; }
    }

    public record Critter(String name, Biome biome, Rarity rarity) {}

    public static final List<Critter> ALL = List.of(
            new Critter("Cavernfish", Biome.CAVERN, Rarity.COMMON),
            new Critter("Flitter", Biome.CAVERN, Rarity.COMMON),
            new Critter("Shyworm", Biome.CAVERN, Rarity.COMMON),
            new Critter("Driftling", Biome.CAVERN, Rarity.UNCOMMON),
            new Critter("Chuckwalla", Biome.CAVERN, Rarity.RARE),
            new Critter("Rockmite", Biome.CAVERN, Rarity.RARE),
            new Critter("Scrappy", Biome.CAVERN, Rarity.RARE),
            new Critter("Snoozle", Biome.CAVERN, Rarity.RARE),
            new Critter("Gemzie", Biome.CAVERN, Rarity.EPIC),

            new Critter("Foxtrot", Biome.FOREST, Rarity.COMMON),
            new Critter("Bluebird", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Honeybug", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Treefrog", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Woodchucker", Biome.FOREST, Rarity.UNCOMMON),
            new Critter("Fluffling", Biome.FOREST, Rarity.RARE),
            new Critter("Hideonfloor", Biome.FOREST, Rarity.RARE),
            new Critter("Parakeet", Biome.FOREST, Rarity.RARE),
            new Critter("Macaw", Biome.FOREST, Rarity.LEGENDARY),

            new Critter("Areita", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Bloodbat", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Duplico", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gazer", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Litterbug", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Solsnatcher", Biome.HAUNTED, Rarity.UNCOMMON),
            new Critter("Gimmiegold", Biome.HAUNTED, Rarity.RARE),
            new Critter("Hideonwall", Biome.HAUNTED, Rarity.RARE),
            new Critter("Hideyho", Biome.HAUNTED, Rarity.RARE),
            new Critter("Doomspiral", Biome.HAUNTED, Rarity.LEGENDARY),

            new Critter("Strongarm", Biome.ICY, Rarity.COMMON),
            new Critter("Tepid", Biome.ICY, Rarity.COMMON),
            new Critter("Polaris", Biome.ICY, Rarity.UNCOMMON),
            new Critter("Shuddersquid", Biome.ICY, Rarity.UNCOMMON),
            new Critter("Billygoat", Biome.ICY, Rarity.RARE),
            new Critter("Mantis Shrimp", Biome.ICY, Rarity.RARE),
            new Critter("Nozzlenose", Biome.ICY, Rarity.RARE),
            new Critter("Troodon", Biome.ICY, Rarity.RARE),
            new Critter("Wumpa", Biome.ICY, Rarity.LEGENDARY));

    private static final Map<String, Critter> BY_NAME = new HashMap<>();
    static {
        for (Critter c : ALL) BY_NAME.put(c.name(), c);
    }

    public static Critter byName(String name) {
        return name == null ? null : BY_NAME.get(name.trim());
    }

    /** The eight Icy critters that have to be caught before Wumpa spawns. */
    public static List<Critter> wumpaRequirements() {
        List<Critter> out = new ArrayList<>();
        for (Critter c : ALL) {
            if (c.biome() == Biome.ICY && !c.name().equals("Wumpa")) out.add(c);
        }
        return out;
    }

    public static int countIn(Biome biome) {
        int n = 0;
        for (Critter c : ALL) if (c.biome() == biome) n++;
        return n;
    }

    /** The Safari itself, not the entrance in Torrhus Canyon. */
    public static boolean inSafari() {
        String a = SkyblockUtils.getArea().toLowerCase();
        return a.contains("safari") && !a.contains("entrance");
    }

    /** Hypixel ships the four zones as real biomes, so the client knows where it stands. */
    public static Biome biomeAt(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || pos == null) return null;
        String id = mc.level.getBiome(pos).getRegisteredName();
        for (Biome b : Biome.values()) {
            if (id.startsWith(b.id)) return b;
        }
        return null;
    }

    public static Biome currentBiome() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        return biomeAt(mc.player.blockPosition());
    }
}
