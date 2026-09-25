package com.teslamaps.features.gardenqol;

// After NEU's MutingComposter, PresetWarning and ItemCooldowns (Sprayonator)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Composter mute, plot preset delete confirmation and the Sprayonator cooldown bar. */
public final class GardenSafety {

    // NEU mutes mob.wolf.growl, tile.piston.out, liquid.water, mob.chicken.plop in the garden
    private static final Set<String> COMPOSTER = Set.of(
            "minecraft:entity.wolf.growl", "minecraft:block.piston.extend",
            "minecraft:block.water.ambient", "minecraft:entity.chicken.egg");
    private static final Pattern SPRAYED = Pattern.compile("^SPRAYONATOR! You sprayed Plot - .+ with .+!");
    private static final Pattern COOLDOWN = Pattern.compile("Cooldown: (\\d+)s");
    private static final long CONFIRM_MS = 3000;

    private static Feature feature;
    private static int pendingSlot = -1;
    private static int pendingMenu = -1;
    private static long pendingAt;
    private static long sprayStart, sprayLength;

    private GardenSafety() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GardenSafety").section("Garden")
                .when(() -> cfg().gardenMuteComposter || cfg().plotPresetWarning || cfg().sprayonatorCooldownBar)
                .serverChat(GardenSafety::onChat)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Garden Extras");
                    b.toggle("Mute Composter", () -> c.gardenMuteComposter, v -> c.gardenMuteComposter = v)
                            .desc("Silences the composter's piston, water, growl and plop sounds while in the Garden.")
                            .tags("composter", "sound", "mute");
                    b.toggle("Confirm Deleting Plot Presets", () -> c.plotPresetWarning, v -> c.plotPresetWarning = v)
                            .desc("Plot Modification menus: the right click that deletes a preset has to be done twice within 3 seconds.")
                            .tags("preset", "plot", "delete", "protect");
                    b.toggle("Sprayonator Cooldown Bar", () -> c.sprayonatorCooldownBar, v -> c.sprayonatorCooldownBar = v)
                            .desc("The Sprayonator's durability bar shows its cooldown after spraying a plot.")
                            .tags("sprayonator", "spray", "cooldown", "durability");
                });
    }

    // --- composter ---
    public static boolean muteComposter(String id) {
        return cfg().gardenMuteComposter && cfg().section("Garden") && COMPOSTER.contains(id) && SkyblockUtils.isInGarden();
    }

    // --- preset delete ---
    public static boolean blockPresetDelete(AbstractContainerScreen<?> cs, Slot slot, int button, ContainerInput type) {
        if (feature == null || !feature.active() || !cfg().plotPresetWarning || slot == null) return false;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Plot Modification:")) return false;
        if (button != 1 || type != ContainerInput.PICKUP) return false;
        List<String> lore = ItemUtil.loreLines(slot.getItem());
        boolean deletes = false;
        for (String l : lore) if (l.contains("Right-Click to delete!")) { deletes = true; break; }
        if (!deletes) return false;
        long now = System.currentTimeMillis();
        int menu = cs.getMenu().containerId;
        if (pendingSlot == slot.index && pendingMenu == menu && now - pendingAt < CONFIRM_MS) {
            pendingSlot = -1;
            return false;
        }
        pendingSlot = slot.index;
        pendingMenu = menu;
        pendingAt = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cAre you SURE? §7Right-click §f"
                    + StringUtil.stripTrim(slot.getItem().getHoverName().getString()) + " §7again to delete it."));
        }
        return true;
    }

    // --- sprayonator ---
    private static void onChat(String raw) {
        if (!cfg().sprayonatorCooldownBar) return;
        if (!SPRAYED.matcher(StringUtil.stripTrim(raw)).matches()) return;
        Minecraft mc = Minecraft.getInstance();
        long len = 0;
        if (mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (ItemUtil.skyblockId(held).endsWith("SPRAYONATOR")) len = cooldown(held);
        }
        sprayStart = System.currentTimeMillis();
        sprayLength = len > 0 ? len : 2000L;
    }

    private static long cooldown(ItemStack st) {
        for (String l : ItemUtil.loreLines(st)) {
            Matcher m = COOLDOWN.matcher(l);
            if (m.find()) return Long.parseLong(m.group(1)) * 1000L;
        }
        return 0;
    }

    /** From ItemBarMixin: draws the cooldown bar, true replaces the vanilla bar. */
    public static boolean drawBar(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (sprayStart == 0 || stack.isEmpty() || !cfg().sprayonatorCooldownBar) return false;
        long left = sprayStart + sprayLength - System.currentTimeMillis();
        if (left <= 0) {
            sprayStart = 0;
            return false;
        }
        if (!ItemUtil.skyblockId(stack).endsWith("SPRAYONATOR")) return false;
        float done = 1f - (float) left / sprayLength;
        int w = Math.round(13 * done);
        int rgb = net.minecraft.util.Mth.hsvToRgb(done / 3f, 1f, 1f);
        g.fill(x + 2, y + 13, x + 15, y + 15, 0xFF000000);
        g.fill(x + 2, y + 13, x + 2 + w, y + 14, 0xFF000000 | rgb);
        return true;
    }
}
