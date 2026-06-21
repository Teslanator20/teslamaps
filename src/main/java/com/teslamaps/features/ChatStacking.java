/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.ChatComponentAccessor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatStacking {

    private static final Pattern COUNTER = Pattern.compile("^(.*?)\\s\\((\\d+)x\\)$", Pattern.DOTALL);

    public static void afterAdd(ChatComponent chat) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.chatStacking) return;

        ChatComponentAccessor acc = (ChatComponentAccessor) chat;
        List<GuiMessage> all = acc.teslamaps$getAllMessages();
        if (all.size() < 2) return;

        GuiMessage newest = all.get(0);
        String newBase = base(strip(newest.content().getString()));
        if (shouldNotStack(newBase)) return;

        int windowTicks = Math.max(1, c.chatStackWindowMinutes) * 60 * 20;

        int matchIdx = -1;
        for (int i = 1; i < all.size(); i++) {
            GuiMessage prev = all.get(i);
            if (newest.addedTime() - prev.addedTime() > windowTicks) break; // past the window -> stop
            if (base(strip(prev.content().getString())).equals(newBase)) { matchIdx = i; break; }
        }
        if (matchIdx < 0) return;

        int count = parseCount(strip(all.get(matchIdx).content().getString())) + 1;

        Style counterStyle = Style.EMPTY.withColor(0xAAAAAA)
                .withStrikethrough(false).withUnderlined(false).withBold(false)
                .withItalic(false).withObfuscated(false);
        MutableComponent stacked = newest.content().copy()
                .append(Component.literal(" (" + count + "x)").withStyle(counterStyle));

        GuiMessage replacement = new GuiMessage(newest.addedTime(), stacked,
                newest.signature(), newest.source(), newest.tag());

        all.set(0, replacement);
        all.remove(matchIdx); // drop the older occurrence; the stack moves to the bottom
        acc.teslamaps$refreshTrimmedMessages();
    }

    private static boolean shouldNotStack(String plain) {
        String t = plain.trim();
        if (t.isEmpty()) return true;                                          // empty / whitespace-only
        if (t.chars().noneMatch(Character::isLetterOrDigit)) return true;       // separators ("------", etc.)
        if (t.contains("ⓐ") || t.contains("ⓑ") || t.contains("ⓒ")) return true; // quiz answer options
        if (com.teslamaps.dungeon.puzzle.QuizSolver.isActive()) return true;    // quiz question lines
        return false;
    }

    private static String strip(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private static String base(String plain) {
        Matcher m = COUNTER.matcher(plain);
        return m.matches() ? m.group(1) : plain;
    }

    private static int parseCount(String plain) {
        Matcher m = COUNTER.matcher(plain);
        if (m.matches()) {
            try { return Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }
}
