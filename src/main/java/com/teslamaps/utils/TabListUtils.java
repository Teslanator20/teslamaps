package com.teslamaps.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for parsing the tab list (player list) in dungeons.
 */
public class TabListUtils {
    // Pattern to match "Secrets Found: X" in tab
    private static final Pattern SECRETS_FOUND_PATTERN = Pattern.compile("Secrets Found:\\s*(\\d+)");
    // Pattern to match "Crypts: X" in tab
    private static final Pattern CRYPTS_PATTERN = Pattern.compile("Crypts:\\s*(\\d+)");

    /**
     * Get all lines from the tab list.
     */
    public static List<String> getTabListLines() {
        List<String> lines = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.getNetworkHandler() == null) return lines;

        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getDisplayName() != null) {
                lines.add(entry.getDisplayName().getString());
            }
        }

        return lines;
    }

    /**
     * Get the number of secrets found from tab list.
     * @return secrets found, or -1 if not found
     */
    public static int getSecretsFound() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = SECRETS_FOUND_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /**
     * Get the number of crypts found from tab list.
     * @return crypts found, or -1 if not found
     */
    public static int getCryptsFound() {
        for (String line : getTabListLines()) {
            String clean = line.replaceAll("§.", "");
            Matcher matcher = CRYPTS_PATTERN.matcher(clean);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }
}
