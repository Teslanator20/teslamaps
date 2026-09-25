package com.teslamaps.features;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

// Ported from SkyHanni's CopyPlaytime, AutoJoinSkyblock, CrashOnDeath, CrashOnTttFail
/** Copy playtime, auto /skyblock, auto jump outside SkyBlock, joke crashes. */
public final class MiscQol {

    private static final String PLAYTIME_TITLE = "Detailed /playtime";
    private static final int PLAYTIME_SLOT = 4;
    private static final long JOIN_COOLDOWN_MS = 30_000;

    private static ClientPacketListener lastConnection;
    private static long lastJoin;
    private static int jumpTicks;

    private MiscQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("MiscQol.Jump").tickAlways(MiscQol::tickAutoJump);
        Feature.of("MiscQol.Join").section("Auto").legit().when(() -> cfg().autoJoinSkyblock)
                .tickAlways(MiscQol::tickJoin)
                .config("Auto", b -> {
                    b.label("Hypixel");
                    b.toggle("Auto Join SkyBlock", () -> cfg().autoJoinSkyblock, v -> cfg().autoJoinSkyblock = v).cheat()
                            .desc("Sends /skyblock 1s after joining Hypixel, at most every 30s");
                });
        Feature.of("MiscQol.Crash").when(() -> cfg().crashOnDeath || cfg().crashOnTttFail)
                .serverChat(MiscQol::onChat)
                .config("Interface", b -> {
                    b.label("Misc");
                    b.toggle("Copy /playtime Stats", () -> cfg().copyPlaytime, v -> cfg().copyPlaytime = v)
                            .desc("Click the summary item in Detailed /playtime to copy it without color codes");
                    b.toggle("Auto Jump Outside SkyBlock", () -> cfg().autoJumpOutsideSkyblock, v -> cfg().autoJumpOutsideSkyblock = v)
                            .desc("Vanilla auto jump on in lobbies, off on SkyBlock; restores your setting when turned off");
                    b.toggle("Crash On Death (joke)", () -> cfg().crashOnDeath, v -> cfg().crashOnDeath = v)
                            .desc("Really crashes the game when you die on SkyBlock");
                    b.toggle("Crash On Tic Tac Toe Fail (joke)", () -> cfg().crashOnTttFail, v -> cfg().crashOnTttFail = v)
                            .desc("Really crashes the game when you lose Tic Tac Toe in a dungeon");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!cfg().copyPlaytime || lines.isEmpty() || stack.isEmpty()) return;
            Slot slot = playtimeSlot();
            if (slot == null || slot.getItem() != stack) return;
            lines.add(Component.empty());
            lines.add(Component.literal("§eClick to Copy!"));
        });
    }

    private static Slot playtimeSlot() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return null;
        if (!PLAYTIME_TITLE.equals(StringUtil.strip(cs.getTitle().getString()).trim())) return null;
        Slot slot = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (slot == null || slot.container instanceof Inventory || slot.index != PLAYTIME_SLOT) return null;
        return slot;
    }

    /** mouseClicked HEAD. True = swallow. */
    public static boolean onClick(Slot focused, int button) {
        if (!cfg().copyPlaytime || button != 0 || focused == null) return false;
        Slot slot = playtimeSlot();
        if (slot != focused || slot.getItem().isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        List<String> out = new ArrayList<>();
        String profile = SbProfile.name();
        String player = mc.player == null ? "?" : mc.player.getGameProfile().name();
        out.add(player + "'s" + (profile == null ? "" : " - " + StringUtil.titleCase(profile)) + " Playtime Stats");
        out.addAll(ItemUtil.loreLines(slot.getItem()));
        mc.keyboardHandler.setClipboard(String.join("\n", out));
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7Copied playtime stats into clipboard."));
        return true;
    }

    private static void tickJoin() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == lastConnection) return;
        lastConnection = conn;
        if (conn == null || !SkyblockUtils.isOnHypixel()) return;
        long now = System.currentTimeMillis();
        if (now - lastJoin < JOIN_COOLDOWN_MS) return;
        lastJoin = now;
        TickScheduler.schedule(20, () -> {
            ClientPacketListener c = Minecraft.getInstance().getConnection();
            if (c == null || c != lastConnection || !cfg().autoJoinSkyblock || LegitMode.blocksCheats()) return;
            if (SkyblockUtils.isInSkyblock()) return;
            c.sendCommand("skyblock");
        });
    }

    private static void tickAutoJump() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        var opt = mc.options.autoJump();
        if (!c.autoJumpOutsideSkyblock) {
            if (c.autoJumpOriginal != null) {
                opt.set(c.autoJumpOriginal);
                c.autoJumpOriginal = null;
                mc.options.save();
                TeslaMapsConfig.save();
            }
            return;
        }
        if (mc.player == null || ++jumpTicks % 20 != 0) return;
        if (c.autoJumpOriginal == null) {
            c.autoJumpOriginal = opt.get();
            TeslaMapsConfig.save();
        }
        boolean want = !SkyblockUtils.isInSkyblock();
        if (opt.get() != want) opt.set(want);
    }

    private static void onChat(String raw) {
        String line = StringUtil.strip(raw).trim();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String me = mc.player.getGameProfile().name();
        if (cfg().crashOnDeath && line.startsWith("☠ ") && SkyblockUtils.isInSkyblock()) {
            String rest = line.substring(2);
            if (rest.startsWith("You ") || rest.startsWith(me + " ")) {
                mc.delayCrash(new CrashReport("TeslaMaps Crash on Death",
                        new Throwable("Disable Crash On Death in TeslaMaps if you want to stop crashing")));
            }
        }
        if (cfg().crashOnTttFail && line.startsWith("PUZZLE FAIL! ") && line.endsWith(" lost Tic Tac Toe! Yikes!")
                && DungeonManager.isInDungeon()) {
            String name = line.substring("PUZZLE FAIL! ".length(), line.length() - " lost Tic Tac Toe! Yikes!".length());
            if (name.equals(me)) {
                mc.delayCrash(new CrashReport("TeslaMaps Crash on TTT Fail",
                        new Throwable("Get gud or disable Crash On Tic Tac Toe Fail")));
            }
        }
    }
}
