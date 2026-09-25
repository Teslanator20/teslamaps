package com.teslamaps.features;

// Ported from SkyHanni's CompactSplashPotionMessage / CompactBestiaryChatMessage,
// DulkirMod's AbiphoneDND and Devonian's NoAbilityCdSound
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatCompacts {

    private static final Pattern SPLASHED = Pattern.compile("^BUFF! You were splashed by (.+?) with (.+?)!(?: Press TAB or type /effects to view your active effects!)?$");
    private static final Pattern GAINED = Pattern.compile("^BUFF! You have gained (.+?)!(?: Press TAB or type /effects to view your active effects!)?$");
    private static final Pattern SELF = Pattern.compile("^BUFF! You splashed yourself with (.+?)!(?: Press TAB or type /effects to view your active effects!)?$");

    private static final Pattern BORDER = Pattern.compile("^▬{20,}$");
    private static final Pattern MILESTONE = Pattern.compile("^.+(§8\\d{1,3}➡§e\\d{1,3})$");

    private static final Pattern CALL = Pattern.compile("^✆ (.+?) ✆.*$");
    private static final long RING_MS = 5000;

    private static boolean inBestiary, acceptMore;
    private static int bestiaryLines;
    private static final List<String> bestiary = new ArrayList<>();
    private static String bestiaryCommand;
    private static String milestone;

    private static long ringUntil;

    private static Component pendingOut;
    private static String pendingText;

    private ChatCompacts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatCompacts").section("Chat")
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Compact Messages");
                    b.toggle("Compact Splash Potion Messages", () -> c.compactPotionMessages, v -> c.compactPotionMessages = v)
                            .desc("\"BUFF! You were splashed by X with Y! Press TAB...\" becomes \"Potion Effect! Y by X\"")
                            .tags("potion", "splash", "buff", "compact");
                    b.toggle("  Click Opens /effects", () -> c.compactPotionClickable, v -> c.compactPotionClickable = v);
                    b.toggle("Compact Bestiary Level-Ups", () -> c.compactBestiary, v -> c.compactBestiary = v)
                            .desc("The BESTIARY block becomes one line, details on hover. Milestones get their own line.")
                            .tags("bestiary", "compact");
                });
    }

    public static boolean soundActive(TeslaMapsConfig c) {
        return (c.muteAbilityCooldownSound || c.abiphoneDnd) && c.section("Chat");
    }

    public static boolean shouldMuteSound(TeslaMapsConfig c, String id, float vol) {
        if (c.abiphoneDnd && id.equals("minecraft:block.note_block.pling") && System.currentTimeMillis() < ringUntil) return true;
        if (!c.muteAbilityCooldownSound || vol < 7.9f || !id.equals("minecraft:entity.enderman.teleport")) return false;
        if (DungeonManager.isInDungeon()) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && ItemUtil.loreContains(mc.player.getMainHandItem(), "Ability: ");
    }

    /** From ChatMixin on server lines: true = drop. May queue a rewrite. */
    public static boolean process(Component message, String text) {
        pendingOut = null;
        pendingText = null;
        TeslaMapsConfig c = cfg();
        if (!c.compactPotionMessages && !c.compactBestiary && !c.abiphoneDnd) return false;
        String t = StringUtil.stripTrim(text);
        if (c.abiphoneDnd && abiphone(t)) return true;
        if (c.compactBestiary && bestiary(message, text, t)) return true;
        else if (!c.compactBestiary) inBestiary = false;
        if (c.compactPotionMessages && t.startsWith("BUFF! ")) potion(text, t);
        return false;
    }

    public static Component rewrite(Component message) {
        if (pendingOut == null) return message;
        Component out = pendingOut;
        boolean same = message.getString().equals(pendingText);
        pendingOut = null;
        pendingText = null;
        return same ? out : message;
    }

    private static boolean abiphone(String t) {
        long now = System.currentTimeMillis();
        if (t.startsWith("✆ RING") || t.startsWith("✆ Ring")) {
            if (now < ringUntil) {
                ringUntil = now + RING_MS;
                return true;
            }
            return false;
        }
        if (!t.startsWith("✆ ")) return false;
        Matcher m = CALL.matcher(t);
        if (!m.matches()) return false;
        String caller = m.group(1).trim();
        if (caller.equals("Elle") || caller.equals("Dean")) return false;
        ringUntil = now + RING_MS;
        if (cfg().abiphoneDndCallerId) say(Component.literal("§b[TeslaMaps] §6Call blocked from " + caller + "!"));
        return true;
    }

    private static void potion(String raw, String t) {
        String effect, by = null;
        Matcher m = SPLASHED.matcher(t);
        if (m.matches()) {
            by = m.group(1);
            effect = m.group(2);
        } else if ((m = GAINED.matcher(t)).matches() || (m = SELF.matcher(t)).matches()) {
            effect = m.group(1);
        } else return;
        if (by != null) {
            int br = by.lastIndexOf("] ");
            if (by.startsWith("[") && br > 0) by = by.substring(br + 2);
        }
        MutableComponent out = Component.literal("§a§lPotion Effect! §r§f" + effect + (by != null ? " §aby §f" + by : ""));
        if (cfg().compactPotionClickable) {
            out.setStyle(Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/effects"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to view your potion effects."))));
        }
        pendingText = raw;
        pendingOut = out;
    }

    private static boolean bestiary(Component message, String raw, String t) {
        if (t.equals("BESTIARY")) {
            dropRecentBorders();
            bestiaryCommand = null;
            message.visit((style, s) -> {
                if (style.getClickEvent() instanceof ClickEvent.RunCommand run) bestiaryCommand = run.command();
                return Optional.empty();
            }, Style.EMPTY);
            inBestiary = true;
            acceptMore = true;
            bestiaryLines = 0;
            milestone = null;
            bestiary.clear();
            bestiary.add(raw);
            return true;
        }
        if (!inBestiary) return false;
        if (++bestiaryLines > 15) {
            inBestiary = false;
            bestiary.clear();
            return true;
        }
        if (BORDER.matcher(t).matches()) {
            inBestiary = false;
            List<String> list = new ArrayList<>();
            for (String s : bestiary) list.add(s.replace("§f", "").trim());
            bestiary.clear();
            if (list.size() < 2) return true;
            MutableComponent hover = Component.empty();
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) hover.append("\n");
                hover.append(Component.literal(list.get(i)));
            }
            MutableComponent line = Component.literal("§6§lBESTIARY §r" + list.get(1));
            Style style = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(hover));
            if (bestiaryCommand != null) style = style.withClickEvent(new ClickEvent.RunCommand(bestiaryCommand));
            line.setStyle(style);
            say(line);
            return true;
        }
        if (milestone != null) {
            say(Component.literal("§6§lBESTIARY MILESTONE " + milestone));
            milestone = null;
        }
        Matcher m = MILESTONE.matcher(raw.stripTrailing());
        if (m.matches()) {
            acceptMore = false;
            milestone = m.group(1);
        }
        if (acceptMore) bestiary.add(raw);
        return true;
    }

    // the opening bar and blank line arrive before the title
    private static void dropRecentBorders() {
        Minecraft mc = Minecraft.getInstance();
        var chat = mc.gui.getChat();
        List<GuiMessage> all = ((ChatComponentAccessor) chat).teslamaps$getAllMessages();
        int now = mc.gui.getGuiTicks();
        boolean removed = false;
        for (int i = 0; i < 2 && !all.isEmpty(); i++) {
            GuiMessage g = all.getFirst();
            if (now - g.addedTime() > 10) break;
            String s = StringUtil.stripTrim(g.content().getString());
            if (!s.isEmpty() && !BORDER.matcher(s).matches()) break;
            all.removeFirst();
            removed = true;
        }
        if (removed) ((ChatComponentAccessor) chat).teslamaps$refreshTrimmedMessages();
    }

    private static void say(Component line) {
        TickScheduler.schedule(1, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendSystemMessage(line);
        });
    }
}
