package com.teslamaps.features;

// Ported from SkyHanni's CompactJacobClaim, CompactBingoChat, CompactExperimentRewards,
// ShortenCoins, DarkAuctionItemDisplay and ArachneChatMessageHider
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Compacts and small rewrites of SkyBlock chat blocks. Hooked from ChatFilter. */
public final class ChatCompactsMore {

    private ChatCompactsMore() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean emitting;
    private static Object lastLevel;
    private static String pendingText;
    private static Component pendingOut;

    public static void register() {
        Feature.of("ChatCompacts").when(() -> cfg().compactJacobClaim || cfg().compactExperimentRewards)
                .tick(ChatCompactsMore::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Compact & Tweaks");
                    b.toggle("Compact Jacob Contest Claims", () -> c.compactJacobClaim, v -> c.compactJacobClaim = v)
                            .desc("Garden: a bulk claim becomes one summary line, the full list on hover");
                    b.toggle("Compact Level-Ups", () -> c.compactLevelUps, v -> c.compactLevelUps = v)
                            .desc("Skill, SkyBlock and collection level-ups and New Area blocks cut to the key lines");
                    b.toggle("  Hide Level-Up Borders", () -> c.compactLevelUpsHideBorder, v -> c.compactLevelUpsHideBorder = v);
                    b.toggle("Compact Experiment Rewards", () -> c.compactExperimentRewards, v -> c.compactExperimentRewards = v)
                            .desc("Experimentation Table: the claim and all +reward lines as one line with hover");
                    b.toggle("Shorten Coin Amounts", () -> c.shortenChatCoins, v -> c.shortenChatCoins = v)
                            .desc("Gold coin numbers in SkyBlock chat: 5,100,000 -> 5.1M");
                    b.toggle("Dark Auction Item In Bid Line", () -> c.darkAuctionItemName, v -> c.darkAuctionItemName = v)
                            .desc("Appends the item to Sirius' highest bidder / no bids line");
                    b.toggle("Hide Arachne Messages", () -> c.hideArachneMessages, v -> c.hideArachneMessages = v)
                            .desc("Spider's Den: other players' Arachne callings, spawns and ARACHNE DOWN outside the Sanctuary, Venom Shot lines everywhere");
                    b.toggle("Hide Message Indicator Bar", () -> c.hideChatIndicator, v -> c.hideChatIndicator = v)
                            .desc("Removes the coloured bar left of system / modified / unsigned chat lines");
                });
    }

    /** From ChatFilter.process; true = drop the line. May queue a rewrite. */
    public static boolean handle(Component message, String t) {
        pendingText = null;
        pendingOut = null;
        if (emitting) return false;
        TeslaMapsConfig c = cfg();
        if (!(c.compactJacobClaim || c.compactLevelUps || c.compactExperimentRewards || c.shortenChatCoins
                || c.darkAuctionItemName || c.hideArachneMessages)) return false;
        PlayerChat.Msg pm = PlayerChat.current();
        boolean npc = pm != null && pm.channel() == PlayerChat.Channel.NPC;
        if (pm != null && !npc) return false;

        Object level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            lastLevel = level;
            daItem = null;
            arachneHide = false;
        }

        String trimmed = t.trim();
        if (!npc) {
            if (c.compactJacobClaim && jacob(message, trimmed)) return true;
            if (c.compactLevelUps && levelUp(message, t, trimmed)) return true;
            if (c.compactExperimentRewards && experiment(message, t, trimmed)) return true;
        }
        if (c.hideArachneMessages && arachne(message, trimmed)) return true;

        Component out = message;
        if (c.darkAuctionItemName) {
            Component da = darkAuction(message, trimmed);
            if (da != null) out = da;
        }
        if (c.shortenChatCoins && hasDigitGroup(t) && SkyblockUtils.isInSkyblock()) {
            Component sc = shortenCoins(out);
            if (sc != null) out = sc;
        }
        if (out != message) {
            pendingText = message.getString();
            pendingOut = out;
        }
        return false;
    }

    /** Right before vanilla stores the line, after ChatFilter's own rewrite. */
    public static Component rewrite(Component message) {
        if (pendingOut == null) return message;
        Component out = pendingOut;
        boolean same = message.getString().equals(pendingText);
        pendingOut = null;
        pendingText = null;
        return same ? out : message;
    }

    private static void tick() {
        long now = System.currentTimeMillis();
        if (cfg().compactJacobClaim && jacobOpen && (jacobBits || now - jacobLast >= 300)) jacobPublish();
        if (cfg().compactExperimentRewards) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractContainerScreen<?> && experimentTitle(mc.screen.getTitle().getString())) {
                expMenuSeen = now;
            }
            if (!expRewards.isEmpty() && now - expLast >= 150) expPublish();
        }
    }

    private static void emit(Component line) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        emitting = true;
        try {
            player.sendSystemMessage(line);
        } finally {
            emitting = false;
        }
    }

    private static Component hover(List<Component> lines) {
        MutableComponent h = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) h.append("\n");
            h.append(lines.get(i));
        }
        return h;
    }

    // ---- CHAT-024 Jacob contest claim ----

    private static final Pattern J_CONTEST = Pattern.compile("» [\\w ]+ Contest on [\\w ]+ \\d+[stndrh]{2,3}, Year \\d+");
    private static final Pattern J_TICKET = Pattern.compile("(Jacob's|Carnival) Ticket(?: x([\\d,]+))?");
    private static final Pattern J_BOOK = Pattern.compile("([\\d,]+)x Turbo-(\\S+) I Book");
    private static final Pattern J_MEDAL = Pattern.compile("\\+([\\d,]+) (gold|silver|bronze) medals?");
    private static final Pattern J_BITS = Pattern.compile("\\+([\\d,]+) Bits");
    private static final Pattern J_OC = Pattern.compile("Overclocker 3000(?: x(\\d+))?");

    private enum Medal { GOLD("§6"), SILVER("§f"), BRONZE("§c");
        final String color;
        Medal(String color) { this.color = color; }
    }

    private static boolean jacobOpen, jacobBits;
    private static long jacobLast;
    private static int jacobTickets, carnivalTickets, overclockers, bits;
    private static final Map<String, Integer> books = new LinkedHashMap<>();
    private static final EnumMap<Medal, Integer> medals = new EnumMap<>(Medal.class);
    private static final List<Component> jacobLines = new ArrayList<>();

    private static boolean jacob(Component message, String t) {
        if (t.equals("FARMING CONTEST REWARDS CLAIMED")) {
            if (!SkyblockUtils.isInGarden()) return false;
            if (jacobOpen) jacobPublish();
            jacobOpen = true;
            jacobLast = System.currentTimeMillis();
            jacobLines.add(message);
            return true;
        }
        if (!jacobOpen) return false;
        if (J_CONTEST.matcher(t).matches() || t.equals("REWARDS")) return jacobBlock(message);
        Matcher m = J_TICKET.matcher(t);
        if (m.matches()) {
            int n = m.group(2) == null ? 1 : parseInt(m.group(2));
            if (m.group(1).equals("Jacob's")) jacobTickets += n; else carnivalTickets += n;
            return jacobBlock(message);
        }
        m = J_OC.matcher(t);
        if (m.matches()) {
            overclockers += m.group(1) == null ? 1 : parseInt(m.group(1));
            return jacobBlock(message);
        }
        m = J_BOOK.matcher(t);
        if (m.matches()) {
            books.merge(m.group(2), parseInt(m.group(1)), Integer::sum);
            return jacobBlock(message);
        }
        m = J_MEDAL.matcher(t);
        if (m.matches()) {
            medals.merge(Medal.valueOf(m.group(2).toUpperCase()), parseInt(m.group(1)), Integer::sum);
            return jacobBlock(message);
        }
        m = J_BITS.matcher(t);
        if (m.matches()) {
            bits += parseInt(m.group(1));
            jacobBits = true;
            return jacobBlock(message);
        }
        return false;
    }

    private static boolean jacobBlock(Component message) {
        jacobLines.add(message);
        jacobLast = System.currentTimeMillis();
        return true;
    }

    private static void jacobPublish() {
        List<String> parts = new ArrayList<>();
        parts.add("§6§lContest Rewards");
        if (jacobTickets > 0 || carnivalTickets > 0) {
            List<String> l = new ArrayList<>();
            if (jacobTickets > 0) l.add("§a" + jacobTickets + " Ja");
            if (carnivalTickets > 0) l.add("§a" + carnivalTickets + " Ca");
            parts.add("§rTickets: " + String.join("§7, ", l));
        }
        if (!books.isEmpty()) {
            List<String> l = new ArrayList<>();
            books.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(e -> l.add("§e" + e.getValue() + " " + e.getKey()));
            parts.add("§rBooks: " + String.join("§7, ", l));
        }
        if (!medals.isEmpty()) {
            List<String> l = new ArrayList<>();
            medals.forEach((k, v) -> l.add(k.color + v));
            parts.add("§rMedals: " + String.join("§7, ", l));
        }
        if (bits > 0) parts.add("§rBits: §b" + bits);
        if (overclockers > 0) parts.add("§rOverclocker" + (overclockers == 1 ? "" : "s") + ": §6" + overclockers);
        Component h = hover(jacobLines);
        emit(Component.literal(String.join(" §8§l| ", parts))
                .withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(h))));
        jacobOpen = jacobBits = false;
        jacobTickets = carnivalTickets = overclockers = bits = 0;
        books.clear();
        medals.clear();
        jacobLines.clear();
    }

    // ---- CHAT-025 level-up blocks ----

    private static boolean inSkill, inSkyBlock, inCollection;
    private static long levelUpStart;
    private static Component collectionLast;
    private static int newArea;

    private static boolean levelUp(Component message, String t, String trimmed) {
        if (isBorder(trimmed)) {
            TextColor col = firstColor(message);
            inSkill = inSkyBlock = inCollection = false;
            return cfg().compactLevelUpsHideBorder && col != null
                    && (col.equals(TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
                    || col.equals(TextColor.fromLegacyFormat(ChatFormatting.DARK_AQUA)));
        }
        long now = System.currentTimeMillis();
        if ((inSkill || inSkyBlock || inCollection) && now - levelUpStart > 3000) inSkill = inSkyBlock = inCollection = false;

        if (trimmed.startsWith("SKILL LEVEL UP ")) {
            inSkill = true;
            levelUpStart = now;
            return false;
        }
        if (trimmed.startsWith("SKYBLOCK LEVEL UP Level ")) {
            inSkyBlock = true;
            levelUpStart = now;
            return false;
        }
        if (trimmed.startsWith("COLLECTION LEVEL UP ")) {
            inCollection = true;
            levelUpStart = now;
            return false;
        }
        if (inSkill && !t.contains("Access to") && !t.endsWith(" Enchantment")) return true;
        if (inSkyBlock) {
            if (trimmed.equals("REWARDS")) return true;
            if (trimmed.startsWith("+") && (trimmed.endsWith(" Health") || trimmed.endsWith(" Strength"))) return true;
        }
        if (inCollection) {
            if (t.contains("Trade") || t.contains("Recipe")) {
                String bare = trimmed.replace(" ", "");
                if ((bare.equals("Trade") || bare.equals("Recipe")) && collectionLast != null) {
                    emit(collectionLast);
                    collectionLast = null;
                }
            } else {
                collectionLast = message;
                return true;
            }
        }
        return newArea(t, trimmed);
    }

    private static boolean newArea(String t, String trimmed) {
        if (trimmed.equals("NEW AREA DISCOVERED!")) {
            newArea = 1;
            return false;
        }
        if (t.isEmpty()) return false;
        if (newArea == 1) {
            newArea = 2;
            return false;
        }
        if (newArea == 2) {
            if (trimmed.startsWith("■") || t.startsWith("   ")) return true;
            newArea = 0;
        }
        return false;
    }

    private static boolean isBorder(String t) {
        if (t.length() < 20) return false;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) != '▬') return false;
        return true;
    }

    /** Colour of the first visible character, legacy § codes included. */
    private static TextColor firstColor(Component c) {
        return c.visit((style, text) -> {
            TextColor col = style.getColor();
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '§' && i + 1 < text.length()) {
                    ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                    if (f != null && f.isColor()) col = TextColor.fromLegacyFormat(f);
                    else if (f == ChatFormatting.RESET) col = style.getColor();
                    i++;
                } else if (!Character.isWhitespace(ch)) {
                    return Optional.ofNullable(col);
                }
            }
            return Optional.<TextColor>empty();
        }, Style.EMPTY).orElse(null);
    }

    // ---- CHAT-026 experiment rewards ----

    private static final Pattern EXP_CLAIM = Pattern.compile("You claimed the \\S+ rewards!");

    private static long expMenuSeen, expLast;
    private static Component expClaim;
    private static final List<Component> expRewards = new ArrayList<>();

    private static boolean experimentTitle(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("Experimentation Table")) return true;
        if (!(t.startsWith("Superpairs ") || t.startsWith("Chronomatron ") || t.startsWith("Ultrasequencer "))) return false;
        return t.endsWith(")") || t.endsWith("➜ Stakes") || t.endsWith(" Rewards");
    }

    private static boolean experiment(Component message, String t, String trimmed) {
        long now = System.currentTimeMillis();
        if (now - expMenuSeen >= 3000) return false;
        if (EXP_CLAIM.matcher(trimmed).matches()) {
            expClaim = message;
            expLast = now;
            return true;
        }
        if (t.startsWith(" +") && trimmed.length() > 1) {
            expRewards.add(message);
            expLast = now;
            return true;
        }
        return false;
    }

    private static void expPublish() {
        Component head = expClaim != null ? expClaim : Component.literal("§eExperiment rewards claimed!");
        Component h = hover(expRewards);
        emit(Component.empty().append(head).withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(h))));
        expRewards.clear();
        expClaim = null;
    }

    // ---- CHAT-027 shorten coins ----

    private static boolean hasDigitGroup(String t) {
        for (int i = 1; i + 1 < t.length(); i++) {
            if (t.charAt(i) == ',' && Character.isDigit(t.charAt(i - 1)) && Character.isDigit(t.charAt(i + 1))) return true;
        }
        return false;
    }

    private static Component shortenCoins(Component message) {
        MutableComponent out = Component.empty();
        boolean[] changed = {false};
        message.visit((style, text) -> {
            boolean styleGold = TextColor.fromLegacyFormat(ChatFormatting.GOLD).equals(style.getColor());
            boolean gold = styleGold, afterCode = false;
            StringBuilder sb = new StringBuilder(text.length());
            int n = text.length();
            for (int i = 0; i < n; i++) {
                char ch = text.charAt(i);
                if (ch == '§' && i + 1 < n) {
                    ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                    if (f != null && f.isColor()) gold = f == ChatFormatting.GOLD;
                    else if (f == ChatFormatting.RESET) gold = styleGold;
                    sb.append(ch).append(text.charAt(i + 1));
                    i++;
                    afterCode = true;
                    continue;
                }
                if (gold && Character.isDigit(ch) && (i == 0 || afterCode || !startsWordBefore(text.charAt(i - 1)))) {
                    afterCode = false;
                    int end = i;
                    while (end < n && (Character.isDigit(text.charAt(end)) || text.charAt(end) == ',' || text.charAt(end) == '.')) end++;
                    int numEnd = end;
                    while (numEnd > i && !Character.isDigit(text.charAt(numEnd - 1))) numEnd--;
                    String num = text.substring(i, numEnd);
                    boolean spaced = numEnd == end && (end == n || text.charAt(end) == ' ');
                    double v = spaced ? parseDouble(num) : -1;
                    if (v >= 1000) {
                        sb.append(EstimatedValue.shortFormat(v));
                        changed[0] = true;
                    } else {
                        sb.append(num);
                    }
                    i = numEnd - 1;
                    continue;
                }
                afterCode = false;
                sb.append(ch);
            }
            out.append(Component.literal(sb.toString()).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return changed[0] ? out : null;
    }

    private static boolean startsWordBefore(char c) {
        return Character.isLetterOrDigit(c) || c == ',' || c == '.';
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- CHAT-028 dark auction ----

    private static final Pattern DA_NEXT = Pattern.compile("\\[NPC] Sirius: (?:First|Next) up we have an? ([^,]+), the starting bid is [\\d,]+ Coins!");
    private static final Pattern DA_BID = Pattern.compile("\\[NPC] Sirius: (?:The highest bidder was \\w+ with a bid of [\\d,]+ Coins!|No one made any bids!.*)");
    private static String daItem;

    private static Component darkAuction(Component message, String t) {
        if (!t.startsWith("[NPC] Sirius: ")) return null;
        Matcher m = DA_NEXT.matcher(t);
        if (m.matches()) {
            daItem = m.group(1);
            return null;
        }
        if (daItem == null || !DA_BID.matcher(t).matches()) return null;
        Component out = message.copy().append(Component.literal(" (" + daItem + ")").withStyle(ChatFormatting.GRAY));
        daItem = null;
        return out;
    }

    // ---- CHAT-029 arachne ----

    private static final Pattern AR_VENOM = Pattern.compile("Arachne(?:'s (?:Keeper|Brood))? used Venom Shot on you hitting you for [\\d.,]+ damage and infecting you with venom\\.");
    private static final Pattern AR_CALLING = Pattern.compile("☄ .* placed an Arachne's Calling! Something is awakening!.*");
    private static final Pattern AR_CRYSTAL = Pattern.compile("☄ .* placed an Arachne Crystal! Something is awakening!");
    private static final Pattern AR_SPAWN = Pattern.compile("\\[BOSS] Arachne: (?:The Era of Spiders begins now\\.|Ahhhh\\.\\.\\.A Calling\\.\\.\\.)");
    private static boolean arachneHide;
    private static long arachneHideSince;

    private static boolean arachne(Component message, String t) {
        if (!SkyblockUtils.isInArea("Spider's Den")) return false;
        if (t.startsWith("Arachne") && AR_VENOM.matcher(t).matches()) return true;
        if (subArea().equals("Arachne's Sanctuary")) return false;
        if (t.startsWith("☄") && (AR_CALLING.matcher(t).matches() || AR_CRYSTAL.matcher(t).matches())) return true;
        if (t.startsWith("[BOSS] Arachne") && AR_SPAWN.matcher(t).matches()) return true;
        long now = System.currentTimeMillis();
        if (arachneHide && now - arachneHideSince > 5000) arachneHide = false;
        if (isBorder(t) && TextColor.fromLegacyFormat(ChatFormatting.GREEN).equals(firstColor(message))) {
            arachneHide = !arachneHide;
            arachneHideSince = now;
            return true;
        }
        if (t.equals("ARACHNE DOWN!")) {
            arachneHide = true;
            arachneHideSince = now;
        }
        return arachneHide;
    }

    private static String subArea() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String s = ScoreboardUtils.cleanLine(line);
            if (s.startsWith("⏣") || s.startsWith("ф")) return s.substring(1).trim();
        }
        return "";
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
