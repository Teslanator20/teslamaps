package com.teslamaps.features;

// Ported from SkyHanni's features/minion/InfernoMinionFeatures.kt + InfernoMinionProfitTracker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class InfernoMinion {

    private static final Pattern TITLE = Pattern.compile("Inferno Minion (?:[IVX]{1,4}|\\d+)$");
    private static final int FUEL_SLOT = 19, PICKUP_SLOT = 53;
    private static final String EYEDROPS = "CAPSAICIN_EYEDROPS_NO_CHARGES";
    private static final long AFTER_CLOSE_COUNT = 5_000, AFTER_CLOSE_SHOW = 10_000;

    private static Tracker tracker;
    private static HudEditElement hud;

    private static int containerId = -1;
    private static String lastFuel;
    private static final Set<String> minionItems = new HashSet<>();
    private static Map<String, Integer> inventory = new HashMap<>();
    private static long lastClose;
    private static int ticks;

    private InfernoMinion() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        tracker = Tracker.create("inferno_minion", "§e§lInferno Minion")
                .priceSource(() -> cfg().infernoTrackerPriceSource)
                .name(EYEDROPS, "§cCapsaicin Eyedrops");
        hud = HudEditElement.of("infernoTracker", "Inferno Minion Tracker").section("Inventory")
                .enabled(() -> cfg().infernoProfitTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        Feature.of("InfernoMinion").section("Inventory")
                .when(() -> cfg().infernoFuelBlocker || cfg().infernoProfitTracker)
                .tick(InfernoMinion::tick)
                .serverChat(InfernoMinion::onChat)
                .hudForeground("infernotracker", InfernoMinion::render)
                .config("Inventory", b -> {
                    b.label("Inferno Minion");
                    b.toggle("Inferno Fuel Blocker", () -> cfg().infernoFuelBlocker, v -> cfg().infernoFuelBlocker = v)
                            .desc("Blocks taking out Inferno fuel and picking up the minion while fuel is in it. Hold Ctrl to bypass");
                    b.toggle("Inferno Minion Profit Tracker", () -> cfg().infernoProfitTracker, v -> cfg().infernoProfitTracker = v)
                            .desc("Counts collected items minus fuel and eyedrops. Shown in the minion menu (/tmap gui)");
                    b.toggle("  Show After Collecting", () -> cfg().infernoTrackerAfterCollect, v -> cfg().infernoTrackerAfterCollect = v)
                            .desc("Keeps the tracker up for 10s after closing the minion");
                    b.priceSource("  Price Source", () -> cfg().infernoTrackerPriceSource, v -> cfg().infernoTrackerPriceSource = v);
                    b.trackerButtons(tracker);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().infernoFuelBlocker || containerId == -1) return;
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || cs.getMenu().containerId != containerId) return;
            Slot s = ((HandledScreenAccessor) cs).getFocusedSlot();
            if (s == null || s.getItem() != stack || !hasFuel(cs)) return;
            if (s.index == FUEL_SLOT) {
                lines.add(Component.empty());
                lines.add(Component.literal("§c[TeslaMaps] is blocking you from taking this out!"));
                lines.add(Component.literal("  §7(Bypass by holding Ctrl)"));
            } else if (s.index == PICKUP_SLOT) {
                lines.add(Component.empty());
                lines.add(Component.literal("§c[TeslaMaps] is blocking you from picking this minion up!"));
                lines.add(Component.literal("  §7(Bypass by holding Ctrl)"));
            }
        });
    }

    private static boolean isInferno(AbstractContainerScreen<?> cs) {
        return TITLE.matcher(StringUtil.stripTrim(cs.getTitle().getString())).find();
    }

    private static String fuelId(ItemStack s) {
        if (s.isEmpty() || s.is(Items.STAINED_GLASS_PANE.black()) || s.is(Items.STAINED_GLASS_PANE.gray())) return null;
        String name = StringUtil.stripTrim(s.getHoverName().getString());
        String id = ItemUtil.skyblockId(s);
        if (id.isEmpty()) {
            String byName = SkyblockItems.idForName(name);
            if (byName != null) id = byName;
        }
        boolean fuel = id.startsWith("INFERNO_FUEL") || name.contains("Inferno Minion Fuel") || name.contains("Gabagool");
        return fuel ? (id.isEmpty() ? name : id) : null;
    }

    private static boolean hasFuel(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        return slots.size() > FUEL_SLOT && fuelId(slots.get(FUEL_SLOT).getItem()) != null;
    }

    /** Slot click hook, true = cancel. */
    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot) {
        if (!cfg().infernoFuelBlocker || slot == null || !cfg().section("Inventory")) return false;
        if (slot.index != FUEL_SLOT && slot.index != PICKUP_SLOT) return false;
        if (slot.container instanceof Inventory || !isInferno(cs) || !hasFuel(cs)) return false;
        long w = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) != GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) != GLFW.GLFW_PRESS;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.gui.screen() instanceof AbstractContainerScreen<?> cs && isInferno(cs);
        if (open) {
            AbstractContainerScreen<?> cs = (AbstractContainerScreen<?>) mc.gui.screen();
            boolean fresh = cs.getMenu().containerId != containerId;
            containerId = cs.getMenu().containerId;
            if (cfg().infernoProfitTracker) trackMenu(mc, cs, fresh);
        } else if (containerId != -1) {
            containerId = -1;
            lastFuel = null;
            lastClose = System.currentTimeMillis();
        }
        if (!cfg().infernoProfitTracker) return;
        if (!open && System.currentTimeMillis() - lastClose < AFTER_CLOSE_COUNT) countInventory(mc);
        if (++ticks % 5 == 0 || open) tracker.tick();
    }

    private static void trackMenu(Minecraft mc, AbstractContainerScreen<?> cs, boolean fresh) {
        var slots = cs.getMenu().slots;
        if (slots.size() <= PICKUP_SLOT) return;
        String fuel = fuelId(slots.get(FUEL_SLOT).getItem());
        if (fresh) {
            minionItems.clear();
            inventory = snapshot(mc.player.getInventory());
        } else if (fuel != null && !fuel.equals(lastFuel)) {
            tracker.add(fuel, -1);
        }
        lastFuel = fuel;
        for (Slot s : slots) {
            if (s.container instanceof Inventory || s.index == FUEL_SLOT) continue;
            String id = ItemUtil.skyblockId(s.getItem());
            if (!id.isEmpty() && !id.contains("MINION") && !id.startsWith("INFERNO_FUEL")) minionItems.add(id);
        }
        countInventory(mc);
    }

    private static void countInventory(Minecraft mc) {
        if (mc.player == null || minionItems.isEmpty()) return;
        Map<String, Integer> now = snapshot(mc.player.getInventory());
        for (Map.Entry<String, Integer> e : now.entrySet()) {
            if (!minionItems.contains(e.getKey())) continue;
            int gained = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
            if (gained > 0) tracker.add(e.getKey(), gained);
        }
        inventory = now;
    }

    private static Map<String, Integer> snapshot(Inventory inv) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            String id = ItemUtil.skyblockId(s);
            if (!id.isEmpty()) m.merge(id, s.getCount(), Integer::sum);
        }
        return m;
    }

    private static void onChat(String raw) {
        if (!cfg().infernoProfitTracker) return;
        if (StringUtil.stripTrim(raw).equals("You applied the eyedrops on the minion and ran out!")) tracker.add(EYEDROPS, -1);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!cfg().infernoProfitTracker) return;
        boolean show = containerId != -1
                || cfg().infernoTrackerAfterCollect && lastClose != 0 && System.currentTimeMillis() - lastClose < AFTER_CLOSE_SHOW;
        if (!show) return;
        tracker.draw(g, Minecraft.getInstance(), hud.x(), hud.y(), hud.scale());
    }
}
