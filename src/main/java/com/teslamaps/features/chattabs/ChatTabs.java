package com.teslamaps.features.chattabs;

// Ported from Skysoft's ChatTabs.kt / ChatTabMessageRouter.kt / ChatMessageClassifier.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Tab bar on the chat screen. One message list, a vanilla visible-message filter
 * decides per tab; everything is still stored so switching tabs is lossless.
 */
public class ChatTabs {

    public enum Channel {
        ALL("All"), GUILD("Guild"), DM("DM"), PARTY("Party");

        public final String label;

        Channel(String label) {
            this.label = label;
        }
    }

    public static final int TAB_HEIGHT = 14;
    public static final int TAB_GAP = 2;
    public static final int TAB_PADDING = 12;
    public static final int MIN_TAB_WIDTH = 36;
    private static final int CHAT_LEFT = 4;
    private static final int CHAT_BOTTOM_MARGIN = 40;

    private static Channel selected = Channel.ALL;
    private static boolean applied = false;
    private static Channel appliedChannel = null;
    private static boolean appliedHideSeparators = false;

    public static final String ORDER_ID = "chatTabs";

    static {
        com.teslamaps.utils.LineOrder.define(ORDER_ID,
                new String[]{"ALL", "GUILD", "DM", "PARTY"},
                new String[]{"All", "Guild", "DM", "Party"});
    }

    private static boolean hideSeparators() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chatFilterSeparators && c.chatFilterEnabled;
    }

    /** Shown tabs in the user's order; all four when every tab is switched off. */
    public static List<Channel> channels() {
        List<Channel> out = new ArrayList<>(4);
        for (String k : com.teslamaps.utils.LineOrder.resolved(ORDER_ID)) {
            if (k.startsWith("-")) continue;
            try {
                out.add(Channel.valueOf(k));
            } catch (IllegalArgumentException ignored) {}
        }
        if (out.isEmpty()) out.addAll(List.of(Channel.values()));
        return out;
    }

    private static final ChatTabCommandResponseTracker commandTracker = new ChatTabCommandResponseTracker();
    private static final ChatTabFeedbackTracker feedbackTracker = new ChatTabFeedbackTracker();

    // ---- chat line classification (full-line matches on stripped text) ----

    private static final Pattern PARTY_CHAT = Pattern.compile("^Party > (?<sender>.+?): (?<body>.*)$");
    private static final Pattern GUILD_CHAT = Pattern.compile("^(?:Guild|Officer) > (?<sender>.+?): (?<body>.*)$");
    private static final Pattern PRIVATE_CHAT = Pattern.compile("^(?:From|To) (?<sender>.+?): (?<body>.*)$");

    private static final Pattern PARTY_SYSTEM = Pattern.compile(
            "^(?:Party invite from .+|.+ has invited you to join their party!|.+ invited .+ to the party!(?: They have 60 seconds to accept\\.)?|You have 60 seconds to accept\\. Click here to join!|You have joined .+'s party!|You'll be partying with: .+|.+ joined the party\\.|.+ has disconnected, they have \\d+ minutes to rejoin before (?:they are removed from the party|the party is disbanded)\\.|.+ has rejoined\\.|You left the party\\.|.+ has left the party\\.|You have been (?:removed|kicked) from the party.*|.+ has been removed from the party\\.|Kicked .+ because they were offline\\.|.+ was removed from your party because they disconnected\\.|.+ has disbanded the party!|The party was disbanded.*|The party was transferred to .+|.+ was (?:promoted to Party Moderator|demoted to Party Member).*|.+ has (?:promoted .+ to Party Moderator|demoted .+ to Party Member).*|Party chat is (?:now )?(?:muted|unmuted).*|All [Ii]nvite is now (?:enabled|disabled).*|You are not (?:currently )?in a party(?: right now)?\\.|Party Members \\(\\d+\\)|Party (?:Leader|Moderators|Members):.*)$");

    private static final Pattern GUILD_SYSTEM = Pattern.compile(
            "^(?:Guild > .+ (?:joined|left)\\.|\\[Guild] .+|Guild invite from .+|.+ has invited you to join their guild!|You have been invited to join .+ guild!|You left the guild!|.+ (?:joined|left) the guild\\.|.+ (?:was|has been) kicked from the guild\\.|The guild was disbanded.*|Guild chat is (?:now )?(?:muted|unmuted).*|Guild (?:Name|Master|Members|Level|Created|MOTD):.*)$");

    private static final Pattern DM_SYSTEM = Pattern.compile(
            "^(?:Friend request from .+|You sent a friend request to .+!|The friend request to .+ has expired\\.|You are now friends with .+|.+ is now your friend!|You removed .+ from your friends list!|.+ removed you from their friends list!|.+ is (?:now|no longer) a best friend!|Friend > .+ (?:joined|left)\\.|You have no friends(?: online)?\\.|You don't have any friends.*|You cannot message (?:this player|yourself)[.!]|You don't have anyone to reply to!|This player has private messages disabled\\.|You must wait .+ before messaging this player again\\.|Friends(?: List)?(?: .*)?)$");

    public static boolean enabled() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return c.chatTabs && c.section("Chat");
    }

    public static boolean isFilterApplied() {
        return applied;
    }

    public static Channel selected() {
        return selected;
    }

    public static void select(Channel channel) {
        selected = channel;
        if (channel != Channel.PARTY) feedbackTracker.clearPendingResponse();
        updateFilter(true);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null && applied) clearPendingResponses();
        if (enabled() || applied) updateFilter(false);
    }

    private static void updateFilter(boolean forced) {
        boolean nowEnabled = enabled();
        boolean hideSep = hideSeparators();
        if (!forced && nowEnabled == applied
                && (!nowEnabled || (selected == appliedChannel && hideSep == appliedHideSeparators))) return;
        appliedHideSeparators = hideSep;

        Minecraft mc = Minecraft.getInstance();
        ChatComponent chat = mc.gui.getChat();
        if (nowEnabled) {
            Channel channel = selected;
            chat.setVisibleMessageFilter(message -> isVisible(channel, message));
            chat.resetChatScroll();
        } else if (applied) {
            clearPendingResponses();
            chat.setVisibleMessageFilter(message -> true);
            chat.resetChatScroll();
        }
        applied = nowEnabled;
        appliedChannel = selected;
    }

    public static void clearPendingResponses() {
        commandTracker.clear();
        feedbackTracker.clear();
    }

    static boolean isVisible(Channel channel, GuiMessage message) {
        String text = StringUtil.strip(message.content().getString()).trim();
        commandTracker.observe(message, text);
        boolean hideSep = appliedHideSeparators && com.teslamaps.features.ChatFilter.isSeparator(text);
        if (channel == Channel.ALL) return !hideSep;
        if (message.source() == GuiMessageSource.SYSTEM_CLIENT && text.startsWith("[TeslaMaps]")) return true;
        if (feedbackTracker.isVisible(channel, message, text)) return !hideSep;
        if (commandTracker.isVisible(channel, message)) return !hideSep;
        if (hideSep) return false;
        return switch (channel) {
            case GUILD -> GUILD_CHAT.matcher(text).matches() || GUILD_SYSTEM.matcher(text).matches();
            case DM -> (PRIVATE_CHAT.matcher(text).matches() && !text.regionMatches(true, 0, "From stash:", 0, 11))
                    || DM_SYSTEM.matcher(text).matches();
            case PARTY -> PARTY_CHAT.matcher(text).matches() || PARTY_SYSTEM.matcher(text).matches();
            case ALL -> true;
        };
    }

    // ---- outgoing routing ----

    /** Null = send as-is; otherwise the command (no leading slash) to send instead. */
    public static String prepareOutgoingCommand(String message) {
        if (!enabled()) return null;
        String prefix = switch (selected) {
            case ALL -> null;
            case GUILD -> "gc";
            case DM -> "r";
            case PARTY -> "pc";
        };
        if (prefix == null) return null;
        if (selected == Channel.PARTY) {
            feedbackTracker.recordOutgoingAttempt(allMessages());
        }
        return prefix + " " + message;
    }

    public static void recordOutgoingCommand(String command) {
        if (!enabled()) return;
        commandTracker.record(command, selected, allMessages());
    }

    private static List<GuiMessage> allMessages() {
        return ((ChatComponentAccessor) Minecraft.getInstance().gui.getChat()).teslamaps$getAllMessages();
    }

    // ---- layout ----

    public record TabBounds(Channel channel, int x, int y, int width, int height) {}

    public static List<TabBounds> layout(Minecraft mc) {
        var font = mc.font;
        int guiHeight = mc.getWindow().getGuiScaledHeight();
        int chatWidth = ChatComponent.getWidth(mc.options.chatWidth().get());
        int chatHeight = ChatComponent.getHeight(mc.options.chatHeightFocused().get());
        int chatBottom = guiHeight - CHAT_BOTTOM_MARGIN;
        int position = TeslaMapsConfig.get().chatTabPosition;

        List<TabBounds> out = new ArrayList<>();
        List<Channel> channels = channels();
        if (!channels.contains(selected)) select(channels.get(0));

        if (position == 2) { // right of chat
            int x = CHAT_LEFT + chatWidth + TAB_GAP;
            int total = channels.size() * TAB_HEIGHT + (channels.size() - 1) * TAB_GAP;
            int y = Math.max(chatBottom - Math.max(chatHeight, total), 0);
            for (Channel channel : channels) {
                int w = Math.max(font.width(channel.label) + TAB_PADDING, MIN_TAB_WIDTH);
                out.add(new TabBounds(channel, x, y, w, TAB_HEIGHT));
                y += TAB_HEIGHT + TAB_GAP;
            }
        } else {
            int y = position == 1
                    ? chatBottom + TAB_GAP // under chat
                    : Math.max(chatBottom - chatHeight - TAB_HEIGHT - TAB_GAP, 0); // above chat
            int x = CHAT_LEFT;
            for (Channel channel : channels) {
                int w = Math.max(font.width(channel.label) + TAB_PADDING, MIN_TAB_WIDTH);
                out.add(new TabBounds(channel, x, y, w, TAB_HEIGHT));
                x += w + TAB_GAP;
            }
        }
        return out;
    }
}
