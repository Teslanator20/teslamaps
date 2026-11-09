package com.teslamaps.features.visitors;

// GanKura's VisitorHandler (title + queue full pling), Skytils' clickable notification, SkyHanni's arrival item blink
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TabListUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VisitorArrival {

    private static final Pattern ARRIVAL = Pattern.compile("^(.+) has arrived on your Garden!$");

    private static boolean queueFull;
    private static int ticks;
    private static String blinkId;
    private static String blinkName;
    private static long blinkUntil;

    private VisitorArrival() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean anyOn() {
        TeslaMapsConfig c = cfg();
        return c.visitorArrivalTitle || c.visitorArrivalWarp || c.visitorQueueFullTitle || c.visitorRareArrivalItem;
    }

    public static void register() {
        Feature.of("VisitorArrival").section("Garden").when(VisitorArrival::anyOn)
                .tick(VisitorArrival::tick)
                .serverChat(VisitorArrival::onChat)
                .hud("visitorrareitem", VisitorArrival::render);
    }

    private static void tick() {
        if (!cfg().visitorQueueFullTitle || ++ticks % 20 != 0) return;
        if (!SkyblockUtils.isInGarden()) {
            queueFull = false;
            return;
        }
        boolean full = false;
        for (String raw : TabListUtils.getTabListLines()) {
            if (StringUtil.stripTrim(raw).equals("Next Visitor: Queue Full!")) {
                full = true;
                break;
            }
        }
        if (full && !queueFull) {
            Minecraft mc = Minecraft.getInstance();
            title(mc, Component.literal("§cQueue Full!"), Component.literal("§eVisitors"));
            if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
        }
        queueFull = full;
    }

    private static void onChat(String raw) {
        String clean = StringUtil.stripTrim(raw);
        Matcher m = ARRIVAL.matcher(clean);
        if (!m.matches()) return;
        String name = m.group(1).trim();
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean inGarden = SkyblockUtils.isInGarden();
        if (c.visitorArrivalTitle && inGarden) {
            title(mc, Component.literal(color(raw, name) + name), Component.literal("§eVisited!"));
        }
        if (c.visitorArrivalWarp && !inGarden && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + color(raw, name) + name
                    + " §eis visiting your garden! §a[Warp]").withStyle(s -> s
                    .withClickEvent(new ClickEvent.RunCommand("/warp garden"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to /warp garden")))));
        }
        if (c.visitorRareArrivalItem) {
            if (name.equals("Jerry")) blink("JERRY;4", "§6Legendary Jerry");
            else if (name.contains("Spaceman")) blink("DCTR_SPACE_HELM", "§cSpace Helmet");
            else if (name.contains("Rhino")) blink("ENCHANTED_BOOK", "§9Dedication IV");
        }
    }

    /** Colour code right in front of the name in the raw line, rarity colour on Hypixel. */
    private static String color(String raw, String name) {
        int at = raw.indexOf(name);
        for (int i = at - 2; i >= 0; i--) {
            if (raw.charAt(i) == '§' && "0123456789abcdef".indexOf(Character.toLowerCase(raw.charAt(i + 1))) >= 0) {
                return "§" + raw.charAt(i + 1);
            }
        }
        return "§a";
    }

    private static void title(Minecraft mc, Component title, Component sub) {
        mc.gui.hud.setTimes(0, 70, 0);
        mc.gui.hud.setTitle(title);
        mc.gui.hud.setSubtitle(sub);
    }

    private static void blink(String id, String name) {
        SkyblockItems.prefetch(id);
        blinkId = id;
        blinkName = name;
        blinkUntil = System.currentTimeMillis() + 5000;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        if (blinkId == null) return;
        if (System.currentTimeMillis() > blinkUntil) {
            blinkId = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int cx = mc.getWindow().getGuiScaledWidth() / 2, y = mc.getWindow().getGuiScaledHeight() / 5;
        ItemStack st = SkyblockItems.stackNow(blinkId);
        if (!st.isEmpty()) {
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(cx - 32, y);
            pose.scale(4f, 4f);
            g.item(st, 0, 0);
            pose.popMatrix();
        }
        g.centeredText(mc.font, blinkName, cx, y + 68, 0xFFFFFFFF);
    }
}
