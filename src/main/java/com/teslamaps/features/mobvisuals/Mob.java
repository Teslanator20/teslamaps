package com.teslamaps.features.mobvisuals;

import com.teslamaps.config.TeslaMapsConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * Every mob the visuals cover, with its label colour and the colour used for glow/tracer.
 * Catalog ported from GanKura's MobVisual.
 */
public enum Mob {

    // --- THE_END ---
    GOLEM(MobArea.THE_END, "§6End Stone Protector", 0xFFAA00),
    DRAGON(MobArea.THE_END, "§dDragon", 0xFF55FF),

    // --- SPIDERS_DEN ---
    BROODMOTHER(MobArea.SPIDERS_DEN, "§cBroodmother", 0xFF5555),
    ARACHNE(MobArea.SPIDERS_DEN, "§5Arachne", 0xAA00AA),
    ARACHNE_BROOD(MobArea.SPIDERS_DEN, "§dArachne's Brood", 0xFF55FF),

    // --- CRIMSON_ISLE ---
    BARBARIAN_DUKE_X(MobArea.CRIMSON_ISLE, "§cBarbarian Duke X", 0xFF5555),
    BLADESOUL(MobArea.CRIMSON_ISLE, "§8Bladesoul", 0x555555),
    MAGE_OUTLAW(MobArea.CRIMSON_ISLE, "§5Mage Outlaw", 0xAA00AA),
    ASHFANG(MobArea.CRIMSON_ISLE, "§7Ashfang", 0xAAAAAA),
    ASHFANG_FOLLOWER(MobArea.CRIMSON_ISLE, "§8Ashfang Follower", 0x555555),
    ASHFANG_ACOLYTE(MobArea.CRIMSON_ISLE, "§9Ashfang Acolyte", 0x5555FF),
    ASHFANG_UNDERLING(MobArea.CRIMSON_ISLE, "§cAshfang Underling", 0xFF5555),
    MAGMA_BOSS(MobArea.CRIMSON_ISLE, "§6Magma Boss", 0xFFAA00),
    MAGMA_GLARE(MobArea.CRIMSON_ISLE, "§cMagma Glare", 0xFF5555),
    MATCHO(MobArea.CRIMSON_ISLE, "§bMatcho", 0x55FFFF),

    // --- CRYSTAL_HOLLOWS ---
    BOSS_CORLEONE(MobArea.CRYSTAL_HOLLOWS, "§dBoss Corleone", 0xFF55FF),

    // --- SAFARI_CAVERN ---
    CAVERNFISH(MobArea.SAFARI_CAVERN, "§bCavernfish", 0x55FFFF),
    FLITTER(MobArea.SAFARI_CAVERN, "§eFlitter", 0xFFFF55),
    SHYWORM(MobArea.SAFARI_CAVERN, "§aShyworm", 0x55FF55),
    DRIFTLING(MobArea.SAFARI_CAVERN, "§9Driftling", 0x5555FF),
    CHUCKWALLA(MobArea.SAFARI_CAVERN, "§6Chuckwalla", 0xFFAA00),
    ROCKMITE(MobArea.SAFARI_CAVERN, "§7Rockmite", 0xAAAAAA),
    SCRAPPY(MobArea.SAFARI_CAVERN, "§cScrappy", 0xFF5555),
    SNOOZLE(MobArea.SAFARI_CAVERN, "§dSnoozle", 0xFF55FF),
    GEMZIE(MobArea.SAFARI_CAVERN, "§5Gemzie", 0xAA00AA),

    // --- SAFARI_FOREST ---
    FOXTROT(MobArea.SAFARI_FOREST, "§cFoxtrot", 0xFF5555),
    BLUEBIRD(MobArea.SAFARI_FOREST, "§9Bluebird", 0x5555FF),
    HONEYBUG(MobArea.SAFARI_FOREST, "§eHoneybug", 0xFFFF55),
    TREEFROG(MobArea.SAFARI_FOREST, "§aTreefrog", 0x55FF55),
    WOODCHUCKER(MobArea.SAFARI_FOREST, "§8Woodchucker", 0x555555),
    FLUFFLING(MobArea.SAFARI_FOREST, "§fFluffling", 0xFFFFFF),
    HIDEONFLOOR(MobArea.SAFARI_FOREST, "§aHideonfloor", 0x55FF55),
    PARAKEET(MobArea.SAFARI_FOREST, "§aParakeet", 0x55FF55),
    MACAW(MobArea.SAFARI_FOREST, "§cMacaw", 0xFF5555),

    // --- SAFARI_HAUNTED ---
    AREITA(MobArea.SAFARI_HAUNTED, "§5Areita", 0xAA00AA),
    BLOODBAT(MobArea.SAFARI_HAUNTED, "§cBloodbat", 0xFF5555),
    DUPLICO(MobArea.SAFARI_HAUNTED, "§dDuplico", 0xFF55FF),
    GAZER(MobArea.SAFARI_HAUNTED, "§bGazer", 0x55FFFF),
    LITTERBUG(MobArea.SAFARI_HAUNTED, "§8Litterbug", 0x555555),
    SOLSNATCHER(MobArea.SAFARI_HAUNTED, "§eSolsnatcher", 0xFFFF55),
    GIMMIEGOLD(MobArea.SAFARI_HAUNTED, "§6Gimmiegold", 0xFFAA00),
    HIDEONWALL(MobArea.SAFARI_HAUNTED, "§5Hideonwall", 0xAA00AA),
    HIDEYHO(MobArea.SAFARI_HAUNTED, "§fHideyho", 0xFFFFFF),
    DOOMSPIRAL(MobArea.SAFARI_HAUNTED, "§5Doomspiral", 0xAA00AA),

    // --- SAFARI_ICY ---
    BILLYGOAT(MobArea.SAFARI_ICY, "§fBillygoat", 0xFFFFFF),
    MANTIS_SHRIMP(MobArea.SAFARI_ICY, "§cMantis Shrimp", 0xFF5555),
    NOZZLENOSE(MobArea.SAFARI_ICY, "§bNozzlenose", 0x55FFFF),
    POLARIS(MobArea.SAFARI_ICY, "§fPolaris", 0xFFFFFF),
    SHUDDERSQUID(MobArea.SAFARI_ICY, "§9Shuddersquid", 0x5555FF),
    STRONGARM(MobArea.SAFARI_ICY, "§fStrongarm", 0xFFFFFF),
    TEPID(MobArea.SAFARI_ICY, "§6Tepid", 0xFFAA00),
    TROODON(MobArea.SAFARI_ICY, "§aTroodon", 0x55FF55),
    WUMPA(MobArea.SAFARI_ICY, "§bWumpa", 0x55FFFF),

    // --- MOONGLADE_MARSH ---
    HIDEONLEAF(MobArea.MOONGLADE_MARSH, "§aHideonleaf", 0x55FF55),
    CORALOT(MobArea.MOONGLADE_MARSH, "§dCoralot", 0xFF55FF),
    MOSSYBIT(MobArea.MOONGLADE_MARSH, "§aMossybit", 0x55FF55),
    COD(MobArea.MOONGLADE_MARSH, "§7Cod", 0xAAAAAA),
    SALMON(MobArea.MOONGLADE_MARSH, "§cSalmon", 0xFF5555),
    JOYDIVE(MobArea.MOONGLADE_MARSH, "§bJoydive", 0x55FFFF),
    LUMISQUID(MobArea.MOONGLADE_MARSH, "§9Lumisquid", 0x5555FF),
    SHELLWISE(MobArea.MOONGLADE_MARSH, "§aShellwise", 0x55FF55),
    SPIKE(MobArea.MOONGLADE_MARSH, "§eSpike", 0xFFFF55),
    BIRRIES(MobArea.MOONGLADE_MARSH, "§8Birries", 0x555555),
    BAMBULEAF(MobArea.MOONGLADE_MARSH, "§fBambuleaf", 0xFFFFFF),
    MOCHIBEAR(MobArea.MOONGLADE_MARSH, "§6Mochibear", 0xFFAA00),
    PHANPYRE(MobArea.MOONGLADE_MARSH, "§5Phanpyre", 0xAA00AA),
    PHANFLARE(MobArea.MOONGLADE_MARSH, "§6Phanflare", 0xFFAA00),
    DREADWING(MobArea.MOONGLADE_MARSH, "§cDreadwing", 0xFF5555),
    INVISIBUG(MobArea.MOONGLADE_MARSH, "§dInvisibug", 0xFF55FF),
    AZURE(MobArea.MOONGLADE_MARSH, "§bAzure", 0x55FFFF),
    VERDANT(MobArea.MOONGLADE_MARSH, "§aVerdant", 0x55FF55),
    HEWVER(MobArea.MOONGLADE_MARSH, "§6Hewver", 0xFFAA00),
    HONEYHOG(MobArea.MOONGLADE_MARSH, "§eHoneyhog", 0xFFFF55),
    HONEYMITE(MobArea.MOONGLADE_MARSH, "§5Honeymite", 0xAA00AA),
    MURKBAT(MobArea.MOONGLADE_MARSH, "§8Murkbat", 0x555555),
    TIDETOT(MobArea.MOONGLADE_MARSH, "§9Tidetot", 0x5555FF),
    CHILL(MobArea.MOONGLADE_MARSH, "§bChill", 0x55FFFF),
    STAG_BEETLE(MobArea.MOONGLADE_MARSH, "§7Stag Beetle", 0xAAAAAA),
    WOODLOUSE(MobArea.MOONGLADE_MARSH, "§aWoodlouse", 0x55FF55),

    // --- TORRHUS_CANYON ---
    HIDEONSUN(MobArea.TORRHUS_CANYON, "§eHideonsun", 0xFFFF55),
    BLUE_JAY(MobArea.TORRHUS_CANYON, "§9Blue Jay", 0x5555FF),
    DUSTYBIT(MobArea.TORRHUS_CANYON, "§7Dustybit", 0xAAAAAA),
    SEPIALOT(MobArea.TORRHUS_CANYON, "§6Sepialot", 0xFFAA00),
    GOLDOLOT(MobArea.TORRHUS_CANYON, "§eGoldolot", 0xFFFF55),
    PANGOLIN(MobArea.TORRHUS_CANYON, "§bPangolin", 0x55FFFF),
    BUNBUN(MobArea.TORRHUS_CANYON, "§fBunbun", 0xFFFFFF),
    DRYBARK(MobArea.TORRHUS_CANYON, "§8Drybark", 0x555555),
    FIREFOX(MobArea.TORRHUS_CANYON, "§cFirefox", 0xFF5555),
    GROUNDHOG(MobArea.TORRHUS_CANYON, "§dGroundhog", 0xFF55FF),
    HIVETHIEF(MobArea.TORRHUS_CANYON, "§bHivethief", 0x55FFFF),
    MOUNTAIN_GOAT(MobArea.TORRHUS_CANYON, "§7Mountain Goat", 0xAAAAAA),
    PUCK(MobArea.TORRHUS_CANYON, "§5Puck", 0xAA00AA),
    BEEHEEMOTH(MobArea.TORRHUS_CANYON, "§6Beeheemoth", 0xFFAA00),
    HONEYBUZZ(MobArea.TORRHUS_CANYON, "§eHoneybuzz", 0xFFFF55),
    POLLENDART(MobArea.TORRHUS_CANYON, "§aPollendart", 0x55FF55),
    EMBER(MobArea.TORRHUS_CANYON, "§6Ember", 0xFFAA00),
    SOLAR(MobArea.TORRHUS_CANYON, "§eSolar", 0xFFFF55),
    TIMIL(MobArea.TORRHUS_CANYON, "§dTimil", 0xFF55FF),
    WATER_SNAKE(MobArea.TORRHUS_CANYON, "§9Water Snake", 0x5555FF),
    PARCHED(MobArea.TORRHUS_CANYON, "§6Parched", 0xFFAA00),
    GRIZZLY_BEAR(MobArea.TORRHUS_CANYON, "§6Grizzly Bear", 0xFFAA00),
    QUEEN_ANT(MobArea.TORRHUS_CANYON, "§5Queen Ant", 0xAA00AA),
    ANT(MobArea.TORRHUS_CANYON, "§cAnt", 0xFF5555),
    SNEAKY_TIKI(MobArea.TORRHUS_CANYON, "§aSneaky Tiki", 0x55FF55),
    SHRIEKY_TIKI(MobArea.TORRHUS_CANYON, "§dShrieky Tiki", 0xFF55FF),
    CHEEKY_TIKI(MobArea.TORRHUS_CANYON, "§bCheeky Tiki", 0x55FFFF),

    // --- LOTUS_ATOLL ---
    LOTUSFISH(MobArea.LOTUS_ATOLL, "§bLotusfish", 0x55FFFF),
    LOTUM(MobArea.LOTUS_ATOLL, "§dLotum", 0xFF55FF),
    TEWTIL(MobArea.LOTUS_ATOLL, "§aTewtil", 0x55FF55),
    FLIPFLOPPER(MobArea.LOTUS_ATOLL, "§6Flipflopper", 0xFFAA00),
    SEASHINE(MobArea.LOTUS_ATOLL, "§eSeashine", 0xFFFF55),

    // --- GLACITE_MINESHAFT ---
    LITTLEFOOT(MobArea.GLACITE_MINESHAFT, "§bLittlefoot", 0x55FFFF),

    // --- GARDEN ---
    FLY(MobArea.GARDEN, "§8Fly", 0x555555),
    FIELD_MOUSE(MobArea.GARDEN, "§7Field Mouse", 0xAAAAAA),
    RAT(MobArea.GARDEN, "§4Rat", 0xAA0000),
    MITE(MobArea.GARDEN, "§cMite", 0xFF5555),
    MOSQUITO(MobArea.GARDEN, "§9Mosquito", 0x5555FF),
    EARTHWORM(MobArea.GARDEN, "§dEarthworm", 0xFF55FF),
    CRICKET(MobArea.GARDEN, "§aCricket", 0x55FF55),
    LOCUST(MobArea.GARDEN, "§eLocust", 0xFFFF55),
    MOTH(MobArea.GARDEN, "§fMoth", 0xFFFFFF),
    LUNAR_MOTH(MobArea.GARDEN, "§1Lunar Moth", 0x0000AA),
    SLUG(MobArea.GARDEN, "§5Slug", 0xAA00AA),
    BEETLE(MobArea.GARDEN, "§3Beetle", 0x00AAAA),
    FIREFLY(MobArea.GARDEN, "§6Firefly", 0xFFAA00),
    DRAGONFLY(MobArea.GARDEN, "§bDragonfly", 0x55FFFF),
    PRAYING_MANTIS(MobArea.GARDEN, "§2Praying Mantis", 0x00AA00),

    // --- SEA_CREATURE ---
    WATER_HYDRA(MobArea.SEA_CREATURE, "§6Water Hydra", 0xFFAA00, FishArea.ANYWHERE),
    ALLIGATOR(MobArea.SEA_CREATURE, "§6Alligator", 0xFFAA00, FishArea.BACKWATER_BAYOU),
    PUDDLE_JUMPER(MobArea.SEA_CREATURE, "§6Puddle Jumper", 0xFFAA00, FishArea.LOTUS_ATOLL),
    THE_LOCH_EMPEROR(MobArea.SEA_CREATURE, "§6The Loch Emperor", 0xFFAA00, FishArea.MOONGLADE_MARSH),
    SILKBREEZE(MobArea.SEA_CREATURE, "§6Silkbreeze", 0xFFAA00, FishArea.TORRHUS_CANYON),
    THUNDER(MobArea.SEA_CREATURE, "§6Thunder", 0xFFAA00, FishArea.CRIMSON_ISLE),
    FIERY_SCUTTLER(MobArea.SEA_CREATURE, "§6Fiery Scuttler", 0xFFAA00, FishArea.CRIMSON_ISLE),
    BLUE_RINGED_OCTOPUS(MobArea.SEA_CREATURE, "§6Blue Ringed Octopus", 0xFFAA00, FishArea.ANYWHERE),
    YETI(MobArea.SEA_CREATURE, "§6Yeti", 0xFFAA00, FishArea.JERRYS_WORKSHOP),
    PHANTOM_FISHER(MobArea.SEA_CREATURE, "§6Phantom Fisher", 0xFFAA00, FishArea.ANYWHERE),
    GREAT_WHITE_SHARK(MobArea.SEA_CREATURE, "§6Great White Shark", 0xFFAA00, FishArea.ANYWHERE),
    TITANOBOA(MobArea.SEA_CREATURE, "§dTitanoboa", 0xFF55FF, FishArea.BACKWATER_BAYOU),
    FROG_PRINCE(MobArea.SEA_CREATURE, "§dFrog Prince", 0xFF55FF, FishArea.LOTUS_ATOLL),
    NESSIE(MobArea.SEA_CREATURE, "§dNessie", 0xFF55FF, FishArea.MOONGLADE_MARSH),
    GIANT_ISOPOD(MobArea.SEA_CREATURE, "§dGiant Isopod", 0xFF55FF, FishArea.TORRHUS_CANYON),
    LORD_JAWBUS(MobArea.SEA_CREATURE, "§dLord Jawbus", 0xFF55FF, FishArea.CRIMSON_ISLE),
    RAGNAROK(MobArea.SEA_CREATURE, "§dRagnarok", 0xFF55FF, FishArea.CRIMSON_ISLE),
    WIKI_TIKI(MobArea.SEA_CREATURE, "§dWiki Tiki", 0xFF55FF, FishArea.ANYWHERE),
    REINDRAKE(MobArea.SEA_CREATURE, "§dReindrake", 0xFF55FF, FishArea.JERRYS_WORKSHOP),
    GRIM_REAPER(MobArea.SEA_CREATURE, "§dGrim Reaper", 0xFF55FF, FishArea.ANYWHERE),
    MITHRIL_GRUBBER(MobArea.SEA_CREATURE, "§aMithril Grubber", 0x55FF55, FishArea.DWARVEN_MINES),
    OASIS_SHEEP(MobArea.SEA_CREATURE, "§aOasis Sheep", 0x55FF55, FishArea.FARMING_ISLANDS),
    OASIS_RABBIT(MobArea.SEA_CREATURE, "§aOasis Rabbit", 0x55FF55, FishArea.FARMING_ISLANDS),
    CARROT_KING(MobArea.SEA_CREATURE, "§9Carrot King", 0x5555FF, FishArea.ANYWHERE),
    AGARIMOO(MobArea.SEA_CREATURE, "§9Agarimoo", 0x5555FF, FishArea.ANYWHERE),
    WATER_WORM(MobArea.SEA_CREATURE, "§9Water Worm", 0x5555FF, FishArea.CRYSTAL_HOLLOWS),
    POISONED_WATER_WORM(MobArea.SEA_CREATURE, "§9Poisoned Water Worm", 0x5555FF, FishArea.CRYSTAL_HOLLOWS),
    FLAMING_WORM(MobArea.SEA_CREATURE, "§9Flaming Worm", 0x5555FF, FishArea.CRYSTAL_HOLLOWS),
    LAVA_BLAZE(MobArea.SEA_CREATURE, "§5Lava Blaze", 0xAA00AA, FishArea.CRYSTAL_HOLLOWS),
    LAVA_PIGMAN(MobArea.SEA_CREATURE, "§5Lava Pigman", 0xAA00AA, FishArea.CRYSTAL_HOLLOWS),
    ABYSSAL_MINER(MobArea.SEA_CREATURE, "§6Abyssal Miner", 0xFFAA00, FishArea.CRYSTAL_HOLLOWS),
    PLHLEGBLAST(MobArea.SEA_CREATURE, "§dPlhlegblast", 0xFF55FF, FishArea.CRIMSON_ISLE);

    private final MobArea area;
    private final String label;
    private final int colorRGB;
    private final FishArea fishArea;

    Mob(MobArea area, String label, int colorRGB) {
        this(area, label, colorRGB, null);
    }

    Mob(MobArea area, String label, int colorRGB, FishArea fishArea) {
        this.area = area;
        this.label = label;
        this.colorRGB = colorRGB;
        this.fishArea = fishArea;
    }

    public MobArea area() {
        return area;
    }

    /** Label with its legacy colour code. */
    public String label() {
        return label;
    }

    /** Label without the colour code - for a nameplate that colours itself. */
    public String plainLabel() {
        return label.length() > 2 && label.charAt(0) == '§' ? label.substring(2) : label;
    }

    public int glowColorRGB() {
        return colorRGB;
    }

    public int tracerColorARGB() {
        return 0xFF000000 | colorRGB;
    }

    public FishArea fishArea() {
        return fishArea;
    }

    /** Is the player on the island this mob lives on. */
    public boolean inOwnArea() {
        if (area == MobArea.SEA_CREATURE) return fishArea != null && fishArea.isHere();
        return area.isHere();
    }

    /** Picked in the config, on the right island, and not hidden as a captured critter. */
    private boolean shown() {
        if (!TeslaMapsConfig.get().mobVisualTargets.contains(name())) return false;
        if (!inOwnArea()) return false;
        return !MobVisuals.hiddenAsCaptured(this);
    }

    public boolean glow() {
        return TeslaMapsConfig.get().mobVisualGlow && shown();
    }

    public boolean tracer() {
        return TeslaMapsConfig.get().mobVisualTracer && shown();
    }

    public boolean nameplate() {
        return TeslaMapsConfig.get().mobVisualNameplate && shown();
    }

    /** Any of the three on, so the mob has to be looked for at all. */
    public boolean anyEnabled() {
        return glow() || tracer() || nameplate();
    }

    public static boolean anyEnabled(List<Mob> mobs) {
        for (Mob mob : mobs) if (mob.anyEnabled()) return true;
        return false;
    }

    private static final java.util.EnumMap<MobArea, List<Mob>> BY_AREA = new java.util.EnumMap<>(MobArea.class);

    static {
        for (MobArea a : MobArea.values()) {
            List<Mob> out = new ArrayList<>();
            for (Mob mob : values()) if (mob.area == a) out.add(mob);
            BY_AREA.put(a, List.copyOf(out));
        }
    }

    public static List<Mob> of(MobArea area) {
        return BY_AREA.get(area);
    }

    @Override
    public String toString() {
        return label;
    }
}
