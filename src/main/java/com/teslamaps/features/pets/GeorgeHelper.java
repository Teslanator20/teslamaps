package com.teslamaps.features.pets;

// Sell price after Skyblocker's GeorgePriceTooltip (hysky.de/api/georgeprices), Taming 60 list after SkyHanni's GeorgeHelper.kt
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.features.PetValue;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GeorgeHelper {

    private static final String URL = "https://hysky.de/api/georgeprices";
    private static final long REFRESH_MS = 60 * 60_000L;
    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    private static final String[] TIER_COLORS = {"§f", "§a", "§9", "§5", "§6", "§d"};
    private static final Pattern NEEDED = Pattern.compile("(?i)^\\s*(uncommon|common|rare|epic|legendary|mythic) ([A-Za-z][A-Za-z' -]+)$");
    private static final int EGG_SLOT = 41;
    private static final int ROW_H = 10;

    private record Row(String text, String search, double price) {}

    private static final Map<String, Double> prices = new ConcurrentHashMap<>();
    private static volatile long fetched;
    private static volatile boolean loading;

    private static Feature feature;
    private static List<Row> rows = List.of();
    private static String total = "";
    private static long builtAt, lastClick;
    private static int listX, listY, listW;
    private static CustomData tipData;
    private static Component tipLine;

    private GeorgeHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("GeorgeHelper").section("Skills")
                .when(() -> cfg().georgePetPrice || cfg().georgeTamingHelper)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("George Pet Sell Price", () -> c.georgePetPrice, v -> c.georgePetPrice = v)
                            .desc("Level 1 pets (0 XP) get a \"Pet Sell Price\" line with what George pays for them. "
                                    + "Prices from hysky.de (Skyblocker's API)")
                            .tags("george", "pet", "sell", "price");
                    b.toggle("George Taming 60 Helper", () -> c.georgeTamingHelper, v -> c.georgeTamingHelper = v)
                            .desc("George's \"Offer Pets\" menu: the pets still needed for Taming 60 with the cheapest lowest BIN "
                                    + "and the total. Click a row to search it in the AH")
                            .tags("george", "taming", "taming 60", "offer pets");
                    b.toggle("  Check Rarity Above / Below", () -> c.georgeOtherTiers, v -> c.georgeOtherTiers = v)
                            .desc("Also looks one rarity lower and higher, upgrading through Kat is not counted");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().georgePetPrice || stack.isEmpty() || lines.isEmpty() || feature == null || !feature.active()) return;
            Component line = sellLine(stack);
            if (line != null) lines.add(line);
        });
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs)) return;
            if (!StringUtil.stripTrim(screen.getTitle().getString()).equals("Offer Pets")) return;
            builtAt = 0;
            rows = List.of();
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
                if (!helperOn()) { listW = 0; return; }
                if (System.currentTimeMillis() - builtAt > 1000) build(cs);
                draw(cs, g, mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !helperOn() || e.button() != 0 || !click(e.x(), e.y()));
        });
    }

    private static boolean helperOn() {
        return cfg().georgeTamingHelper && feature != null && feature.active();
    }

    // ---------------------------------------------------------------- sell price

    private static Component sellLine(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        if (d == null || !"PET".equals(ItemUtil.skyblockId(stack))) return null;
        if (d != tipData) {
            tipData = d;
            tipLine = null;
            PetValue.Pet pet = PetValue.parse(d.copyTag().getStringOr("petInfo", ""));
            if (pet != null && pet.exp() <= 0) {
                ensurePrices();
                Double p = prices.get(pet.type() + ";" + tierIndex(pet.tier()));
                if (p != null) tipLine = Component.literal("§ePet Sell Price: §6" + EstimatedValue.fmt(p) + " coins");
                else if (prices.isEmpty()) tipData = null;
            }
        }
        return tipLine;
    }

    private static int tierIndex(String tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equalsIgnoreCase(tier)) return i;
        return 0;
    }

    private static void ensurePrices() {
        if (loading || System.currentTimeMillis() - fetched < REFRESH_MS) return;
        loading = true;
        fetched = System.currentTimeMillis();
        CompletableFuture.runAsync(() -> {
            try {
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(10)).build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(20)).GET().build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() == 200) {
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    for (var e : json.entrySet()) {
                        try { prices.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                TeslaMaps.LOGGER.warn("George price fetch failed: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    // ---------------------------------------------------------------- taming 60

    private static void build(AbstractContainerScreen<?> cs) {
        builtAt = System.currentTimeMillis();
        if (cs.getMenu().slots.size() <= EGG_SLOT) return;
        ItemStack egg = cs.getMenu().getSlot(EGG_SLOT).getItem();
        if (egg.isEmpty()) return;
        List<Row> out = new ArrayList<>();
        double sum = 0;
        for (String line : ItemUtil.loreLines(egg)) {
            Matcher m = NEEDED.matcher(line);
            if (!m.matches()) continue;
            String petName = m.group(2).trim();
            int tier = tierIndex(m.group(1));
            String type = PetConstants.internalName(petName);
            int best = tier;
            Double bestPrice = PetValue.lvl1Price(TIERS[tier], type);
            if (cfg().georgeOtherTiers) {
                for (int t = tier - 1; t <= tier + 1; t += 2) {
                    if (t < 0 || t > 4) continue;
                    Double p = PetValue.lvl1Price(TIERS[t], type);
                    if (p != null && (bestPrice == null || p < bestPrice)) { best = t; bestPrice = p; }
                }
            }
            String label = TIER_COLORS[best] + TIER_NAMES[best] + " " + petName;
            if (bestPrice != null) {
                sum += bestPrice;
                out.add(new Row("§7- " + label + "§7: §6" + EstimatedValue.fmt(bestPrice), petName, bestPrice));
            } else {
                out.add(new Row("§7- " + label + "§7: §cno price", petName, -1));
            }
        }
        rows = out;
        total = "§7Total: §6" + EstimatedValue.fmt(sum) + " coins";
    }

    private static void draw(AbstractContainerScreen<?> cs, GuiGraphicsExtractor g, int mx, int my) {
        List<Row> r = rows;
        if (r.isEmpty()) { listW = 0; return; }
        Font font = Minecraft.getInstance().font;
        HandledScreenAccessor a = (HandledScreenAccessor) cs;
        int w = font.width(total);
        for (Row row : r) w = Math.max(w, font.width(row.text()));
        listW = Math.min(w + 8, a.getX() - 10);
        if (listW < 80) { listW = 0; return; }
        listX = a.getX() - listW - 6;
        listY = a.getY();
        HudStyle.screenPanel(g, listX, listY, listW, 28 + r.size() * ROW_H, 4);
        g.text(font, "§dTaming 60 Helper", listX + 4, listY + 4, 0xFFFFFFFF);
        for (int i = 0; i < r.size(); i++) {
            int y = listY + 15 + i * ROW_H;
            if (mx >= listX && mx < listX + listW && my >= y && my < y + ROW_H) g.fill(listX + 2, y, listX + listW - 2, y + ROW_H, 0x30FFFFFF);
            g.text(font, r.get(i).text(), listX + 4, y + 1, 0xFFFFFFFF);
        }
        g.text(font, total, listX + 4, listY + 17 + r.size() * ROW_H, 0xFFFFFFFF);
    }

    private static boolean click(double mx, double my) {
        if (listW <= 0 || mx < listX || mx >= listX + listW) return false;
        int i = (int) Math.floor((my - listY - 15) / ROW_H);
        List<Row> r = rows;
        if (my < listY + 15 || i < 0 || i >= r.size()) return false;
        long now = System.currentTimeMillis();
        if (now - lastClick < 500) return true;
        lastClick = now;
        Minecraft mc = Minecraft.getInstance();
        // packet, not sendCommand: a client /ahs would catch it
        if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket("ahs " + r.get(i).search().toLowerCase(Locale.ROOT)));
        return true;
    }
}
