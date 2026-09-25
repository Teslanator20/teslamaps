package com.teslamaps.command;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SkyblockUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lists behind the tab completion for Hypixel's own commands. Hypixel sends its
 * command tree without any suggestions; HypixelNodes rebuilds the interesting nodes with
 * these lists while the tree is being read.
 */
public class HypixelSuggestions {

    private static final List<String> WARPS = load("warps.txt");
    private static final List<String> CONTACTS = load("contacts.txt");

    private static List<String> load(String file) {
        List<String> out = new ArrayList<>();
        try (InputStream in = HypixelSuggestions.class.getResourceAsStream(
                "/assets/teslamaps/suggestions/" + file)) {
            if (in == null) return out;
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) out.add(trimmed);
            }
        } catch (IOException e) {
            TeslaMaps.LOGGER.warn("Could not read suggestion list {}", file, e);
        }
        return out;
    }

    public static List<String> warps() {
        if (ExtraSuggestions.enabled()) {
            List<String> online = ExtraSuggestions.warps();
            if (online != null) return online;
        }
        return WARPS;
    }

    /** Abiphone contacts for /call. */
    public static List<String> contacts() {
        return CONTACTS;
    }

    /** Guild, friends, recently seen players and the tab list. */
    public static List<String> players() {
        return PlayerNameCache.names();
    }

    /** Off-island the lists would only be noise on servers with their own /warp. */
    public static boolean enabled() {
        return TeslaMapsConfig.get().commandSuggestions
                && (SkyblockUtils.isOnHypixel()
                    || net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment());
    }

    /**
     * Once the typed word IS one of the options this returns nothing. While the popup is
     * open Enter applies the highlighted entry instead of sending the command, so a name
     * that suggests itself ("/warp canyon") could never be submitted.
     */
    public static List<String> filter(List<String> options, String written) {
        String w = written.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.equalsIgnoreCase(w)) return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(w)) out.add(option);
        }
        return out;
    }
}
