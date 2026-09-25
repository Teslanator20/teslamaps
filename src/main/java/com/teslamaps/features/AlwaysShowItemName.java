package com.teslamaps.features;

// Bax / Skysoft SelectedItemNameMixin
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;

public final class AlwaysShowItemName {

    private AlwaysShowItemName() {}

    public static void register() {
        Feature.of("AlwaysShowItemName").config("Interface", b -> {
            b.label("Hotbar");
            b.toggle("Always Show Item Name", () -> TeslaMapsConfig.get().alwaysShowItemName,
                            v -> TeslaMapsConfig.get().alwaysShowItemName = v)
                    .desc("The held item's name above the hotbar never fades out.");
        });
    }
}
