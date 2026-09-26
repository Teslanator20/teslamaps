package com.teslamaps.features.rift;

// Ported from SkyHanni's KloonHacking.kt / KloonTerminal.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class KloonHacking {

    private static final String[] NAMES = {"RED", "ORANGE", "YELLOW", "GREEN", "AQUA", "BLUE", "PURPLE", "PINK"};
    private static final String[] LABELS = {"§4RED", "§4ORANGE", "§4YELLOW", "§4GREEN", "§4AQUA", "§4BLUE", "§4PURPLE", "§4PINK"};
    private static final double[][] POS = {
            {-69, 65, -63}, {-44, 71, -62}, {-39, 71, -95}, {-62, 71, -83},
            {-33, 70, -134.5}, {-66.5, 72, -119}, {-89, 73, -115}, {-110, 73, -107}};
    private static final String SET_PREFIX = "You've set the color of this terminal to ";

    private static final int GREEN = 0xC055FF55, RED = 0xC0FF5555, YELLOW = 0xC0FFFF55;

    private static boolean inRift, helmet;
    private static AbstractContainerScreen<?> seen;
    private static boolean hacking, picker;
    private static final String[] correct = new String[5];
    private static int nearest = -1;
    private static int ticks;
    private static Feature feature;

    private KloonHacking() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("KloonHacking").section("Rift").when(() -> cfg().kloonHackingSolver)
                .tick(KloonHacking::tick)
                .world(KloonHacking::render)
                .serverChat(KloonHacking::onChat)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Kloon Hacking Solver", () -> c.kloonHackingSolver, v -> c.kloonHackingSolver = v)
                            .desc("In the Hacking menu the right button in each row turns green (wrong: red), the matching buttons yellow.")
                            .tags("kloon", "hacking", "terminal", "rift", "west village");
                    b.toggle("  Color Picker Highlight", () -> c.kloonColorHelper, v -> c.kloonColorHelper = v)
                            .desc("Highlights the color of the nearest terminal in the Hacked Terminal Color Picker.");
                    b.toggle("  Terminal Waypoints", () -> c.kloonTerminalWaypoints, v -> c.kloonTerminalWaypoints = v)
                            .desc("With the Retro-Encabulating Visor on: boxes on all 8 terminals, done ones disappear.");
                    b.button("  Reset Done Terminals", () -> {
                        c.kloonDoneTerminals.clear();
                        TeslaMapsConfig.save();
                    });
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) {
            inRift = RiftSpot.inRift();
            helmet = inRift && "RETRO_ENCABULATING_VISOR".equals(ItemUtil.skyblockId(mc.player.getItemBySlot(EquipmentSlot.HEAD)));
        }
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            seen = null;
            hacking = picker = false;
            return;
        }
        if (!inRift) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (cs != seen) {
            seen = cs;
            hacking = title.equals("Hacking") || title.equals("Hacking (As seen on CSI)");
            picker = title.equals("Hacked Terminal Color Picker");
            if (picker) nearest = nearestTerminal(mc);
            for (int i = 0; i < 5; i++) correct[i] = null;
        }
        if (hacking) {
            var menu = cs.getMenu();
            for (int i = 0; i < 5; i++) {
                if (correct[i] != null || menu.slots.size() <= 2 + i) continue;
                var stack = menu.getSlot(2 + i).getItem();
                if (!stack.isEmpty()) correct[i] = StringUtil.stripTrim(stack.getHoverName().getString());
            }
        }
    }

    private static int nearestTerminal(Minecraft mc) {
        int best = -1;
        double bestD = 64;
        for (int i = 0; i < POS.length; i++) {
            double d = mc.player.distanceToSqr(POS[i][0], POS[i][1], POS[i][2]);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** From SlotBackgrounds. */
    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if ((!hacking && !picker) || slot.container instanceof Inventory) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != seen || feature == null || !feature.active()) return;
        int s = slot.getContainerSlot();
        if (hacking) {
            if (s < 9 || s > 53) return;
            int row = s / 9 - 1;
            String want = correct[row];
            if (want == null) return;
            boolean match = want.equals(StringUtil.stripTrim(slot.getItem().getHoverName().getString()));
            if (s == 11 + 10 * row) fill(ctx, slot, match ? GREEN : RED);
            else if (match && !slot.getItem().isEmpty()) fill(ctx, slot, YELLOW);
        } else if (cfg().kloonColorHelper && nearest >= 0 && !slot.getItem().isEmpty()
                && ItemUtil.loreContains(slot.getItem(), NAMES[nearest])) {
            fill(ctx, slot, GREEN);
        }
    }

    private static void fill(GuiGraphicsExtractor ctx, Slot slot, int color) {
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    private static void onChat(String raw) {
        if (!helmet) return;
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith(SET_PREFIX) || !t.endsWith("!")) return;
        String color = t.substring(SET_PREFIX.length(), t.length() - 1);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            int near = nearestTerminal(mc);
            if (near < 0 || !NAMES[near].equals(color)) return;
            TeslaMapsConfig c = cfg();
            if (!c.kloonDoneTerminals.contains(color)) {
                c.kloonDoneTerminals.add(color);
                TeslaMapsConfig.save();
            }
        });
    }

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (!helmet || !c.kloonTerminalWaypoints) return;
        for (int i = 0; i < NAMES.length; i++) {
            if (c.kloonDoneTerminals.contains(NAMES[i])) continue;
            double x = Math.floor(POS[i][0]), y = POS[i][1], z = Math.floor(POS[i][2]);
            AABB box = new AABB(x, y, z, x + 1, y + 1, z + 1);
            ESPRenderer.drawFilledBox(pose, box, 0x60AA0000, cam, true);
            ESPRenderer.drawBoxOutline(pose, box, 0xFFAA0000, 2f, cam, true);
            ESPRenderer.drawText(pose, LABELS[i], new Vec3(x + 0.5, y + 1.5, z + 0.5), 1f, cam);
        }
    }
}
