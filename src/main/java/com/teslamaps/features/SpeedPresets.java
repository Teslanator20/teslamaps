package com.teslamaps.features;

// Ported from Skyblocker's SpeedPresets.java + the speed-cap part of AbstractSignEditScreenMixin
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Named /setmaxspeed values: /setmaxspeed crops = /setmaxspeed 93, also in the speed-cap sign. */
public final class SpeedPresets {

    private static final Pattern COMMAND = Pattern.compile("^setmaxspeed\\s([a-zA-Z]\\w*)$");
    private static final String SPEED_SIGN = "speed cap!";

    private static Feature feature;

    private SpeedPresets() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("SpeedPresets").section("Garden").when(() -> cfg().speedPresets)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Speed Presets");
                    b.toggle("Speed Presets", () -> c.speedPresets, v -> c.speedPresets = v)
                            .desc("/setmaxspeed crops becomes /setmaxspeed 93, tab-completes the names, and the speed cap sign takes a name too. Edit with /tmap speedpreset")
                            .tags("setmaxspeed", "speed", "cap", "rancher", "farming");
                    b.info("/tmap speedpreset set <name> <speed> | remove <name> | list | reset");
                });
        ClientSendMessageEvents.MODIFY_COMMAND.register(SpeedPresets::modify);
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("speedpreset")
                        .executes(c -> { list(); return 1; })
                        .then(ClientCommands.literal("list").executes(c -> { list(); return 1; }))
                        .then(ClientCommands.literal("reset").executes(c -> {
                            cfg().speedPresetValues = TeslaMapsConfig.defaultSpeedPresets();
                            TeslaMapsConfig.save();
                            msg("§aSpeed presets reset");
                            return 1;
                        }))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests((c, sb) -> net.minecraft.commands.SharedSuggestionProvider.suggest(names(), sb))
                                        .executes(c -> {
                                            String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                                            msg(cfg().speedPresetValues.remove(n) != null ? "§7Removed §f" + n : "§cNo preset " + n);
                                            TeslaMapsConfig.save();
                                            return 1;
                                        })))
                        .then(ClientCommands.literal("set")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .then(ClientCommands.argument("speed", IntegerArgumentType.integer(1, 500))
                                                .executes(c -> {
                                                    String n = StringArgumentType.getString(c, "name").toLowerCase(Locale.ROOT);
                                                    if (!n.matches("[a-z]\\w*")) {
                                                        msg("§cNames start with a letter");
                                                        return 0;
                                                    }
                                                    int v = IntegerArgumentType.getInteger(c, "speed");
                                                    cfg().speedPresetValues.put(n, v);
                                                    TeslaMapsConfig.save();
                                                    msg("§7Preset §f" + n + " §7= §a" + v);
                                                    return 1;
                                                })))))));
    }

    private static boolean on() {
        return feature != null && feature.active() && SkyblockUtils.isInSkyblock();
    }

    private static String modify(String command) {
        if (!on()) return command;
        Matcher m = COMMAND.matcher(command);
        if (!m.matches()) return command;
        Integer v = cfg().speedPresetValues.get(m.group(1).toLowerCase(Locale.ROOT));
        return v == null ? command : "setmaxspeed " + v;
    }

    /** Preset names for /setmaxspeed tab completion, empty while off. */
    public static List<String> suggestions() {
        return on() ? names() : List.of();
    }

    private static List<String> names() {
        return new ArrayList<>(cfg().speedPresetValues.keySet());
    }

    private static boolean speedSign(String[] m) {
        return m != null && m.length >= 4 && SPEED_SIGN.equals(m[3]) && on();
    }

    /** True = drew the preset line, the calculator stays quiet. */
    public static boolean renderSign(GuiGraphicsExtractor g, String[] m) {
        if (!speedSign(m)) return false;
        Integer v = cfg().speedPresetValues.get(m[0].trim().toLowerCase(Locale.ROOT));
        if (v == null) return false;
        g.centeredText(Minecraft.getInstance().font, "§a" + m[0].trim() + " » " + v, g.guiWidth() / 2, 55, 0xFFFFFFFF);
        return true;
    }

    public static void onSignDone(String[] m) {
        if (!speedSign(m)) return;
        Integer v = cfg().speedPresetValues.get(m[0].trim().toLowerCase(Locale.ROOT));
        if (v != null) m[0] = String.valueOf(v);
    }

    private static void list() {
        StringBuilder sb = new StringBuilder("§7Speed presets:");
        for (Map.Entry<String, Integer> e : cfg().speedPresetValues.entrySet()) {
            sb.append("\n §f").append(e.getKey()).append(" §7» §a").append(e.getValue());
        }
        msg(sb.toString());
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
