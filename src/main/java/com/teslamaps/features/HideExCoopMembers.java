package com.teslamaps.features;

// Ported from SkyHanni's HideExCoopMembers.kt
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HideExCoopMembers {

    private static final Pattern PLAYER_COUNTER = Pattern.compile("(?:\\[[^]]+] )?([A-Za-z0-9_]{2,16}): (.+)");
    private static final Pattern RANK = Pattern.compile("^\\[[^]]+] ");

    private static Feature feature;
    private static Screen scanned;
    private static long openedAt;

    private HideExCoopMembers() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("HideExCoopMembers").when(() -> cfg().hideExCoopMembers)
                .tick(HideExCoopMembers::tick)
                .config("Tooltips & Value", b -> {
                    b.toggle("Hide Ex-Coop Members in Collections", () -> cfg().hideExCoopMembers, v -> cfg().hideExCoopMembers = v)
                            .desc("Removes former co-op members from Co-op Contributions. Open Historic Members once, or use /tmap excoop add|remove <name>.");
                    b.button("  Clear Ex-Coop List", () -> {
                        cfg().hiddenCoopMembers.clear();
                        TeslaMapsConfig.save();
                    });
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active()) onTooltip(lines);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) { scanned = null; openedAt = 0; return; }
        if (scanned == cs) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Historic Members")) { scanned = cs; return; }
        if (openedAt == 0) openedAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - openedAt < 500) return;
        openedAt = 0;
        scanned = cs;
        boolean changed = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory || !slot.getItem().is(Items.PLAYER_HEAD)) continue;
            String name = RANK.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString())).replaceFirst("").trim();
            if (name.isEmpty() || name.contains(" ")) continue;
            if (!contains(name)) {
                cfg().hiddenCoopMembers.add(name);
                changed = true;
            }
        }
        if (changed) TeslaMapsConfig.save();
    }

    private static boolean contains(String name) {
        for (String s : cfg().hiddenCoopMembers) if (s.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static void onTooltip(List<Component> lines) {
        if (cfg().hiddenCoopMembers.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.endsWith("Collection") && !title.endsWith("Collections")) return;

        int coop = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (StringUtil.stripTrim(lines.get(i).getString()).equals("Co-op Contributions:")) { coop = i; break; }
        }
        if (coop == -1) return;

        int remaining = 0;
        List<Integer> remove = new ArrayList<>();
        for (int i = coop + 1; i < lines.size(); i++) {
            String s = StringUtil.stripTrim(lines.get(i).getString());
            if (s.isEmpty()) break;
            Matcher m = PLAYER_COUNTER.matcher(s);
            if (!m.matches()) continue;
            if (contains(m.group(1))) remove.add(i);
            else remaining++;
        }
        if (remove.isEmpty()) return;
        for (int k = remove.size() - 1; k >= 0; k--) lines.remove((int) remove.get(k));
        if (remaining >= 2) return;

        boolean notMaxed = false;
        for (Component c : lines) {
            String s = StringUtil.stripTrim(c.getString());
            if (s.startsWith("Progress to ") || s.startsWith("Total Collected: ")) { notMaxed = true; break; }
        }
        if (!notMaxed) {
            lines.set(coop, Component.literal("§a§lCOLLECTION MAXED OUT!"));
        } else {
            for (int i = coop + 1; i >= coop - 1; i--) {
                if (i >= 0 && i < lines.size()) lines.remove(i);
            }
        }
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("excoop")
                .executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§b[TeslaMaps] §7Hidden ex-coop members: §f"
                            + (cfg().hiddenCoopMembers.isEmpty() ? "none" : String.join(", ", cfg().hiddenCoopMembers))));
                    return 1;
                })
                .then(ClientCommands.literal("add")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean added = !contains(name) && cfg().hiddenCoopMembers.add(name);
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal(added
                                            ? "§b[TeslaMaps] §fHiding §e" + name : "§b[TeslaMaps] §7Already hidden: §e" + name));
                                    return 1;
                                })))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    boolean gone = cfg().hiddenCoopMembers.removeIf(s -> s.equalsIgnoreCase(name));
                                    TeslaMapsConfig.save();
                                    c.getSource().sendFeedback(Component.literal(gone
                                            ? "§b[TeslaMaps] §fNo longer hiding §e" + name : "§b[TeslaMaps] §7Not in the list: §e" + name));
                                    return 1;
                                })));
    }
}
