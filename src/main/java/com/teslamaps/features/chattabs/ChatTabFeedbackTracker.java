package com.teslamaps.features.chattabs;

// Ported from Skysoft's ChatTabFeedbackTracker.kt
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Typing into the Party tab while not in a party gets a 3-line error block
 * (separator / "You are not in a party right now." / separator) that would be
 * invisible in the very tab it was typed in. Show exactly those lines.
 */
class ChatTabFeedbackTracker {

    private static final String SEPARATOR = "-----------------------------------------------------";
    private static final String NOT_IN_PARTY = "You are not in a party right now.";

    private enum State { NONE, LEADING_SEPARATOR, ERROR, TRAILING_SEPARATOR }

    private State state = State.NONE;
    private final Set<GuiMessage> visibleFeedback = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<GuiMessage> beforeAttempt = Collections.newSetFromMap(new IdentityHashMap<>());

    void recordOutgoingAttempt(List<GuiMessage> existing) {
        visibleFeedback.removeIf(m -> !existing.contains(m));
        state = State.LEADING_SEPARATOR;
        beforeAttempt.clear();
        beforeAttempt.addAll(existing);
    }

    boolean isVisible(ChatTabs.Channel channel, GuiMessage message, String text) {
        if (channel != ChatTabs.Channel.PARTY) {
            clearPendingResponse();
            return false;
        }
        if (visibleFeedback.contains(message)) return true;
        if (state == State.NONE || beforeAttempt.contains(message)) return false;

        switch (state) {
            case LEADING_SEPARATOR -> {
                if (!text.equals(SEPARATOR)) {
                    clearPendingResponse();
                    return false;
                }
                visibleFeedback.add(message);
                state = State.ERROR;
                return true;
            }
            case ERROR -> {
                if (!text.equals(NOT_IN_PARTY)) {
                    clearPendingResponse();
                    return false;
                }
                visibleFeedback.add(message);
                state = State.TRAILING_SEPARATOR;
                return true;
            }
            case TRAILING_SEPARATOR -> {
                boolean separator = text.equals(SEPARATOR);
                if (separator) visibleFeedback.add(message);
                clearPendingResponse();
                return separator;
            }
            default -> {
                return false;
            }
        }
    }

    void clearPendingResponse() {
        state = State.NONE;
        beforeAttempt.clear();
    }

    void clear() {
        clearPendingResponse();
        visibleFeedback.clear();
    }
}
