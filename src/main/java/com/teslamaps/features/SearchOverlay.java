package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.screen.SearchOverlayScreen;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Replaces the auction house / bazaar search sign with a textbox that suggests item names. */
public class SearchOverlay {

    public enum Location { AUCTION, BAZAAR, RECIPE, OTHER }

    private static final int HISTORY_LENGTH = 12;

    private static String lastContainer = "";

    public static void init() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (screen instanceof AbstractContainerScreen<?> cs) {
                lastContainer = StringUtil.strip(cs.getTitle().getString());
                if (lastContainer.trim().equals("Craft Item") && TeslaMapsConfig.get().searchOverlay
                        && TeslaMapsConfig.get().searchOverlayRecipes) {
                    var acc = (com.teslamaps.mixin.HandledScreenAccessor) cs;
                    net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(new com.teslamaps.screen.TeslaButton(
                            acc.getX() + acc.getImageWidth() - 80, acc.getY() - 20, 80, 16, "§fRecipe Search",
                            () -> mc.setScreen(new SearchOverlayScreen(null, null, Location.RECIPE,
                                    TeslaMapsConfig.get().searchOverlayKeepText && !history(Location.RECIPE).isEmpty()
                                            ? history(Location.RECIPE).getFirst() : ""))));
                }
                return;
            }
            if (!TeslaMapsConfig.get().searchOverlay) return;
            if (!(screen instanceof AbstractSignEditScreen)) return;

            SignBlockEntity sign = ((AbstractSignEditScreenAccessor) screen).getSign();
            if (sign == null) return;

            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) lines[i] = sign.getFrontText().getMessage(i, false).getString();
            if (!isQuerySign(lines)) return;

            Location location = locationOf(lastContainer);
            if (location == null) return;

            String keep = TeslaMapsConfig.get().searchOverlayKeepText ? join(lines[0], lines[1]) : "";
            String recent = TeslaMapsConfig.get().searchPasteCopied && keep.isBlank()
                    ? CopyItemName.recent() : null;
            String start = recent != null ? recent : keep;

            // set the screen by hand so the sign gui does not close
            mc.execute(() -> {
                SearchOverlayScreen next = new SearchOverlayScreen(sign, lines, location, start);
                mc.screen = next;
                next.init(width, height);
            });
        });
    }

    private static boolean isQuerySign(String[] lines) {
        for (int i = 1; i < 4; i++) {
            if (lines[i].trim().equalsIgnoreCase("enter query")) return true;
        }
        return false;
    }

    private static Location locationOf(String title) {
        if (title.contains("Auction") || title.contains("Cosmetics")) return Location.AUCTION;
        if (title.contains("Bazaar")) return Location.BAZAAR;
        if (title.contains("Recipe") && TeslaMapsConfig.get().searchOverlayRecipes) return Location.RECIPE;
        return TeslaMapsConfig.get().searchOverlayAllSigns ? Location.OTHER : null;
    }

    private static String join(String first, String second) {
        String text = first.trim();
        if (!second.isBlank()) text = text.isEmpty() ? second.trim() : text + " " + second.trim();
        return text;
    }

    /** Item names matching the typed text, exact prefix first. */
    public static List<String> suggestions(String query, Location location, int max) {
        List<String> out = new ArrayList<>();
        if (max <= 0 || query.isBlank()) return out;

        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();

        // without prices loaded there is nothing to filter against, so list everything
        boolean bazaarOnly = false;
        if (location == Location.BAZAAR) {
            BazaarPrices.refreshIfStale();
            bazaarOnly = BazaarPrices.loaded();
        }

        for (String name : SkyblockItems.allNames()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!lower.contains(needle)) continue;
            if (bazaarOnly && !BazaarPrices.onBazaar(SkyblockItems.idForName(lower))) continue;
            if (lower.startsWith(needle)) starts.add(name);
            else contains.add(name);
            if (starts.size() >= max) break;
        }

        starts.sort(String.CASE_INSENSITIVE_ORDER);
        contains.sort(String.CASE_INSENSITIVE_ORDER);
        out.addAll(starts);
        for (String name : contains) {
            if (out.size() >= max) break;
            out.add(name);
        }
        return out.size() > max ? out.subList(0, max) : out;
    }

    public static List<String> history(Location location) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.searchOverlayHistory) return List.of();
        return list(config, location);
    }

    private static List<String> list(TeslaMapsConfig config, Location location) {
        return switch (location) {
            case AUCTION -> config.auctionSearchHistory;
            case BAZAAR -> config.bazaarSearchHistory;
            case RECIPE -> config.recipeSearchHistory;
            case OTHER -> config.otherSearchHistory;
        };
    }

    public static void removeHistory(Location location, String search) {
        history(location).remove(search);
        TeslaMapsConfig.save();
    }

    private static void addHistory(Location location, String search) {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        if (!config.searchOverlayHistory || search.isBlank()) return;
        List<String> list = list(config, location);
        list.remove(search);
        list.addFirst(search);
        while (list.size() > HISTORY_LENGTH) list.removeLast();
        TeslaMapsConfig.save();
    }

    /** Writes the search back onto the sign, which is what actually runs it. */
    public static void push(SignBlockEntity sign, String[] originalText, Location location, String search) {
        String text = search.trim();
        addHistory(location, text);

        // "null ovoid" and friends break the server side search unless quoted
        if (text.toLowerCase(Locale.ROOT).startsWith("null")) text = "\"" + text + "\"";

        String first = text;
        String second = "";
        if (text.length() > 15) {
            int cut = text.lastIndexOf(' ', 15);
            if (cut == -1) {
                first = text.substring(0, 15);
            } else {
                first = text.substring(0, cut);
                second = text.substring(cut + 1, Math.min(cut + 16, text.length())).trim();
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        // opened from the /craft button, no sign behind it
        if (sign == null) {
            String query = search.trim();
            mc.execute(() -> {
                if (query.isEmpty()) {
                    if (mc.player != null) mc.player.closeContainer();
                } else if (mc.getConnection() != null) {
                    mc.getConnection().sendCommand("recipe " + query);
                }
            });
            return;
        }
        mc.getConnection().send(new ServerboundSignUpdatePacket(
                sign.getBlockPos(), true, first, second, originalText[2], originalText[3]));
    }
}
