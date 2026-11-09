package com.teslamaps.features;

// Actions from Bax's ChatTrigger + ShowToast/PlaySound/ScreenFlash actions, on our own chat filters
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.config.TeslaMapsConfig.ChatUserFilter;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.SoundOptions;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Title / sound / flash when one of the player's own chat filters matches. */
public final class ChatAlerts {

    private static final Pattern GROUP_REF = Pattern.compile("\\{(\\w+)}");

    private ChatAlerts() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ChatAlerts").section("Chat").when(() -> cfg().chatUserAlerts).chat(ChatAlerts::onChat);
    }

    static boolean hasAlert(ChatUserFilter f) {
        return f.title != null && !f.title.isEmpty() || f.sound != null && !f.sound.isEmpty() || f.flash;
    }

    private static void onChat(String raw) {
        List<ChatUserFilter> filters = cfg().chatUserFilters;
        if (filters == null || filters.isEmpty()) return;
        String t = StringUtil.stripTrim(raw);
        if (t.isEmpty()) return;
        int known = -1;
        for (int i = 0, n = filters.size(); i < n; i++) {
            ChatUserFilter f = filters.get(i);
            if (f == null || f.text == null || f.text.isEmpty() || !hasAlert(f)) continue;
            if (f.systemOnly) {
                if (known < 0) {
                    PlayerChat.Msg pm = PlayerChat.current();
                    known = pm == null || pm.channel() == PlayerChat.Channel.NPC ? 1 : 0;
                }
                if (known == 0) continue;
            }
            if (!ChatUserFilters.hits(f, t)) continue;
            fire(f, t);
        }
    }

    private static void fire(ChatUserFilter f, String line) {
        Minecraft mc = Minecraft.getInstance();
        if (f.title != null && !f.title.isEmpty()) {
            String title = fill(f, line).replace('&', '§');
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal(title));
        }
        if (f.sound != null && !f.sound.isEmpty()) LoudSound.play(SoundOptions.resolve(f.sound), 1f, 1f);
        if (f.flash) {
            TeslaMapsConfig c = cfg();
            ScreenFlash.flash(TeslaMapsConfig.parseColor(c.screenFlashColor), c.screenFlashMs);
        }
    }

    private static String fill(ChatUserFilter f, String line) {
        String title = f.title;
        if (title.indexOf('{') < 0) return title;
        Matcher m = "regex".equals(f.mode) ? ChatUserFilters.regex(f.text).matcher(line) : null;
        boolean found = m != null && m.find();
        Matcher ref = GROUP_REF.matcher(title);
        StringBuilder out = new StringBuilder();
        while (ref.find()) {
            String key = ref.group(1), val;
            if (!found) {
                val = key.equals("0") ? line : "";
            } else {
                try {
                    val = key.chars().allMatch(Character::isDigit) ? m.group(Integer.parseInt(key)) : m.group(key);
                } catch (RuntimeException e) {
                    val = "";
                }
            }
            ref.appendReplacement(out, Matcher.quoteReplacement(val == null ? "" : val));
        }
        ref.appendTail(out);
        return out.toString();
    }

    public static String describe(ChatUserFilter f) {
        if (!hasAlert(f) && !f.keep && !f.systemOnly) return "";
        StringBuilder b = new StringBuilder(" §8[");
        if (f.title != null && !f.title.isEmpty()) b.append("§btitle §f").append(f.title).append(' ');
        if (f.sound != null && !f.sound.isEmpty()) b.append("§bsound §f").append(f.sound).append(' ');
        if (f.flash) b.append("§bflash ");
        if (f.systemOnly) b.append("§7system ");
        b.append(f.keep ? "§ashown" : "§chidden");
        return b.append("§8]").toString();
    }

    private static void say(FabricClientCommandSource src, String msg) {
        src.sendFeedback(Component.literal(msg));
    }

    private static ChatUserFilter get(CommandContext<FabricClientCommandSource> ctx) {
        int n = IntegerArgumentType.getInteger(ctx, "n");
        List<ChatUserFilter> l = cfg().chatUserFilters;
        if (n > l.size()) {
            say(ctx.getSource(), "§c[TeslaMaps] No filter " + n);
            return null;
        }
        return l.get(n - 1);
    }

    private static int edit(CommandContext<FabricClientCommandSource> ctx, BiFunction<ChatUserFilter, Boolean, String> change) {
        ChatUserFilter f = get(ctx);
        if (f == null) return 0;
        boolean first = !hasAlert(f);
        String msg = change.apply(f, first);
        if (first && hasAlert(f)) f.keep = true;
        TeslaMapsConfig.save();
        say(ctx.getSource(), "§a[TeslaMaps] §7" + msg + " §8→ §f" + f.text + describe(f));
        if (!cfg().chatUserAlerts && hasAlert(f)) say(ctx.getSource(), "§e[TeslaMaps] §7Turn on Chat > Own Filter Alerts for it to fire");
        return 1;
    }

    private static String clean(String v) {
        v = v.trim();
        return v.equals("-") || v.equalsIgnoreCase("off") ? null : v;
    }

    static void commands(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        root.then(ClientCommands.literal("title")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> edit(ctx, (f, first) -> {
                                    f.title = clean(StringArgumentType.getString(ctx, "text"));
                                    return f.title == null ? "Title removed" : "Title set";
                                })))));
        root.then(ClientCommands.literal("sound")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .then(ClientCommands.argument("sound", StringArgumentType.word())
                                .suggests((ctx, sb) -> {
                                    for (String k : SoundOptions.keys()) if (k.startsWith(sb.getRemainingLowerCase().toUpperCase())) sb.suggest(k);
                                    sb.suggest("-");
                                    return sb.buildFuture();
                                })
                                .executes(ctx -> edit(ctx, (f, first) -> {
                                    String v = clean(StringArgumentType.getString(ctx, "sound"));
                                    f.sound = v == null ? null : v.toUpperCase();
                                    return f.sound == null ? "Sound removed" : "Sound set";
                                })))));
        root.then(ClientCommands.literal("flash")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> edit(ctx, (f, first) -> {
                            f.flash = !f.flash;
                            return f.flash ? "Flash on" : "Flash off";
                        }))));
        root.then(ClientCommands.literal("show")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> edit(ctx, (f, first) -> {
                            f.keep = !f.keep;
                            return f.keep ? "Line stays visible" : "Line gets filtered";
                        }))));
        root.then(ClientCommands.literal("system")
                .then(ClientCommands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> edit(ctx, (f, first) -> {
                            f.systemOnly = !f.systemOnly;
                            return f.systemOnly ? "Server lines only" : "Player chat too";
                        }))));
    }
}
