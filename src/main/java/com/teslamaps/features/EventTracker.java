package com.teslamaps.features;

// Ported from SkyHanni's TrackCommand.kt / TrackWorldCommand.kt / TrackSoundsCommand.kt / TrackParticlesCommand.kt

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;

/** /tmap tracksounds | trackparticles: record sounds or particles for a while, HUD list + world labels, clipboard at the end. */
public final class EventTracker {

    private record Entry(String id, double x, double y, double z, float a, float b, long time) {}
    private record Label(Vec3 pos, List<String> lines) {}

    public static final EventTracker SOUNDS = new EventTracker("sound", "sounds", "p", "v");
    public static final EventTracker PARTICLES = new EventTracker("particle", "particles", "c", "s");

    private final String name, plural, aName, bName;
    private final ConcurrentLinkedDeque<Entry> tracked = new ConcurrentLinkedDeque<>();
    public final Set<String> ignored = new HashSet<>();
    private boolean recording = false;
    private long cutOff = 0L;
    private long startTime = 0L;
    private long lastKeyToggle = 0L;
    private boolean keyWasDown = false;
    private List<String> display = List.of();
    private List<Label> labels = List.of();

    private static HudEditElement el;

    private EventTracker(String name, String plural, String aName, String bName) {
        this.name = name;
        this.plural = plural;
        this.aName = aName;
        this.bName = bName;
    }

    public static void register() {
        el = HudEditElement.of("eventTracker", "Sound/Particle Tracker")
                .enabled(() -> SOUNDS.recording || PARTICLES.recording)
                .size(() -> 220, () -> 11 * 4)
                .preview((g, m, x, y, s) -> draw(g, x, y, s, List.of("§eTracking sounds",
                        "§3minecraft:entity.experience_orb.pickup §8p:0.61 §7v:0.10",
                        "§3minecraft:block.note_block.pling §8p:1.19 §7v:1.00")));
        Feature.of("EventTracker")
                .when(EventTracker::used)
                .tick(EventTracker::tickAll)
                .world(EventTracker::renderWorld)
                .hud("eventtracker", EventTracker::renderHud);
    }

    private static boolean used() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        return SOUNDS.recording || PARTICLES.recording || c.trackSoundsKey != -1 || c.trackParticlesKey != -1;
    }

    public boolean active() {
        return System.currentTimeMillis() < cutOff;
    }

    public static void onSound(SoundInstance s) {
        if (!SOUNDS.active()) return;
        String id;
        float pitch, vol;
        try {
            id = s.getIdentifier().toString();
            pitch = s.getPitch();
            vol = s.getVolume();
        } catch (Throwable t) { return; }
        if (id.isEmpty()) return;
        if (id.equals("minecraft:entity.player.hurt") && pitch == 0f && vol == 0f) return;
        if (Minecraft.getInstance().player == null) return;
        SOUNDS.add(id, s.getX(), s.getY(), s.getZ(), pitch, vol);
    }

    public static void onParticle(ClientboundLevelParticlesPacket p) {
        if (!PARTICLES.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) return;
        var key = BuiltInRegistries.PARTICLE_TYPE.getKey(p.particle().getType());
        if (key == null) return;
        PARTICLES.add(key.toString(), p.x(), p.y(), p.z(), p.count(), p.xMaxSpeed());
    }

    private void add(String id, double x, double y, double z, float a, float b) {
        if (ignored.contains(id)) return;
        tracked.addFirst(new Entry(id, x, y, z, a, b, System.currentTimeMillis()));
    }

    public void start(Integer seconds) {
        if (recording) {
            chat("§cStill tracking §e" + plural + "§c, use §e/tmap track" + plural + " end §cfirst");
            return;
        }
        recording = true;
        tracked.clear();
        startTime = System.currentTimeMillis();
        if (seconds != null) {
            cutOff = startTime + seconds * 1000L;
            chat("§eNow tracking §a" + plural + " §efor §b" + seconds + "s");
        } else {
            cutOff = Long.MAX_VALUE;
            chat("§eNow tracking §a" + plural + " §euntil ended");
        }
    }

    public void end() {
        if (!recording) chat("§cNothing to end");
        else cutOff = System.currentTimeMillis();
    }

    public void toggleIgnore(String id) {
        if (ignored.remove(id)) chat("§cRemoved " + name + " §e" + id + " §cfrom the ignore list");
        else {
            ignored.add(id);
            chat("§aAdded " + name + " §e" + id + " §ato the ignore list");
        }
    }

    private static void tickAll() {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        SOUNDS.tick(c.trackSoundsKey, c);
        PARTICLES.tick(c.trackParticlesKey, c);
    }

    private void tick(int key, TeslaMapsConfig c) {
        Minecraft mc = Minecraft.getInstance();
        boolean down = key != -1 && mc.gui.screen() == null && KeyNames.isDown(key);
        long now = System.currentTimeMillis();
        if (down && !keyWasDown && now - lastKeyToggle >= 1000) {
            if (recording) end();
            else start(null);
            lastKeyToggle = now;
        }
        keyWasDown = down;
        if (!recording) return;

        long cutoff = now - (long) (c.eventTrackerWindow * 1000L);
        List<Entry> recent = new ArrayList<>();
        for (Entry e : tracked) {
            if (e.time <= cutoff) break;
            recent.add(e);
        }
        int max = Math.max(1, c.eventTrackerMaxLines);
        List<String> lines = new ArrayList<>();
        for (int i = Math.min(recent.size(), max) - 1; i >= 0; i--) {
            Entry e = recent.get(i);
            lines.add("§3" + e.id + " §8" + aName + ":" + fmt(e.a) + " §7" + bName + ":" + fmt(e.b));
        }
        display = lines;

        Map<Vec3, List<Entry>> byPos = new LinkedHashMap<>();
        for (Entry e : recent) byPos.computeIfAbsent(new Vec3(e.x, e.y, e.z), k -> new ArrayList<>()).add(e);
        List<Label> out = new ArrayList<>();
        for (Map.Entry<Vec3, List<Entry>> g : byPos.entrySet()) {
            List<Entry> es = g.getValue();
            List<String> ls = new ArrayList<>();
            if (es.size() == 1) {
                Entry e = es.get(0);
                ls.add("§7§l" + e.id);
                ls.add(worldLine(e));
            } else {
                ls.add("§e" + es.size() + " " + plural);
                Map<String, Integer> counts = new LinkedHashMap<>();
                for (Entry e : es) counts.merge(e.id, 1, Integer::sum);
                counts.forEach((id, n) -> ls.add("§7§l" + id + " §7(§e" + n + "§7)"));
            }
            out.add(new Label(g.getKey(), ls));
        }
        labels = out;

        if (now - cutOff > 100) finish();
    }

    private String worldLine(Entry e) {
        if (this == SOUNDS) {
            String vc = e.b <= 0.25f ? "§c" : e.b <= 0.5f ? "§6" : "§a";
            return "§7P: §e" + fmt(e.a) + " §7V: " + vc + fmt(e.b);
        }
        return "§7C: §e" + (int) e.a + " §7S: §a" + fmt(e.b);
    }

    private void finish() {
        StringBuilder sb = new StringBuilder();
        List<Entry> all = new ArrayList<>(tracked);
        for (int i = all.size() - 1; i >= 0; i--) {
            Entry e = all.get(i);
            sb.append("Time: ").append(e.time - startTime)
                    .append(' ').append(e.id)
                    .append(String.format(Locale.ROOT, " x=%.2f y=%.2f z=%.2f %s=%s %s=%s",
                            e.x, e.y, e.z, aName, fmt(e.a), bName, fmt(e.b)))
                    .append('\n');
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(sb.toString());
        chat("§b" + all.size() + " §a" + plural + " §ecopied into the clipboard!");
        tracked.clear();
        display = List.of();
        labels = List.of();
        recording = false;
    }

    public void reset() {
        recording = false;
        cutOff = 0L;
        tracked.clear();
        display = List.of();
        labels = List.of();
    }

    private static void renderWorld(PoseStack matrices, Vec3 cam) {
        SOUNDS.drawLabels(matrices, cam);
        PARTICLES.drawLabels(matrices, cam);
    }

    private void drawLabels(PoseStack matrices, Vec3 cam) {
        if (!recording) return;
        for (Label l : labels) {
            double off = 0;
            for (String line : l.lines) {
                ESPRenderer.drawText(matrices, line, l.pos.add(0, -off, 0), 0.8f, cam);
                off += 0.2;
            }
        }
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (el == null || !SOUNDS.recording && !PARTICLES.recording) return;
        List<String> lines = new ArrayList<>();
        if (SOUNDS.recording) { lines.add("§eTracking sounds"); lines.addAll(SOUNDS.display); }
        if (PARTICLES.recording) { lines.add("§eTracking particles"); lines.addAll(PARTICLES.display); }
        if (lines.isEmpty()) return;
        draw(g, el.x(), el.y(), el.scale(), lines);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int y, float scale, List<String> lines) {
        Minecraft mc = Minecraft.getInstance();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        int w = 0;
        for (String s : lines) w = Math.max(w, mc.font.width(s));
        com.teslamaps.render.gui.HudStyle.panel(g, -2, -2, w + 4, lines.size() * 11 + 2, 2);
        int row = 0;
        for (String s : lines) {
            g.text(mc.font, s, 0, row, 0xFFFFFFFF);
            row += 11;
        }
        pose.popMatrix();
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> soundsCommand() {
        return command("tracksounds", SOUNDS, () -> BuiltInRegistries.SOUND_EVENT.keySet());
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> particlesCommand() {
        return command("trackparticles", PARTICLES, () -> BuiltInRegistries.PARTICLE_TYPE.keySet());
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> command(String lit, EventTracker t,
            java.util.function.Supplier<Set<net.minecraft.resources.Identifier>> ids) {
        return ClientCommands.literal(lit)
                .executes(c -> { t.start(null); return 1; })
                .then(ClientCommands.literal("end").executes(c -> { t.end(); return 1; }))
                .then(ClientCommands.literal("ignore")
                        .then(ClientCommands.literal("list").executes(c -> {
                            chat(t.ignored.isEmpty() ? "§cNo ignored " + t.name + " types"
                                    : "§aIgnored " + t.name + " types: §e" + String.join(", ", t.ignored));
                            return 1;
                        }))
                        .then(ClientCommands.literal("clear").executes(c -> {
                            t.ignored.clear();
                            chat("§aCleared all ignored " + t.name + " types");
                            return 1;
                        }))
                        .then(ClientCommands.argument("id", StringArgumentType.greedyString())
                                .suggests((c, b) -> {
                                    String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                    for (var id : ids.get()) {
                                        String s = id.toString();
                                        if (s.contains(rem) && !s.equals(rem)) b.suggest(s);
                                    }
                                    return b.buildFuture();
                                })
                                .executes(c -> {
                                    String id = StringArgumentType.getString(c, "id").trim();
                                    if (!id.contains(":")) id = "minecraft:" + id;
                                    t.toggleIgnore(id);
                                    return 1;
                                })))
                .then(ClientCommands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                        .executes(c -> { t.start(IntegerArgumentType.getInteger(c, "seconds")); return 1; }));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> playSoundCommand() {
        return ClientCommands.literal("playsound")
                .then(ClientCommands.argument("id", StringArgumentType.string())
                        .suggests((c, b) -> {
                            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                            for (var id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                                String s = id.getNamespace().equals("minecraft") ? id.getPath() : "\"" + id + "\"";
                                if (s.contains(rem) && !s.equals(rem)) b.suggest(s);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> playSound(StringArgumentType.getString(c, "id"), 1f, 1f))
                        .then(ClientCommands.argument("pitch", FloatArgumentType.floatArg(0f, 2f))
                                .executes(c -> playSound(StringArgumentType.getString(c, "id"),
                                        FloatArgumentType.getFloat(c, "pitch"), 1f))
                                .then(ClientCommands.argument("volume", FloatArgumentType.floatArg(0f, 10f))
                                        .executes(c -> playSound(StringArgumentType.getString(c, "id"),
                                                FloatArgumentType.getFloat(c, "pitch"), FloatArgumentType.getFloat(c, "volume"))))));
    }

    private static int playSound(String raw, float pitch, float volume) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(raw.trim());
        if (id == null) { chat("§cInvalid sound id"); return 0; }
        var event = net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id);
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, pitch, volume));
        return 1;
    }

    private static String fmt(float f) {
        return Float.toString(Math.round(f * 100f) / 100f);
    }

    private static void chat(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
