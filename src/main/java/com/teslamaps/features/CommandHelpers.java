package com.teslamaps.features;

// /warp is + /viewrecipe fix from SkyHanni's WarpIsCommand.kt / ViewRecipeCommand.kt,
// coop/guild confirm from Skytils' MiscFeatures.kt and NEU's AntiCoopAdd.java
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CommandHelpers {

    private static final long CONFIRM_MS = 10_000L;
    private static long lastCoopAdd = 0L;
    private static long lastGuildLeave = 0L;
    private static List<String> itemIds = List.of();
    private static int itemIdsSize = -1;

    private CommandHelpers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        ClientSendMessageEvents.MODIFY_COMMAND.register(CommandHelpers::modify);
        ClientSendMessageEvents.ALLOW_COMMAND.register(CommandHelpers::allow);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("colors").executes(c -> { printColors(); return 1; }))
                .then(ClientCommands.literal("clearchat").executes(c -> {
                    Minecraft.getInstance().gui.getChat().clearMessages(false);
                    return 1;
                }))));
        Feature.of("CommandHelpers").section("Chat")
                .config("Chat", b -> {
                    b.label("Command Helpers");
                    b.toggle("Command Fixes", () -> cfg().commandHelpers, v -> cfg().commandHelpers = v)
                            .desc("Small fixes for commands typed on SkyBlock.");
                    b.toggle("  /warp is -> /is", () -> cfg().cmdWarpIs, v -> cfg().cmdWarpIs = v);
                    b.toggle("  /viewrecipe Lowercase & Spaces", () -> cfg().cmdViewRecipe, v -> cfg().cmdViewRecipe = v)
                            .desc("/viewrecipe hyperion 2 is sent as /viewrecipe HYPERION 2.");
                    b.toggle("  Tab-Complete /viewrecipe Items", () -> cfg().cmdViewRecipeTab, v -> cfg().cmdViewRecipeTab = v)
                            .desc("Suggests SkyBlock item ids. Needs a rejoin after switching.");
                    b.toggle("Confirm /coopadd & /g leave", () -> cfg().coopAddConfirm, v -> cfg().coopAddConfirm = v)
                            .desc("The first /coopadd or /g leave is blocked with a warning; repeat it within 10s to send it.")
                            .tags("coop", "scam", "guild");
                    b.toggle("  Block Co-op Request Button", () -> cfg().coopRequestBlock, v -> cfg().coopRequestBlock = v)
                            .desc("Swallows clicks on the Co-op Request diamond in a player's profile menu.");
                    b.toggle("Warp Names As Shortcuts", () -> cfg().warpShortcuts, v -> cfg().warpShortcuts = v)
                            .desc("Every warp name works as a command: /crimson is sent as /warp crimson. Your own shortcuts win. /jerry on your island and /barn in the Garden stay untouched.")
                            .tags("warp", "alias", "shortcut");
                    b.info("/tmap colors lists all & codes, /tmap clearchat empties the chat");
                });
    }

    private static boolean chatOn() { return cfg().section("Chat"); }

    private static String modify(String command) {
        TeslaMapsConfig c = cfg();
        if (command == null || !c.commandHelpers || !chatOn() || !SkyblockUtils.isInSkyblock()) return command;
        String trimmed = command.trim();
        String low = trimmed.toLowerCase(Locale.ROOT);
        if (c.cmdWarpIs && low.equals("warp is")) return "is";
        if (c.cmdViewRecipe && low.startsWith("viewrecipe ")) return viewRecipe(trimmed.substring(11).trim(), command);
        return command;
    }

    private static String viewRecipe(String input, String original) {
        if (input.isEmpty()) return original;
        String[] parts = input.toUpperCase(Locale.ROOT).split(" +");
        String last = parts[parts.length - 1];
        boolean page = parts.length > 1 && !last.isEmpty() && last.chars().allMatch(Character::isDigit);
        if (page) {
            String whole = String.join("_", parts);
            if (!SkyblockItems.known(whole)) {
                String item = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 1));
                return "viewrecipe " + item + " " + last;
            }
        }
        return "viewrecipe " + String.join("_", parts);
    }

    private static boolean allow(String command) {
        TeslaMapsConfig c = cfg();
        if (command == null || !c.coopAddConfirm || !chatOn()) return true;
        String low = command.trim().toLowerCase(Locale.ROOT);
        if (low.startsWith("hypixelcommand:")) low = low.substring("hypixelcommand:".length());
        long now = System.currentTimeMillis();
        if (low.startsWith("coopadd ")) {
            if (now - lastCoopAdd < CONFIRM_MS) return true;
            lastCoopAdd = now;
            warn("§c§lBe careful! §cBlocked /coopadd - it gives that player full control of your island. §6Run it again within 10s if you are sure.");
            return false;
        }
        if (low.equals("g leave") || low.equals("guild leave")) {
            if (now - lastGuildLeave < CONFIRM_MS) return true;
            lastGuildLeave = now;
            warn("§cBlocked /" + low + ". §6Run it again within 10s to really leave your guild.");
            return false;
        }
        return true;
    }

    /** True = swallow: the Co-op Request button in someone's profile menu. */
    public static boolean blockCoopClick(AbstractContainerScreen<?> screen, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.coopAddConfirm || !c.coopRequestBlock || !chatOn()) return false;
        if (slot == null || slot.container instanceof Inventory) return false;
        ItemStack st = slot.getItem();
        if (st.isEmpty() || !st.is(Items.DIAMOND)) return false;
        if (!StringUtil.stripTrim(st.getHoverName().getString()).contains("Co-op Request")) return false;
        String title = StringUtil.stripTrim(screen.getTitle().getString());
        if (!title.contains("Profile")) return false;
        int cut = title.indexOf("'s Profile");
        String ign = cut > 0 ? title.substring(0, cut) : "<name>";
        warn("§c§lBe careful! §cBlocked the Co-op Request button. §6Type /coopadd " + ign + " yourself if you really want to add them.");
        return true;
    }

    private static void warn(String msg) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] " + msg));
        });
    }

    public static boolean viewRecipeTabEnabled() {
        return cfg().commandHelpers && cfg().cmdViewRecipeTab;
    }

    public static CompletableFuture<Suggestions> suggestItems(SuggestionsBuilder builder) {
        if (!viewRecipeTabEnabled() || !chatOn() || !SkyblockUtils.isInSkyblock()) return builder.buildFuture();
        String typed = builder.getRemaining().trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        if (typed.isEmpty()) return builder.buildFuture();
        List<String> ids = itemIds();
        int n = 0;
        for (String id : ids) {
            if (id.equals(typed)) return builder.buildFuture();
        }
        for (String id : ids) {
            if (id.startsWith(typed) && n++ < 50) builder.suggest(id);
        }
        return builder.buildFuture();
    }

    private static List<String> itemIds() {
        var all = SkyblockItems.allIds();
        if (all.size() != itemIdsSize) {
            List<String> l = new ArrayList<>(all);
            l.sort(null);
            itemIds = l;
            itemIdsSize = all.size();
        }
        return itemIds;
    }

    private static void printColors() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §7Color codes (use & in TeslaMaps text fields):"));
        MutableComponent colors = Component.empty();
        MutableComponent formats = Component.empty();
        for (ChatFormatting f : ChatFormatting.values()) {
            String line = f == ChatFormatting.OBFUSCATED
                    ? "§7&k §kabc§r §7" + f.getName() + "§r  "
                    : "§" + f.getChar() + "&" + f.getChar() + " " + f.getName() + "§r  ";
            if (f.isFormat() || f == ChatFormatting.RESET) formats.append(Component.literal(line));
            else colors.append(Component.literal(line));
        }
        mc.player.sendSystemMessage(colors);
        mc.player.sendSystemMessage(formats);
    }
}
