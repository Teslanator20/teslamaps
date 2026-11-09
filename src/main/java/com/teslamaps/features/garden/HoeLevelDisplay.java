package com.teslamaps.features.garden;

// Ported from SkyHanni's HoeLevelDisplay.kt + MuteHoeLevelUp.kt, levels from SkyHanni-REPO constants/Garden.json
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HoeLevelDisplay {

    private static final String URL = "https://raw.githubusercontent.com/hannibal002/SkyHanni-REPO/main/constants/Garden.json";
    private static final Pattern LEVEL_UP = Pattern.compile("^OVERFLOW! Your (.+) has just dropped a Tool Exp Capsule!$");
    private static final int OVERCLOCK_THRESHOLD = 40;
    private static final int MAX_LEVEL = 50;
    private static final List<String> SAMPLE = List.of("§6Hoe Levels", "§7Level §842➜§343", "§e412,000§8/§e450,000");

    private static volatile int[] levels = {1000, 2000, 3000, 5000, 8000, 12000, 16000, 20000, 25000, 40000, 60000,
            80000, 100000, 120000, 140000, 160000, 180000, 200000, 250000, 300000, 350000, 400000, 450000, 500000,
            550000, 600000, 650000, 700000, 750000, 800000, 850000, 900000, 950000, 1000000, 1050000, 1100000,
            1150000, 1200000, 1250000, 1300000, 1500000, 2000000, 2500000, 3000000, 3500000, 4000000, 4500000,
            5000000, 5500000};
    private static volatile int overflow = 200000;
    private static volatile boolean fetched;

    private static List<String> lines = List.of();
    private static int width;
    private static int ticks;
    private static HudEditElement hud;
    private static Feature feature;

    private HoeLevelDisplay() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("hoeLevelHud", "Hoe Levels").ctx(HudEditElement.HudCtx.GARDEN).section("Garden")
                .enabled(() -> cfg().hoeLevelHud)
                .size(() -> TextHud.width(SAMPLE), () -> TextHud.height(SAMPLE.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, SAMPLE, TextHud.width(SAMPLE)));
        feature = Feature.of("HoeLevelDisplay").section("Garden").when(() -> cfg().hoeLevelHud)
                .tick(HoeLevelDisplay::tick)
                .hudHiddenInInventory("hoelevels", HoeLevelDisplay::render)
                .config("Garden", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Farming Tool");
                    b.toggle("Hoe Level Display", () -> c.hoeLevelHud, v -> c.hoeLevelHud = v)
                            .desc("Level and tool XP of the held farming tool, with UPGRADE / OVERCLOCK REQUIRED and a wrong-crop warning. Position in /tmap gui.")
                            .tags("hoe", "tool", "level", "xp", "overclock");
                    b.toggle("  Count Overflow Levels", () -> c.hoeLevelOverflow, v -> c.hoeLevelOverflow = v)
                            .desc("Levels past 50 are counted per tool from the OVERFLOW chat line, which gets the level added. /tmap sethoelevel <level> corrects it.");
                    b.toggle("Mute Hoe Level-Up Sound", () -> c.muteHoeLevelUp, v -> c.muteHoeLevelUp = v)
                            .desc("Silences the portal sound Hypixel plays on a tool level-up, only in the Garden.")
                            .tags("mute", "sound", "portal", "hoe");
                });
        RepoCache.onReload("HoeLevelDisplay", () -> fetched = false);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("sethoelevel")
                        .then(ClientCommands.argument("level", IntegerArgumentType.integer(MAX_LEVEL))
                                .executes(c -> setLevel(IntegerArgumentType.getInteger(c, "level")))))));
    }

    private static void fetch() {
        if (fetched) return;
        fetched = true;
        Thread t = new Thread(() -> {
            try {
                JsonElement json = RepoCache.fetch("sh-Garden", URL, 10);
                if (!(json instanceof JsonObject o) || !(o.get("hoe_exp_levels") instanceof JsonArray arr)) return;
                int[] out = new int[arr.size()];
                for (int i = 0; i < out.length; i++) out[i] = arr.get(i).getAsInt();
                if (out.length > 0) levels = out;
                if (o.has("hoe_exp_overflow")) overflow = o.get("hoe_exp_overflow").getAsInt();
            } catch (Exception ignored) {
            }
        }, "teslamaps-hoelevels");
        t.setDaemon(true);
        t.start();
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData d = stack == null || stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? null : d.copyTag();
    }

    private static void tick() {
        if (++ticks % 5 != 0) return;
        fetch();
        Minecraft mc = Minecraft.getInstance();
        CompoundTag tag = SkyblockUtils.isInGarden() ? data(mc.player.getMainHandItem()) : null;
        if (tag == null || !tag.contains("levelable_exp") || !tag.contains("levelable_lvl")) {
            if (!lines.isEmpty()) lines = List.of();
            return;
        }
        long exp = (long) tag.getDoubleOr("levelable_exp", 0);
        int level = tag.getIntOr("levelable_lvl", 0);
        if (level <= 0) { lines = List.of(); return; }
        int[] table = levels;
        long next = level <= table.length ? table[level - 1] : overflow;
        if (level > table.length && cfg().hoeLevelOverflow) {
            String uuid = tag.getString("uuid").orElse(null);
            if (uuid != null) level += cfg().hoeOverflowLevels.getOrDefault(uuid, 0);
        }
        List<String> out = new ArrayList<>(5);
        out.add("§6Hoe Levels");
        out.add("§7Level §8" + level + "➜§3" + (level + 1));
        String color = "§e";
        if (exp > next) {
            color = "§c§l";
            out.add(level >= OVERCLOCK_THRESHOLD ? "§3§lOVERCLOCK REQUIRED!" : "§c§lUPGRADE REQUIRED!");
        }
        out.add(color + String.format(Locale.US, "%,d", exp) + "§8/§e" + String.format(Locale.US, "%,d", next));
        GardenCrop broken = GardenCropData.lastBroken();
        if (broken != null && broken != GardenCropData.inHand()) out.add("§cNot gaining XP! (Wrong crop)");
        lines = out;
        width = TextHud.width(out);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    /** From ChatMixin: counts the overflow level and appends it to the line. */
    public static Component rewrite(Component message) {
        if (feature == null || !cfg().hoeLevelOverflow || !feature.active()) return message;
        String text = StringUtil.stripTrim(message.getString());
        if (!text.startsWith("OVERFLOW! ")) return message;
        Matcher m = LEVEL_UP.matcher(text);
        if (!m.matches()) return message;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return message;
        ItemStack held = mc.player.getMainHandItem();
        if (!StringUtil.stripTrim(held.getHoverName().getString()).contains(m.group(1))) return message;
        CompoundTag tag = data(held);
        String uuid = tag == null ? null : tag.getString("uuid").orElse(null);
        if (uuid == null) return message;
        int extra = cfg().hoeOverflowLevels.getOrDefault(uuid, 0) + 1;
        cfg().hoeOverflowLevels.put(uuid, extra);
        TeslaMapsConfig.save();
        int base = tag.getIntOr("levelable_lvl", MAX_LEVEL);
        return message.copy().append(Component.literal(" §8(§3Level " + (base + extra) + "§8)"));
    }

    private static int setLevel(int newLevel) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        ItemStack held = mc.player.getMainHandItem();
        CompoundTag tag = data(held);
        String uuid = tag == null ? null : tag.getString("uuid").orElse(null);
        int real = tag == null ? 0 : tag.getIntOr("levelable_lvl", 0);
        if (uuid == null || real <= 0) {
            say("§cHold a farming tool with a level first");
            return 0;
        }
        if (real < MAX_LEVEL) {
            say("§cTools below level " + MAX_LEVEL + " have no overflow levels");
            return 0;
        }
        int old = MAX_LEVEL + cfg().hoeOverflowLevels.getOrDefault(uuid, 0);
        cfg().hoeOverflowLevels.put(uuid, newLevel - MAX_LEVEL);
        TeslaMapsConfig.save();
        say("§fOverflow level of §r" + held.getHoverName().getString() + " §fchanged from §b" + old + " §fto §b" + newLevel);
        return 1;
    }

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
