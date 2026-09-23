package com.teslamaps.utils;

import com.teslamaps.TeslaMaps;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * A json file that lives once per SkyBlock profile (sbprofiles/<uuid>/<profile>/<name>).
 * Until a profile is known the old shared file is used; the first profile seen takes it over,
 * the original stays as {@code <name>.migrated}.
 */
public final class ProfiledFile {

    private final String name;
    private final Path legacy;
    private int loadedVersion = Integer.MIN_VALUE;

    public ProfiledFile(String name, Path legacy) {
        this.name = name;
        this.legacy = legacy;
    }

    /** True once the active profile differs from the one {@link #path()} was last taken for. */
    public boolean stale() {
        return loadedVersion != SbProfile.version();
    }

    /** Path for the active profile. Marks the file as loaded for it. */
    public Path path() {
        loadedVersion = SbProfile.version();
        Path dir = SbProfile.dir();
        if (dir == null) return legacy;
        Path p = dir.resolve(name);
        migrate(p);
        return p;
    }

    private void migrate(Path p) {
        if (legacy == null || !Files.exists(legacy) || Files.exists(p)) return;
        try {
            Files.createDirectories(p.getParent());
            Files.copy(legacy, p);
            Files.move(legacy, legacy.resolveSibling(legacy.getFileName() + ".migrated"), StandardCopyOption.REPLACE_EXISTING);
            TeslaMaps.LOGGER.info("Moved {} to profile {}", legacy.getFileName(), SbProfile.key());
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to migrate " + legacy, e);
        }
    }
}
