package com.teslamaps.slayer;

// Ported from SkyHanni's CarryTracker (customers, prices, trade coins) + Zen's CarryCounter (kills via "Spawned by")
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.SlayerCarry;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CarryTracker {

    private static final Pattern TRADE_DONE = Pattern.compile("^Trade completed with (?:\\[[^\\]]+\\] )?(\\w{1,16})!$");
    private static final Pattern TRADE_COINS = Pattern.compile("^\\+ ([\\d.,]+[kKmMbB]?) coins$");
    private static final String[] TYPES = {"REVENANT", "TARANTULA", "SVEN", "VOIDGLOOM", "DEMONLORD", "VAMPIRE"};
    private static final String[] TYPE_NAMES = {"Revenant", "Tarantula", "Sven", "Voidgloom", "Inferno", "Vampire"};
    private static final List<String> SAMPLE = List.of("§c§lCarries", "§bSteve §61.0m§8/§62.0m", "  §dRevenant 5 §e3§8/§e10 §66.0m");

    private record Tracked(String owner, String key) {}

    private static final Map<ArmorStand, Tracked> tracked = new IdentityHashMap<>();
    private static List<String> lines = List.of();
    private static String lastTrader = null;
    private static long lastTradeMs = 0;
    private static int ticks = 0;
    private static Object lastLevel = null;
    private static HudEditElement hud;

    private CarryTracker() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("slayerCarryTracker", "Carry Tracker").section("Combat")
                .enabled(() -> cfg().slayerCarryTracker)
                .size(() -> width(lines.isEmpty() ? SAMPLE : lines), () -> height(lines.isEmpty() ? SAMPLE : lines))
                .scaleRange(0.5f, 5.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines));
        Feature.of("CarryTracker").section("Combat").when(() -> cfg().slayerCarryTracker)
                .tick(CarryTracker::tick)
                .hud("carrytracker", CarryTracker::render)
                .serverChat(CarryTracker::onChat)
                .config("Combat", b -> {
                    b.label("Slayer Carries");
                    b.toggle("Carry Tracker", () -> cfg().slayerCarryTracker, v -> cfg().slayerCarryTracker = v)
                            .desc("Counts your customers' boss kills through the \"Spawned by\" tag and books trade coins as paid. "
                                    + "/tmap carry add <name> <rev5|sven4|eman3|blaze2|tara4|vamp3> <amount>, "
                                    + "/tmap carry price <type> <coins>, /tmap carry pc <name>, /tmap carry remove <name>, /tmap carry clear");
                    b.toggle("Carry Mode (follow a boss)", () -> cfg().slayerCarryMode, v -> cfg().slayerCarryMode = v)
                            .desc("Middle click a slayer boss to track it for boss ESP and the slayer HUDs. "
                                    + "Without a pick, bosses of the carry tier below are tracked too.");
                    b.slider("  Carry Tier", 1, 5, () -> (float) cfg().slayerCarryTier, v -> cfg().slayerCarryTier = Math.round(v), 0);
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("carry")
                        .executes(c -> run(CarryTracker::list))
                        .then(ClientCommands.literal("list").executes(c -> run(CarryTracker::list)))
                        .then(ClientCommands.literal("clear").executes(c -> run(() -> {
                            cfg().slayerCarries.clear();
                            changed("§aCleared all carries.");
                        })))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("customer", StringArgumentType.word())
                                        .then(ClientCommands.argument("type", StringArgumentType.word())
                                                .then(ClientCommands.argument("amount", IntegerArgumentType.integer(-1000, 1000))
                                                        .executes(c -> run(() -> add(StringArgumentType.getString(c, "customer"),
                                                                StringArgumentType.getString(c, "type"),
                                                                IntegerArgumentType.getInteger(c, "amount"))))))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("customer", StringArgumentType.word())
                                        .executes(c -> run(() -> remove(StringArgumentType.getString(c, "customer"))))))
                        .then(ClientCommands.literal("price")
                                .then(ClientCommands.argument("type", StringArgumentType.word())
                                        .then(ClientCommands.argument("coins", StringArgumentType.word())
                                                .executes(c -> run(() -> price(StringArgumentType.getString(c, "type"),
                                                        StringArgumentType.getString(c, "coins")))))))
                        .then(ClientCommands.literal("pc")
                                .then(ClientCommands.argument("customer", StringArgumentType.word())
                                        .executes(c -> run(() -> partyChat(StringArgumentType.getString(c, "customer")))))))));
    }

    private static int run(Runnable r) {
        Minecraft.getInstance().execute(r);
        return 1;
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] " + s));
    }

    private static void changed(String s) {
        TeslaMapsConfig.save();
        rebuild();
        msg(s);
    }

    /** "rev5" -> "REVENANT_5", null if unknown. */
    static String parseType(String raw) {
        if (raw == null || raw.length() < 2) return null;
        String s = raw.toLowerCase(Locale.ROOT);
        char last = s.charAt(s.length() - 1);
        if (last < '1' || last > '5') return null;
        String n = s.substring(0, s.length() - 1);
        String type;
        if (n.startsWith("rev") || n.startsWith("zombie") || n.equals("t")) type = "REVENANT";
        else if (n.startsWith("tara") || n.startsWith("spider") || n.startsWith("brood")) type = "TARANTULA";
        else if (n.startsWith("sven") || n.startsWith("wolf")) type = "SVEN";
        else if (n.startsWith("eman") || n.startsWith("ender") || n.startsWith("void") || n.equals("voidgloom")) type = "VOIDGLOOM";
        else if (n.startsWith("blaze") || n.startsWith("inferno") || n.startsWith("demon")) type = "DEMONLORD";
        else if (n.startsWith("vamp") || n.startsWith("blood") || n.startsWith("rift")) type = "VAMPIRE";
        else return null;
        return type + "_" + last;
    }

    static String display(String key) {
        int u = key.lastIndexOf('_');
        String type = u < 0 ? key : key.substring(0, u);
        for (int i = 0; i < TYPES.length; i++) {
            if (TYPES[i].equals(type)) return TYPE_NAMES[i] + " " + key.substring(u + 1);
        }
        return key;
    }

    private static String keyOfTag(String name) {
        String type;
        if (name.contains("Revenant") || name.contains("Atoned")) type = "REVENANT";
        else if (name.contains("Tarantula") || name.contains("Conjoined")) type = "TARANTULA";
        else if (name.contains("Sven")) type = "SVEN";
        else if (name.contains("Voidgloom")) type = "VOIDGLOOM";
        else if (name.contains("Demonlord")) type = "DEMONLORD";
        else if (name.contains("Bloodfiend")) type = "VAMPIRE";
        else return null;
        return type + "_" + SlayerHUD.tagTier(name);
    }

    private static void add(String customer, String rawType, int amount) {
        String key = parseType(rawType);
        if (key == null) {
            msg("§cUnknown carry type '" + rawType + "'. Use e.g. rev5, sven4, eman3, blaze2, tara4, vamp3.");
            return;
        }
        for (SlayerCarry c : cfg().slayerCarries) {
            if (!c.customer.equalsIgnoreCase(customer) || !c.type.equals(key)) continue;
            int n = c.requested + amount;
            if (n < 1) {
                msg("§cCarry amount must stay positive.");
                return;
            }
            c.requested = n;
            changed("§aUpdated carry: §b" + c.customer + " §8x" + n + " §d" + display(key));
            return;
        }
        if (amount < 1) {
            msg("§cCarry amount must be positive.");
            return;
        }
        SlayerCarry c = new SlayerCarry();
        c.customer = customer;
        c.type = key;
        c.requested = amount;
        cfg().slayerCarries.add(c);
        changed("§aStarted carry: §b" + customer + " §8x" + amount + " §d" + display(key));
    }

    private static void remove(String customer) {
        boolean any = cfg().slayerCarries.removeIf(c -> c.customer.equalsIgnoreCase(customer));
        if (any) changed("§aRemoved customer §b" + customer);
        else msg("§cCustomer not found: §b" + customer);
    }

    private static void price(String rawType, String coins) {
        String key = parseType(rawType);
        if (key == null) {
            msg("§cUnknown carry type '" + rawType + "'.");
            return;
        }
        double p = NumberUtil.parseSuffixed(coins);
        if (p <= 0) cfg().slayerCarryPrices.remove(key);
        else cfg().slayerCarryPrices.put(key, p);
        changed("§aPrice for §d" + display(key) + "§a: §6" + (p <= 0 ? "none" : NumberUtil.formatSuffixed(p)));
    }

    private static void partyChat(String customer) {
        StringBuilder sb = new StringBuilder();
        for (SlayerCarry c : cfg().slayerCarries) {
            if (!c.customer.equalsIgnoreCase(customer)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(display(c.type)).append(": ").append(c.done).append('/').append(c.requested);
        }
        if (sb.length() == 0) {
            msg("§cCustomer not found: §b" + customer);
            return;
        }
        Announce.party(customer + " carry " + sb);
    }

    private static void list() {
        if (cfg().slayerCarries.isEmpty()) {
            msg("§7No carries. /tmap carry add <name> <type> <amount>");
            return;
        }
        for (SlayerCarry c : cfg().slayerCarries) {
            msg("§b" + c.customer + " §d" + display(c.type) + " §e" + c.done + "§8/§e" + c.requested
                    + (c.paid > 0 ? " §7paid §6" + NumberUtil.formatSuffixed(c.paid) : ""));
        }
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        Matcher m = TRADE_DONE.matcher(t);
        if (m.matches()) {
            lastTrader = m.group(1);
            lastTradeMs = System.currentTimeMillis();
            return;
        }
        if (lastTrader == null || System.currentTimeMillis() - lastTradeMs > 3000) return;
        m = TRADE_COINS.matcher(t);
        if (!m.matches()) return;
        double coins = NumberUtil.parseSuffixed(m.group(1));
        for (SlayerCarry c : cfg().slayerCarries) {
            if (!c.customer.equalsIgnoreCase(lastTrader)) continue;
            c.paid += coins;
            changed("§aBooked §6" + NumberUtil.formatSuffixed(coins) + " §afrom §b" + c.customer);
            return;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            tracked.clear();
            rebuild();
        }
        if (++ticks % 5 != 0 || mc.player == null) return;
        List<SlayerCarry> carries = cfg().slayerCarries;
        if (carries.isEmpty()) {
            tracked.clear();
            return;
        }
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (tracked.containsKey(s) || s.distanceToSqr(mc.player) > 40 * 40) continue;
            String name = s.getName().getString();
            if (!SlayerHUD.isBossTag(name)) continue;
            String owner = SlayerHUD.ownerOf(s);
            if (owner == null || !isCustomer(owner)) continue;
            String key = keyOfTag(name);
            if (key != null) tracked.put(s, new Tracked(owner, key));
        }
        Iterator<Map.Entry<ArmorStand, Tracked>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ArmorStand, Tracked> e = it.next();
            ArmorStand s = e.getKey();
            boolean zero = s.getName().getString().endsWith(" 0❤");
            boolean gone = s.isRemoved();
            if (!zero && !gone) continue;
            it.remove();
            if (zero || s.distanceToSqr(mc.player) < 24 * 24) onKill(e.getValue());
        }
    }

    private static boolean isCustomer(String name) {
        for (SlayerCarry c : cfg().slayerCarries) if (c.customer.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void onKill(Tracked t) {
        SlayerCarry best = null;
        String type = t.key.substring(0, t.key.lastIndexOf('_'));
        for (SlayerCarry c : cfg().slayerCarries) {
            if (!c.customer.equalsIgnoreCase(t.owner) || c.done >= c.requested) continue;
            if (c.type.equals(t.key)) {
                best = c;
                break;
            }
            if (best == null && t.key.endsWith("_0") && c.type.startsWith(type + "_")) best = c;
        }
        if (best == null) return;
        best.done++;
        TeslaMapsConfig.save();
        rebuild();
        if (best.done == best.requested) {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setTimes(0, 40, 5);
            mc.gui.setTitle(Component.literal("§eCarry done!"));
            msg("§aCarry done for §b" + best.customer + "§a!");
        }
    }

    private static void rebuild() {
        List<SlayerCarry> carries = cfg().slayerCarries;
        if (carries.isEmpty()) {
            lines = List.of();
            return;
        }
        Map<String, Double> prices = cfg().slayerCarryPrices;
        List<String> out = new ArrayList<>();
        out.add("§c§lCarries");
        List<String> seen = new ArrayList<>();
        for (SlayerCarry first : carries) {
            String lower = first.customer.toLowerCase(Locale.ROOT);
            if (seen.contains(lower)) continue;
            seen.add(lower);
            double total = 0, paid = 0;
            List<String> rows = new ArrayList<>();
            for (SlayerCarry c : carries) {
                if (!c.customer.equalsIgnoreCase(first.customer)) continue;
                Double p = prices.get(c.type);
                double cost = p == null ? 0 : p * c.requested;
                total += cost;
                paid += c.paid;
                String col = c.done > c.requested ? "§c" : c.done == c.requested ? "§a" : "§e";
                rows.add("  §d" + display(c.type) + " " + col + c.done + "§8/" + col + c.requested
                        + (cost > 0 ? " §6" + NumberUtil.formatSuffixed(cost) : ""));
            }
            out.add("§b" + first.customer + (total > 0 || paid > 0
                    ? " §6" + NumberUtil.formatSuffixed(paid) + "§8/§6" + NumberUtil.formatSuffixed(total) : ""));
            out.addAll(rows);
        }
        lines = out;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static int width(List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w + 6;
    }

    private static int height(List<String> l) {
        return l.size() * 10 + 4;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(l), height(l));
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 3, 3 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
