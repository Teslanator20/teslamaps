package com.teslamaps.adm.adr;

import com.teslamaps.TeslaMaps;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Ported from ADM's ADR.kt (local only: no upload, no ADR Online)
/**
 * ADR: records each dungeon run locally to {@link AdrRunFiles#runsDir()} (ADS runs to simDir).
 * A session starts on every world load; {@link RunRecorder} keeps it only if it turns out to be a dungeon.
 */
public final class Adr {
    private Adr() {}

    private static final Pattern CONTROL_CODES = Pattern.compile("§.");

    private static volatile RunRecorder session;
    private static ClientLevel lastLevel;
    private static boolean tidied = false;

    public static boolean on() {
        return Adm.on() && TeslaMapsConfig.get().admAdr;
    }

    public static RunRecorder session() {
        return session;
    }

    static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
        });
    }

    // ------------------------------------------------------------------ lifecycle

    /** Every client tick while ADR is on. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PartyTracker.tick();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) onLevelLoad();
            else stop();
        }
        RunRecorder s = session;
        if (s == null || mc.level == null) return;
        s.onTick(mc.level);
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            AbstractContainerMenu menu = screen.getMenu();
            s.onContainerTick(items(menu), menu.getCarried());
        }
        if (s.abandoned()) session = null;
    }

    private static void onLevelLoad() {
        if (!tidied) { tidied = true; tidy(); }
        RunRecorder old = session;
        if (old != null) old.finish();
        RoomKeys.reset();
        Minecraft mc = Minecraft.getInstance();
        boolean adsWorld = AdrSimHooks.get(AdrSimHooks.adsWorld) && mc.hasSingleplayerServer();
        if (adsWorld) session = TeslaMapsConfig.get().admAdrRecordAds ? new RunRecorder(AdrRunFiles.simDir(), true, true) : null;
        else session = new RunRecorder(AdrRunFiles.runsDir(), true, false);
    }

    /** Ends the session (world left, ADR or ADM switched off). */
    static void stop() {
        RunRecorder s = session;
        session = null;
        if (s != null) s.finish();
    }

    static boolean dirty() {
        return session != null || lastLevel != null;
    }

    static void offTick() {
        lastLevel = null;
        stop();
    }

    /** Every rendered frame: your own camera, and the mouse over an open container. */
    static void frame() {
        RunRecorder s = session;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (s == null || player == null) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        s.onFrame(pt, player.getYRot(), player.getXRot());
        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            HandledScreenAccessor box = (HandledScreenAccessor) screen;
            s.onContainerMouse(pt, (float) (mc.mouseHandler.getScaledXPos(mc.getWindow()) - box.getX()),
                    (float) (mc.mouseHandler.getScaledYPos(mc.getWindow()) - box.getY()));
        }
    }

    private static List<ItemStack> items(AbstractContainerMenu menu) {
        List<ItemStack> out = new ArrayList<>(menu.slots.size());
        for (Slot sl : menu.slots) out.add(sl.getItem());
        return out;
    }

    static void onScreenInit(AbstractContainerScreen<?> screen) {
        RunRecorder s = session;
        if (s == null) return;
        AbstractContainerMenu menu = screen.getMenu();
        HandledScreenAccessor box = (HandledScreenAccessor) screen;
        String type;
        if (menu instanceof InventoryMenu) type = "inventory";
        else {
            try {
                var key = BuiltInRegistries.MENU.getKey(menu.getType());
                type = key == null ? "unknown" : key.toString();
            } catch (Throwable t) {
                type = "unknown";
            }
        }
        List<int[]> slots = new ArrayList<>(menu.slots.size());
        for (Slot sl : menu.slots) slots.add(new int[]{sl.x, sl.y});
        s.onContainerOpen(screen.getTitle().getString(), type, box.getImageWidth(), box.getImageHeight(), slots);
        s.onContainerTick(items(menu), menu.getCarried());
    }

    static void onScreenClick(AbstractContainerScreen<?> screen, double x, double y, int button) {
        RunRecorder s = session;
        if (s == null) return;
        HandledScreenAccessor box = (HandledScreenAccessor) screen;
        s.onContainerClick((float) (x - box.getX()), (float) (y - box.getY()), button);
    }

    static void onScreenClose() {
        RunRecorder s = session;
        if (s != null) s.onGuiClose();
    }

    // ------------------------------------------------------------------ mixin hooks (main thread unless noted)

    public static void onSlotClick(int slot, int button, String type) {
        RunRecorder s = session;
        if (s != null) safely(() -> s.onSlotClick(slot, button, type));
    }

    public static void onBlockUse(BlockPos pos) {
        RunRecorder s = session;
        if (s != null) safely(() -> s.onBlockUse(pos));
    }

    public static void onUseAttempt(BlockPos pos) {
        RunRecorder s = session;
        if (s != null && RunRecorder.isSsButton(pos)) safely(() -> s.onSsAttempt(pos));
    }

    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        RunRecorder s = session;
        if (s != null) safely(() -> s.onBlockUpdate(pos.immutable(), state));
    }

    public static void onTeleport(ClientboundPlayerPositionPacket p) {
        RunRecorder s = session;
        if (s == null) return;
        LocalPlayer player = Minecraft.getInstance().player;
        PositionMoveRotation abs = player != null ? PositionMoveRotation.calculateAbsolute(PositionMoveRotation.of(player), p.change(), p.relatives()) : p.change();
        List<String> rel = new ArrayList<>();
        if (player == null) for (Relative r : p.relatives()) rel.add(r.name());
        safely(() -> s.onTeleport(abs.position().x, abs.position().y, abs.position().z, abs.yRot(), abs.xRot(), rel));
    }

    public static void onSound(ClientboundSoundPacket p) {
        RunRecorder s = session;
        if (s == null) return;
        SoundEvent sound = p.getSound().value();
        double x = p.getX(), y = p.getY(), z = p.getZ();
        if (x >= 105.0 && x <= 116.0 && y >= 116.0 && y <= 127.0 && z >= 87.0 && z <= 99.0)
            safely(() -> s.onSsSound(sound.location().toString(), x, y, z, p.getVolume(), p.getPitch()));
        if (sound != SoundEvents.BAT_HURT && sound != SoundEvents.BAT_DEATH) return;
        safely(() -> s.onBatSound(x, y, z, p.getVolume()));
    }

    public static void onPickup(ClientboundTakeItemEntityPacket p) {
        RunRecorder s = session;
        if (s != null) safely(() -> s.onPickup(p.getItemId(), p.getPlayerId()));
    }

    public static void onBlockEvent(ClientboundBlockEventPacket p) {
        RunRecorder s = session;
        if (s == null || p.getB0() != 1) return;
        if (p.getBlock() != Blocks.CHEST && p.getBlock() != Blocks.TRAPPED_CHEST && p.getBlock() != Blocks.ENDER_CHEST) return;
        BlockPos at = p.getPos().immutable();
        int open = p.getB1();
        safely(() -> s.onChestEvent(at, open));
    }

    // server tick count when a chat packet came off the network (netty thread), read on the main thread
    private static final Map<Object, Integer> CHAT_STAMP = new IdentityHashMap<>();

    /** Netty thread: only stamps the server tick. */
    public static void stampChat(ClientboundSystemChatPacket p) {
        RunRecorder s = session;
        if (s == null) return;
        synchronized (CHAT_STAMP) {
            if (CHAT_STAMP.size() > 512) CHAT_STAMP.clear();
            CHAT_STAMP.put(p, s.serverTickCount());
        }
    }

    /** Main thread. True when the line is the answer to our own /p list and must be hidden. */
    public static boolean onSystemChat(ClientboundSystemChatPacket p) {
        Integer stamp;
        synchronized (CHAT_STAMP) {
            stamp = CHAT_STAMP.remove(p);
        }
        if (!on() || p.overlay()) return false;
        try {
            String text = CONTROL_CODES.matcher(p.content().getString()).replaceAll("");
            String colored = legacyText(p.content());
            boolean hide = PartyTracker.onChat(text, colored);
            TeslaMapsConfig c = TeslaMapsConfig.get();
            if (c.admAdrHidePrivateChats && privateChat(text)) return hide;
            if (c.admAdrHidePartyChat && partyChat(text)) return hide;
            RunRecorder s = session;
            if (s != null) s.onChat(text, colored, stamp != null ? stamp : s.serverTickCount());
            return hide;
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("[adr] chat", t);
            return false;
        }
    }

    private static void safely(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            TeslaMaps.LOGGER.warn("[adr] recorder hook failed", t);
        }
    }

    // ------------------------------------------------------------------ chat text

    /** A chat line with its formatting as § codes (§#rrggbb for non-legacy colours). */
    public static String legacyText(Component message) {
        StringBuilder sb = new StringBuilder();
        String[] last = {""};
        message.visit((style, text) -> {
            if (!text.isEmpty()) {
                String codes = styleCodes(style);
                if (!codes.equals(last[0])) {
                    if (!last[0].isEmpty()) sb.append("§r");
                    sb.append(codes);
                    last[0] = codes;
                }
                sb.append(text);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return sb.toString();
    }

    private static String styleCodes(Style style) {
        StringBuilder sb = new StringBuilder();
        if (style.getColor() != null) sb.append(colorCode(style.getColor()));
        if (style.isObfuscated()) sb.append("§k");
        if (style.isBold()) sb.append("§l");
        if (style.isStrikethrough()) sb.append("§m");
        if (style.isUnderlined()) sb.append("§n");
        if (style.isItalic()) sb.append("§o");
        return sb.toString();
    }

    private static String colorCode(TextColor color) {
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor tc = TextColor.fromLegacyFormat(f);
            if (tc != null && tc.getValue() == color.getValue()) return f.toString();
        }
        return "§#" + String.format(Locale.ROOT, "%06x", color.getValue() & 0xFFFFFF);
    }

    private static final Pattern PRIVATE_LINE = Pattern.compile("^(?:(?:From|To) (?:\\[[^\\]]+] )?\\w{1,16}: |(?:Guild|Officer|Co-op|Friend) > |Friend request from |You are now friends with |-*\\s*Friends \\(Page |Guild Name: |Total Members: |Online Members: |Offline Members: |-- .+ --$)");
    private static final Pattern PRIVATE_ANYWHERE = Pattern.compile("(?i)\\bco-op\\b|\\b(?:joined|left) the guild\\b|\\bto join (?:their|the|your) guild\\b|\\bremoved you from your friends\\b");

    /** Private messages, guild/officer/co-op chat, friend notices: any such line hides the message. */
    public static boolean privateChat(String text) {
        for (String raw : text.split("\n", -1)) {
            String l = raw.trim();
            if (PRIVATE_LINE.matcher(l).find() || (!l.startsWith("Party > ") && PRIVATE_ANYWHERE.matcher(l).find())) return true;
        }
        return false;
    }

    public static boolean partyChat(String text) {
        for (String raw : text.split("\n", -1)) if (raw.trim().startsWith("Party > ")) return true;
        return false;
    }

    // ------------------------------------------------------------------ the runs folder

    /** Once a game: .part files a crash left (untouched for 30 min) are saved as runs. Nothing is deleted. */
    private static void tidy() {
        Thread t = new Thread(() -> {
            for (Path dir : new Path[]{AdrRunFiles.runsDir(), AdrRunFiles.simDir()}) {
                try {
                    if (!Files.isDirectory(dir)) continue;
                    long now = System.currentTimeMillis();
                    List<Path> parts;
                    try (Stream<Path> s = Files.list(dir)) {
                        parts = s.filter(p -> AdrRunFiles.PART_NAME.matcher(p.getFileName().toString()).matches()).toList();
                    }
                    for (Path part : parts)
                        if (now - Files.getLastModifiedTime(part).toMillis() > 30 * 60_000L) AdrRunFiles.salvage(part);
                } catch (Throwable e) {
                    TeslaMaps.LOGGER.warn("[adr] tidying the runs folder failed", e);
                }
            }
        }, "teslamaps-adr-tidy");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public static void openFolder() {
        Path dir = AdrRunFiles.runsDir();
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        net.minecraft.util.Util.getPlatform().openPath(dir);
    }

    /** /adr: the local runs, newest first. */
    public static void listRuns() {
        List<Path> runs = AdrRunFiles.list(AdrRunFiles.runsDir());
        List<Path> sims = AdrRunFiles.list(AdrRunFiles.simDir());
        long bytes = 0;
        for (Path p : runs) { try { bytes += Files.size(p); } catch (Exception ignored) {} }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§7ADR: §f" + runs.size() + "§7 runs saved ("
                + String.format(Locale.ROOT, "%.1f", bytes / 1_000_000.0) + " MB)" + (sims.isEmpty() ? "" : ", §f" + sims.size() + "§7 ADS runs") + " ")
                .append(Component.literal("§b§n[open folder]").withStyle(s -> s
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/adr folder"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§7" + AdrRunFiles.runsDir()))))));
        for (int i = 0; i < Math.min(5, runs.size()); i++) {
            Path p = runs.get(i);
            long size = 0;
            try { size = Files.size(p); } catch (Exception ignored) {}
            mc.player.sendSystemMessage(Component.literal("§8 - §7" + p.getFileName() + " §8(" + String.format(Locale.ROOT, "%.1f", size / 1_000_000.0) + " MB)"));
        }
    }
}
