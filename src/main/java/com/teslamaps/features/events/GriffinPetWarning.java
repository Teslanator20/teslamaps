package com.teslamaps.features.events;

// Ported from SkyHanni's GriffinPetWarning.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.pets.PetTracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

public final class GriffinPetWarning {

    private static int ticks;
    private static long lastWarn;

    private GriffinPetWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("GriffinPetWarning").section("Events").when(() -> cfg().griffinPetWarning)
                .tick(GriffinPetWarning::tick)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Griffin Pet Warning", () -> c.griffinPetWarning, v -> c.griffinPetWarning = v)
                            .desc("Title \"Griffin Pet!\" when you hold a Diana spade in the Hub without a Griffin pet out. "
                                    + "Reads the pet from the tab list Pet widget (needs the Skills section on).")
                            .tags("diana", "mythological", "spade", "griffin", "pet");
                });
    }

    static boolean isSpade(String id) {
        return id.equals("ANCESTRAL_SPADE") || id.equals("ARCHAIC_SPADE") || id.equals("DEIFIC_SPADE");
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (!SkyblockUtils.isInArea("Hub")) return;
        if (!isSpade(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return;
        String pet = PetTracker.displayName();
        if (pet != null && pet.equalsIgnoreCase("Griffin")) return;
        if (pet == null && PetTracker.widgetMissing()) return;
        long now = System.currentTimeMillis();
        if (now - lastWarn < 30_000) return;
        lastWarn = now;
        mc.gui.hud.setTitle(Component.literal("§cGriffin Pet!"));
        mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] Reminder to use a Griffin pet for Mythological Ritual! §a[Open Pets]")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/pets"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to open the pets menu")))));
    }
}
