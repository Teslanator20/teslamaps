package com.teslamaps.features.qol;

// SkyHanni TiaRelayHelper + TiaRelayWaypoints + Relay: 9f Network Relay sound order and relay waypoints
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TiaRelayHelper {

    private record Relay(String name, BlockPos pos, String island, String chat) {}

    private static final List<Relay> RELAYS = List.of(
            new Relay("1st Relay", new BlockPos(144, 108, 93), "Hub", "first relay is on a branch of the large tree"),
            new Relay("2nd Relay", new BlockPos(-246, 123, 56), "Hub", "next relay is in the castle ruins"),
            new Relay("3rd Relay", new BlockPos(129, 232, 201), "Dwarven Mines", "Royal Palace"),
            new Relay("4th Relay", new BlockPos(-560, 164, -287), "The End", "highest spike of The End"),
            new Relay("5th Relay", new BlockPos(-375, 207, -799), "Crimson Isle", "our consultant, Odger"),
            new Relay("6th Relay", new BlockPos(-69, 157, -879), "Crimson Isle", "Scarleton itself has one of the most robust"),
            new Relay("7th Relay", new BlockPos(93, 85, 187), "Hub", "shack next to the shady inn"),
            new Relay("8th Relay", new BlockPos(0, 146, -76), "Dungeon Hub", "statue in the dungeon hub"),
            new Relay("9th Relay", new BlockPos(-23, 86, -13), "Hub", "on top of the Auction House"));

    private static final int COLOR = 0xFFFF55FF;
    private static final WaypointSet waypoints = new WaypointSet().box(true).beam(true);

    private record Heard(String id, float pitch) {}

    private static final Map<Integer, Heard> sounds = new HashMap<>();
    private static final Map<Integer, String> result = new HashMap<>();
    private static List<Integer> hear = List.of();
    private static boolean inMenu;
    private static int lastClickSlot = -1;
    private static long lastClickTime;
    private static Relay next;
    private static int timer;
    private static Feature feature;

    private TiaRelayHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("TiaRelayHelper").section("Inventory").when(() -> cfg().tiaRelayHelper);
        feature.tick(TiaRelayHelper::tick)
                .serverChat(TiaRelayHelper::onChat)
                .world(waypoints::render)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Tia Relay Helper", () -> c.tiaRelayHelper, v -> c.tiaRelayHelper = v)
                            .desc("Network Relay menu: right-click the 4 slots, the order #1-#4 appears on the slots (measured from the sound pitch).");
                    b.toggle("  Mute Wolf Whine", () -> c.tiaRelayMute, v -> c.tiaRelayMute = v);
                    b.toggle("  Waypoint To Next Relay", () -> c.tiaRelayNextWaypoint, v -> c.tiaRelayNextWaypoint = v);
                    b.toggle("  Show All Relays On Island", () -> c.tiaRelayAllWaypoints, v -> c.tiaRelayAllWaypoints = v);
                });
    }

    private static void tick() {
        waypoints.tick();
        timer++;
        if (timer % 5 == 0) menuTick();
        if (timer % 20 != 0) return;
        TeslaMapsConfig c = cfg();
        String area = SkyblockUtils.getArea();
        waypoints.clear();
        if (c.tiaRelayAllWaypoints) {
            for (Relay r : RELAYS) if (r.island.equalsIgnoreCase(area)) waypoints.add(r.pos, "§d" + r.name, COLOR, 0);
        } else if (c.tiaRelayNextWaypoint && next != null && next.island.equalsIgnoreCase(area)) {
            waypoints.add(next.pos, "§d" + next.name, COLOR, 0);
        }
    }

    private static void menuTick() {
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen instanceof AbstractContainerScreen<?> s && s.getTitle().getString().contains("Network Relay");
        inMenu = open;
        if (!open) {
            sounds.clear();
            result.clear();
            hear = List.of();
            return;
        }
        List<Slot> slots = ((AbstractContainerScreen<?>) mc.screen).getMenu().slots;
        for (int id : result.keySet()) {
            if (id >= 0 && id < slots.size() && ItemUtil.loreContains(slots.get(id).getItem(), "Done!")) {
                result.clear();
                break;
            }
        }
        List<Integer> h = new ArrayList<>(4);
        if (result.isEmpty()) {
            for (int i = 0, n = Math.min(slots.size(), 54); i < n; i++) {
                if (sounds.containsKey(i) || slots.get(i).getItem().isEmpty()) continue;
                if (ItemUtil.loreContains(slots.get(i).getItem(), "Hear!")) h.add(i);
            }
        }
        hear = h;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("You completed the maintenance on the relay!")) {
            next = null;
            return;
        }
        if (!t.startsWith("[NPC] Tia the Fairy:")) return;
        for (Relay r : RELAYS) {
            if (t.contains(r.chat)) {
                next = r;
                return;
            }
        }
    }

    public static void onSlotClick(Slot slot, int slotId, int button, ContainerInput type) {
        if (!inMenu || feature == null || !feature.active() || button != 1 || type != ContainerInput.PICKUP) return;
        lastClickSlot = slotId;
        lastClickTime = System.currentTimeMillis();
    }

    /** true = cancel the sound */
    public static boolean onSound(SoundInstance instance, String id, float pitch) {
        if (feature == null || !feature.active()) return false;
        if (cfg().tiaRelayMute && id.contains("wolf") && id.endsWith("whine")) return true;
        if (!inMenu || lastClickSlot < 0 || System.currentTimeMillis() - lastClickTime > 60_000L) return false;
        if (sounds.containsKey(lastClickSlot)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(instance.getX(), instance.getY(), instance.getZ()) >= 4) return false;
        sounds.put(lastClickSlot, new Heard(id, pitch));
        lastClickSlot = -1;
        tryResult(mc);
        return false;
    }

    private static void tryResult(Minecraft mc) {
        if (sounds.size() < 4) return;
        String name = sounds.values().iterator().next().id;
        for (Heard h : sounds.values()) {
            if (!h.id.equals(name)) {
                sounds.clear();
                mc.execute(() -> {
                    if (mc.player == null) return;
                    mc.player.sendSystemMessage(Component.literal(
                            "§c[TeslaMaps] Tia Relay Helper: too much background noise! Turn off the music and try again."));
                    mc.player.sendSystemMessage(Component.literal("§e[Click here to run /togglemusic]").withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand("/togglemusic"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e/togglemusic")))));
                });
                return;
            }
        }
        List<Map.Entry<Integer, Heard>> list = new ArrayList<>(sounds.entrySet());
        list.sort((a, b) -> Float.compare(a.getValue().pitch, b.getValue().pitch));
        sounds.clear();
        result.clear();
        for (int i = 0; i < list.size(); i++) result.put(list.get(i).getKey(), "#" + (i + 1));
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!inMenu || feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int left = acc.getX(), top = acc.getY();
        List<Slot> slots = cs.getMenu().slots;
        if (!result.isEmpty()) {
            for (Map.Entry<Integer, String> e : result.entrySet()) {
                int id = e.getKey();
                if (id < 0 || id >= slots.size()) continue;
                Slot slot = slots.get(id);
                String text = e.getValue();
                ctx.text(mc.font, text, left + slot.x + 17 - mc.font.width(text), top + slot.y + 9, 0xFFFFFF55);
            }
            return;
        }
        List<Integer> h = hear;
        for (int i = 0, n = h.size(); i < n; i++) {
            int id = h.get(i);
            if (id >= slots.size() || sounds.containsKey(id)) continue;
            Slot slot = slots.get(id);
            ctx.text(mc.font, "Hear!", left + slot.x + 5, top + slot.y - 5, 0xFF55FFFF);
        }
    }
}
