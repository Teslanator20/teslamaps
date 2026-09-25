package com.teslamaps.features;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.mixin.BossHealthOverlayAccessor;
import com.teslamaps.mixin.PlayerTabOverlayAccessor;
import com.teslamaps.utils.GuiDump;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.TabListUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /tmap debug copy ... to the clipboard, /tmap debug item, /tmap debug giveitem (singleplayer). */
public final class DebugCopy {

    private DebugCopy() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("debug")
                        .then(ClientCommands.literal("copy")
                                .executes(c -> msg(c.getSource(), "§7/tmap debug copy <scoreboard|bossbar|actionbar|tab|item|pos|entities [radius]>"))
                                .then(ClientCommands.literal("scoreboard").executes(c -> copy(c.getSource(), "scoreboard", scoreboard())))
                                .then(ClientCommands.literal("bossbar").executes(c -> copy(c.getSource(), "boss bars", bossbar())))
                                .then(ClientCommands.literal("actionbar").executes(c -> copy(c.getSource(), "action bar",
                                        List.of(ActionBarFilter.escape(ActionBarFilter.lastRaw())))))
                                .then(ClientCommands.literal("tab").executes(c -> copy(c.getSource(), "tab list", tab())))
                                .then(ClientCommands.literal("item").executes(c -> copy(c.getSource(), "item", item())))
                                .then(ClientCommands.literal("pos")
                                        .executes(c -> copy(c.getSource(), "position", pos(false)))
                                        .then(ClientCommands.literal("exact").executes(c -> copy(c.getSource(), "position", pos(true)))))
                                .then(ClientCommands.literal("entities")
                                        .executes(c -> copy(c.getSource(), "entities", entities(10)))
                                        .then(ClientCommands.argument("radius", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> copy(c.getSource(), "entities",
                                                        entities(IntegerArgumentType.getInteger(c, "radius")))))))
                        .then(ClientCommands.literal("item").executes(c -> {
                            GuiDump.itemToChat(GuiDump.hoveredOrHeld());
                            return 1;
                        }))
                        .then(ClientCommands.literal("giveitem")
                                .executes(c -> msg(c.getSource(), "§7/tmap debug giveitem aotv [tuners] | <SKYBLOCK_ID> [material] §8(singleplayer)"))
                                .then(ClientCommands.literal("aotv")
                                        .executes(c -> giveAotv(c.getSource(), 3))
                                        .then(ClientCommands.argument("tuners", IntegerArgumentType.integer(0, 4))
                                                .executes(c -> giveAotv(c.getSource(), IntegerArgumentType.getInteger(c, "tuners")))))
                                .then(ClientCommands.argument("id", StringArgumentType.word())
                                        .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "id"), "diamond_sword"))
                                        .then(ClientCommands.argument("material", StringArgumentType.word())
                                                .executes(c -> give(c.getSource(), StringArgumentType.getString(c, "id"),
                                                        StringArgumentType.getString(c, "material")))))))));
    }

    private static int msg(FabricClientCommandSource src, String text) {
        src.sendFeedback(Component.literal("§b[TeslaMaps] " + text));
        return 1;
    }

    private static int copy(FabricClientCommandSource src, String what, List<String> lines) {
        if (lines == null || lines.isEmpty()) return msg(src, "§cNothing to copy (" + what + ")");
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", lines));
        return msg(src, "§fCopied " + what + " §7(" + lines.size() + " lines)");
    }

    private static List<String> scoreboard() {
        List<String> lines = ScoreboardUtils.getScoreboardLines();
        if (lines.isEmpty()) return lines;
        List<String> out = new ArrayList<>();
        out.add(ScoreboardUtils.getScoreboardTitle());
        out.addAll(lines);
        out.add("");
        out.add("--- stripped ---");
        out.add(GuiDump.strip(ScoreboardUtils.getScoreboardTitle()));
        for (String l : lines) out.add(GuiDump.strip(l));
        return out;
    }

    private static List<String> bossbar() {
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        for (var e : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).teslamaps$getEvents().values()) {
            out.add(e.getName().getString() + " | progress=" + e.getProgress() + " | color=" + e.getColor() + " | overlay=" + e.getOverlay());
        }
        return out;
    }

    private static List<String> tab() {
        Minecraft mc = Minecraft.getInstance();
        PlayerTabOverlayAccessor acc = (PlayerTabOverlayAccessor) mc.gui.getTabList();
        List<String> out = new ArrayList<>();
        out.add("--- header ---");
        if (acc.teslamaps$getHeader() != null) out.add(acc.teslamaps$getHeader().getString());
        out.add("--- lines ---");
        out.addAll(TabListUtils.getTabListLines());
        out.add("--- footer ---");
        if (acc.teslamaps$getFooter() != null) out.add(acc.teslamaps$getFooter().getString());
        return out;
    }

    private static List<String> item() {
        ItemStack stack = GuiDump.hoveredOrHeld();
        if (stack.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(GuiDump.itemInfo(stack, 0));
        var lore = stack.get(DataComponents.LORE);
        if (lore != null) {
            out.add("lore:");
            for (Component l : lore.lines()) out.add("  " + l.getString());
        }
        out.add("snbt: " + StorageCache.encode(stack));
        return out;
    }

    private static List<String> pos(boolean exact) {
        var p = Minecraft.getInstance().player;
        if (p == null) return List.of();
        if (exact) return List.of(String.format(Locale.ROOT, "%.2f, %.2f, %.2f", p.getX(), p.getY(), p.getZ()));
        var b = p.blockPosition();
        return List.of(b.getX() + ", " + b.getY() + ", " + b.getZ());
    }

    private static List<String> entities(int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return List.of();
        List<String> out = new ArrayList<>();
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius), x -> true)) {
            out.add(String.format(Locale.ROOT, "%s | %s | %.2f, %.2f, %.2f | d=%.1f%s",
                    net.minecraft.world.entity.EntityType.getKey(e.getType()), e.getName().getString(),
                    e.getX(), e.getY(), e.getZ(), Math.sqrt(e.distanceToSqr(mc.player)),
                    e.getCustomName() != null ? " | custom=" + e.getCustomName().getString() : ""));
        }
        return out;
    }

    private static boolean singleplayer(FabricClientCommandSource src) {
        if (Minecraft.getInstance().hasSingleplayerServer() && Minecraft.getInstance().getConnection() != null) return true;
        msg(src, "§cSingleplayer only");
        return false;
    }

    private static int giveAotv(FabricClientCommandSource src, int tuners) {
        if (!singleplayer(src)) return 0;
        Minecraft.getInstance().getConnection().sendCommand("give @s minecraft:diamond_shovel[minecraft:custom_name={\"text\":\"Aspect of the Void\",\"color\":\"dark_purple\",\"italic\":false},"
                + "minecraft:custom_data={id:\"ASPECT_OF_THE_VOID\",ethermerge:1,tuned_transmission:" + tuners + "}]");
        return 1;
    }

    private static int give(FabricClientCommandSource src, String id, String material) {
        if (!singleplayer(src)) return 0;
        id = id.toUpperCase(Locale.ROOT);
        material = material.toLowerCase(Locale.ROOT);
        if (!id.matches("[A-Z0-9_:;\\-]+") || !material.matches("[a-z0-9_]+")) return msg(src, "§cBad id or material");
        String name = GuiDump.strip(SkyblockItems.name(id));
        if (name == null || name.isEmpty()) name = id;
        name = name.replace("\\", "").replace("\"", "");
        Minecraft.getInstance().getConnection().sendCommand("give @s minecraft:" + material
                + "[minecraft:custom_name={\"text\":\"" + name + "\",\"italic\":false},minecraft:custom_data={id:\"" + id + "\"}]");
        return 1;
    }
}
