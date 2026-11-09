package com.teslamaps.features;

// Ported from Skyblocker's MuseumManager / ItemSorter and SkyOcean's MuseumDonationHelper
import com.google.gson.JsonObject;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.CraftCost;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.profileviewer.data.MuseumRepo;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MuseumHelper {

    public static final String[] SORTS = {"Lowest Price", "Craft Cost", "Coins per XP"};
    private static final int ROW = 10;
    private static final int GREEN = 0x55FF55, YELLOW = 0xFFFF55, ORANGE = 0xFFAA00, RED = 0xFF5555;

    private record Missing(String name, Double price, Double craft, int xp) {}
    private record Mark(int slot, int color) {}

    private static volatile Set<String> donated;
    private static String donatedKey;
    private static long requestedAt;
    private static List<String> lines = List.of();
    private static int linesWidth;
    private static List<Mark> marks = List.of();
    private static final Map<String, Integer> markCache = new HashMap<>();
    private static int ticks;
    private static Feature feature;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("MuseumHelper").section("Inventory")
                .when(() -> cfg().museumHelper)
                .tick(MuseumHelper::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Museum Helper");
                    b.toggle("Museum Donation Helper", () -> c.museumHelper, v -> c.museumHelper = v);
                    b.info("In the museum: missing donations by price, missing slots coloured");
                    b.info("green = on your profile, yellow = materials in inventory, orange = craftable, red = no recipe");
                    b.dropdown("  Sort", SORTS, () -> c.museumHelperSort, v -> c.museumHelperSort = v);
                    b.dropdown("  Category", categories(), () -> c.museumHelperCategory, v -> c.museumHelperCategory = v);
                    b.slider("  Rows", 5, 40, () -> (float) c.museumHelperRows, v -> c.museumHelperRows = Math.round(v));
                });
    }

    private static String[] categories() {
        MuseumRepo.Data data = MuseumRepo.get();
        List<String> out = new ArrayList<>();
        out.add("All");
        if (data != null) for (String k : data.items().keySet()) out.add(StringUtil.titleCase(k));
        return out.toArray(new String[0]);
    }

    private static boolean inMuseum(AbstractContainerScreen<?> cs) {
        return StringUtil.strip(cs.getTitle().getString()).contains("Museum");
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !inMuseum(cs)) {
            if (!lines.isEmpty()) lines = List.of();
            if (!marks.isEmpty()) marks = List.of();
            markCache.clear();
            return;
        }
        if (++ticks % 10 != 0) return;
        request(mc);
        buildMarks(mc, cs);
        if (ticks % 20 == 0 || lines.isEmpty()) buildList();
    }

    private static void request(Minecraft mc) {
        String key = SbProfile.key();
        String name = SbProfile.name();
        if (key == null || name == null) return;
        long now = System.currentTimeMillis();
        if (key.equals(donatedKey) && now - requestedAt < 10 * 60_000L) return;
        donatedKey = key;
        requestedAt = now;
        String uuid = mc.getUser().getProfileId().toString().replace("-", "");
        HypixelApi.getProfiles(uuid).thenCompose(resp -> {
            String profileId = profileId(resp, name);
            return profileId == null ? java.util.concurrent.CompletableFuture.completedFuture(null)
                    : HypixelApi.getMuseum(profileId);
        }).thenAccept(museum -> {
            Set<String> ids = donatedIds(museum, uuid);
            if (ids != null && key.equals(SbProfile.key())) mc.execute(() -> { donated = ids; lines = List.of(); });
        }).exceptionally(e -> null);
    }

    private static String profileId(JsonObject resp, String name) {
        if (resp == null || !resp.has("profiles") || !resp.get("profiles").isJsonObject()) return null;
        for (var e : resp.getAsJsonObject("profiles").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject p = e.getValue().getAsJsonObject();
            String cute = p.has("cute_name") ? p.get("cute_name").getAsString().toLowerCase() : "";
            if (cute.equals(name.toLowerCase()) && p.has("profile_id")) return p.get("profile_id").getAsString();
        }
        return null;
    }

    private static Set<String> donatedIds(JsonObject museum, String uuid) {
        if (museum == null || !museum.has("members") || !museum.get("members").isJsonObject()) return null;
        JsonObject members = museum.getAsJsonObject("members");
        JsonObject member = null;
        for (String k : members.keySet()) {
            if (k.replace("-", "").equalsIgnoreCase(uuid) && members.get(k).isJsonObject()) member = members.getAsJsonObject(k);
        }
        if (member == null) return null;
        Set<String> out = new HashSet<>();
        if (member.has("items") && member.get("items").isJsonObject()) out.addAll(member.getAsJsonObject("items").keySet());
        return out;
    }

    private static void buildList() {
        MuseumRepo.Data data = MuseumRepo.get();
        Set<String> have = donated;
        if (data == null || have == null) {
            lines = List.of(have == null ? "§7Museum Helper: loading donations…" : "§7Museum Helper: no museum data");
            linesWidth = width(lines);
            return;
        }
        TeslaMapsConfig c = cfg();
        Set<String> covered = MuseumRepo.covered(data, have);
        List<Missing> missing = new ArrayList<>();
        for (Map.Entry<String, List<String>> cat : data.items().entrySet()) {
            if (!c.museumHelperCategory.equals("All") && !c.museumHelperCategory.equalsIgnoreCase(cat.getKey())) continue;
            for (String id : cat.getValue()) {
                if (covered.contains(id)) continue;
                List<String> pieces = data.setsToItems().get(id);
                Double price = sum(pieces == null ? List.of(id) : pieces, false);
                Double craft = sum(pieces == null ? List.of(id) : pieces, true);
                missing.add(new Missing(name(data, id), price, craft, data.itemToXp().getOrDefault(id, 0)));
            }
        }
        String sort = c.museumHelperSort;
        missing.sort((a, b) -> {
            double x = key(a, sort), y = key(b, sort);
            return Double.compare(x, y);
        });
        List<String> out = new ArrayList<>();
        out.add("§f§lMissing Donations §7(" + missing.size() + ")");
        int rows = Math.min(missing.size(), c.museumHelperRows);
        for (int i = 0; i < rows; i++) {
            Missing m = missing.get(i);
            Double v = sort.equals("Craft Cost") ? m.craft : m.price;
            String val = v == null ? "§8?" : "§6" + NumberUtil.formatSuffixed(v);
            if (sort.equals("Coins per XP") && v != null && m.xp > 0) val += " §7(" + NumberUtil.formatSuffixed(v / m.xp) + "/xp)";
            out.add(m.name + "§8: " + val);
        }
        if (missing.size() > rows) out.add("§8+" + (missing.size() - rows) + " more");
        lines = out;
        linesWidth = width(out);
    }

    private static double key(Missing m, String sort) {
        Double v = sort.equals("Craft Cost") ? m.craft : m.price;
        if (v == null) return Double.MAX_VALUE;
        if (sort.equals("Coins per XP")) return m.xp > 0 ? v / m.xp : Double.MAX_VALUE / 2;
        return v;
    }

    private static Double sum(List<String> ids, boolean craft) {
        double total = 0;
        for (String id : ids) {
            Double p = craft ? CraftCost.costOrNull(id, PriceManager.SOURCE_LOWEST_BIN)
                    : PriceManager.marketPriceOrNull(id, PriceManager.SOURCE_LOWEST_BIN);
            if (p == null) return null;
            total += p;
        }
        return total;
    }

    private static String name(MuseumRepo.Data data, String id) {
        String helmet = data.armorToId().get(id);
        String name = SkyblockItems.coloredName(helmet != null ? helmet : id);
        if (name == null) return "§f" + StringUtil.titleCase(id.replace('_', ' ').toLowerCase());
        if (helmet != null) {
            for (String piece : new String[]{" Helmet", " Hood", " Hat", " Mask", " Cap", " Crown"}) {
                if (name.endsWith(piece)) return name.substring(0, name.length() - piece.length()) + " Armor";
            }
        }
        return name;
    }

    // SkyOcean: the grey "not donated" dyes, coloured by how close the item is
    private static void buildMarks(Minecraft mc, AbstractContainerScreen<?> cs) {
        List<Mark> out = new ArrayList<>();
        Map<String, Integer> inv = null;
        List<Slot> slots = cs.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (!st.is(Items.DYE.gray())) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            Integer col = markCache.get(name);
            if (col == null) {
                if (inv == null) inv = inventory(mc);
                col = colorFor(name, inv);
                if (col != null) markCache.put(name, col);
            }
            if (col != null && col != 0) out.add(new Mark(i, col));
        }
        marks = out;
    }

    private static Map<String, Integer> inventory(Minecraft mc) {
        Map<String, Integer> out = new HashMap<>();
        if (mc.player == null) return out;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            String id = st.isEmpty() ? null : ItemUtil.skyblockId(st);
            if (id != null && !id.isEmpty()) out.merge(id, st.getCount(), Integer::sum);
        }
        return out;
    }

    /** null = not known yet (recipe loading), 0 = nothing to show. */
    private static Integer colorFor(String name, Map<String, Integer> inv) {
        String id = PriceManager.idForName(name);
        if (id == null) return 0;
        if (inv.containsKey(id)) return GREEN;
        for (ItemIndex.Hit hit : ItemIndex.search(name, null)) {
            if (ItemIndex.MUSEUM.equals(hit.type())) continue;
            if (id.equals(ItemUtil.skyblockId(hit.stack()))) return GREEN;
        }
        Map<String, Integer> ing = CraftCost.ingredientsOrNull(id);
        if (ing == null) return null;
        if (ing.isEmpty()) return RED;
        for (Map.Entry<String, Integer> e : ing.entrySet()) {
            if (inv.getOrDefault(e.getKey().replace('-', ':'), 0) < e.getValue()) return ORANGE;
        }
        return YELLOW;
    }

    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || !feature.active()) return;
        List<Mark> mk = marks;
        List<String> l = lines;
        if (mk.isEmpty() && l.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        for (Mark m : mk) {
            if (m.slot >= slots.size()) continue;
            Slot slot = slots.get(m.slot);
            int sx = acc.getX() + slot.x, sy = acc.getY() + slot.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0x77000000 | m.color);
        }
        if (l.isEmpty()) return;
        int x = Math.max(4, acc.getX() - linesWidth - 10), y = acc.getY() + 4;
        HudStyle.panel(g, x - 3, y - 3, linesWidth + 6, l.size() * ROW + 4);
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), x, y + i * ROW, 0xFFFFFFFF, true);
    }

    private static int width(List<String> l) {
        var font = Minecraft.getInstance().font;
        int w = 0;
        for (String s : l) w = Math.max(w, font.width(s));
        return w;
    }
}
