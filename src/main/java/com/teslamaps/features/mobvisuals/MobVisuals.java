package com.teslamaps.features.mobvisuals;

import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import com.teslamaps.utils.SkyblockUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariants;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.skeleton.Stray;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3fc;

/**
 * Finds the mobs picked in the config and hands glow / tracer / nameplate their targets.
 * Runs once per tick - the render side only draws what this leaves behind.
 * Ported from GanKura's EntityHighlightManager.
 */
public final class MobVisuals {

    // Safari's four biomes are quadrants around this point - the area name is just "Safari"
    private static final double SAFARI_CENTER_X = -50.0;
    private static final double SAFARI_CENTER_Z = 0.0;

    private static final float LOTUM_MAX_WIDTH = 0.7f;
    private static final float PHANFLARE_MIN_WIDTH = 0.63f;
    private static final float DREADWING_MIN_WIDTH = 1.5f;
    private static final float BEEHEEMOTH_MIN_WIDTH = 1.4f;

    private static final double HITBOX_HEAD_RADIUS = 1.5;
    private static final double NAMETAG_RADIUS = 3.0;

    private static final double DISPLAY_ANCHOR = 0.0;
    private static final double BLOCK_DISPLAY_ANCHOR = 0.5;
    private static final double SKULL_DISPLAY_HALF_HEIGHT = 0.25;
    private static final double DISPLAY_MIN_SCALE = 0.01;
    private static final double STAND_MIN_SCALE = 0.1;
    private static final double HEAD_STAND_ANCHOR = 1.7;
    private static final double SMALL_HEAD_STAND_ANCHOR = 0.85;

    /** Read by the glow mixin - an entity in here is forced to outline. */
    public static final Set<Entity> glowing = new HashSet<>();
    /** Outline colour per entity; same mob type can differ by variant or biome. */
    public static final Map<Entity, Integer> glowColors = new HashMap<>();
    /** Armor stands whose body model is hidden while glowing, so only the head outlines. */
    public static final Set<Entity> headOnlyGlow = new HashSet<>();

    public static final Map<Entity, Integer> tracers = new LinkedHashMap<>();
    public static final Map<Entity, Label> nameplates = new LinkedHashMap<>();

    public record Label(String[] lines, int[] widths) {}

    private static Label label(Minecraft mc, String text) {
        String[] lines = text.split("\n");
        int[] widths = new int[lines.length];
        for (int i = 0; i < lines.length; i++) widths[i] = mc.font.width(lines[i]);
        return new Label(lines, widths);
    }

    private static final Map<Entity, Double> renderAnchors = new HashMap<>();
    private static final Map<Mob, Entity> tracerNearest = new HashMap<>();
    /** Silverfish hitboxes already owned by a shulker, so they are not counted twice. */
    private static final Set<Entity> shulkerClaimed = new HashSet<>();

    private MobVisuals() {}

    public static boolean active() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.section("Mob Visuals") || !c.mobVisuals) return false;
        if (LegitMode.blocksCheats()) return false;
        return SkyblockUtils.isInSkyblock();
    }

    /**
     * GanKura drops a Safari critter off the list once it was captured, read off its own
     * tab-list scan. Nothing here tracks that yet, so every picked mob stays shown.
     */
    static boolean hiddenAsCaptured(Mob mob) {
        return false;
    }

    // --- tick ---

    public static void tick() {
        clear();
        if (!active()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // an area the player is not on, or whose mobs are all unpicked, costs no scan at all
        boolean isSafari = enabledHere(MobArea.SAFARI_CAVERN, MobArea.SAFARI_FOREST,
                MobArea.SAFARI_HAUNTED, MobArea.SAFARI_ICY);
        boolean isMarsh = enabledHere(MobArea.MOONGLADE_MARSH);
        boolean isCanyon = enabledHere(MobArea.TORRHUS_CANYON);
        boolean isAtoll = enabledHere(MobArea.LOTUS_ATOLL);
        boolean isGarden = enabledHere(MobArea.GARDEN);
        boolean isSea = enabledHere(MobArea.SEA_CREATURE);

        boolean scanSkins = isGarden || isCanyon || isSafari || isMarsh || isSea;
        boolean scanAnimals = isMarsh || isCanyon || isAtoll;
        boolean scanShulkers = isMarsh || isCanyon || isSafari;

        if (scanSkins || scanAnimals || scanShulkers) scanCatalog(mc, scanSkins, scanAnimals, scanShulkers,
                isSafari, isMarsh, isAtoll);
        MobVisualBosses.scan(mc);
    }

    public static void onParticle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) {
        if (Minecraft.getInstance().isSameThread() && active()) MobVisualBosses.onParticle(packet);
    }

    private static void scanCatalog(Minecraft mc, boolean scanSkins, boolean scanAnimals, boolean scanShulkers,
                                    boolean isSafari, boolean isMarsh, boolean isAtoll) {
        if (scanShulkers) claimShulkerHitboxes(mc);

        for (Entity entity : com.teslamaps.utils.EntityCache.all()) {
            if (entity == mc.player || entity.isRemoved()) continue;

            Mob mob = null;
            if (scanSkins) mob = headSkinTarget(entity);
            if (mob == null && scanShulkers && entity instanceof Shulker shulker) mob = shulkerTarget(shulker);
            if (mob == null && isSafari) mob = safariTarget(mc, entity);
            if (mob == null && scanAnimals) mob = areaAnimalTarget(entity, isMarsh, isAtoll);

            if (mob == null || !mob.anyEnabled()) continue;
            if (mob.area().isSafari() && !inSafariBiome(entity, mob.area())) continue;

            register(mc, entity, mob);
        }
    }

    /** Player is on one of these areas and at least one of its mobs is picked. */
    private static boolean enabledHere(MobArea... areas) {
        for (MobArea area : areas) {
            if (!area.isHere()) continue;
            if (Mob.anyEnabled(Mob.of(area))) return true;
        }
        return false;
    }

    private static void clear() {
        glowing.clear();
        glowColors.clear();
        headOnlyGlow.clear();
        tracers.clear();
        nameplates.clear();
        renderAnchors.clear();
        tracerNearest.clear();
        shulkerClaimed.clear();
    }

    /** Tracer and nameplate only - for a nametag whose body is not loaded. */
    static void registerNoGlow(Minecraft mc, Entity entity, Mob mob) {
        if (mob.tracer()) addTracer(mc, entity, mob);
        if (mob.nameplate()) nameplates.put(entity, label(mc, nameplateLabel(mc, entity, mob)));
    }

    static void register(Minecraft mc, Entity entity, Mob mob) {
        if (mob.glow()) {
            glowing.add(entity);
            glowColors.put(entity, PestGlowColors.color(mob, mob.glowColorRGB()));
            // a non-marker stand would outline arms, body and legs as well as the head
            if (entity instanceof ArmorStand stand && !stand.isMarker()) headOnlyGlow.add(entity);
        }
        if (mob.tracer()) addTracer(mc, entity, mob);
        if (mob.nameplate()) nameplates.put(entity, label(mc, nameplateLabel(mc, entity, mob)));
    }

    /** One line per mob by default, so a swarm does not turn into a fan of lines. */
    private static void addTracer(Minecraft mc, Entity entity, Mob mob) {
        if (TeslaMapsConfig.get().mobVisualTracerAll) {
            tracers.put(entity, mob.tracerColorARGB());
            return;
        }
        Entity current = tracerNearest.get(mob);
        if (current != null) {
            if (current.distanceToSqr(mc.player) <= entity.distanceToSqr(mc.player)) return;
            tracers.remove(current);
        }
        tracerNearest.put(mob, entity);
        tracers.put(entity, mob.tracerColorARGB());
    }

    // --- detection: head skins ---

    private static final Map<String, Optional<Mob>> SKIN_CACHE = new HashMap<>();
    private static final int MAX_SKIN_CACHE = 512;

    private static Mob headSkinTarget(Entity entity) {
        for (String value : headSkinValues(entity)) {
            Mob mob = cachedSkinTarget(value);
            if (mob != null) return mob;
        }
        if (entity instanceof Display.ItemDisplay item && isDrawnDisplay(item)) {
            for (String value : skinValues(displayItem(item))) {
                Mob mob = cachedSkinTarget(value);
                if (mob != null) return mob;
            }
        }
        return null;
    }

    /** Skull texture values worn by the entity; empty when it wears none. */
    private static List<String> headSkinValues(Entity entity) {
        if (!(entity instanceof ArmorStand) && !(entity instanceof Zombie)) return List.of();

        LivingEntity wearer = (LivingEntity) entity;
        // a named stand is a nametag, not a body
        if (wearer.getCustomName() != null) return List.of();

        return skinValues(wearer.getItemBySlot(EquipmentSlot.HEAD));
    }

    private static List<String> skinValues(ItemStack stack) {
        if (stack.isEmpty()) return List.of();

        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return List.of();

        java.util.List<String> values = new java.util.ArrayList<>();
        for (Property property : profile.partialProfile().properties().get("textures")) {
            if (property != null) values.add(property.value());
        }
        return values;
    }

    private static Mob cachedSkinTarget(String value) {
        Optional<Mob> cached = SKIN_CACHE.get(value);
        if (cached != null) return cached.orElse(null);

        if (SKIN_CACHE.size() >= MAX_SKIN_CACHE) SKIN_CACHE.clear();

        String decoded = decodeSkin(value);
        Mob found = null;
        for (Map.Entry<String, Mob> entry : MobSkins.SKINS.entrySet()) {
            if (decoded.contains(entry.getKey())) {
                found = entry.getValue();
                break;
            }
        }
        SKIN_CACHE.put(value, Optional.ofNullable(found));
        return found;
    }

    private static String decodeSkin(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    // --- detection: shulkers (the Hideon family) ---

    private static Mob shulkerTarget(Shulker shulker) {
        if (MobArea.MOONGLADE_MARSH.isHere()) return Mob.HIDEONLEAF;
        if (MobArea.TORRHUS_CANYON.isHere()) return Mob.HIDEONSUN;
        if (!MobArea.SAFARI_CAVERN.isHere()) return null;
        return shulkerColorTarget(shulker.getColor());
    }

    private static Mob shulkerColorTarget(DyeColor color) {
        if (color == null) return null;
        return switch (color) {
            case GREEN, LIME -> Mob.HIDEONFLOOR;
            case PURPLE, MAGENTA -> Mob.HIDEONWALL;
            default -> null;
        };
    }

    /**
     * While a Hideon sits still its shulker overlaps the invisible silverfish hitbox; once it moves
     * the two drift apart. Claiming from the shulker side keeps the hitbox from being picked up
     * again as a Duplico, which uses the same invisible silverfish.
     */
    private static void claimShulkerHitboxes(Minecraft mc) {
        for (Entity entity : com.teslamaps.utils.EntityCache.byType(Shulker.class)) {
            if (!(entity instanceof Shulker)) continue;
            Entity hitbox = nearestOfType(mc, entity, Silverfish.class);
            if (hitbox != null) shulkerClaimed.add(hitbox);
        }
    }

    // --- detection: Critter Safari ---

    private static Mob safariTarget(Minecraft mc, Entity entity) {
        if (entity instanceof Display.ItemDisplay || entity instanceof ArmorStand) {
            // handled by the skin pass; a stand with no skull is just a nametag
            return null;
        }

        if (entity.isInvisible()) {
            if (!(entity instanceof Silverfish)) return null;
            if (hasHeadStandNear(mc, entity)) return null;
            if (shulkerClaimed.contains(entity)) return null;

            // a moving Hideon swaps its shulker for an ItemDisplay holding a shulker box
            ShulkerBoxBlock box = shulkerBoxDisplayNear(mc, entity);
            if (box != null) return shulkerColorTarget(box.getColor());

            // skull-display mobs are picked up on the display itself
            if (hasSkinnedDisplayNear(mc, entity)) return null;

            if (inSafariHaunted(entity)) return Mob.DUPLICO;
            if (inSafariForest(entity)) return Mob.HIDEONFLOOR;
            return null;
        }

        if (entity instanceof Silverfish) return Mob.ROCKMITE;
        if (entity instanceof Armadillo) return Mob.SCRAPPY;
        if (entity instanceof Sniffer) return Mob.SNOOZLE;
        if (entity instanceof Vex) return Mob.GEMZIE;

        if (entity instanceof Fox) return Mob.FOXTROT;
        if (entity instanceof Bee) return Mob.HONEYBUG;
        if (entity instanceof Frog) return Mob.TREEFROG;
        if (entity instanceof Creaking) return Mob.WOODCHUCKER;
        if (entity instanceof Panda) return Mob.FLUFFLING;
        if (entity instanceof Parrot parrot) {
            return switch (parrot.getVariant()) {
                case BLUE -> Mob.BLUEBIRD;
                case GREEN -> Mob.PARAKEET;
                case RED_BLUE -> Mob.MACAW;
                default -> null;
            };
        }

        if (entity instanceof CaveSpider) return Mob.AREITA;
        if (entity instanceof Bat) return Mob.BLOODBAT;
        if (entity instanceof Endermite) return Mob.LITTERBUG;
        if (entity instanceof Phantom) return Mob.SOLSNATCHER;

        if (entity instanceof Goat) return Mob.BILLYGOAT;
        if (entity instanceof Dolphin) return Mob.NOZZLENOSE;
        if (entity instanceof PolarBear) return Mob.POLARIS;
        if (entity instanceof GlowSquid) return Mob.SHUDDERSQUID;
        if (entity instanceof SnowGolem) return Mob.STRONGARM;
        if (entity instanceof TropicalFish fish) return tropicalFishTarget(fish);
        return null;
    }

    static boolean inSafariBiome(Entity entity, MobArea area) {
        return switch (area) {
            case SAFARI_CAVERN -> inSafariCavern(entity);
            case SAFARI_FOREST -> inSafariForest(entity);
            case SAFARI_HAUNTED -> inSafariHaunted(entity);
            case SAFARI_ICY -> inSafariIcy(entity);
            default -> true;
        };
    }

    private static boolean inSafariCavern(Entity e) {
        return e.getX() < SAFARI_CENTER_X && e.getZ() > SAFARI_CENTER_Z;
    }

    private static boolean inSafariForest(Entity e) {
        return e.getX() > SAFARI_CENTER_X && e.getZ() > SAFARI_CENTER_Z;
    }

    private static boolean inSafariHaunted(Entity e) {
        return e.getX() < SAFARI_CENTER_X && e.getZ() < SAFARI_CENTER_Z;
    }

    private static boolean inSafariIcy(Entity e) {
        return e.getX() > SAFARI_CENTER_X && e.getZ() < SAFARI_CENTER_Z;
    }

    // --- detection: Moonglade Marsh / Torrhus Canyon / Lotus Atoll ---

    private static Mob areaAnimalTarget(Entity entity, boolean marsh, boolean atoll) {
        // invisible ones are another mob's hitbox - the lasso tip is an invisible bat
        if (entity.isInvisible()) return null;

        if (atoll) return lotusAtollTarget(entity);

        if (entity instanceof Frog) return marsh ? Mob.MOSSYBIT : Mob.DUSTYBIT;
        if (entity instanceof Armadillo) return marsh ? null : Mob.PANGOLIN;
        if (entity instanceof Parrot) return marsh ? null : Mob.BLUE_JAY;
        if (entity instanceof Axolotl axolotl) {
            if (marsh) return Mob.CORALOT;
            return switch (axolotl.getVariant()) {
                case WILD -> Mob.SEPIALOT;
                case GOLD -> Mob.GOLDOLOT;
                default -> null;
            };
        }

        if (marsh) {
            if (entity instanceof Cod) return Mob.COD;
            if (entity instanceof Salmon) return Mob.SALMON;
            if (entity instanceof Dolphin) return Mob.JOYDIVE;
            if (entity instanceof GlowSquid) return Mob.LUMISQUID;
            if (entity instanceof Turtle) return Mob.SHELLWISE;
            if (entity instanceof Pufferfish) return Mob.SPIKE;
            if (entity instanceof Tadpole) return Mob.BIRRIES;
            // Hewver is the baby sniffer; the adult is the Nessie sea creature
            if (entity instanceof Sniffer sniffer) return sniffer.isBaby() ? Mob.HEWVER : Mob.NESSIE;
            if (entity instanceof Hoglin) return Mob.HONEYHOG;
            if (entity instanceof Endermite) return Mob.HONEYMITE;
            if (entity instanceof Bat) return Mob.MURKBAT;
            // the Stridersurfer is a drowned too, but always rides a strider
            if (entity instanceof Drowned drowned && !drowned.isPassenger()) return Mob.TIDETOT;
            if (entity instanceof Stray) return Mob.CHILL;
            if (entity instanceof Phantom) {
                if (entity.getBbWidth() >= DREADWING_MIN_WIDTH) return Mob.DREADWING;
                return entity.getBbWidth() >= PHANFLARE_MIN_WIDTH ? Mob.PHANFLARE : Mob.PHANPYRE;
            }
            if (entity instanceof TropicalFish fish) return tropicalFishTarget(fish);
            if (entity instanceof Panda panda) {
                return panda.getVariant() == Panda.Gene.BROWN ? Mob.MOCHIBEAR : Mob.BAMBULEAF;
            }
            return null;
        }

        // the EVIL variant is the Carrot King sea creature
        if (entity instanceof Rabbit rabbit) {
            return rabbit.getVariant() == Rabbit.Variant.EVIL
                    ? null : Mob.BUNBUN;
        }
        if (entity instanceof Creaking) return Mob.DRYBARK;
        if (entity instanceof Fox) return Mob.FIREFOX;
        if (entity instanceof Hoglin) return Mob.GROUNDHOG;
        if (entity instanceof Ghast) return Mob.HIVETHIEF;
        if (entity instanceof Goat) return Mob.MOUNTAIN_GOAT;
        if (entity instanceof Vex) return Mob.PUCK;
        // only the big bee is a Beeheemoth; the rest are told apart by their nametag
        if (entity instanceof Bee) return entity.getBbWidth() >= BEEHEEMOTH_MIN_WIDTH ? Mob.BEEHEEMOTH : null;
        if (entity instanceof TropicalFish fish) return tropicalFishTarget(fish);
        return null;
    }

    private static Mob lotusAtollTarget(Entity entity) {
        if (entity instanceof TropicalFish) return Mob.LOTUSFISH;
        if (entity instanceof Turtle) return Mob.TEWTIL;
        if (entity instanceof Dolphin) return Mob.FLIPFLOPPER;
        if (entity instanceof GlowSquid) return Mob.SEASHINE;

        if (entity instanceof Frog frog) {
            // the big frogs are the Puddle Jumper / Frog Prince sea creatures
            if (frog.getBbWidth() > LOTUM_MAX_WIDTH) return null;
            // of the small ones, Lotum is orange and the Atoll Croaker is green
            if (frog.getVariant().is(FrogVariants.COLD)) return null;
            if (TeslaMapsConfig.get().mobVisualLotumConfirm && !LotumHelper.confirmed(frog)) return null;
            return Mob.LOTUM;
        }
        return null;
    }

    /** Several mobs are tropical fish and only the colour tells them apart. */
    private static Mob tropicalFishTarget(TropicalFish fish) {
        boolean safari = MobArea.SAFARI_CAVERN.isHere();
        // the Safari Tepid is plain white; anything with orange on it is a different mob
        if (safari && (fish.getBaseColor() == DyeColor.ORANGE || fish.getPatternColor() == DyeColor.ORANGE)) {
            return null;
        }
        Mob byBase = tropicalFishColor(fish.getBaseColor(), safari);
        return byBase != null ? byBase : tropicalFishColor(fish.getPatternColor(), safari);
    }

    private static Mob tropicalFishColor(DyeColor color, boolean safari) {
        if (color == null) return null;
        if (MobArea.MOONGLADE_MARSH.isHere()) {
            return switch (color) {
                case LIGHT_BLUE, CYAN, BLUE -> Mob.AZURE;
                case GREEN, LIME -> Mob.VERDANT;
                default -> null;
            };
        }
        if (safari) {
            return switch (color) {
                case BROWN, GRAY, LIGHT_GRAY -> Mob.CAVERNFISH;
                case WHITE -> Mob.TEPID;
                default -> null;
            };
        }
        return switch (color) {
            case ORANGE -> Mob.EMBER;
            case YELLOW -> Mob.SOLAR;
            case PINK, MAGENTA -> Mob.TIMIL;
            default -> null;
        };
    }

    // --- nameplate text ---

    private static String nameplateLabel(Minecraft mc, Entity entity, Mob mob) {
        String name = mob.label();
        if (!TeslaMapsConfig.get().mobVisualNameplateHealth) return name;

        String health = nearbyHealth(mc, entity);
        return health.isEmpty() ? name : name + "\n" + health;
    }

    private static final java.util.regex.Pattern HEALTH =
            java.util.regex.Pattern.compile("([\\d.,]+[kM]?)/([\\d.,]+[kM]?)");

    /** Hypixel puts the health on a nametag armor stand above the body. */
    private static String nearbyHealth(Minecraft mc, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(NAMETAG_RADIUS);
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, box,
                e -> e.getCustomName() != null)) {
            var matcher = HEALTH.matcher(stand.getCustomName().getString());
            if (!matcher.find()) continue;
            return colorHealth(matcher.group(1), matcher.group(2));
        }
        return "";
    }

    private static String colorHealth(String current, String max) {
        double now = parseHealth(current);
        double full = parseHealth(max);
        String color = "§a";
        if (now >= 0 && full > 0) {
            if (now < full * 0.2) color = "§c";
            else if (now < full * 0.5) color = "§e";
        }
        return color + current + "§f/§a" + max;
    }

    private static double parseHealth(String s) {
        try {
            s = s.trim().replace(",", "");
            if (s.isEmpty()) return 0;
            double mult = 1.0;
            char last = s.charAt(s.length() - 1);
            if (last == 'M' || last == 'm') { mult = 1_000_000.0; s = s.substring(0, s.length() - 1); }
            else if (last == 'k' || last == 'K') { mult = 1_000.0; s = s.substring(0, s.length() - 1); }
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // --- render anchors ---

    /**
     * Height above the entity's feet that the tracer and the nameplate aim at. Normally the
     * hitbox centre, but a marker armor stand has no hitbox, so its scale decides instead.
     */
    public static double renderAnchorHeight(Entity entity) {
        Double measured = renderAnchors.get(entity);
        if (measured != null) return measured;

        if (entity instanceof Display.ItemDisplay item) {
            ItemStack stack = displayItem(item);
            // a skull display is the mob itself, not a block sitting on the ground
            if (stack.get(DataComponents.PROFILE) != null) return skullDisplayAnchor(item);
            return stack.getItem() instanceof BlockItem ? BLOCK_DISPLAY_ANCHOR : DISPLAY_ANCHOR;
        }
        if (entity instanceof Display) return DISPLAY_ANCHOR;

        if (entity instanceof ArmorStand stand && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            double base = stand.isSmall() ? SMALL_HEAD_STAND_ANCHOR : HEAD_STAND_ANCHOR;
            return base * stand.getScale();
        }
        return entity.getBbHeight() / 2.0;
    }

    private static double skullDisplayAnchor(Display display) {
        Display.RenderState state = display.renderState();
        if (state == null) return SKULL_DISPLAY_HALF_HEIGHT;
        Vector3fc scale = state.transformation().get(1.0F).scale();
        return SKULL_DISPLAY_HALF_HEIGHT * scale.y();
    }

    // --- small helpers ---

    private static boolean isDrawnDisplay(Display display) {
        Display.RenderState state = display.renderState();
        if (state == null) return true;

        Vector3fc scale = state.transformation().get(1.0F).scale();
        return scale.x() > DISPLAY_MIN_SCALE && scale.y() > DISPLAY_MIN_SCALE && scale.z() > DISPLAY_MIN_SCALE;
    }

    private static ItemStack displayItem(Display.ItemDisplay display) {
        Display.ItemDisplay.ItemRenderState state = display.itemRenderState();
        return state == null ? ItemStack.EMPTY : state.itemStack();
    }

    private static Entity nearestOfType(Minecraft mc, Entity entity, Class<? extends Entity> type) {
        AABB box = entity.getBoundingBox().inflate(HITBOX_HEAD_RADIUS);
        Entity closest = null;
        double best = Double.MAX_VALUE;
        for (Entity candidate : mc.level.getEntitiesOfClass(type, box, e -> true)) {
            double d = candidate.distanceToSqr(entity);
            if (d < best) { best = d; closest = candidate; }
        }
        return closest;
    }

    private static boolean hasHeadStandNear(Minecraft mc, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(HITBOX_HEAD_RADIUS);
        return !mc.level.getEntitiesOfClass(ArmorStand.class, box,
                e -> e.getCustomName() == null && !e.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                        && e.getScale() > STAND_MIN_SCALE).isEmpty();
    }

    private static boolean hasSkinnedDisplayNear(Minecraft mc, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(HITBOX_HEAD_RADIUS);
        for (Display.ItemDisplay display : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> true)) {
            if (!isDrawnDisplay(display)) continue;
            if (displayItem(display).get(DataComponents.PROFILE) != null) return true;
        }
        return false;
    }

    private static ShulkerBoxBlock shulkerBoxDisplayNear(Minecraft mc, Entity entity) {
        AABB box = entity.getBoundingBox().inflate(HITBOX_HEAD_RADIUS);
        for (Display.ItemDisplay display : mc.level.getEntitiesOfClass(Display.ItemDisplay.class, box, e -> true)) {
            if (displayItem(display).getItem() instanceof BlockItem item
                    && item.getBlock() instanceof ShulkerBoxBlock shulkerBox) {
                return shulkerBox;
            }
        }
        return null;
    }
}
