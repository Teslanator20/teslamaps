package com.teslamaps.features.kuudra;

// Ported from Odin's nether/NoPre.kt + skyblock/Supply.kt, spot names like Shizo's Priority.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.regex.Matcher;

public final class KuudraNoPre {

    enum Supply {
        Triangle(-67, 77, -122), X(-142, 77, -151), Equals(-65, 76, -87), Slash(-113, 77, -68),
        Shop(-81, 76, -143), xCannon(-143, 76, -125), Square(-143, 76, -80), None(0, 0, 0);

        final BlockPos pickUp;
        Supply(int x, int y, int z) { pickUp = new BlockPos(x, y, z); }
    }

    private static Supply preSpot = Supply.None;
    private static int checkIn = -1;

    private KuudraNoPre() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Kuudra.LISTENERS.add((prev, now) -> {
            if (now == Kuudra.Phase.NONE || now == Kuudra.Phase.BUILD) {
                preSpot = Supply.None;
                checkIn = -1;
            }
        });
        Feature.of("KuudraNoPre").section("Kuudra").when(() -> cfg().kuudraSupplyHelper && cfg().kuudraNoPre && Kuudra.inKuudra())
                .serverChat(KuudraNoPre::onServerChat)
                .chat(KuudraNoPre::onChat)
                .tick(KuudraNoPre::tick);
    }

    /** Rows under the Supply Helper. */
    static void config(com.teslamaps.screen.config.ConfigBuilder b) {
        TeslaMapsConfig c = cfg();
        b.toggle("  No Pre Callouts", () -> c.kuudraNoPre, v -> c.kuudraNoPre = v)
                .desc("Registers your pre spot when Elle says \"Head over to the main platform\"");
        b.toggle("    Announce Missing Pre", () -> c.kuudraNoPreAnnounce, v -> c.kuudraNoPreAnnounce = v)
                .desc("Sends \"No Triangle!\" / \"No Shop!\" etc. to party chat when your pre or second spot has no supply");
        b.toggle("    Crate Priority", () -> c.kuudraCratePriority, v -> c.kuudraCratePriority = v)
                .desc("Reads the party's \"No <Spot>!\" calls and shows where you should go");
        b.toggle("      Advanced Priority", () -> c.kuudraCratePriorityAdvanced, v -> c.kuudraCratePriorityAdvanced = v)
                .desc("Harder routes in some cases (pull Square and X Cannon, go Shop)");
    }

    /** Party call spot name -> Supply name, null = unknown. */
    static String canonical(String raw) {
        String s = raw.replace("!", "").trim().toUpperCase(Locale.ROOT);
        if (s.startsWith("TRI")) return "Triangle";
        if (s.equals("=") || s.startsWith("EQUAL")) return "Equals";
        if (s.equals("/") || s.startsWith("SLASH")) return "Slash";
        if (s.startsWith("SHOP")) return "Shop";
        if (s.startsWith("SQUARE") || s.equals("SQ")) return "Square";
        if (s.equals("X")) return "X";
        if (s.startsWith("XC") || s.startsWith("X CANNON") || s.startsWith("XCANNON") || s.startsWith("CANNON")) return "xCannon";
        return null;
    }

    private static void msg(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§a[TeslaMaps] §7" + text));
    }

    private static void onServerChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (s.startsWith(Kuudra.HEAD_OVER)) {
            var p = Minecraft.getInstance().player;
            if (p == null) return;
            BlockPos pos = p.blockPosition();
            if (Supply.Triangle.pickUp.closerThan(pos, 15)) preSpot = Supply.Triangle;
            else if (Supply.X.pickUp.closerThan(pos, 30)) preSpot = Supply.X;
            else if (Supply.Equals.pickUp.closerThan(pos, 15)) preSpot = Supply.Equals;
            else if (Supply.Slash.pickUp.closerThan(pos, 15)) preSpot = Supply.Slash;
            else preSpot = Supply.None;
            msg(preSpot == Supply.None ? "§cPre spot not registered, you were not at one in time" : "Pre spot: §f" + preSpot.name());
        } else if (s.equals(Kuudra.NOT_AGAIN) && preSpot != Supply.None) {
            checkIn = 30;
        }
    }

    private static void tick() {
        if (checkIn < 0 || --checkIn > 0) return;
        checkIn = -1;
        if (!cfg().kuudraNoPreAnnounce) return;
        boolean pre = false, second = false;
        for (Giant g : Kuudra.giants()) {
            Vec3 loc = new Vec3(g.getX(), 76.0, g.getZ());
            if (preSpot.pickUp.closerToCenterThan(loc, 18)) pre = true;
            else if (preSpot == Supply.Triangle && Supply.Shop.pickUp.closerToCenterThan(loc, 18)) second = true;
            else if (preSpot == Supply.X && Supply.xCannon.pickUp.closerToCenterThan(loc, 16)) second = true;
            else if (preSpot == Supply.Slash && Supply.Square.pickUp.closerToCenterThan(loc, 20)) second = true;
        }
        if (Kuudra.giants().isEmpty() || pre && second) return;
        String out = null;
        if (!pre) out = "No " + preSpot.name() + "!";
        else if (preSpot == Supply.Triangle) out = "No Shop!";
        else if (preSpot == Supply.X) out = "No xCannon!";
        else if (preSpot == Supply.Slash) out = "No Square!";
        if (out != null) Announce.party(out);
    }

    private static void onChat(String raw) {
        if (!cfg().kuudraCratePriority || preSpot == Supply.None || Kuudra.phase() != Kuudra.Phase.SUPPLIES) return;
        Matcher m = KuudraAlerts.NO_SPOT.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String name = canonical(m.group(2));
        if (name == null) return;
        String todo = priority(Supply.valueOf(name), cfg().kuudraCratePriorityAdvanced);
        if (todo.isEmpty()) return;
        KuudraAlerts.title("§b§l" + todo, true);
        msg("Crate Priority: §f" + todo);
    }

    private static String priority(Supply missing, boolean adv) {
        String pull = adv ? "Pull Square and X Cannon. Next: collect Shop" : "Pull Square. Next: collect Shop";
        return switch (missing) {
            case Shop -> switch (preSpot) {
                case Triangle, X -> "Go X Cannon";
                case Equals, Slash -> "Go Square, place on Shop";
                default -> "";
            };
            case Triangle -> switch (preSpot) {
                case Triangle -> pull;
                case X -> "Go X Cannon";
                case Equals -> adv ? "Go Shop" : "Go X Cannon";
                case Slash -> "Go Square, place on Triangle";
                default -> "";
            };
            case Equals -> switch (preSpot) {
                case Triangle -> adv ? "Go Shop" : "Go X Cannon";
                case X -> "Go X Cannon";
                case Equals -> pull;
                case Slash -> "Go Square, place on Equals";
                default -> "";
            };
            case Slash -> switch (preSpot) {
                case Triangle -> "Go Square, place on Slash";
                case X -> "Go X Cannon";
                case Equals -> adv ? "Go Shop" : "Go X Cannon";
                case Slash -> pull;
                default -> "";
            };
            case Square -> switch (preSpot) {
                case Triangle, Equals -> "Go Shop";
                case X, Slash -> "Go X Cannon";
                default -> "";
            };
            case xCannon -> switch (preSpot) {
                case Triangle, Equals -> "Go Shop";
                case Slash, X -> "Go Square, place on X Cannon";
                default -> "";
            };
            case X -> switch (preSpot) {
                case Triangle -> "Go X Cannon";
                case X -> pull;
                case Equals -> adv ? "Go Shop" : "Go X Cannon";
                case Slash -> "Go Square, place on X";
                default -> "";
            };
            default -> "";
        };
    }
}
