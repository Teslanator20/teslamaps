package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

public class SlotLock {

    private static int lastSelected = -1;
    private static long switchedAt;
    private static List<Integer> cached;
    private static String cachedKey, cachedSb;
    private static boolean cachedRift;

    private static boolean isPlayerInv(Slot slot) {
        return slot != null && slot.container instanceof Inventory;
    }

    // per profile / rift sets after Skysoft's SlotLockManager and Devonian's SlotLocking
    private static List<Integer> locks() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLockPerProfile && !c.slotLockRiftSet) return c.lockedSlots;
        boolean rift = c.slotLockRiftSet && com.teslamaps.utils.SkyblockUtils.isInArea("The Rift");
        String sb = c.slotLockPerProfile ? com.teslamaps.utils.SbProfile.key() : null;
        if (!rift && sb == null) return c.lockedSlots;
        if (cached != null && rift == cachedRift && sb == cachedSb && c.slotLockData.get(cachedKey) == cached) return cached;
        String key = (rift ? "rift" : "") + (sb != null ? ":sb:" + sb : "");
        List<Integer> l = c.slotLockData.get(key);
        if (l == null) {
            l = key.startsWith("rift") ? new ArrayList<>() : new ArrayList<>(c.lockedSlots);
            c.slotLockData.put(key, l);
        }
        cached = l;
        cachedKey = key;
        cachedRift = rift;
        cachedSb = sb;
        return l;
    }

    public static boolean isLocked(int containerSlot) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (locks().contains(containerSlot)) return true;
        return c.slotLockBinds && c.slotBinds && SlotBinds.isBound(containerSlot) && !Minecraft.getInstance().hasShiftDown();
    }

    public static void resetAll() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.lockedSlots.clear();
        c.slotLockData.clear();
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7[TeslaMaps] All slot locks cleared"));
    }

    private static boolean worldKeyDown;

    public static void register() {
        com.teslamaps.registry.Feature.of("SlotLockWorld")
                .when(() -> TeslaMapsConfig.get().slotLock && TeslaMapsConfig.get().slotLockWorldKey != -1)
                .tick(SlotLock::tickWorldKey);
        com.teslamaps.registry.Feature.of("SlotLockHotbar")
                .when(() -> TeslaMapsConfig.get().slotLock && TeslaMapsConfig.get().slotLockHotbarIcon
                        && !TeslaMapsConfig.get().lockedSlots.isEmpty())
                .hud("slotlockhotbar", SlotLock::renderHotbar);
        com.teslamaps.registry.Feature.of("SlotLock")
                .when(() -> TeslaMapsConfig.get().slotLock && TeslaMapsConfig.get().slotLockDropDelay > 0)
                .tick(SlotLock::tick);
    }

    private static void tickWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.gui.screen() == null && com.teslamaps.utils.KeyNames.isDown(TeslaMapsConfig.get().slotLockWorldKey);
        if (down && !worldKeyDown && mc.player != null) toggle(mc.player.getInventory().getSelectedSlot());
        worldKeyDown = down;
    }

    private static void renderHotbar(GuiGraphicsExtractor ctx, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.player.isSpectator()) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        int col = (TeslaMapsConfig.parseColor(c.colorSlotLock) & 0x00FFFFFF) | 0xE0000000;
        int left = ctx.guiWidth() / 2 - 91, top = ctx.guiHeight() - 19;
        for (int i = 0; i < 9; i++) {
            if (isLocked(i)) com.teslamaps.render.gui.LockIcon.draw(ctx, left + 3 + i * 20 + 9, top, col);
        }
    }

    public static boolean onKey(int key, Slot hovered) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock || c.slotLockKey == -1 || key != c.slotLockKey) return false;
        if (!isPlayerInv(hovered)) return false;
        toggle(hovered.getContainerSlot());
        return true;
    }

    private static void toggle(int s) {
        List<Integer> set = locks();
        boolean locked;
        if (set.remove((Integer) s)) locked = false;
        else { set.add(s); locked = true; }
        TeslaMapsConfig.save();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, locked ? 1.5f : 0.7f);
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    locked ? "§a[TeslaMaps] §fLocked slot §e" + s : "§7[TeslaMaps] Unlocked slot " + s));
        }
    }

    public static boolean blockSlot(Slot slot) {
        return TeslaMapsConfig.get().slotLock && isPlayerInv(slot) && isLocked(slot.getContainerSlot());
    }

    public static final String[] STYLES = {"Box", "Icon"};

    /** Any click on a locked slot, and a number-key / offhand swap into a locked hotbar slot. */
    public static boolean blockSlotClick(Slot slot, int button, net.minecraft.world.inventory.ContainerInput type) {
        if (!TeslaMapsConfig.get().slotLock) return false;
        if (isPlayerInv(slot) && isLocked(slot.getContainerSlot())) return true;
        return type == net.minecraft.world.inventory.ContainerInput.SWAP && isLocked(button);
    }

    public static boolean blockSelectedDrop() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock) return false;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && isLocked(mc.player.getInventory().getSelectedSlot()) && !shiningCoin(mc);
    }

    // Skyblocker's ignoreSlotLockingForShiningCoins
    private static boolean shiningCoin(Minecraft mc) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.slotLockShiningCoins && mc.player != null
                && com.teslamaps.utils.ItemUtil.skyblockId(mc.player.getMainHandItem()).equals("SHINING_COIN")
                && com.teslamaps.safari.SafariData.inSafari()
                && com.teslamaps.safari.SafariData.currentBiome() == com.teslamaps.safari.SafariData.Biome.HAUNTED;
    }

    /** Q drop: locked slot, or the held slot changed less than the drop delay ago. */
    public static boolean blockDrop() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        int sel = mc.player.getInventory().getSelectedSlot();
        if (c.slotLockDropDelay > 0) {
            long now = System.currentTimeMillis();
            if (sel != lastSelected) {
                lastSelected = sel;
                switchedAt = now;
            }
            if (now - switchedAt < c.slotLockDropDelay) return true;
        }
        return isLocked(sel) && !shiningCoin(mc);
    }

    // NEU's drop delay: remember when the held slot changed
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        int sel = mc.player.getInventory().getSelectedSlot();
        if (sel != lastSelected) {
            lastSelected = sel;
            switchedAt = System.currentTimeMillis();
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.slotLock) return;
        if (locks().isEmpty() && !(c.slotLockBinds && c.slotBinds)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        int col = TeslaMapsConfig.parseColor(c.colorSlotLock);
        for (Slot slot : cs.getMenu().slots) {
            if (!isPlayerInv(slot) || !isLocked(slot.getContainerSlot())) continue;
            int sx = left + slot.x, sy = top + slot.y;
            if ("Icon".equals(c.slotLockStyle)) {
                com.teslamaps.render.gui.LockIcon.draw(ctx, sx, sy, (col & 0x00FFFFFF) | 0xC0000000);
                continue;
            }
            ctx.fill(sx, sy, sx + 16, sy + 16, 0x80FF0000);
            ctx.fill(sx, sy, sx + 16, sy + 1, col);
            ctx.fill(sx, sy + 15, sx + 16, sy + 16, col);
            ctx.fill(sx, sy, sx + 1, sy + 16, col);
            ctx.fill(sx + 15, sy, sx + 16, sy + 16, col);
        }
    }
}
