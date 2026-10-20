package com.teslamaps.adm.adr;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class AdmAdr {
    private AdmAdr() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    public static void register() {
        Feature.of("AdmAdr")
                .section(Adm.SECTION)
                .when(() -> cfg().admAdr)
                .tickAlways(Adr::tick)
                .world((pose, cam) -> Adr.frame())
                .config("ADM Recorder", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("ADR", () -> c.admAdr, v -> c.admAdr = v)
                            .desc("Records everything about each dungeon run (players, mobs, blocks, chat, rooms) into config/teslamaps/adm/adr/runs, on this computer only. /adr lists them.");
                    b.toggle("Private Runs", () -> c.admAdrPrivateRuns, v -> c.admAdrPrivateRuns = v)
                            .desc("ADM setting for uploaded runs. Runs are never uploaded here, so this changes nothing.");
                    b.toggle("Hide Private Chats", () -> c.admAdrHidePrivateChats, v -> c.admAdrHidePrivateChats = v)
                            .desc("Leaves private messages, guild, officer and co-op chat, friend requests and friends coming online out of recordings.");
                    b.toggle("Record ADS Runs", () -> c.admAdrRecordAds, v -> c.admAdrRecordAds = v)
                            .desc("Records your ADS full runs (the clear and the boss in the ADS world) into config/teslamaps/adm/adr/sim. Used to check ADS against real runs.");
                    b.toggle("Hide Party Chat", () -> c.admAdrHidePartyChat, v -> c.admAdrHidePartyChat = v)
                            .desc("Leaves party chat out of recordings.");
                    b.toggle("Saved Message", () -> c.admAdrSavedMessage, v -> c.admAdrSavedMessage = v)
                            .desc("Says \"saved run\" in chat, with the file's size, when a run's recording is saved.");
                    b.slider("Camera FPS", 20f, 160f, () -> (float) c.admAdrCameraFps,
                                    v -> c.admAdrCameraFps = Math.max(20, Math.min(160, Math.round(v / 10f) * 10)), 0)
                            .desc("How many times a second your view is saved (at most - never more than the game draws). Each 60 more adds about 3% to a run.");
                    b.button("Open Runs Folder", Adr::openFolder);
                });
        Feature.of("AdmAdrOff")
                .when(() -> !Adr.on() && Adr.dirty())
                .tickAlways(Adr::offTick);

        ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> PartyTracker.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> PartyTracker.onDisconnect());

        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (!(screen instanceof AbstractContainerScreen<?> cs) || Adr.session() == null) return;
            Adr.onScreenInit(cs);
            ScreenMouseEvents.afterMouseClick(screen).register((sc, click, consumed) -> {
                Adr.onScreenClick(cs, click.x(), click.y(), click.button());
                return consumed;
            });
            ScreenEvents.remove(screen).register(sc -> Adr.onScreenClose());
        });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            for (String name : new String[]{"adr", "ADR"}) {
                d.register(ClientCommands.literal(name)
                        .executes(x -> { Adr.listRuns(); return 1; })
                        .then(ClientCommands.literal("folder").executes(x -> { Adr.openFolder(); return 1; })));
            }
        });
    }
}
