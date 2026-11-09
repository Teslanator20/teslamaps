package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;

import java.util.concurrent.ThreadLocalRandom;

// Ported from FloydAddons' FloydLoadoutSwapper
public class InstaLoadoutSwap {

    private static final int[] FALLBACK_SLOTS = {14, 15, 16, 23, 24, 25, 32, 33, 34};
    private static final boolean[] held = new boolean[9];

    private static int pending = -1;
    private static int openTicks = -1;
    private static long selectAt = -1;
    private static int closeTicks = -1;

    public static void register() {
        com.teslamaps.registry.Feature.of("InstaLoadoutSwap").section("Inventory").legit()
                .when(() -> TeslaMapsConfig.get().instaLoadoutSwap)
                .tick(InstaLoadoutSwap::tick);
    }

    private static void tick() {
        if (LegitMode.blocksCheats()) return;
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        AbstractContainerScreen<?> loadouts = mc.gui.screen() instanceof AbstractContainerScreen<?> cs
                && StringUtil.strip(cs.getTitle().getString()).endsWith("Loadouts") ? cs : null;

        if (pending >= 0) {
            if (loadouts != null) {
                long now = System.currentTimeMillis();
                if (selectAt < 0) selectAt = now + delayMs(c);
                if (now >= selectAt && select(mc, loadouts, pending)) {
                    pending = -1;
                    closeTicks = c.instaLoadoutAutoClose ? 5 : -1;
                }
                if (pending >= 0 && --openTicks < -60) pending = -1;
            } else if (--openTicks <= 0) {
                pending = -1;
            }
        }

        if (closeTicks >= 0) {
            if (loadouts == null) closeTicks = -1;
            else if (closeTicks-- == 0) loadouts.onClose();
        }

        boolean free = mc.gui.screen() == null;
        for (int i = 0; i < 9; i++) {
            int key = c.instaLoadoutKeys[i];
            boolean down = key != -1 && KeyNames.isDown(key);
            if (down && !held[i] && free && pending < 0 && mc.getConnection() != null) {
                pending = i;
                openTicks = 40;
                selectAt = -1;
                mc.getConnection().sendCommand("loadout");
            }
            held[i] = down || (!free && key != -1 && held[i]);
        }
    }

    private static long delayMs(TeslaMapsConfig c) {
        long base = (long) (Math.max(0.1f, c.instaLoadoutDelay) * 1000f);
        int rnd = Math.max(0, c.instaLoadoutRandomMs);
        return base + (rnd > 0 ? ThreadLocalRandom.current().nextLong(rnd + 1L) : 0);
    }

    private static boolean select(Minecraft mc, AbstractContainerScreen<?> cs, int idx) {
        if (mc.gameMode == null || mc.player == null) return false;
        if (!mc.player.containerMenu.getCarried().isEmpty()) return false;
        String title = StringUtil.strip(cs.getTitle().getString());
        int target = WardrobeKeys.findTarget(cs, title, true, idx);
        if (target < 0) {
            int s = FALLBACK_SLOTS[idx];
            if (s >= cs.getMenu().slots.size() || !cs.getMenu().slots.get(s).hasItem()) return false;
            target = s;
        }
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, target, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }
}
