package com.teslamaps.safari;

// Ported from Sparkling Mutuals' SafariManagerGuard.kt, EntryGuardDecision.kt, EntryKickHold.kt, EntryManagerTargets.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.HypixelModApi;
import com.teslamaps.utils.PartyTracker;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

/** Cancels our own click on the Safari Manager / ticket / entry menu while the party is not ready. Shift enters anyway. */
public final class SafariEntryGuard {

    public enum Stage { UNKNOWN, OTHER, TORRHUS, SAFARI }

    private static final String SECTION = "Critter Safari";

    private static Object kickWorld;
    private static long kickUntil;
    private static Object targetWorld;
    private static final Set<String> knownManagers = new HashSet<>();
    private static final Set<List<Double>> anchors = new LinkedHashSet<>();
    private static long messagedAt;
    private static int apiTicks;

    private SafariEntryGuard() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean anyOn() {
        TeslaMapsConfig c = cfg();
        return (c.safariEntryGuard || c.safariDowntime || c.safariContestGuard) && c.section(SECTION);
    }

    public static void register() {
        Feature.of("SafariEntryGuard").section(SECTION)
                .when(SafariEntryGuard::anyOn)
                .tick(SafariEntryGuard::tick)
                .chat(SafariEntryGuard::onChat)
                .config(SECTION, b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Entry Guard");
                    b.toggle("Safari Entry Guard", () -> c.safariEntryGuard, v -> c.safariEntryGuard = v)
                            .desc("Blocks your own click on the Safari Manager, ticket and entry menu while party members are missing from tab or someone wrote \"kick\". Hold Shift to enter anyway")
                            .tags("safari", "entry", "guard", "kick", "party", "manager");
                    b.toggle("Downtime Command (!dt)", () -> c.safariDowntime, v -> c.safariDowntime = v)
                            .desc("!dt [reason] in party chat holds the entry until that player says r / ready / ticket. Hold Shift to enter anyway")
                            .tags("safari", "dt", "downtime", "ready");
                    b.toggle("  Leader Posts DT Requests", () -> c.safariDowntimeAnnounce, v -> c.safariDowntimeAnnounce = v)
                            .desc("As party leader in Torrhus Canyon, post \"DT request by X (Reason: ...)\" / \"" + SafariDowntime.TICKET_REMINDER + "\" to party chat");
                    b.toggle("Contest Guard", () -> c.safariContestGuard, v -> c.safariContestGuard = v)
                            .desc("As party leader: posts \"New Contest Started!\" when a Miria's Contest starts and holds the entry until everyone wrote r or ticket, reminds once after 30s")
                            .tags("safari", "contest", "miria", "ticket");
                });
        ClientSendMessageEvents.COMMAND.register(SafariContestGuard::onCommand);
    }

    private static void tick() {
        SafariDowntime.tick();
        SafariContestGuard.tick();
        Minecraft mc = Minecraft.getInstance();
        if (kickWorld != null && kickWorld != mc.level) {
            kickWorld = null;
            kickUntil = 0;
        }
        if (cfg().safariEntryGuard && stage() == Stage.SAFARI && PartyTracker.leader() == null && ++apiTicks >= 100) {
            apiTicks = 0;
            HypixelModApi.requestParty();
        }
    }

    private static void onChat(String raw) {
        SafariDowntime.onChat(raw);
        SafariContestGuard.onChat(raw);
        if (!cfg().safariEntryGuard || stage() != Stage.SAFARI) return;
        String[] chat = SafariDowntime.party(raw);
        if (chat == null || !chat[1].toLowerCase(Locale.ROOT).contains("kick")) return;
        Minecraft mc = Minecraft.getInstance();
        if (kickWorld != mc.level) kickUntil = 0;
        kickWorld = mc.level;
        kickUntil = Math.max(kickUntil, System.currentTimeMillis() + 2000L);
    }

    public static Stage stage() {
        String mode = HypixelModApi.mode();
        if (mode != null) {
            if (mode.equalsIgnoreCase("safari")) return Stage.SAFARI;
            if (mode.equalsIgnoreCase("foraging_3")) return Stage.TORRHUS;
            return Stage.OTHER;
        }
        String area = SkyblockUtils.getArea();
        if (area.isEmpty()) return Stage.UNKNOWN;
        if (area.equalsIgnoreCase("Torrhus Canyon") || area.equalsIgnoreCase("Torrhus Springs")
                || area.equalsIgnoreCase("Critter Safari Entrance")) return Stage.TORRHUS;
        if (area.toLowerCase(Locale.ROOT).contains("safari")) return Stage.SAFARI;
        return Stage.OTHER;
    }

    /** Inside a running Safari, past the manager. */
    public static boolean activeRun() {
        return stage() == Stage.SAFARI && SafariData.inSafari();
    }

    public static boolean inTorrhus() {
        return stage() == Stage.TORRHUS;
    }

    public static boolean inCanyon() {
        return !activeRun() && inTorrhus();
    }

    static String selfName() {
        var p = Minecraft.getInstance().player;
        return p == null ? "" : p.getGameProfile().name();
    }

    private static boolean kickActive() {
        return kickWorld == Minecraft.getInstance().level && System.currentTimeMillis() < kickUntil;
    }

    private static List<String> missing(Minecraft mc) {
        List<String> out = new ArrayList<>();
        if (stage() != Stage.SAFARI || mc.getConnection() == null || !PartyTracker.inParty()) return out;
        Set<String> present = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getListedOnlinePlayers()) present.add(info.getProfile().name().toLowerCase(Locale.ROOT));
        if (mc.level != null) for (Player p : mc.level.players()) present.add(p.getGameProfile().name().toLowerCase(Locale.ROOT));
        for (String m : PartyTracker.members()) {
            if (!present.contains(m.toLowerCase(Locale.ROOT))) out.add(m);
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    private static boolean decide(Stage stage, boolean downtime, boolean kick, boolean presenceReady) {
        return switch (stage) {
            case OTHER -> false;
            case SAFARI -> downtime || kick || !presenceReady;
            case TORRHUS -> downtime;
            case UNKNOWN -> kick || !presenceReady;
        };
    }

    private static boolean blocks(Minecraft mc) {
        if (mc.player == null || !anyOn()) return false;
        TeslaMapsConfig c = cfg();
        boolean guard = c.safariEntryGuard;
        boolean downtime = SafariDowntime.entryHold();
        boolean shift = mc.hasShiftDown();
        if (shift) SafariContestGuard.bypass();
        Stage stage = stage();
        List<String> contestWaiting = stage == Stage.TORRHUS ? SafariContestGuard.waiting() : List.of();
        List<String> missing = guard ? missing(mc) : List.of();
        boolean presenceReady = !guard || stage == Stage.SAFARI && missing.isEmpty();
        boolean kick = guard && kickActive();

        boolean blocked = !contestWaiting.isEmpty() && !shift
                || !shift && (guard || c.safariDowntime) && decide(stage, downtime, kick, presenceReady);
        if (!blocked) return false;

        String reason;
        if (!contestWaiting.isEmpty()) reason = "Waiting for ticket confirmation from " + String.join(", ", contestWaiting);
        else if (stage == Stage.UNKNOWN) reason = "Waiting for server info";
        else if (downtime) reason = "Waiting for " + SafariDowntime.waitingFor();
        else if (!missing.isEmpty()) reason = missing.size() + " missing: " + String.join(", ", missing);
        else if (kick) reason = "Kick reported; waiting 2 seconds";
        else reason = "Waiting for party readiness";

        long now = System.currentTimeMillis();
        if (now - messagedAt >= 3000L) {
            messagedAt = now;
            mc.player.sendSystemMessage(Component.literal(
                    "§a[TeslaMaps] §cSafari: " + reason + ". §7Hold Shift to enter anyway."));
        }
        return true;
    }

    public static boolean shouldBlock(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == null || !anyOn() || !isManager(mc, entity)) return false;
        return blocks(mc);
    }

    public static boolean shouldBlockTicket(Player player, InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        if (player != mc.player || !anyOn()) return false;
        String name = StringUtil.stripTrim(player.getItemInHand(hand).getHoverName().getString());
        if (!name.toLowerCase(Locale.ROOT).contains("critter safari ticket")) return false;
        return blocks(mc);
    }

    public static boolean shouldBlockEntry(int containerId, int slotId) {
        if (!anyOn() || entrySlot(containerId, slotId) == null) return false;
        return blocks(Minecraft.getInstance());
    }

    /** Shift-click on the entry tag would quick-move; turn it into a normal pick so Shift still enters. */
    public static ContainerInput entryInput(ContainerInput input, int containerId, int slotId) {
        if (input != ContainerInput.QUICK_MOVE || !anyOn() || !Minecraft.getInstance().hasShiftDown()) return input;
        return entrySlot(containerId, slotId) != null ? ContainerInput.PICKUP : input;
    }

    private static Slot entrySlot(int containerId, int slotId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?> screen)) return null;
        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu.containerId != containerId || slotId < 0 || slotId >= menu.slots.size()) return null;
        Slot slot = menu.slots.get(slotId);
        String title = StringUtil.stripTrim(screen.getTitle().getString());
        if (!title.equalsIgnoreCase("Critter Safari Entry")) return null;
        if (!slot.getItem().is(Items.NAME_TAG) || slot.container == mc.player.getInventory()) return null;
        return slot;
    }

    private static boolean named(Entity e) {
        Component custom = e.getCustomName();
        if (custom != null && StringUtil.stripTrim(custom.getString()).equalsIgnoreCase("Safari Manager")) return true;
        return StringUtil.stripTrim(e.getName().getString()).equalsIgnoreCase("Safari Manager");
    }

    private static boolean isManager(Minecraft mc, Entity entity) {
        ClientLevel level = mc.level;
        if (level == null) return false;
        if (targetWorld != level) {
            targetWorld = level;
            knownManagers.clear();
            anchors.clear();
        }
        if (entity == mc.player) return false;
        if (entity instanceof Player p && PartyTracker.isMember(p.getGameProfile().name())) return false;
        for (Entity e : level.entitiesForRendering()) {
            if (!e.isRemoved() && named(e)) anchors.add(List.of(e.getX(), e.getY(), e.getZ()));
        }
        String id = entity.getUUID().toString();
        if (named(entity)) {
            knownManagers.add(id);
            return true;
        }
        String type = entity.getType().toShortString();
        boolean proxy = entity instanceof Player || entity instanceof ArmorStand
                || type.equals("interaction") || type.equals("text_display");
        if (!proxy) return false;
        if (knownManagers.contains(id)) return true;
        for (List<Double> a : anchors) {
            double dy = a.get(1) - entity.getY();
            if (Math.abs(entity.getX() - a.get(0)) <= 0.5 && Math.abs(entity.getZ() - a.get(2)) <= 0.5 && dy >= -0.5 && dy <= 3.5) {
                knownManagers.add(id);
                return true;
            }
        }
        return false;
    }
}
