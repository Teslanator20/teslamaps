package com.teslamaps.features;

// After SkyHanni's NpcDayLimitTracker
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coins sold to NPCs today against Hypixel's 500M daily cap, reset at midnight GMT, per profile. */
public final class NpcDayLimit {

    private static final long LIMIT = 500_000_000L;
    private static final Pattern SOLD = Pattern.compile("^You sold .+ for ([\\d,]+) Coins?!$");
    private static final ProfiledFile FILE = new ProfiledFile("npc_day_limit.json", null);

    private static long day, sold;
    private static boolean loaded;
    private static String line = "";
    private static int ticks;
    private static HudEditElement hud;

    private NpcDayLimit() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("npcDayLimit", "NPC Sell Limit")
                .enabled(() -> cfg().npcDayLimit)
                .size(() -> Minecraft.getInstance().font.width(line.isEmpty() ? sample() : line) + 4, () -> 10)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, line.isEmpty() ? sample() : line));
        SbProfile.onChange(() -> loaded = false);
        Feature.of("NpcDayLimit").when(() -> cfg().npcDayLimit)
                .tick(NpcDayLimit::tick)
                .serverChat(NpcDayLimit::onChat)
                .hudHiddenInInventory("npcdaylimit", NpcDayLimit::render)
                .config("Tooltips & Value", b -> {
                    b.label("NPC Sell Limit");
                    b.toggle("NPC Sell Day Limit", () -> cfg().npcDayLimit, v -> cfg().npcDayLimit = v)
                            .desc("Adds up every \"You sold X for N Coins!\" against the 500M daily NPC limit (reset midnight GMT, per profile). Position in /tmap gui")
                            .tags("npc", "sell", "limit", "500m");
                    b.toggle("  Short Numbers", () -> cfg().npcDayLimitShort, v -> cfg().npcDayLimitShort = v)
                            .desc("400M/500M instead of 400,000,000/500,000,000");
                });
    }

    private static String sample() {
        return "§7NPC Sold: §6120M§f/§6500M";
    }

    private static void onChat(String raw) {
        Matcher m = SOLD.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        long amount;
        try { amount = Long.parseLong(m.group(1).replace(",", "")); } catch (NumberFormatException e) { return; }
        if (amount <= 0) return;
        ensure();
        roll();
        sold += amount;
        save();
        ticks = 0;
    }

    private static void tick() {
        if (ticks++ % 20 != 0) return;
        ensure();
        if (roll()) save();
        String s = fmt(sold), l = fmt(LIMIT);
        String col = sold >= LIMIT ? "§c" : sold >= LIMIT * 0.8 ? "§e" : "§6";
        line = "§7NPC Sold: " + col + s + "§f/§6" + l;
    }

    private static String fmt(long v) {
        return cfg().npcDayLimitShort ? EstimatedValue.fmt(v) : EstimatedValue.exact(v);
    }

    private static boolean roll() {
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        if (day == today) return false;
        day = today;
        sold = 0;
        return true;
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        day = 0;
        sold = 0;
        Path p = FILE.path();
        if (p == null || !Files.exists(p)) return;
        try {
            JsonObject o = new Gson().fromJson(Files.readString(p), JsonObject.class);
            if (o == null) return;
            if (o.has("day")) day = o.get("day").getAsLong();
            if (o.has("sold")) sold = o.get("sold").getAsLong();
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("npc_day_limit.json: {}", e.toString());
        }
    }

    private static void save() {
        Path p = FILE.path();
        if (p == null) return;
        try {
            JsonObject o = new JsonObject();
            o.addProperty("day", day);
            o.addProperty("sold", sold);
            Files.createDirectories(p.getParent());
            Files.writeString(p, o.toString());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("npc_day_limit.json: {}", e.toString());
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (hud == null || line.isEmpty() || mc.options.hideGui || !SkyblockUtils.isInSkyblock()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), line);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float s, String text) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        HudStyle.panel(g, -2, -2, mc.font.width(text) + 4, 12);
        g.text(mc.font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
