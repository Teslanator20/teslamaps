package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel silently drops /gfs commands that arrive too fast, so every request goes through
 * one queue that spaces them out. Also adds a default amount, a hover keybind, a bazaar
 * follow-up when the sack ran dry, and a grab-it link after supercrafting.
 */
public class GetFromSacks {

    // Hypixel's command cooldown - SkyHanni uses the same 1.65s
    private static final int SEND_INTERVAL_TICKS = 33;

    // SkyHanni queues /gfs itself, cancelling the packet and resending it via sendCommand.
    // Our re-send hits its packet hook, its re-send hits our ALLOW_COMMAND hook -> the command
    // ping-pongs between both queues and never reaches Hypixel. Stand down instead.
    public static final boolean SKYHANNI = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skyhanni");

    private static final Pattern MOVED = Pattern.compile("Moved (?<amount>[\\d,]+) (?<item>.+) from your Sacks to your inventory\\.");
    private static final Pattern MISSING = Pattern.compile("You have no (?<item>.+) in your Sacks!");
    private static final Pattern SUPERCRAFTED = Pattern.compile("You Supercrafted (?<item>[^§]+?)(?: x(?<amount>[\\d,]+))?!");

    private record Request(String item, int amount) {}

    private static final Deque<Request> queue = new ArrayDeque<>();
    private static int cooldown = 0;
    private static boolean sendingOwnCommand = false;
    private static Request lastSent = null;

    /**
     * Hook for ClientSendMessageEvents.ALLOW_COMMAND.
     *
     * @return false to swallow the command because it went into the queue instead.
     */
    public static boolean allowCommand(String command) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (SKYHANNI || !c.gfsQueued || sendingOwnCommand) return true;

        String[] parts = command.trim().split("\\s+");
        String name = parts[0].toLowerCase();
        if (!name.equals("gfs") && !name.equals("getfromsacks")) return true;

        if (parts.length < 2) {
            userError("Missing arguments! Usage: /getfromsacks <name/id> [amount]");
            return false;
        }

        String last = parts[parts.length - 1];
        int amount;
        int itemEnd;
        Double parsed = parseAmount(last);
        if (parsed == null) {
            amount = c.gfsDefaultAmount;
            itemEnd = parts.length;
        } else {
            amount = parsed.intValue();
            itemEnd = parts.length - 1;
            if (itemEnd <= 1) {
                userError("Missing arguments! Usage: /getfromsacks <name/id> [amount]");
                return false;
            }
        }

        StringBuilder query = new StringBuilder();
        for (int i = 1; i < itemEnd; i++) {
            if (!query.isEmpty()) query.append('_');
            query.append(parts[i]);
        }

        // no list yet (still downloading) means no validation, better than swallowing the command
        String item = query.toString();
        if (SackItems.ready()) {
            String resolved = SackItems.resolve(item);
            // unknown item goes to Hypixel untouched, same as SkyHanni
            if (resolved == null) return true;
            item = resolved;
        }

        enqueue(item, amount);
        return false;
    }

    private static Double parseAmount(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void userError(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] " + message));
    }

    public static void enqueue(String item, int amount) {
        if (item == null || item.isBlank() || amount <= 0) return;
        queue.add(new Request(item, amount));
    }

    public static void tick() {
        if (cooldown > 0) cooldown--;
        if (queue.isEmpty() || cooldown > 0) return;

        Request request = queue.poll();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;

        lastSent = request;
        cooldown = SEND_INTERVAL_TICKS;
        sendingOwnCommand = true;
        try {
            mc.getConnection().sendCommand("gfs " + request.item().replace(':', '-') + " " + request.amount());
        } finally {
            sendingOwnCommand = false;
        }
    }

    public static void onChatMessage(String raw) {
        if (SKYHANNI) return; // SkyHanni prints the same supercraft/bazaar links
        TeslaMapsConfig c = TeslaMapsConfig.get();
        String text = StringUtil.strip(raw).trim();

        if (c.gfsSuperCraft) {
            Matcher m = SUPERCRAFTED.matcher(text);
            if (m.matches()) {
                String item = m.group("item").trim();
                int amount = m.group("amount") == null ? 1 : parseInt(m.group("amount"));
                // only offer it when the item actually lives in a sack
                if (!SackItems.ready() || SackItems.resolve(item.replace(' ', '_')) != null) {
                    grabLink(item, amount);
                }
                return;
            }
        }

        if (!c.gfsBazaar || lastSent == null) return;

        Matcher m = MOVED.matcher(text);
        if (m.matches()) {
            int diff = lastSent.amount() - parseInt(m.group("amount"));
            String item = m.group("item").trim();
            lastSent = null;
            if (diff > 0) bazaarLink(item, diff, true);
            return;
        }
        m = MISSING.matcher(text);
        if (m.matches()) {
            bazaarLink(m.group("item").trim(), lastSent.amount(), false);
            lastSent = null;
        }
    }

    /** Called from ContainerKeyMixin while a slot is hovered. */
    public static boolean onKey(int key, net.minecraft.world.inventory.Slot hovered) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (c.gfsKeybind < 0 || key != c.gfsKeybind) return false;
        if (hovered == null) return false;

        ItemStack stack = hovered.getItem();
        if (stack.isEmpty()) return false;

        String id = com.teslamaps.utils.ItemUtil.skyblockId(stack);
        String item = id != null && !id.isBlank() ? id : StringUtil.strip(stack.getHoverName().getString()).trim();
        enqueue(item, 9999);
        return true;
    }

    private static void grabLink(String item, int amount) {
        send(Component.literal("§e§lCLICK HERE§r§e to grab §ax" + amount + " §9" + item + "§e from sacks!")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/gfs " + item + " " + amount))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to get from sacks!")))));
    }

    private static void bazaarLink(String item, int amount, boolean remaining) {
        MutableComponent line = Component.literal("§e§lCLICK §r§eto get the " + (remaining ? "remaining " : "")
                + "§ax" + amount + " §9" + item + " §efrom bazaar")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/bz " + item))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to find on the bazaar!"))));
        send(line);
    }

    private static void send(Component component) {
        Minecraft mc = Minecraft.getInstance();
        com.teslamaps.utils.TickScheduler.schedule(1, () -> {
            if (mc.player != null) mc.player.sendSystemMessage(component);
        });
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
