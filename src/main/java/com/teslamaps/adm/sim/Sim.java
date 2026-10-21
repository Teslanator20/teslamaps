package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Sim.kt

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Sim {
    private Sim() {}

    public static final String TAG = "p3sim";

    public static ServerLevel level() {
        ServerLevel l = SimServer.level();
        if (l == null) throw new IllegalStateException("sim not running");
        return l;
    }

    public static ServerPlayer player() {
        ServerPlayer a = acting();
        return a != null ? a : SimServer.player();
    }

    private static boolean onServerThread() {
        MinecraftServer s = SimServer.server;
        return s != null && s.isSameThread();
    }

    public static ServerPlayer acting() {
        ServerPlayer a = actor;
        return a != null && onServerThread() ? a : null;
    }

    private static ServerPlayer actor = null;
    private static boolean all = false;

    public static boolean toAll() { return all && onServerThread(); }

    public static List<ServerPlayer> players() {
        MinecraftServer s = SimServer.server;
        if (s == null) return new ArrayList<>();
        return new ArrayList<>(s.getPlayerList().getPlayers());
    }

    public static ServerPlayer host() { return SimServer.player(); }

    public static boolean isHost(ServerPlayer p) { return p != null && p == SimServer.player(); }

    public static <T> T asPlayer(ServerPlayer p, Supplier<T> run) { return asPlayer(p, false, run); }

    public static <T> T asPlayer(ServerPlayer p, boolean toAll, Supplier<T> run) {
        ServerPlayer was = actor;
        boolean wasAll = all;
        actor = p;
        all = toAll;
        try {
            return run.get();
        } finally {
            actor = was;
            all = wasAll;
        }
    }

    public static void asPlayer(ServerPlayer p, Runnable run) { asPlayer(p, false, run); }

    public static void asPlayer(ServerPlayer p, boolean toAll, Runnable run) {
        asPlayer(p, toAll, () -> { run.run(); return null; });
    }

    public static void enterPacket(ServerPlayer p) {
        MinecraftServer s = SimServer.server;
        if (s == null) return;
        if (!s.isSameThread() || p.level().getServer() != s) return;
        actor = p;
        all = false;
    }

    public static void leavePacket(ServerPlayer p) {
        if (actor == p && onServerThread()) actor = null;
    }

    public static void clearActor() {
        actor = null;
        all = false;
    }

    public static void forEachPlayer(Consumer<ServerPlayer> run) {
        for (ServerPlayer p : players()) asPlayer(p, () -> run.accept(p));
    }

    public static <T> T everyone(Supplier<T> run) {
        boolean wasAll = all;
        all = true;
        try {
            return run.get();
        } finally {
            all = wasAll;
        }
    }

    public static void everyone(Runnable run) { everyone(() -> { run.run(); return null; }); }

    private static List<ServerPlayer> audience() {
        ServerPlayer a = acting();
        if (a != null && !toAll()) return List.of(a);
        return players();
    }

    private static boolean guarded = false;

    public static void guardBlocks() {
        if (guarded) return;
        guarded = true;
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, p, pos, state, be) -> !SimServer.isSimLevel(level));
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((p, level, hand, hit) ->
                SimServer.isSimLevel(level) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    public static String me() {
        ServerPlayer p = player();
        return p != null ? p.getGameProfile().name() : "You";
    }

    public static String hostName() {
        ServerPlayer p = host();
        return p != null ? p.getGameProfile().name() : "You";
    }

    public static boolean isPlayerName(String name) {
        if (name == null) return false;
        for (ServerPlayer p : players()) if (p.getGameProfile().name().equals(name)) return true;
        return false;
    }

    public static void chat(String text) {
        List<ServerPlayer> to = audience();
        for (ServerPlayer p : to) if (isHost(p)) { Recorder.event(text); break; }
        for (ServerPlayer p : to) p.sendSystemMessage(Component.literal(text));
    }

    public static void chatStyled(String text) {
        List<ServerPlayer> to = audience();
        for (ServerPlayer p : to) if (isHost(p)) { Recorder.event(text); break; }
        for (ServerPlayer p : to) p.sendSystemMessage(legacy(text));
    }

    public static void chatOthers(String text) {
        ServerPlayer a = acting();
        if (a == null) return;
        for (ServerPlayer p : players()) {
            if (p == a) continue;
            if (isHost(p)) Recorder.event(text);
            p.sendSystemMessage(Component.literal(text));
        }
    }

    public static void note(String text) { chat("§8[§6ADS§8] §7" + text); }

    public static Component legacy(String text) {
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                if (f != null) {
                    if (!sb.isEmpty()) { out.append(Component.literal(sb.toString()).withStyle(style)); sb.setLength(0); }
                    if (f == ChatFormatting.RESET) style = Style.EMPTY;
                    else if (TextColor.fromLegacyFormat(f) != null) style = Style.EMPTY.withColor(f);
                    else style = style.applyFormat(f);
                    i += 2;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        if (!sb.isEmpty()) out.append(Component.literal(sb.toString()).withStyle(style));
        return out;
    }

    public static void boss(String name, String line) { boss(name, line, null, true); }

    public static void boss(String name, String line, Vec3 at) { boss(name, line, at, true); }

    public static void boss(String name, String line, Vec3 at, boolean stand) {
        everyone(() -> {
            chat("§4[BOSS] " + name + "§r§c: " + line);
            if (stand) BossWither.speak(name, line);
            sound(SoundEvents.WITHER_AMBIENT, 5f, 1.19f, at);
        });
    }

    public static void title(String title) { title(title, "", 0, 30, 5); }

    public static void title(String title, String sub) { title(title, sub, 0, 30, 5); }

    public static void title(String title, String sub, int fadeIn) { title(title, sub, fadeIn, 30, 5); }

    public static void title(String title, String sub, int fadeIn, int stay) { title(title, sub, fadeIn, stay, 5); }

    public static void title(String title, String sub, int fadeIn, int stay, int fadeOut) {
        List<ServerPlayer> to = audience();
        for (ServerPlayer p : to) if (isHost(p)) { Recorder.event("title: " + title + " | " + sub); break; }
        for (ServerPlayer p : to) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
            p.connection.send(new ClientboundSetTitleTextPacket(legacy(title)));
            p.connection.send(new ClientboundSetSubtitleTextPacket(legacy(sub)));
        }
    }

    public static void sound(SoundEvent sound) { sound(sound, 1f, 1f, null, SoundSource.MASTER); }

    public static void sound(SoundEvent sound, float volume) { sound(sound, volume, 1f, null, SoundSource.MASTER); }

    public static void sound(SoundEvent sound, float volume, float pitch) { sound(sound, volume, pitch, null, SoundSource.MASTER); }

    public static void sound(SoundEvent sound, float volume, float pitch, Vec3 at) { sound(sound, volume, pitch, at, SoundSource.MASTER); }

    public static void sound(SoundEvent sound, float volume, float pitch, Vec3 at, SoundSource source) {
        ServerLevel l = level();
        if (at != null) {
            l.playSound(null, at.x, at.y, at.z, sound, source, volume, pitch);
            return;
        }
        Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound);
        long seed = l.getRandom().nextLong();
        for (ServerPlayer p : audience())
            p.connection.send(new ClientboundSoundPacket(holder, source, p.getX(), p.getY(), p.getZ(), volume, pitch, seed));
    }

    public static void sound(Holder<SoundEvent> sound) { sound(sound.value(), 1f, 1f, null, SoundSource.MASTER); }

    public static void sound(Holder<SoundEvent> sound, float volume) { sound(sound.value(), volume, 1f, null, SoundSource.MASTER); }

    public static void sound(Holder<SoundEvent> sound, float volume, float pitch) { sound(sound.value(), volume, pitch, null, SoundSource.MASTER); }

    public static void sound(Holder<SoundEvent> sound, float volume, float pitch, Vec3 at) { sound(sound.value(), volume, pitch, at, SoundSource.MASTER); }

    public static void sound(Holder<SoundEvent> sound, float volume, float pitch, Vec3 at, SoundSource source) { sound(sound.value(), volume, pitch, at, source); }

    public static <T extends Entity> T spawn(T e) {
        e.addTag(TAG);
        level().addFreshEntity(e);
        return e;
    }

    public static void clearEntities() {
        ServerLevel l = level();
        List<Entity> all = new ArrayList<>();
        for (Entity e : l.getAllEntities()) if (e.entityTags().contains(TAG)) all.add(e);
        for (Entity e : all) e.discard();
    }

    public static void tp(ServerPlayer p, double x, double y, double z) { tp(p, x, y, z, null, null); }

    public static void tp(ServerPlayer p, double x, double y, double z, Float yaw) { tp(p, x, y, z, yaw, null); }

    public static void tp(ServerPlayer p, double x, double y, double z, Float yaw, Float pitch) {
        p.teleportTo(level(), x, y, z, Set.<Relative>of(), yaw != null ? yaw : p.getYRot(), pitch != null ? pitch : p.getXRot(), false);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0;
    }

    public static AABB box(double x0, double y0, double z0, double x1, double y1, double z1) { return new AABB(x0, y0, z0, x1, y1, z1); }

    public static BlockPos pos(int x, int y, int z) { return new BlockPos(x, y, z); }

    public static void command(String cmd) {
        MinecraftServer s = SimServer.server;
        if (s == null) return;
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(), cmd);
    }
}
