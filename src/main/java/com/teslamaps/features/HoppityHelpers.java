package com.teslamaps.features;

// Ported from SkyHanni's HoppityRabbitTheFishChecker, HoppityNpc, MythicRabbitPetWarning,
// HoppityMuteEggSounds, HoppityEggsShared and HoppityEggDisplayManager (player transparency)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HoppityHelpers {

    public static final String[] SOUND_MODES = {"Normal", "Mute", "Revert"};

    private static final Pattern MEAL_EGG = Pattern.compile("Chocolate (?:Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg.*");
    private static final Pattern EGG_FOUND = Pattern.compile(
            "(?:HOPPITY'S HUNT You found a Chocolate|You have already collected this Chocolate) "
            + "(?:Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg");
    private static final Pattern SHARED = Pattern.compile(
            "\\[(?:TeslaMaps|SkyHanni)] (\\S+) Chocolate Egg located at x: (-?\\d+), y: (-?\\d+), z: (-?\\d+)");
    private static final Pattern SHARED_SKYBLOCKER = Pattern.compile(
            "\\[Skyblocker] Chocolate (\\S+) Egg found at (-?\\d+), (-?\\d+), (-?\\d+)");
    private static final Vec3 HOPPITY = new Vec3(56.5, 71.0, -2.5);
    private static final long SB_EPOCH = 1560275700000L;
    private static final long YEAR_MS = 446400000L;

    private static Feature feature;
    private static int timer;

    // meal egg menu
    private static Object fishScreen;
    private static int fishSlot = -1;
    private static boolean fishClicked;
    private static long lastInEggMenu;

    // Hoppity's shop
    private static Object shopScreen;
    private static final IntOpenHashSet shopSlots = new IntOpenHashSet();
    private static long lastReminder;
    private static boolean shopPending;

    private static long lastPetCheck;

    private static final WaypointSet shared = new WaypointSet().removeWithin(3);
    private static final IntOpenHashSet offered = new IntOpenHashSet();
    private static WeakReference<Level> lastLevel = new WeakReference<>(null);
    private static volatile List<Vec3> anchors = List.of();

    private HoppityHelpers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.hoppityRabbitFish || c.hoppityShopHighlight || c.hoppityShopReminder || c.hoppityPetWarning
                || !"Normal".equals(c.hoppityEggSounds) || c.hoppityEggShare || c.hoppityPlayerTransparency;
    }

    public static void register() {
        feature = Feature.of("HoppityHelpers").section("Inventory").when(HoppityHelpers::any)
                .tick(HoppityHelpers::tick)
                .world(HoppityHelpers::render)
                .chat(HoppityHelpers::onChat)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Hoppity Event");
                    b.toggle("Rabbit the Fish Protection", () -> c.hoppityRabbitFish, v -> c.hoppityRabbitFish = v)
                            .desc("Meal egg menu: Rabbit the Fish is marked red and the menu can't be closed until you click it. Hold Shift to close anyway");
                    b.toggle("Hoppity Shop Highlight", () -> c.hoppityShopHighlight, v -> c.hoppityShopHighlight = v)
                            .desc("Items in Hoppity's shop you can still trade for are marked green");
                    b.toggle("Hoppity Shop Reminder", () -> c.hoppityShopReminder, v -> c.hoppityShopReminder = v)
                            .desc("During spring, reminds you once every 2 minutes until you opened Hoppity's shop this year, with a waypoint in the Hub");
                    b.toggle("Mythic Rabbit Pet Warning", () -> c.hoppityPetWarning, v -> c.hoppityPetWarning = v)
                            .desc("Using the Egglocator without a Mythic Rabbit pet shows \"No Rabbit Pet!\". Reads the tab list pet widget");
                    b.dropdown("Egg Open Sounds", SOUND_MODES, () -> c.hoppityEggSounds, v -> c.hoppityEggSounds = v)
                            .desc("The note block sounds when you open a meal egg: leave them, mute them, or play the old eating sound");
                    b.toggle("Share Egg Locations", () -> c.hoppityEggShare, v -> c.hoppityEggShare = v)
                            .desc("Spotted eggs get a [Share] chat button that posts the position. Shared positions from others become \"Shared Egg\" waypoints");
                    b.channel("  Share To", () -> c.hoppityEggShareChannel, v -> c.hoppityEggShareChannel = v);
                    b.toggle("Transparent Players at Eggs", () -> c.hoppityPlayerTransparency, v -> c.hoppityPlayerTransparency = v)
                            .desc("Other players closer than 4 blocks to a visible or shared egg are drawn see-through");
                    b.slider("  Player Opacity %", 0f, 100f, () -> c.hoppityPlayerOpacity, v -> c.hoppityPlayerOpacity = v);
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && cfg().hoppityPetWarning && feature.active()
                    && "EGGLOCATOR".equals(ItemUtil.skyblockId(player.getItemInHand(hand)))) checkPet();
            return InteractionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("shareegg")
                        .then(ClientCommands.argument("meal", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(ClientCommands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(ClientCommands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .then(ClientCommands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                        .executes(c -> {
                                                            String meal = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "meal");
                                                            int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "x");
                                                            int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "y");
                                                            int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "z");
                                                            Announce.send(cfg().hoppityEggShareChannel, "[TeslaMaps] " + meal
                                                                    + " Chocolate Egg located at x: " + x + ", y: " + y + ", z: " + z);
                                                            return 1;
                                                        }))))))));
    }

    private static boolean isSpring() {
        long sec = System.currentTimeMillis() / 1000L - SB_EPOCH / 1000L;
        return (sec % 446400L) / 37200L < 3;
    }

    private static int sbYear() {
        return (int) ((System.currentTimeMillis() - SB_EPOCH) / YEAR_MS) + 1;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != lastLevel.get()) {
            lastLevel = new WeakReference<>(mc.level);
            shared.clear();
            offered.clear();
        }
        shared.tick();
        String title = mc.screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        boolean eggMenu = MEAL_EGG.matcher(title).matches();
        if (eggMenu) lastInEggMenu = System.currentTimeMillis();

        if (c.hoppityRabbitFish && eggMenu && isSpring()) scanFish((AbstractContainerScreen<?>) mc.screen);
        else { fishScreen = null; fishSlot = -1; }

        if (title.equals("Hoppity")) scanShop((AbstractContainerScreen<?>) mc.screen);
        else if (shopScreen != null) { shopScreen = null; shopSlots.clear(); }

        if (++timer % 20 != 0) return;
        if (!SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) {
            anchors = List.of();
            shopPending = false;
            return;
        }
        boolean spring = isSpring();
        shopPending = c.hoppityShopReminder && spring && c.hoppityShopYear != sbYear();
        if (shopPending && mc.screen == null && System.currentTimeMillis() - lastReminder > 120_000L && mc.player != null) {
            lastReminder = System.currentTimeMillis();
            mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] New rabbits are available at §aHoppity's Shop§e! ")
                    .append(Component.literal("§b[Warp Hub]").withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand("/warp hub"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to /warp hub"))))));
        }

        if (!spring || (!c.hoppityEggShare && !c.hoppityPlayerTransparency)) {
            anchors = List.of();
            return;
        }
        List<HoppityEggESP.Egg> eggs = HoppityEggESP.nearbyEggs(64);
        List<Vec3> a = new ArrayList<>();
        for (HoppityEggESP.Egg e : eggs) {
            a.add(e.pos());
            if (c.hoppityEggShare && offered.add(e.id()) && mc.player != null) offerShare(mc, e);
        }
        for (WaypointSet.Waypoint w : shared.all()) a.add(Vec3.atCenterOf(w.pos));
        anchors = a;
    }

    private static void offerShare(Minecraft mc, HoppityEggESP.Egg e) {
        int x = (int) Math.floor(e.pos().x), y = (int) Math.floor(e.pos().y), z = (int) Math.floor(e.pos().z);
        String cmd = "/tmap shareegg " + e.meal() + " " + x + " " + y + " " + z;
        mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] " + e.meal() + " Egg spotted §7(" + x + ", " + y + ", " + z + ") ")
                .append(Component.literal("§a[Share]").withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand(cmd))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eSend this egg to " + cfg().hoppityEggShareChannel + " chat"))))));
    }

    private static void scanFish(AbstractContainerScreen<?> cs) {
        if (cs != fishScreen) {
            fishScreen = cs;
            fishSlot = -1;
            fishClicked = false;
        }
        if (fishClicked || fishSlot >= 0 || timer % 5 != 0) return;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.index == 22) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            if (StringUtil.stripTrim(st.getHoverName().getString()).equals("Rabbit the Fish")) {
                fishSlot = slot.index;
                return;
            }
        }
    }

    private static void scanShop(AbstractContainerScreen<?> cs) {
        if (cs != shopScreen) {
            shopScreen = cs;
            TeslaMapsConfig c = cfg();
            int year = sbYear();
            if (c.hoppityShopYear != year) {
                c.hoppityShopYear = year;
                TeslaMapsConfig.save();
            }
        }
        if (!cfg().hoppityShopHighlight || timer % 5 != 0) return;
        shopSlots.clear();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            for (String l : ItemUtil.loreLines(st)) {
                if (l.trim().equals("Click to trade!")) { shopSlots.add(slot.index); break; }
            }
        }
    }

    /** ContainerOverlays. */
    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || (fishSlot < 0 && shopSlots.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !feature.active()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            int color;
            if (cs == fishScreen && slot.index == fishSlot) color = 0xAAFF5555;
            else if (cs == shopScreen && shopSlots.contains(slot.index)) color = 0xC855FF55;
            else continue;
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            g.fill(x, y, x + 16, y + 16, color);
        }
    }

    private static boolean protecting(AbstractContainerScreen<?> cs) {
        return feature != null && cs == fishScreen && fishSlot >= 0 && feature.active();
    }

    /** HandledScreenClickMixin: no way into the Chocolate Factory while Rabbit the Fish is unclaimed. */
    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (slot == null || !protecting(cs)) return false;
        if (slot.index == fishSlot && !(slot.container instanceof Inventory)) {
            fishClicked = true;
            fishSlot = -1;
            return false;
        }
        if (!(slot.container instanceof Inventory) && ItemUtil.loreContains(slot.getItem(), "Click to open Chocolate Factory!")) {
            preventedTitle();
            return true;
        }
        return false;
    }

    /** ContainerKeyMixin: Esc / inventory key while Rabbit the Fish is still in the menu. Shift bypasses. */
    public static boolean blockClose(AbstractContainerScreen<?> cs, KeyEvent event) {
        if (!protecting(cs) || event.hasShiftDown()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!event.isEscape() && !mc.options.keyInventory.matches(event)) return false;
        preventedTitle();
        return true;
    }

    private static void preventedTitle() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTitle(Component.literal("§cRabbit the Fish Prevented Close"));
        mc.gui.setSubtitle(Component.literal("§7Hold §eShift §7to bypass"));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1f));
    }

    private static void checkPet() {
        long now = System.currentTimeMillis();
        if (now - lastPetCheck < 30_000L) return;
        Boolean ok = mythicRabbit();
        if (ok == null || ok) return;
        lastPetCheck = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] Use a §dMythic Rabbit Pet §efor more chocolate!"));
        mc.gui.setTitle(Component.literal("§cNo Rabbit Pet!"));
    }

    // null = pet unknown (no widget, no tracker data)
    private static Boolean mythicRabbit() {
        List<String> lines = TabListUtils.getOrderedLines();
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (!StringUtil.strip(lines.get(i)).trim().equals("Pet:")) continue;
            String raw = lines.get(i + 1);
            String pet = StringUtil.strip(raw);
            if (pet.contains("No pet selected")) return false;
            return pet.contains("Rabbit") && raw.contains("§dRabbit");
        }
        if (com.teslamaps.features.pets.PetTracker.hasPet()) {
            return "RABBIT".equals(com.teslamaps.features.pets.PetTracker.internalName())
                    && "MYTHIC".equals(com.teslamaps.features.pets.PetTracker.rarity());
        }
        return null;
    }

    /** handleSoundEvent, main thread. True = cancel. */
    public static boolean onSoundPacket(ClientboundSoundPacket packet) {
        TeslaMapsConfig c = cfg();
        if ("Normal".equals(c.hoppityEggSounds) || feature == null) return false;
        if (System.currentTimeMillis() - lastInEggMenu > 2000L) return false;
        if (packet.getVolume() != 1.0f || packet.getSound().value() != SoundEvents.NOTE_BLOCK_BIT.value()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !feature.active()) return false;
        if (mc.player.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) >= 4.0) return false;
        if ("Revert".equals(c.hoppityEggSounds)) {
            mc.level.playLocalSound(packet.getX(), packet.getY(), packet.getZ(), SoundEvents.GENERIC_EAT.value(),
                    packet.getSource(), 1f, packet.getPitch(), false);
        }
        return true;
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        String text = StringUtil.strip(raw);
        if (!text.contains("Egg")) return;
        if (EGG_FOUND.matcher(text).find()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) shared.all().removeIf(w -> mc.player.distanceToSqr(Vec3.atCenterOf(w.pos)) < 64);
            return;
        }
        if (!c.hoppityEggShare || !isSpring()) return;
        Matcher m = SHARED.matcher(text);
        if (!m.find()) {
            m = SHARED_SKYBLOCKER.matcher(text);
            if (!m.find()) return;
        }
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
            shared.add(pos, "§bShared " + m.group(1) + " Egg", 0x55FFFF, 5 * 60_000L);
        } catch (NumberFormatException ignored) {}
    }

    private static void render(PoseStack pose, Vec3 cam) {
        shared.render(pose, cam);
        if (shopPending && "Hub".equals(SkyblockUtils.getArea())) {
            WaypointSet.draw(pose, cam, BlockPos.containing(HOPPITY), 0x55FF55, "§aHoppity's Shop", true, false, true);
        }
    }

    /** HidePlayers.alpha, every rendered player. 255 = untouched. */
    public static int playerAlpha(Entity entity) {
        List<Vec3> a = anchors;
        if (a.isEmpty() || !(entity instanceof Player) || !cfg().hoppityPlayerTransparency) return 255;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player || entity.getUUID().version() != 4 || mc.getConnection() == null) return 255;
        Vec3 p = entity.position();
        boolean near = false;
        for (int i = 0, n = a.size(); i < n; i++) {
            if (a.get(i).distanceToSqr(p) < 16) { near = true; break; }
        }
        if (!near || mc.getConnection().getPlayerInfo(entity.getUUID()) == null) return 255;
        return Math.round(Math.max(0f, Math.min(100f, cfg().hoppityPlayerOpacity)) * 2.55f);
    }
}
