package com.teslamaps.features.chattabs;

// Ported from Skysoft's ChatTabCommandResponseTracker.kt
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pins the multi-line response of /p list, /g list, /f list to the matching tab
 * (and the tab it was typed from), since those lines match no routing regex.
 * Identity sets on purpose - GuiMessage is a record, duplicate lines would collide.
 */
class ChatTabCommandResponseTracker {

    private static final String SEPARATOR = "-----------------------------------------------------";
    private static final long TIMEOUT_MS = 3000;
    private static final int MAX_LINES = 100;

    private record Response(ChatTabs.Channel channel, Pattern header, Pattern complete) {}

    private static final Response FRIEND = new Response(ChatTabs.Channel.DM,
            Pattern.compile("^Friends(?: List)?(?: .*)?$"),
            Pattern.compile("^(?:You have no friends(?: online)?\\.|You don't have any friends.*)$"));
    private static final Response PARTY = new Response(ChatTabs.Channel.PARTY,
            Pattern.compile("^Party (?:List|Leader|Moderators|Members)(?::| .*)?.*$"),
            Pattern.compile("^You are not (?:currently )?in a party(?: right now)?\\.$"));
    private static final Response GUILD = new Response(ChatTabs.Channel.GUILD,
            Pattern.compile("^Guild (?:List|Name|Master|Members|Online Members|Information)(?::| .*)?.*$"),
            Pattern.compile("^You are not (?:currently )?in a guild\\.$"));

    private enum Phase { LEADING, HEADER, CONTENT }

    private static final class Pending {
        final Response response;
        final ChatTabs.Channel origin;
        Phase phase = Phase.LEADING;
        long expiresAt;
        int lineCount = 0;

        Pending(Response response, ChatTabs.Channel origin, long expiresAt) {
            this.response = response;
            this.origin = origin;
            this.expiresAt = expiresAt;
        }
    }

    private final Map<ChatTabs.Channel, Set<GuiMessage>> routed = new EnumMap<>(ChatTabs.Channel.class);
    private final Set<GuiMessage> beforeCommand = identitySet();
    private Pending pending = null;

    ChatTabCommandResponseTracker() {
        for (ChatTabs.Channel channel : ChatTabs.Channel.values()) routed.put(channel, identitySet());
    }

    private static Set<GuiMessage> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    void record(String command, ChatTabs.Channel origin, List<GuiMessage> existing) {
        Response response = responseFor(command);
        pending = null;
        if (response == null) return;
        beforeCommand.addAll(existing);
        for (Set<GuiMessage> set : routed.values()) set.removeIf(m -> !beforeCommand.contains(m));
        pending = new Pending(response, origin, System.currentTimeMillis() + TIMEOUT_MS);
    }

    private static Response responseFor(String command) {
        String[] words = command.toLowerCase().trim().split("\\s+");
        if (words.length < 2 || !words[1].equals("list")) return null;
        return switch (words[0]) {
            case "f", "friend", "friends" -> FRIEND;
            case "p", "party" -> PARTY;
            case "g", "guild" -> GUILD;
            default -> null;
        };
    }

    void observe(GuiMessage message, String text) {
        if (pending == null) return;
        if (beforeCommand.contains(message)) return;
        for (Set<GuiMessage> set : routed.values()) {
            if (set.contains(message)) return;
        }
        if (System.currentTimeMillis() > pending.expiresAt) {
            pending = null;
            return;
        }

        Response r = pending.response;
        switch (pending.phase) {
            case LEADING -> {
                if (text.equals(SEPARATOR)) {
                    route(message);
                    pending.phase = Phase.HEADER;
                } else if (r.header.matcher(text).matches()) {
                    route(message);
                    pending.phase = Phase.CONTENT;
                } else if (r.complete.matcher(text).matches()) {
                    route(message);
                    pending = null;
                }
                // anything else: stay - unrelated chatter can interleave before the response
            }
            case HEADER -> {
                if (text.equals(SEPARATOR)) {
                    route(message);
                } else if (r.header.matcher(text).matches()) {
                    route(message);
                    pending.phase = Phase.CONTENT;
                } else if (r.complete.matcher(text).matches()) {
                    route(message);
                    pending = null;
                } else {
                    pending = null;
                }
            }
            case CONTENT -> {
                route(message);
                pending.lineCount++;
                if (text.equals(SEPARATOR) || pending.lineCount >= MAX_LINES) {
                    pending = null;
                } else {
                    pending.expiresAt = System.currentTimeMillis() + TIMEOUT_MS;
                }
            }
        }
    }

    private void route(GuiMessage message) {
        routed.get(pending.response.channel).add(message);
        routed.get(pending.origin).add(message);
    }

    boolean isVisible(ChatTabs.Channel channel, GuiMessage message) {
        return routed.get(channel).contains(message);
    }

    void clear() {
        pending = null;
        beforeCommand.clear();
        for (Set<GuiMessage> set : routed.values()) set.clear();
    }
}
