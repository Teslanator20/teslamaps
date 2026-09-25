package com.teslamaps.features;

// Ported from SkyHanni's SuperCraftingInventory + SuperCraftPresets
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.TeslaButton;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Supercraft: blocks crafts that lose coins against sell-mats / buy-result, amount presets on the sign. */
public final class SupercraftHelper {

    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int PICKAXE_SLOT = 32, RESULT_SLOT = 25;
    private static final Pattern COUNT = Pattern.compile("Crafting ([\\d,]+) item");
    private static final Pattern RESOURCE = Pattern.compile("^\\s*✔ ([\\d,]+)/([\\d,]+) (?:\\([\\d,]+x\\) )?(.+)$");

    private static Feature waste, presets;
    private static long blockedAt;
    private static String blockedText = "";

    private SupercraftHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        waste = Feature.of("SupercraftWasteBlock").section("Inventory").when(() -> cfg().supercraftWasteBlock);
        presets = Feature.of("SupercraftPresets").section("Inventory").when(() -> cfg().supercraftPresets);
        waste.config("Inventory", b -> {
            b.label("Supercraft");
            b.toggle("Supercraft Waste Block", () -> cfg().supercraftWasteBlock, v -> cfg().supercraftWasteBlock = v)
                    .desc("Blocks the Supercraft click when instant-selling the materials and instant-buying the result is much cheaper. Hold Ctrl to bypass.")
                    .tags("supercraft", "waste", "craft loss");
            b.slider("  Loss Limit (M, Cookie)", 1f, 100f, () -> cfg().supercraftWasteThreshold, v -> cfg().supercraftWasteThreshold = v, 0);
            b.slider("  Loss Limit Max Craft (M, Cookie)", 1f, 100f, () -> cfg().supercraftWasteBulkThreshold, v -> cfg().supercraftWasteBulkThreshold = v, 0);
            b.slider("  Loss Limit (M, No Cookie)", 1f, 100f, () -> cfg().supercraftWasteThresholdNoCookie, v -> cfg().supercraftWasteThresholdNoCookie = v, 0);
            b.slider("  Loss Limit Max Craft (M, No Cookie)", 1f, 100f, () -> cfg().supercraftWasteBulkThresholdNoCookie, v -> cfg().supercraftWasteBulkThresholdNoCookie = v, 0);
            b.toggle("Supercraft Amount Presets", () -> cfg().supercraftPresets, v -> cfg().supercraftPresets = v)
                    .desc("Clickable amounts on the \"Enter amount of crafts\" sign. /tmap supercraftpreset <n> adds or removes one.")
                    .tags("supercraft", "preset", "sign");
        });

        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof AbstractSignEditScreen sign) || !presets.active()) return;
            String[] m = ((AbstractSignEditScreenAccessor) sign).getMessages();
            if (m == null || m.length < 4 || !m[1].equals("^^^^^^") || !m[2].equals("Enter amount") || !m[3].equals("of crafts")) return;
            List<Integer> list = cfg().supercraftPresetAmounts;
            if (list.isEmpty()) return;
            int perRow = 4, bw = 44, bh = 16, gap = 3;
            for (int i = 0; i < list.size(); i++) {
                int row = i / perRow, col = i % perRow;
                int inRow = Math.min(perRow, list.size() - row * perRow);
                int rowW = inRow * bw + (inRow - 1) * gap;
                int x = width / 2 - rowW / 2 + col * (bw + gap);
                int y = 8 + row * (bh + gap);
                int amount = list.get(i);
                Screens.getWidgets(screen).add(new TeslaButton(x, y, bw, bh, "§e" + String.format("%,d", amount),
                        () -> setSign(sign, String.valueOf(amount))));
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("supercraftpreset")
                        .executes(c -> { showPresets(); return 1; })
                        .then(ClientCommands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(c -> {
                                    togglePreset(IntegerArgumentType.getInteger(c, "amount"));
                                    return 1;
                                })))));
    }

    private static void setSign(AbstractSignEditScreen sign, String text) {
        AbstractSignEditScreenAccessor acc = (AbstractSignEditScreenAccessor) sign;
        int old = acc.getLine();
        acc.setLine(0);
        acc.invokeSetMessage(text);
        if (old == 0 && acc.getSignField() != null) acc.getSignField().setCursorToEnd();
        acc.setLine(old);
    }

    private static void togglePreset(int n) {
        List<Integer> list = cfg().supercraftPresetAmounts;
        if (list.remove(Integer.valueOf(n))) msg("§cRemoved §e" + String.format("%,d", n) + " §cfrom the supercraft presets.");
        else {
            list.add(n);
            msg("§aAdded §e" + String.format("%,d", n) + " §ato the supercraft presets.");
        }
        list.sort(Integer::compare);
        TeslaMapsConfig.save();
        showPresets();
    }

    private static void showPresets() {
        List<Integer> list = cfg().supercraftPresetAmounts;
        StringBuilder sb = new StringBuilder("§7Supercraft presets: ");
        if (list.isEmpty()) sb.append("§cnone");
        for (int i = 0; i < list.size(); i++) sb.append(i == 0 ? "§e" : "§7, §e").append(String.format("%,d", list.get(i)));
        msg(sb.toString());
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §r" + s));
    }

    // ---------------------------------------------------------------- waste block

    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
        if (slot == null || waste == null || !waste.active()) return false;
        if (slot.index != PICKAXE_SLOT || button != 0 || type != ContainerInput.PICKUP) return false;
        if (!StringUtil.stripTrim(screen.getTitle().getString()).endsWith(" Recipe")) return false;
        if (ctrl()) return false;
        List<Slot> slots = screen.getMenu().slots;
        if (slots.size() <= PICKAXE_SLOT) return false;
        List<String> lore = ItemUtil.loreLines(slots.get(PICKAXE_SLOT).getItem());
        long crafting = count(lore);
        if (crafting <= 0) return false;
        ItemStack result = slots.get(RESULT_SLOT).getItem();
        if (result.isEmpty()) return false;
        int mult = Math.max(1, result.getCount());
        Double profit = profit(slots, crafting, mult, result);
        if (profit == null) return false;
        long max = maxCrafts(lore, crafting, mult);
        boolean cookie = cookie();
        double limit = (cookie ? cfg().supercraftWasteThreshold : cfg().supercraftWasteThresholdNoCookie) * 1_000_000d;
        double bulk = (cookie ? cfg().supercraftWasteBulkThreshold : cfg().supercraftWasteBulkThresholdNoCookie) * 1_000_000d;
        boolean block = profit < -limit || (profit < -bulk && crafting == max);
        if (!block) return false;
        String loss = EstimatedValue.fmt(-profit);
        blockedAt = System.currentTimeMillis();
        blockedText = "§cSupercraft blocked, potential loss: " + loss + " §7(Ctrl to bypass)";
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 1f));
        msg("§eSupercraft blocked: instant selling the materials and instant buying the result is cheaper (§c" + loss + "§e).");
        return true;
    }

    private static boolean ctrl() {
        long w = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    private static boolean cookie() {
        String f = StringUtil.strip(com.teslamaps.dungeon.DungeonInfoHud.footer());
        int i = f.indexOf("Cookie Buff");
        if (i < 0) return true;
        return !f.substring(i + 11).trim().startsWith("Not active");
    }

    private static long count(List<String> lore) {
        Matcher m = COUNT.matcher(String.join(" ", lore));
        if (!m.find()) return -1;
        try { return Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return -1; }
    }

    private static long maxCrafts(List<String> lore, long crafting, int mult) {
        long min = Long.MAX_VALUE;
        long crafts = Math.max(1, crafting / mult);
        for (String l : lore) {
            Matcher m = RESOURCE.matcher(l);
            if (!m.matches()) continue;
            try {
                long owned = Long.parseLong(m.group(1).replace(",", ""));
                long used = Long.parseLong(m.group(2).replace(",", ""));
                long per = used / crafts;
                if (per <= 0 || owned <= 0) continue;
                min = Math.min(min, owned / per);
            } catch (NumberFormatException ignored) {}
        }
        return min == Long.MAX_VALUE ? -1 : min;
    }

    private static Double profit(List<Slot> slots, long crafting, int mult, ItemStack result) {
        Map<String, Long> mats = new HashMap<>();
        for (int i : MATERIAL_SLOTS) {
            if (i >= slots.size()) continue;
            ItemStack s = slots.get(i).getItem();
            if (s.isEmpty()) continue;
            String id = id(s);
            if (id == null) return null;
            mats.merge(id, (long) s.getCount(), Long::sum);
        }
        if (mats.isEmpty()) return null;
        long crafts = crafting / mult;
        double matValue = 0;
        for (Map.Entry<String, Long> e : mats.entrySet()) {
            Double p = PriceManager.bazaarSellOrNull(e.getKey());
            if (p == null) return null;
            matValue += p * e.getValue() * crafts;
        }
        String rid = id(result);
        Double rp = rid == null ? null : PriceManager.bazaarBuyOrNull(rid);
        if (rp == null) return null;
        return rp * crafting - matValue;
    }

    private static String id(ItemStack s) {
        String id = ItemUtil.skyblockId(s);
        if (!id.isEmpty()) return id;
        return PriceManager.idForName(s.getHoverName().getString());
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (blockedAt == 0 || System.currentTimeMillis() - blockedAt > 2500) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int w = mc.font.width(blockedText);
        int x = acc.getX() + acc.getImageWidth() / 2 - w / 2;
        int y = acc.getY() - 14;
        com.teslamaps.render.gui.HudStyle.panel(ctx, x - 4, y - 3, w + 8, mc.font.lineHeight + 5);
        ctx.text(mc.font, blockedText, x, y, 0xFFFFFFFF);
    }
}
