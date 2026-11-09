package com.teslamaps.features.crimson;

// After SkyHanni's nether/VanquisherWaypointShare.kt, Odin's VanqNotifier and DulkirMod's VanquisherTrigger
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VanquisherAlert {

    private static final String SPAWN = "A Vanquisher is spawning nearby!";
    private static final Pattern SHARED = Pattern.compile(
            "^(?<channel>Party > |Guild > |Officer > )?(?<name>[^:]+):.*?x:\\s*(?<x>-?[\\d.]+).*?y:\\s*(?<y>-?[\\d.]+).*?z:\\s*(?<z>-?[\\d.]+).*?Vanquisher.*");
    private static final Pattern DEAD = Pattern.compile("^(?<channel>.*> )?(?<name>[^:]+): Vanquisher dead!.*");
    private static final long SHARE_WINDOW_MS = 60_000, WAYPOINT_MS = 60_000;
    private static final int COLOR = 0xFFA025BF;

    private static final WaypointSet waypoints = new WaypointSet().removeWithin(5);
    private static final Map<String, BlockPos> bySender = new HashMap<>();
    private static BlockPos own;
    private static long ownAt, lastShare;
    private static boolean keyWasDown;
    private static Object lastLevel;

    private VanquisherAlert() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VanquisherAlert").section("Crimson Isle").when(() -> cfg().vanquisherAlert)
                .tick(VanquisherAlert::tick)
                .chat(VanquisherAlert::onChat)
                .world(VanquisherAlert::render)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Vanquisher");
                    b.toggle("Vanquisher Alert", () -> c.vanquisherAlert, v -> c.vanquisherAlert = v)
                            .desc("Title, sound and chat line on 'A Vanquisher is spawning nearby!'")
                            .tags("vanquisher", "vanq", "crimson", "share", "coords");
                    b.channel("  Share Coords To", () -> c.vanquisherShareChannel, v -> c.vanquisherShareChannel = v);
                    b.toggle("  Share Instantly", () -> c.vanquisherShareInstant, v -> c.vanquisherShareInstant = v)
                            .desc("Off = a [Share] button in chat and the key below");
                    b.keybind("  Share Key", () -> c.vanquisherShareKey, v -> c.vanquisherShareKey = v);
                    b.toggle("  Waypoints From Chat", () -> c.vanquisherWaypoints, v -> c.vanquisherWaypoints = v)
                            .desc("Beam + tracer to Vanquisher coords others share, gone on arrival, after 60 s or on 'Vanquisher dead!'");
                    b.toggle("    Also Read All Chat", () -> c.vanquisherReadAllChat, v -> c.vanquisherReadAllChat = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            waypoints.clear();
            bySender.clear();
            own = null;
        }
        waypoints.tick();
        boolean down = mc.gui.screen() == null && KeyNames.isDown(cfg().vanquisherShareKey);
        if (down && !keyWasDown) share();
        keyWasDown = down;
    }

    private static void onChat(String raw) {
        String text = StringUtil.stripTrim(raw);
        if (!text.contains("Vanquisher")) return;
        if (text.equals(SPAWN)) {
            if (StringUtil.isServerMessage(raw)) spawned();
            return;
        }
        if (!cfg().vanquisherWaypoints || !Crimson.here()) return;
        Minecraft mc = Minecraft.getInstance();
        Matcher d = DEAD.matcher(text);
        if (d.matches()) {
            if (d.group("channel") == null && !cfg().vanquisherReadAllChat) return;
            BlockPos p = bySender.remove(playerName(d.group("name")));
            if (p != null) waypoints.remove(p);
            return;
        }
        Matcher m = SHARED.matcher(text);
        if (!m.matches()) return;
        if (m.group("channel") == null && !cfg().vanquisherReadAllChat) return;
        String name = playerName(m.group("name"));
        if (mc.player != null && name.equalsIgnoreCase(mc.getUser().getName())) return;
        try {
            BlockPos pos = BlockPos.containing(Double.parseDouble(m.group("x")), Double.parseDouble(m.group("y")),
                    Double.parseDouble(m.group("z")));
            BlockPos old = bySender.put(name, pos);
            if (old != null) waypoints.remove(old);
            waypoints.add(pos, "§5§lVanquisher §7(" + name + ")", COLOR, WAYPOINT_MS);
            Crimson.title("§5§lVanquisher", "§7from " + name, SoundEvents.NOTE_BLOCK_PLING.value());
        } catch (NumberFormatException ignored) {
        }
    }

    private static String playerName(String raw) {
        String s = raw.trim();
        int sp = s.lastIndexOf(' ');
        return sp >= 0 ? s.substring(sp + 1) : s;
    }

    private static void spawned() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        own = mc.player.blockPosition();
        ownAt = System.currentTimeMillis();
        Crimson.title("§5§lVANQUISHER", "§7is spawning nearby!", SoundEvents.ENDER_DRAGON_GROWL);
        String channel = cfg().vanquisherShareChannel;
        if (Announce.NOBODY.equals(channel)) {
            Crimson.say("§5Vanquisher spawning at §b" + own.getX() + " " + own.getY() + " " + own.getZ());
            return;
        }
        if (cfg().vanquisherShareInstant) {
            share();
            return;
        }
        String key = cfg().vanquisherShareKey < 0 ? "" : " §7or press §e" + KeyNames.display(cfg().vanquisherShareKey);
        mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §5Vanquisher spawning! ")
                .append(Component.literal("§a§l[Share]").withStyle(s -> s
                        .withClickEvent(new ClickEvent.RunCommand("/tmap crimson vanqshare"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Send coords to " + channel)))))
                .append(Component.literal(key)));
    }

    /** [Share] click or the key. */
    public static void share() {
        long now = System.currentTimeMillis();
        if (own == null || now - ownAt > SHARE_WINDOW_MS || now - lastShare < 5000) return;
        String channel = cfg().vanquisherShareChannel;
        if (Announce.NOBODY.equals(channel)) return;
        lastShare = now;
        Announce.send(channel, "x: " + own.getX() + ", y: " + own.getY() + ", z: " + own.getZ() + " | Vanquisher");
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (waypoints.all().isEmpty()) return;
        waypoints.render(pose, cam);
        for (WaypointSet.Waypoint w : waypoints.all()) {
            com.teslamaps.render.ESPRenderer.drawTracerFromCamera(pose, Vec3.atCenterOf(w.pos), COLOR, cam);
        }
    }
}
