package com.teslamaps.features;

// Ported from Skyblocker's skyblock/dungeon/DungeonTextures.java (built-in pack, tripwire models)
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;

public final class TripwireRecolor {

    private static final String PACK = "recolored_tripwire";

    private TripwireRecolor() {}

    public static void register() {
        FabricLoader.getInstance().getModContainer("teslamaps").ifPresent(mod ->
                ResourceLoader.registerBuiltinPack(Identifier.fromNamespaceAndPath("teslamaps", PACK), mod,
                        Component.literal("TeslaMaps Red Tripwires"), PackActivationType.NORMAL));
        Feature.of("TripwireRecolor")
                .config("Render", b -> {
                    b.label("Dungeon Textures");
                    b.toggle("Red Tripwires", TripwireRecolor::enabled, TripwireRecolor::set)
                            .desc("Built-in resource pack: tripwires become thick red strips, easy to spot in trap rooms. Reloads resources.");
                });
    }

    private static String packId() {
        PackRepository repo = Minecraft.getInstance().getResourcePackRepository();
        for (String id : repo.getAvailableIds()) if (id.endsWith(PACK)) return id;
        return null;
    }

    private static boolean enabled() {
        String id = packId();
        return id != null && Minecraft.getInstance().getResourcePackRepository().getSelectedIds().contains(id);
    }

    private static void set(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        String id = packId();
        if (id == null || on == enabled()) return;
        PackRepository repo = mc.getResourcePackRepository();
        if (on) repo.addPack(id);
        else repo.removePack(id);
        mc.options.updateResourcePacks(repo);
    }
}
