package com.teslamaps.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from SkyHanni's RescueMissionWaypoints/ParkourHelper. No island graph navigation here -
// only the hostage parkour path. Waypoint data is SkyHanni's RescueMissionWaypoints.json, bundled.
public class RescueMission {

    private static final Pattern TIER_PATTERN = Pattern.compile("^(?<tier>[ⓈⒶⒷⒸⒹ]) Rescue Mission$");
    private static final double DETECTION_RANGE = 3.5;

    private static Map<String, List<Vec3>> barb;
    private static Map<String, List<Vec3>> mage;
    private static boolean loadFailed;

    private static String tier;
    private static boolean tierWasUnknown;
    private static String faction;
    private static long factionTime;

    private static List<Vec3> route;
    private static int current = -1;
    private static boolean visible;

    private static void load() {
        if (barb != null || loadFailed) return;
        try (var in = RescueMission.class.getResourceAsStream("/assets/teslamaps/data/rescue_waypoints.json")) {
            JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            barb = parseSide(root.getAsJsonObject("barb"));
            mage = parseSide(root.getAsJsonObject("mage"));
        } catch (Exception e) {
            loadFailed = true;
            TeslaMaps.LOGGER.error("Failed to load rescue_waypoints.json", e);
        }
    }

    private static Map<String, List<Vec3>> parseSide(JsonObject side) {
        Map<String, List<Vec3>> out = new HashMap<>();
        if (side == null) return out;
        for (String key : side.keySet()) {
            List<Vec3> list = new ArrayList<>();
            for (JsonElement e : side.getAsJsonArray(key)) {
                String[] parts = e.getAsString().split(":");
                list.add(new Vec3(Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
            }
            out.put(key, list);
        }
        return out;
    }

    public static void tick() {
        if (!TeslaMapsConfig.get().rescueMission) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!SkyblockUtils.isInArea("Crimson Isle")) {
            if (route != null || tier != null) stop();
            tier = null;
            return;
        }

        readQuestBoard(mc);
        trackProgress(mc);
    }

    // the quest board's "Rescue" menu carries the tier letter on the center item
    private static void readQuestBoard(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Rescue")) return;

        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            if (slot.getContainerSlot() != 22 || slot.getItem().isEmpty()) continue;
            Matcher m = TIER_PATTERN.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString()));
            if (!m.matches()) return;
            tier = switch (m.group("tier")) {
                case "Ⓢ" -> "S";
                case "Ⓐ" -> "A";
                case "Ⓑ" -> "B";
                case "Ⓒ" -> "C";
                default -> "D";
            };
            if (tierWasUnknown) {
                tierWasUnknown = false;
                say("§aTier known (" + tier + ") - talk to the Undercover Agent to see the path.");
            }
            return;
        }
    }

    // tab list carries "Barbarian Reputation:" / "Mage Reputation:"; config can override
    private static String faction() {
        String forced = TeslaMapsConfig.get().rescueFaction;
        if ("Barbarian".equals(forced)) return "barb";
        if ("Mage".equals(forced)) return "mage";

        long now = System.currentTimeMillis();
        if (now - factionTime < 5000) return faction;
        factionTime = now;
        for (String line : TabListUtils.getTabListLines()) {
            String s = StringUtil.stripTrim(line);
            if (s.startsWith("Barbarian Reputation:")) return faction = "barb";
            if (s.startsWith("Mage Reputation:")) return faction = "mage";
        }
        return faction;
    }

    // same walk detection as SkyHanni's ParkourHelper: on ground within range of a platform
    private static void trackProgress(Minecraft mc) {
        if (route == null) return;
        if (current == route.size() - 1) { stop(); return; }

        Vec3 player = mc.player.position();
        boolean onGround = mc.player.onGround();
        if (onGround) {
            for (int i = 0; i < route.size(); i++) {
                if (player.distanceTo(center(route.get(i))) < DETECTION_RANGE) current = i;
            }
        }

        double startDistance = player.distanceTo(center(route.get(0)));
        if (startDistance < DETECTION_RANGE) {
            visible = true;
        } else if (startDistance > 15 && current < 1) {
            visible = false;
        }
    }

    private static Vec3 center(Vec3 location) {
        return location.add(0.5, 1.0, 0.5);
    }

    public static void onChatMessage(String raw) {
        if (!TeslaMapsConfig.get().rescueMission) return;
        String text = StringUtil.stripTrim(raw);

        if (text.equals("The guards captured you and threw you out.")) {
            current = -1;
            visible = false;
        } else if (text.equals("You took too long and the agents found someone else.")
                || text.equals("[NPC] Undercover Agent: It's too dangerous for you?")
                || text.equals("You left the area and failed your rescue mission.")) {
            stop();
        } else if (text.startsWith("[NPC] Undercover Agent:")) {
            // the dialogue varies with the quest state ("Here's what you need to do.",
            // "Good luck out there!", ...), so any remaining agent line starts the path
            if (tier == null) {
                tierWasUnknown = true;
                say("§cRescue mission tier not known - open the Rescue quest on the Quest Board once.");
                return;
            }
            if (route == null) start();
        } else if (text.equals("[NPC] Rescue Recruiter: You want to help us rescue a hostage from those filthy brutes over there?")) {
            if (tier == null) {
                tierWasUnknown = true;
                say("§cRescue mission tier not known - open the Rescue quest on the Quest Board first!");
            }
        }
    }

    private static void start() {
        load();
        if (barb == null) return;
        String side = faction();
        if (side == null) {
            say("§cFaction unknown - tab list has no Mage/Barbarian Reputation line. Set it in the config (Crimson Isle).");
            return;
        }

        List<Vec3> found;
        if (tier.equals("S")) {
            if (side.equals("mage")) {
                say("§cNo path data for Mage S-rank rescue missions yet.");
                return;
            }
            found = barb.get("S" + TeslaMapsConfig.get().rescueVariant);
        } else {
            found = (side.equals("mage") ? mage : barb).get(tier);
        }
        if (found == null || found.isEmpty()) {
            say("§cNo path data for tier " + tier + " (" + side + ").");
            return;
        }

        route = found;
        current = -1;
        visible = true;
        say("§aRescue path loaded - tier " + tier + ", " + (side.equals("mage") ? "Mage" : "Barbarian")
                + ", " + found.size() + " platforms.");
    }

    private static void stop() {
        route = null;
        current = -1;
        visible = false;
    }

    private static void say(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a[TeslaMaps] " + message));
        }
    }

    public static void render(PoseStack matrices, Vec3 cameraPos) {
        if (route == null || !TeslaMapsConfig.get().rescueMission) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // no island graph navigation here, so until the parkour is entered the start
        // itself is the waypoint - beacon + tracer + distance
        if (current < 0) {
            Vec3 start = center(route.get(0));
            int distance = (int) mc.player.position().distanceTo(start);
            ESPRenderer.drawBeaconBeam(matrices, net.minecraft.core.BlockPos.containing(
                    route.get(0).x, route.get(0).y, route.get(0).z), 0xFF55FF55, cameraPos);
            ESPRenderer.drawTracerFromCamera(matrices, start, 0xC055FF55, cameraPos);
            com.teslamaps.features.waypoints.WaypointSet.drawLabel(matrices, cameraPos, "§aRescue Parkour Start §7(" + distance + "m)",
                    start.add(0, 1.5, 0), 2f);
        }
        if (!visible) return;

        int lookAhead = Math.max(1, TeslaMapsConfig.get().rescueLookAhead);
        int from = Math.max(0, current);

        for (int i = from; i < Math.min(route.size(), from + lookAhead); i++) {
            Vec3 location = route.get(i);
            AABB box = new AABB(location.x, location.y, location.z,
                    location.x + 1, location.y + 1, location.z + 1).inflate(0.05);
            ESPRenderer.drawFilledBox(matrices, box, colorForIndex(i), cameraPos, true);
            ESPRenderer.drawText(matrices, "§a§l" + i, center(location).add(0, 1, 0), 1.5f, cameraPos);
            if (i + 1 < route.size() && i + 1 < from + lookAhead) {
                ESPRenderer.drawLine(matrices, center(location), center(route.get(i + 1)),
                        colorForIndex(i), 3f, cameraPos);
            }
        }
    }

    // SkyHanni's default rainbow: hue steps per platform
    private static int colorForIndex(int index) {
        float hue = ((-index / 12f) % 1f + 1f) % 1f;
        return 0xB0000000 | (java.awt.Color.HSBtoRGB(hue, 1f, 0.7f) & 0xFFFFFF);
    }
}
