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
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ChatCommands {

    private static final String[] EIGHT_BALL = {
            "It is certain", "It is decidedly so", "Without a doubt", "Yes definitely",
            "You may rely on it", "As I see it, yes", "Most likely", "Outlook good", "Yes",
            "Signs point to yes", "Reply hazy try again", "Ask again later", "Better not tell you now",
            "Cannot predict now", "Concentrate and ask again", "Don't count on it", "My reply is no",
            "My sources say no", "Outlook not so good", "Very doubtful"
    };

    private static final java.util.Set<String> downtimePlayers = new java.util.LinkedHashSet<>();

    public static void onChatMessage(String msg) {
        if (!TeslaMapsConfig.get().chatCommands) return;
        msg = msg.replaceAll("(?i)§[0-9A-FK-OR]", ""); // Hypixel sends chat with legacy § codes

        if (msg.contains("> EXTRA STATS <") && !downtimePlayers.isEmpty()) {
            for (String p : downtimePlayers) pc("Downtime: " + p);
            downtimePlayers.clear();
            com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(false);
        }

        if (!msg.startsWith("Party >")) return;
        int colon = msg.indexOf(": ");
        if (colon < 0) return;

        String header = msg.substring(0, colon);          // "Party > [Rank] Name"
        String content = msg.substring(colon + 2).trim();
        if (!content.startsWith("!")) return;

        String[] hw = header.trim().split("\\s+");
        String sender = hw[hw.length - 1];                 // last token = player name

        handle(content.substring(1), sender);
    }

    private static void handle(String command, String sender) {
        String[] w = command.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return;
        String c = w[0].toLowerCase();
        String arg = w.length > 1 ? w[1] : null;
        Minecraft mc = Minecraft.getInstance();

        switch (c) {
            case "8ball" -> pc(EIGHT_BALL[(int) (Math.random() * EIGHT_BALL.length)]);
            case "cf", "coinflip" -> pc(Math.random() < 0.5 ? "heads" : "tails");
            case "dice" -> pc(String.valueOf(1 + (int) (Math.random() * 6)));
            case "coords", "co" -> pc(coords());
            case "ping" -> PingMeter.requestToParty();
            case "fps" -> pc("FPS: " + mc.getFps());
            case "time" -> pc("Time: " + ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            case "warp", "w" -> run("party warp");
            case "allinvite", "allinv" -> run("party settings allinvite");
            case "pt", "transfer" -> run("party transfer " + (arg != null ? arg : sender));
            case "kick", "k" -> run("party kick " + (arg != null ? arg : sender));
            case "promote" -> run("party promote " + (arg != null ? arg : sender));
            case "demote" -> run("party demote " + (arg != null ? arg : sender));
            case "dt", "downtime" -> {
                if (downtimePlayers.contains(sender)) { pc(sender + " already has a downtime reminder!"); break; }
                downtimePlayers.add(sender);
                com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(true);
                pc("Reminder set for end of run (" + sender + ") - auto-requeue paused");
            }
            case "undt", "undowntime" -> {
                if (!downtimePlayers.remove(sender)) { pc(sender + " has no downtime reminder set!"); break; }
                if (downtimePlayers.isEmpty()) com.teslamaps.dungeon.AutoRequeue.setDowntimeHold(false);
                pc("Downtime reminder removed (" + sender + ")");
            }
            case "help", "h" -> pc("Commands: 8ball cf dice coords ping fps time warp allinvite pt kick promote demote dt undt f1-f7 m1-m7");
            default -> {
                String instance = floorInstance(c);
                if (instance != null) run("joininstance " + instance);
            }
        }
    }

    public static boolean queueFloor(String code) {
        String instance = floorInstance(code);
        if (instance == null) return false;
        run("joininstance " + instance);
        return true;
    }

    public static String floorInstance(String c) {
        if (c.length() != 2) return null;
        char type = c.charAt(0);
        char num = c.charAt(1);
        if ((type != 'f' && type != 'm') || num < '1' || num > '7') return null;
        String[] names = {"one", "two", "three", "four", "five", "six", "seven"};
        return (type == 'm' ? "master_" : "") + "catacombs_floor_" + names[num - '1'];
    }

    private static String coords() {
        BlockPos p = Minecraft.getInstance().player.blockPosition();
        return "x: " + p.getX() + ", y: " + p.getY() + ", z: " + p.getZ();
    }

    private static void pc(String message) {
        run("pc " + message);
    }

    private static void run(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.execute(() -> mc.getConnection().sendCommand(command));
    }
}
