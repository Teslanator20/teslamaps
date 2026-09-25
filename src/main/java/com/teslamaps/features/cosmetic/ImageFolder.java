package com.teslamaps.features.cosmetic;

import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** A folder under config/teslamaps with pickable image files. */
public final class ImageFolder {

    public final Path dir;
    private final String[] extensions;

    public ImageFolder(String name, String... extensions) {
        this.dir = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve(name);
        this.extensions = extensions;
    }

    public List<String> list() {
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                        .filter(this::accepted).sorted(String.CASE_INSENSITIVE_ORDER).toList();
            }
        } catch (Exception e) {
            return List.of();
        }
    }

    private boolean accepted(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) if (n.endsWith(ext)) return true;
        return false;
    }

    /** The selected file, or the first one when the selection is gone. Null when the folder is empty. */
    public Path resolve(String selected) {
        if (selected != null && !selected.isEmpty()) {
            Path p = dir.resolve(selected);
            if (Files.isRegularFile(p) && p.getParent().equals(dir)) return p;
        }
        List<String> all = list();
        return all.isEmpty() ? null : dir.resolve(all.get(0));
    }

    public String cycle(String current, int step) {
        List<String> all = list();
        if (all.isEmpty()) {
            message("§cNo files in " + dir.getFileName() + " - open the folder and drop some in");
            return current;
        }
        int i = 0;
        for (int k = 0; k < all.size(); k++) if (all.get(k).equalsIgnoreCase(current)) i = k;
        String next = all.get(Math.floorMod(i + step, all.size()));
        message("§7Selected §f" + next);
        return next;
    }

    public void open() {
        try {
            Files.createDirectories(dir);
            net.minecraft.util.Util.getPlatform().openFile(dir.toFile());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Could not open {}", dir, e);
        }
    }

    public static void message(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + text));
    }
}
