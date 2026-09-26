package com.teslamaps.features.crimson;

// After SkyHanni's nether/reputationhelper (CrimsonIsleReputationHelper, DailyQuestHelper, QuestLoader,
// DailyMiniBossHelper, DailyKuudraBossHelper). Locations from SkyHanni's CrimsonIsleReputation repo file.
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SackApi;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.features.kuudra.Kuudra;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReputationHelper {

    static final class Quest {
        String name;
        String state = "ACCEPTED";
        int need = 1, have;

        Quest() {}

        Quest(String name, String state, int need) {
            this.name = name;
            this.state = state;
            this.need = need;
        }
    }

    static final class Data {
        List<Quest> quests = new ArrayList<>();
        Set<String> minibosses = new LinkedHashSet<>();
        Set<Integer> kuudra = new LinkedHashSet<>();
    }

    private static final Pattern TAB_QUEST = Pattern.compile("^\\s*([✖✔]) (.+?)(?: x(\\d+))?$");
    private static final Pattern COMPLETED = Pattern.compile("^You completed your (\\w+) quest! Visit the Town Board to claim the rewards.*");
    private static final Pattern KUUDRA_QUEST = Pattern.compile("^Kill Kuudra (\\w+) Tier$");
    private static final Pattern TROPHY = Pattern.compile("TROPHY \\w+! You caught an? (.+?) (BRONZE|SILVER|GOLD|DIAMOND)!");
    private static final Pattern MINI_AMOUNT = Pattern.compile("miniboss (\\d)");
    private static final String[] MINIBOSSES = {"Bladesoul", "Mage Outlaw", "Barbarian Duke X", "Ashfang", "Magma Boss"};
    private static final BlockPos[] MINI_POS = {new BlockPos(-295, 82, -518), new BlockPos(-181, 106, -860),
            new BlockPos(-536, 118, -903), new BlockPos(-485, 136, -1016), new BlockPos(-369, 63, -805)};
    private static final String[] KUUDRA_TIERS = {"Basic", "Hot", "Burning", "Fiery", "Infernal"};
    private static final BlockPos KUUDRA_POS = new BlockPos(-373, 114, -1035);
    private static final BlockPos DOJO_POS = new BlockPos(-236, 109, -598);
    private static final BlockPos BOARD_MAGE = new BlockPos(-122, 93, -755), BOARD_BARB = new BlockPos(-582, 101, -688);
    private static final Map<String, BlockPos> FISH_POS = Map.of(
            "Volcanic Stonefish", new BlockPos(-367, 120, -781), "Steaming-Hot Flounder", new BlockPos(-367, 120, -781),
            "Flyfish", new BlockPos(-367, 120, -781), "Moldfin", new BlockPos(-249, 73, -734));
    private static final Set<String> FISH = Set.of("Lavahorse", "Gusher", "Volcanic Stonefish", "Moldfin", "Steaming-Hot Flounder",
            "Blobfish", "Soul Fish", "Flyfish", "Golden Fish", "Slugfish", "Skeleton Fish", "Sulphur Skitter", "Mana Ray");
    private static final Set<String> DOJO = Set.of("Mastery", "Tenacity", "Stamina", "Swiftness", "Discipline", "Control", "Force");
    private static final ProfiledFile FILE = new ProfiledFile("crimson_reputation.json", null);
    private static final Gson GSON = new Gson();
    private static final List<String> SAMPLE = List.of("§e§lReputation Helper", "", "§7Daily Quests (§e1§8/§e5 collected§7)",
            "  §fMini Boss: §fAshfang §e0§8/§e1", "  §a[Ready] §fItem Fetch: §fMagmag §e8§8/§e8", "",
            "§7Daily Bosses (§e2§8/§e5 killed§7)", " §5Ashfang§7: §bTodo", "", "§7Daily Kuudra (§e0§8/§e5 killed§7)");

    private static Data data = new Data();
    private static boolean loaded, dirty = true, tabMissing;
    private static String faction;
    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static Object lastScreen;
    private static String challengeTest;
    private static long screenOpened;
    private static final WaypointSet waypoints = new WaypointSet().beam(false);

    private ReputationHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("crimsonRep", "Crimson Reputation Helper").section("Crimson Isle")
                .enabled(() -> cfg().crimsonRepHelper)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("CrimsonReputationHelper").section("Crimson Isle").when(() -> cfg().crimsonRepHelper)
                .tick(ReputationHelper::tick)
                .serverChat(ReputationHelper::onChat)
                .hudForeground("crimsonrep", ReputationHelper::render)
                .world(ReputationHelper::renderWorld)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Reputation Helper");
                    b.toggle("Reputation Helper", () -> c.crimsonRepHelper, v -> c.crimsonRepHelper = v)
                            .desc("Daily quests from the Faction Quests tab widget with progress, sack stock for fetch quests, "
                                    + "daily bosses x/5 and daily Kuudra x/5. Saved per profile. Move it in /tmap gui")
                            .tags("reputation", "faction", "daily", "quest", "town board", "crimson", "mage", "barbarian");
                    b.toggle("  Hide Done", () -> c.crimsonRepHideDone, v -> { c.crimsonRepHideDone = v; dirty = true; });
                    b.toggle("  Waypoints", () -> c.crimsonRepWaypoints, v -> c.crimsonRepWaypoints = v)
                            .desc("Quest spots, open minibosses, Kuudra and the Town Board when something is ready");
                    b.toggle("  Only While Key Held", () -> c.crimsonRepHotkeyOnly, v -> c.crimsonRepHotkeyOnly = v)
                            .desc("HUD and waypoints only while the key below is held");
                    b.keybind("    Key", () -> c.crimsonRepKey, v -> c.crimsonRepKey = v);
                    b.button("  Reset Quest Data", ReputationHelper::reset);
                });
        SbProfile.onChange(() -> { loaded = false; dirty = true; });
    }

    // --- persistence ---

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        data = new Data();
        Path p = FILE.path();
        if (p == null) return;
        try {
            if (Files.exists(p)) {
                Data d = GSON.fromJson(Files.readString(p), Data.class);
                if (d != null) {
                    if (d.quests != null) data.quests = d.quests;
                    if (d.minibosses != null) data.minibosses = d.minibosses;
                    if (d.kuudra != null) data.kuudra = d.kuudra;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("crimson_reputation.json: {}", e.toString());
        }
        dirty = true;
    }

    private static void save() {
        dirty = true;
        Path p = FILE.path();
        if (p == null) return;
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(data));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("crimson_reputation.json: {}", e.toString());
        }
    }

    public static void reset() {
        ensure();
        data = new Data();
        save();
        Crimson.say("§7Reputation Helper data reset.");
    }

    // --- quests ---

    private static String category(String name) {
        for (String m : MINIBOSSES) if (m.equals(name)) return "MINIBOSS";
        if (KUUDRA_QUEST.matcher(name).matches()) return "KUUDRA";
        if (name.contains(" Rank ") && DOJO.contains(name.substring(0, name.indexOf(" Rank ")))) return "DOJO";
        if (name.equals("Rescue Mission")) return "RESCUE";
        if (FISH.contains(name)) return "FISHING";
        return "FETCH";
    }

    private static String categoryLabel(String cat) {
        return switch (cat) {
            case "MINIBOSS" -> "Mini Boss";
            case "KUUDRA" -> "Kuudra Kill";
            case "DOJO" -> "Dojo Task";
            case "RESCUE" -> "Rescue Mission";
            case "FISHING" -> "Trophy Fish";
            default -> "Item Fetch";
        };
    }

    private static Quest quest(String cat) {
        for (Quest q : data.quests) if (category(q.name).equals(cat)) return q;
        return null;
    }

    private static void progress(Quest q, int have) {
        int h = Math.min(have, q.need);
        if (h == q.have) return;
        q.have = h;
        q.state = h >= q.need ? "READY" : "ACCEPTED";
        Crimson.say("§7" + q.name + " progress: §e" + h + "§8/§e" + q.need);
        save();
    }

    private static void readTab() {
        List<String> tab = TabListUtils.getOrderedLines();
        boolean in = false, found = false, changed = false;
        List<Quest> seen = new ArrayList<>();
        for (String raw : tab) {
            String s = StringUtil.strip(raw);
            if (faction == null) {
                String t = s.trim();
                if (t.startsWith("Barbarian Reputation:")) faction = "barb";
                else if (t.startsWith("Mage Reputation:")) faction = "mage";
            }
            if (s.trim().equals("Faction Quests:")) {
                in = true;
                continue;
            }
            if (!in) continue;
            Matcher m = TAB_QUEST.matcher(s);
            if (!m.matches()) {
                in = false;
                continue;
            }
            found = true;
            seen.add(new Quest(m.group(2), m.group(1).equals("✔") ? "READY" : "ACCEPTED",
                    m.group(3) == null ? 1 : Integer.parseInt(m.group(3))));
        }
        tabMissing = !found;
        for (Quest n : seen) {
            Quest old = null;
            for (Quest q : data.quests) if (q.name.equals(n.name)) old = q;
            if (old == null) {
                if (data.quests.size() >= 5) {
                    data.quests.clear();
                    data.minibosses.clear();
                    data.kuudra.clear();
                }
                data.quests.add(n);
                changed = true;
            } else if (n.state.equals("READY") && old.state.equals("ACCEPTED")) {
                old.state = "READY";
                changed = true;
            }
        }
        if (changed) save();
    }

    private static void onChat(String raw) {
        ensure();
        String t = StringUtil.stripTrim(raw);
        if (t.endsWith(" DOWN!")) {
            if (t.equals("KUUDRA DOWN!")) {
                int tier = Kuudra.tier();
                if (tier < 1 || tier > 5) return;
                data.kuudra.add(tier);
                Quest q = quest("KUUDRA");
                if (q != null && q.state.equals("ACCEPTED")) {
                    Matcher m = KUUDRA_QUEST.matcher(q.name);
                    if (m.matches() && m.group(1).equalsIgnoreCase(KUUDRA_TIERS[tier - 1])) q.state = "READY";
                }
                save();
                return;
            }
            for (String mb : MINIBOSSES) {
                if (!t.equals(mb.toUpperCase(Locale.ROOT) + " DOWN!")) continue;
                data.minibosses.add(mb);
                Quest q = quest("MINIBOSS");
                if (q != null && q.name.equals(mb) && q.state.equals("ACCEPTED")) progress(q, q.have + 1);
                save();
                return;
            }
            return;
        }
        Matcher c = COMPLETED.matcher(t);
        if (c.matches()) {
            Quest q = quest(c.group(1).equalsIgnoreCase("dojo") ? "DOJO" : c.group(1).equalsIgnoreCase("rescue") ? "RESCUE" : "");
            if (q != null) {
                q.state = "READY";
                save();
            }
            return;
        }
        if (t.contains("TROPHY")) {
            Matcher m = TROPHY.matcher(t);
            Quest q = quest("FISHING");
            if (m.find() && q != null && q.state.equals("ACCEPTED") && q.name.equals(m.group(1))) progress(q, q.have + 1);
        }
    }

    private static void checkTownBoard(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            challengeTest = null;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            screenOpened = System.currentTimeMillis();
            challengeTest = null;
            Quest dq = quest("DOJO");
            if (dq != null && dq.state.equals("ACCEPTED") && "Challenges".equals(StringUtil.stripTrim(cs.getTitle().getString()))) {
                challengeTest = dq.name.substring(0, dq.name.indexOf(" Rank "));
            }
        }
        if (System.currentTimeMillis() - screenOpened < 500) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        for (Quest q : data.quests) {
            String cat = category(q.name);
            if (!cat.equalsIgnoreCase(title)) continue;
            ItemStack st = null;
            for (Slot s : cs.getMenu().slots) {
                if (!(s.container instanceof Inventory) && s.getContainerSlot() == 22) st = s.getItem();
            }
            if (st == null || st.isEmpty()) return;
            boolean changed = false;
            for (String l : ItemUtil.loreLines(st)) {
                String s = StringUtil.stripTrim(l);
                if (s.equals("COMPLETE") && !q.state.equals("COLLECTED")) {
                    q.state = "COLLECTED";
                    changed = true;
                }
                if (cat.equals("MINIBOSS") && q.need == 1) {
                    Matcher m = MINI_AMOUNT.matcher(s);
                    if (m.find() && !m.group(1).equals("1")) {
                        q.need = Integer.parseInt(m.group(1));
                        if (q.state.equals("READY") && q.have < q.need) q.state = "ACCEPTED";
                        Crimson.say("§7Fixed miniboss amount from the Town Board.");
                        changed = true;
                    }
                }
            }
            if (changed) save();
            return;
        }
    }

    private static void checkFetch(Minecraft mc) {
        Quest q = quest("FETCH");
        if (q == null || q.state.equals("COLLECTED")) return;
        int count = 0;
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && StringUtil.stripTrim(st.getHoverName().getString()).equals(q.name)) count += st.getCount();
        }
        progress(q, count);
    }

    // --- tick / render ---

    private static boolean keyGate(Minecraft mc) {
        if (!cfg().crimsonRepHotkeyOnly) return true;
        return (mc.screen == null || mc.screen instanceof InventoryScreen) && KeyNames.isDown(cfg().crimsonRepKey);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!Crimson.here()) {
            lines = List.of();
            waypoints.clear();
            return;
        }
        ensure();
        ticks++;
        if (ticks % 20 == 0) readTab();
        if (ticks % 60 == 0) checkFetch(mc);
        if (ticks % 5 == 0) checkTownBoard(mc);
        if (dirty || ticks % 20 == 0) {
            dirty = false;
            rebuild();
            rebuildWaypoints(mc);
        }
        waypoints.tick();
    }

    private static void rebuild() {
        boolean hide = cfg().crimsonRepHideDone;
        List<String> out = new ArrayList<>();
        out.add("§e§lReputation Helper");
        if (tabMissing) {
            out.add("§cFaction Quests widget not found!");
            out.add("§7Enable it in §e/tab");
        } else {
            int done = 0;
            for (Quest q : data.quests) if (q.state.equals("COLLECTED")) done++;
            out.add("");
            out.add("§7Daily Quests (§e" + done + "§8/§e5 collected§7)");
            if (done != 5) {
                for (Quest q : data.quests) {
                    if (hide && q.state.equals("COLLECTED")) continue;
                    String cat = category(q.name);
                    String state = switch (q.state) {
                        case "READY" -> "§a[Ready] ";
                        case "COLLECTED" -> "§7[Collected] ";
                        default -> "";
                    };
                    StringBuilder sb = new StringBuilder("  ").append(state).append("§f").append(categoryLabel(cat)).append(": §f").append(q.name);
                    boolean prog = cat.equals("FETCH") || cat.equals("FISHING") || cat.equals("MINIBOSS");
                    if (prog && !q.state.equals("COLLECTED")) sb.append(" §e").append(q.have).append("§8/§e").append(q.need);
                    if (cat.equals("FETCH") && !q.state.equals("COLLECTED")) {
                        long sack = SackApi.amount(q.name);
                        if (sack >= 0) sb.append(" §7(").append(sack >= q.need ? "§a" : "§c").append(String.format(Locale.ROOT, "%,d", sack)).append(" §7in sacks)");
                    }
                    out.add(sb.toString());
                }
            }
        }
        out.add("");
        out.add("§7Daily Bosses (§e" + data.minibosses.size() + "§8/§e5 killed§7)");
        if (data.minibosses.size() < 5) {
            for (String mb : MINIBOSSES) {
                boolean d = data.minibosses.contains(mb);
                if (hide && d) continue;
                String live = d ? null : MinibossTimer.status(mb);
                out.add(" §5" + mb + "§7: " + (d ? "§aDone" : "§bTodo") + (live != null && cfg().crimsonMinibossTimer ? " §8| " + live : ""));
            }
        }
        out.add("");
        out.add("§7Daily Kuudra (§e" + data.kuudra.size() + "§8/§e5 killed§7)");
        if (data.kuudra.size() < 5) {
            for (int i = 0; i < 5; i++) {
                boolean d = data.kuudra.contains(i + 1);
                if (hide && d) continue;
                out.add(" §cTier " + (i + 1) + " (" + KUUDRA_TIERS[i] + ")§7: " + (d ? "§aDone" : "§bTodo"));
            }
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void rebuildWaypoints(Minecraft mc) {
        waypoints.clear();
        if (!cfg().crimsonRepWaypoints) return;
        boolean board = false;
        for (Quest q : data.quests) {
            String cat = category(q.name);
            if (q.state.equals("READY")) board = true;
            if (!q.state.equals("ACCEPTED")) continue;
            if (cat.equals("FETCH") || cat.equals("RESCUE")) board = true;
            BlockPos p = cat.equals("DOJO") ? DOJO_POS : cat.equals("FISHING") ? FISH_POS.get(q.name) : null;
            if (p != null) waypoints.add(p, "§f" + q.name, 0xFFFFFFFF, 0);
        }
        Vec3 pl = mc.player.position();
        for (int i = 0; i < MINIBOSSES.length; i++) {
            if (data.minibosses.contains(MINIBOSSES[i]) && !miniQuestOpen(MINIBOSSES[i])) continue;
            if (pl.distanceToSqr(Vec3.atCenterOf(MINI_POS[i])) < 40 * 40) continue;
            waypoints.add(MINI_POS[i], "§5" + MINIBOSSES[i], 0xFFFFFFFF, 0);
        }
        if (data.kuudra.size() < 5) waypoints.add(KUUDRA_POS, "§cKuudra", 0xFFFFFFFF, 0);
        if (board && faction != null) waypoints.add(faction.equals("mage") ? BOARD_MAGE : BOARD_BARB, "§eTown Board", 0xFFFFFFFF, 0);
    }

    private static boolean miniQuestOpen(String mb) {
        Quest q = quest("MINIBOSS");
        return q != null && q.name.equals(mb) && q.state.equals("ACCEPTED");
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null || !keyGate(mc)) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (waypoints.all().isEmpty() || !keyGate(Minecraft.getInstance())) return;
        waypoints.render(pose, cam);
    }

    /** From SlotBackgrounds: dojo quest test in the Challenges menu. */
    public static void drawSlot(GuiGraphicsExtractor g, Slot slot) {
        String test = challengeTest;
        if (test == null || !cfg().crimsonRepHelper || slot.container instanceof Inventory || slot.getItem().isEmpty()) return;
        if (Minecraft.getInstance().screen != lastScreen) return;
        if (StringUtil.strip(slot.getItem().getHoverName().getString()).contains(test)) {
            g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8055FFFF);
        }
    }
}
