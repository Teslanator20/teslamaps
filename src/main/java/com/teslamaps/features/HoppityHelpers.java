package com.teslamaps.features;

// Ported from SkyHanni's HoppityRabbitTheFishChecker, MythicRabbitPetWarning and HoppityMuteEggSounds
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Pattern;

public final class HoppityHelpers {

    public static final String[] SOUND_MODES = {"Normal", "Mute", "Revert"};

    private static final Pattern MEAL_EGG = Pattern.compile("Chocolate (?:Breakfast|Lunch|Dinner|Brunch|Déjeuner|Supper) Egg.*");
    private static final long SB_EPOCH = 1560275700000L;

    private static Feature feature;
    private static int timer;

    // meal egg menu
    private static Object fishScreen;
    private static int fishSlot = -1;
    private static boolean fishClicked;
    private static long lastInEggMenu;

    private static long lastPetCheck;

    private HoppityHelpers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.hoppityRabbitFish || c.hoppityPetWarning || !"Normal".equals(c.hoppityEggSounds);
    }

    public static void register() {
        feature = Feature.of("HoppityHelpers").section("Events").when(HoppityHelpers::any)
                .tick(HoppityHelpers::tick)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Rabbit the Fish Protection", () -> c.hoppityRabbitFish, v -> c.hoppityRabbitFish = v)
                            .desc("Meal egg menu: Rabbit the Fish is marked red and the menu can't be closed until you click it. Hold Shift to close anyway");
                    b.toggle("Mythic Rabbit Pet Warning", () -> c.hoppityPetWarning, v -> c.hoppityPetWarning = v)
                            .desc("Using the Egglocator without a Mythic Rabbit pet shows \"No Rabbit Pet!\". Reads the tab list pet widget");
                    b.dropdown("Egg Open Sounds", SOUND_MODES, () -> c.hoppityEggSounds, v -> c.hoppityEggSounds = v)
                            .desc("The note block sounds when you open a meal egg: leave them, mute them, or play the old eating sound");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && cfg().hoppityPetWarning && feature.active()
                    && "EGGLOCATOR".equals(ItemUtil.skyblockId(player.getItemInHand(hand)))) checkPet();
            return InteractionResult.PASS;
        });
    }

    private static boolean isSpring() {
        long sec = System.currentTimeMillis() / 1000L - SB_EPOCH / 1000L;
        return (sec % 446400L) / 37200L < 3;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        timer++;
        String title = mc.screen instanceof AbstractContainerScreen<?> cs ? StringUtil.stripTrim(cs.getTitle().getString()) : "";
        boolean eggMenu = MEAL_EGG.matcher(title).matches();
        if (eggMenu) lastInEggMenu = System.currentTimeMillis();

        if (c.hoppityRabbitFish && eggMenu && isSpring()) scanFish((AbstractContainerScreen<?>) mc.screen);
        else { fishScreen = null; fishSlot = -1; }
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

    /** ContainerOverlays. */
    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || fishSlot < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !feature.active()) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            if (cs != fishScreen || slot.index != fishSlot) continue;
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            g.fill(x, y, x + 16, y + 16, 0xAAFF5555);
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
}
