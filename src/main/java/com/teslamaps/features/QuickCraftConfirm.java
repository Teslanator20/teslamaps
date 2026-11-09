package com.teslamaps.features;

// Ported from SkyHanni's QuickCraftFeatures.kt, whitelist = SkyHanni repo QuickCraftableItems.json
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Craft Item / Quick Crafting: quick-craft results off the whitelist are greyed and need ctrl+click. */
public final class QuickCraftConfirm {

    private static final int NONE = 0, CRAFT_ITEM = 1, QUICK_CRAFTING = 2;
    private static Set<String> whitelist;
    private static Screen screen;
    private static int type;
    private static boolean[] greyed = new boolean[0];
    private static int timer;

    private QuickCraftConfirm() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("QuickCraftConfirm").section("Inventory").when(() -> cfg().quickCraftConfirm)
                .tick(QuickCraftConfirm::tick)
                .config("Inventory", b -> b.toggle("Quick Craft Confirmation",
                                () -> cfg().quickCraftConfirm, v -> cfg().quickCraftConfirm = v)
                        .desc("Craft Item / Quick Crafting: quick-craft results that eat materials by accident are greyed out and need ctrl+click"));
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (type == NONE || !active()) return;
            Slot slot = hovered();
            if (slot == null || slot.getItem() != stack || !isGreyed(slot)) return;
            for (int i = 0; i < lines.size(); i++) {
                if (StringUtil.stripTrim(lines.get(i).getString()).equals("Click to craft!")) {
                    lines.set(i, Component.literal("§cCtrl + Click to craft!"));
                    break;
                }
            }
        });
    }

    private static boolean active() {
        TeslaMapsConfig c = cfg();
        return c.quickCraftConfirm && c.section("Inventory") && Minecraft.getInstance().gui.screen() == screen;
    }

    private static void tick() {
        Screen s = Minecraft.getInstance().gui.screen();
        if (s != screen) {
            screen = s;
            type = NONE;
            timer = 0;
            if (s instanceof ContainerScreen && SkyblockUtils.isInSkyblock()) {
                String title = StringUtil.stripTrim(s.getTitle().getString());
                if (title.equals("Craft Item")) type = CRAFT_ITEM;
                else if (title.equals("Quick Crafting")) type = QUICK_CRAFTING;
            }
        }
        if (type == NONE || timer-- > 0) return;
        timer = 4;
        var slots = ((ContainerScreen) s).getMenu().slots;
        boolean[] out = new boolean[slots.size()];
        for (int i = 0; i < out.length; i++) out[i] = needsConfirm(slots.get(i));
        greyed = out;
    }

    private static boolean isGreyed(Slot slot) {
        return !(slot.container instanceof Inventory) && slot.index >= 0 && slot.index < greyed.length && greyed[slot.index];
    }

    private static Slot hovered() {
        return screen instanceof ContainerScreen cs ? ((HandledScreenAccessor) cs).getFocusedSlot() : null;
    }

    private static boolean needsConfirm(Slot slot) {
        if (slot == null || slot.container instanceof Inventory) return false;
        int i = slot.index;
        if (type == CRAFT_ITEM ? (i != 16 && i != 25 && i != 34) : (i < 10 || i > 44)) return false;
        ItemStack stack = slot.getItem();
        if (stack.isEmpty()) return false;
        String name = StringUtil.stripTrim(stack.getHoverName().getString());
        if (name.equals("Quick Crafting Slot")) return false;
        return !whitelist().contains(name);
    }

    private static boolean ctrl() {
        long h = Minecraft.getInstance().getWindow().handle();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (type == NONE || !active() || ctrl()) return;
        ContainerScreen cs = (ContainerScreen) screen;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        for (Slot slot : cs.getMenu().slots) {
            if (!isGreyed(slot)) continue;
            int x = acc.getX() + slot.x, y = acc.getY() + slot.y;
            ctx.fill(x, y, x + 16, y + 16, 0xB4555555);
        }
    }

    /** True = swallow this click. */
    public static boolean blockClick(Slot slot) {
        return type != NONE && active() && needsConfirm(slot) && !ctrl();
    }

    private static Set<String> whitelist() {
        if (whitelist != null) return whitelist;
        Set<String> out = new HashSet<>();
        try (var in = QuickCraftConfirm.class.getResourceAsStream("/assets/teslamaps/data/quick_craftable.json")) {
            for (JsonElement e : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray()) {
                out.add(e.getAsString());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to read quick_craftable.json: {}", e.toString());
        }
        whitelist = out;
        return out;
    }
}
