package com.teslamaps.features;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.AbstractSignEditScreenAccessor;
import com.teslamaps.screen.SearchOverlayScreen;
import com.teslamaps.profileviewer.data.PetRepo;
import com.teslamaps.utils.SbRepo;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

/** Replaces the auction house / bazaar search sign with a textbox that suggests item names. */
public class SearchOverlay {

    public enum Location { AUCTION, BAZAAR, RECIPE, MUSEUM, OTHER }

    private static String lastContainer = "";

    public static boolean maxPetLevel = false;
    public static int dungeonStars = 0;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            d.register(command("ahs", Location.AUCTION));
            d.register(command("ahsearch", Location.AUCTION));
            d.register(command("bzs", Location.BAZAAR));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String name, Location location) {
        return ClientCommands.literal(name)
                .executes(c -> openCommand(name, location, ""))
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .suggests((c, builder) -> {
                            if (!TeslaMapsConfig.get().searchOverlayCommands) return builder.buildFuture();
                            String typed = builder.getRemaining();
                            for (String s : suggestions(typed, location, 20)) {
                                if (!s.equalsIgnoreCase(typed)) builder.suggest(s);
                            }
                            return builder.buildFuture();
                        })
                        .executes(c -> openCommand(name, location, StringArgumentType.getString(c, "item"))));
    }

    private static int openCommand(String name, Location location, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (!TeslaMapsConfig.get().searchOverlayCommands) {
            String line = text.isEmpty() ? name : name + " " + text;
            if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChatCommandPacket(line));
            return 1;
        }
        mc.schedule(() -> mc.setScreen(new SearchOverlayScreen(null, null, location, text)));
        return 1;
    }

    /** Ctrl+F in the auction house / bazaar opens the search as a command. */
    public static boolean onContainerKey(AbstractContainerScreen<?> screen, int key) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (key != GLFW.GLFW_KEY_F || !c.searchOverlayCommands || !c.searchOverlayCtrlF) return false;
        Minecraft mc = Minecraft.getInstance();
        long w = mc.getWindow().handle();
        if (GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) != GLFW.GLFW_PRESS
                && GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) != GLFW.GLFW_PRESS) return false;
        String title = StringUtil.strip(screen.getTitle().getString());
        Location location = title.contains("Bazaar") ? Location.BAZAAR
                : title.contains("Auction") ? Location.AUCTION : null;
        if (location == null) return false;
        mc.setScreen(new SearchOverlayScreen(null, null, location, ""));
        return true;
    }

    /** "[Lvl 100] " / "] " for pets, stars for starable items. Auction searches only. */
    private static String addExtras(String search) {
        if (!TeslaMapsConfig.get().searchOverlayFilters || search.isEmpty()) return search;
        PetRepo.load();
        String type = search.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (PetRepo.isPetType(type)) {
            search = maxPetLevel ? "[Lvl " + PetRepo.maxLevel(type) + "] " + search : "] " + search;
        } else if (dungeonStars > 0) {
            String id = SkyblockItems.idForName(search.toLowerCase(Locale.ROOT));
            if (id != null && SbRepo.essenceItems().contains(id)) {
                StringBuilder stars = new StringBuilder(" ").append("✪".repeat(Math.min(dungeonStars, 5)));
                if (dungeonStars > 5) stars.append((char) ('➊' + Math.min(dungeonStars, 10) - 6));
                search += stars;
            }
        }
        return search;
    }

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
        if (title.contains("Museum") && TeslaMapsConfig.get().searchOverlayMuseum) return Location.MUSEUM;
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
        if (location == Location.MUSEUM) return museumSuggestions(needle, max);
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

    // Skyblocker's museum suggestions: donated items plus armor set names
    private static List<String> museumSuggestions(String needle, int max) {
        java.util.Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        var data = com.teslamaps.profileviewer.data.MuseumRepo.get();
        java.util.Set<String> donated = MuseumDonations.donatedIds();
        if (data != null) {
            for (List<String> ids : data.items().values()) {
                for (String id : ids) {
                    if (donated != null && !donated.contains(id)) continue;
                    List<String> pieces = data.setsToItems().get(id);
                    if (pieces != null) {
                        names.add(armorName(id, pieces));
                        continue;
                    }
                    String n = SkyblockItems.name(id);
                    if (n != null && !n.isEmpty()) names.add(StringUtil.stripTrim(n));
                }
            }
        }
        List<String> starts = new ArrayList<>();
        List<String> contains = new ArrayList<>();
        for (String n : names) {
            String lower = n.toLowerCase(Locale.ROOT);
            if (lower.startsWith(needle)) starts.add(n);
            else if (lower.contains(needle)) contains.add(n);
        }
        starts.addAll(contains);
        return starts.size() > max ? new ArrayList<>(starts.subList(0, max)) : starts;
    }

    private static String armorName(String id, List<String> pieces) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        boolean equipment = true;
        for (String p : pieces) {
            if (p.endsWith("_HELMET") || p.endsWith("_CHESTPLATE") || p.endsWith("_LEGGINGS") || p.endsWith("_BOOTS")) {
                equipment = false;
                break;
            }
        }
        String lower = id.toLowerCase(Locale.ROOT);
        if (equipment) sb.append("Equipment");
        else if (!lower.contains("armor") && !lower.contains("outfit") && !lower.contains("suit") && !lower.contains("tuxedo")) sb.append("Armor");
        return sb.toString().trim();
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
            case MUSEUM -> config.museumSearchHistory;
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
        while (list.size() > Math.max(1, config.searchOverlayHistorySize)) list.removeLast();
        TeslaMapsConfig.save();
    }

    /** Writes the search back onto the sign, which is what actually runs it. */
    public static void push(SignBlockEntity sign, String[] originalText, Location location, String search) {
        String text = search.trim();
        addHistory(location, text);
        if (location == Location.AUCTION) text = addExtras(text);

        // "null ovoid" and friends break the server side search unless quoted
        if (sign != null && text.toLowerCase(Locale.ROOT).startsWith("null")) text = "\"" + text + "\"";

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
        // opened from the /craft button or a command, no sign behind it
        if (sign == null) {
            String query = location == Location.AUCTION ? text : search.trim();
            mc.execute(() -> {
                if (query.isEmpty()) {
                    if (mc.player != null) mc.player.closeContainer();
                } else if (mc.getConnection() != null) {
                    String cmd = switch (location) {
                        case AUCTION -> "auctionsearch ";
                        case BAZAAR -> "bz ";
                        default -> "recipe ";
                    };
                    mc.getConnection().send(new ServerboundChatCommandPacket(cmd + query));
                }
            });
            return;
        }
        mc.getConnection().send(new ServerboundSignUpdatePacket(
                sign.getBlockPos(), true, first, second, originalText[2], originalText[3]));
    }
}
