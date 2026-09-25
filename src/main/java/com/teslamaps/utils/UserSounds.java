package com.teslamaps.utils;

// GUI-020, after Bax's CustomSoundPlayer: .ogg/.wav files from config/teslamaps/sounds
import com.mojang.blaze3d.audio.SoundBuffer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.valueproviders.ConstantFloat;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class UserSounds {

    public static final String NAMESPACE = "teslamaps_user";
    public static final String PREFIX = "FILE:";
    public static final String ID_PREFIX = "ID:";

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("sounds");
    private static final Map<String, Path> FILES = new HashMap<>();
    private static final Map<String, WeighedSoundEvents> EVENTS = new HashMap<>();
    private static final Map<String, CompletableFuture<SoundBuffer>> BUFFERS = new HashMap<>();
    private static long scanned;

    private UserSounds() {}

    public static void register() {
        com.teslamaps.registry.Feature.of("UserSounds")
                .config("Sounds", b -> {
                    var c = b.config();
                    b.label("Sound Picker");
                    b.toggle("Own Sound Files", () -> c.userSoundFolder, v -> c.userSoundFolder = v)
                            .desc("Every .ogg / .wav in config/teslamaps/sounds shows up in each sound picker (FILE:name). "
                                    + "The picker also gets \"Sound ID\" to type any vanilla sound id and \"Folder\" to open the folder.")
                            .tags("custom sound", "ogg", "wav", "sound file");
                    b.button("  Open Sound Folder", () -> {
                        reload();
                        net.minecraft.util.Util.getPlatform().openPath(DIR);
                    });
                });
    }

    public static Path dir() { return DIR; }

    /** Picker keys of every file in the folder, rescanned at most every 2 s. */
    public static synchronized List<String> keys() {
        long now = System.currentTimeMillis();
        if (now - scanned > 2000) scan();
        scanned = now;
        List<String> out = new ArrayList<>();
        for (Path p : FILES.values()) out.add(PREFIX + p.getFileName());
        out.sort(String::compareToIgnoreCase);
        return out;
    }

    private static void scan() {
        FILES.clear();
        try {
            Files.createDirectories(DIR);
            try (var s = Files.list(DIR)) {
                s.filter(Files::isRegularFile).forEach(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (n.endsWith(".ogg") || n.endsWith(".wav")) FILES.put(idPath(p.getFileName().toString()), p);
                });
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("user sounds: {}", e.toString());
        }
    }

    private static String idPath(String fileName) {
        StringBuilder sb = new StringBuilder();
        for (char ch : fileName.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' ? ch : '_');
        }
        return sb.toString();
    }

    /** Sound id for a {@code FILE:name} key. */
    public static Identifier id(String key) {
        String name = key.substring(PREFIX.length());
        synchronized (UserSounds.class) {
            String p = idPath(name);
            if (!FILES.containsKey(p)) {
                scan();
                scanned = System.currentTimeMillis();
            }
            return Identifier.fromNamespaceAndPath(NAMESPACE, p);
        }
    }

    /** SoundManager hook: our ids resolve to a one-sound event pointing at the file. */
    public static synchronized WeighedSoundEvents event(Identifier id) {
        return EVENTS.computeIfAbsent(id.getPath(), p -> {
            WeighedSoundEvents w = new WeighedSoundEvents(id, null);
            w.addSound(new Sound(id, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, false, false, 16));
            return w;
        });
    }

    /** SoundBufferLibrary hook, {@code path} is {@code teslamaps_user:sounds/<id>.ogg}. */
    public static synchronized CompletableFuture<SoundBuffer> buffer(Identifier path) {
        String p = path.getPath();
        if (p.startsWith("sounds/")) p = p.substring(7);
        if (p.endsWith(".ogg")) p = p.substring(0, p.length() - 4);
        String key = p;
        return BUFFERS.computeIfAbsent(key, k -> {
            Path file = FILES.get(k);
            if (file == null) {
                scan();
                file = FILES.get(k);
            }
            Path f = file;
            CompletableFuture<SoundBuffer> fut = CompletableFuture.supplyAsync(() -> load(f), net.minecraft.util.Util.nonCriticalIoPool());
            fut.whenComplete((b, e) -> { if (e != null) forget(k); });
            return fut;
        });
    }

    private static synchronized void forget(String key) {
        BUFFERS.remove(key);
    }

    private static SoundBuffer load(Path file) {
        if (file == null) throw new java.util.concurrent.CompletionException(new java.io.FileNotFoundException("user sound"));
        try {
            if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".wav")) {
                try (AudioInputStream raw = AudioSystem.getAudioInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
                    AudioFormat src = raw.getFormat();
                    AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                            src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
                    try (AudioInputStream in = AudioSystem.getAudioInputStream(pcm, raw)) {
                        byte[] data = in.readAllBytes();
                        ByteBuffer buf = org.lwjgl.BufferUtils.createByteBuffer(data.length);
                        buf.put(data).flip();
                        return new SoundBuffer(buf, pcm);
                    }
                }
            }
            try (InputStream in = Files.newInputStream(file); JOrbisAudioStream s = new JOrbisAudioStream(in)) {
                ByteBuffer buf = s.readAll();
                return new SoundBuffer(buf, s.getFormat());
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("user sound {}: {}", file.getFileName(), e.toString());
            throw new java.util.concurrent.CompletionException(e);
        }
    }

    /** Drops cached buffers so an edited file is read again. */
    public static synchronized void reload() {
        BUFFERS.clear();
        scan();
        scanned = System.currentTimeMillis();
    }
}
