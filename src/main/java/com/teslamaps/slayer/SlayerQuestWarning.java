package com.teslamaps.slayer;

// Ported from SkyHanni's SlayerQuestWarning.kt; areas from the scoreboard instead of SkyHanni's area graph
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;

public final class SlayerQuestWarning {

    private static final String[] DIANA = {"Minos", "Gaia", "Minotaur", "Lynx", "Inquisitor", "Bagheera", "Azrael"};

    private static Feature feature;
    private static long lastWarning;
    private static boolean failed;

    private SlayerQuestWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("SlayerQuestWarning").section("Combat").when(() -> cfg().slayerQuestWarning)
                .serverChat(SlayerQuestWarning::onChat)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Slayer Quest Warning", () -> c.slayerQuestWarning, v -> c.slayerQuestWarning = v)
                            .desc("Hitting slayer mobs without an active quest shows \"New Slayer Quest!\", "
                                    + "the wrong quest for the area \"Wrong Slayer!\". 10 s cooldown.")
                            .tags("slayer", "quest", "warning", "wrong");
                    b.toggle("  Show Title", () -> c.slayerQuestWarningTitle, v -> c.slayerQuestWarningTitle = v);
                });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && feature != null && feature.active()) onAttack(entity);
            return InteractionResult.PASS;
        });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.equals("SLAYER QUEST FAILED!")) failed = true;
        else if (t.equals("SLAYER QUEST STARTED!")) failed = false;
    }

    private static void onAttack(Entity entity) {
        if (entity instanceof ArmorStand || !SkyblockUtils.isInSkyblock()) return;
        String area = areaType(entity);
        if (area == null) return;
        for (ArmorStand s : NametagMobs.namedStands(entity, 1.5)) {
            String n = s.getName().getString();
            for (String d : DIANA) if (n.contains(d)) return;
        }
        String key = SlayerQuest.key();
        if (key == null) {
            warn("New Slayer Quest!", failed ? "Start a new slayer quest! The old one has failed."
                    : "Start a new slayer quest! You have no quest active.");
        } else if (!key.startsWith(area + "_")) {
            warn("Wrong Slayer!", "Wrong slayer selected! You have " + SlayerQuest.boss()
                    + " selected and you are in a " + name(area) + " area!");
        }
    }

    private static String areaType(Entity e) {
        String island = SkyblockUtils.getArea();
        String sub = subArea();
        return switch (island) {
            case "Hub" -> e instanceof Zombie && (sub.equals("Graveyard") || sub.equals("Coal Mine")) ? "REVENANT"
                    : e instanceof Wolf && sub.equals("Ruins") ? "SVEN" : null;
            case "The Park" -> e instanceof Wolf && sub.equals("Howling Cave") ? "SVEN" : null;
            case "Spider's Den" -> e instanceof Spider ? "TARANTULA" : null;
            case "The End" -> e instanceof Enderman ? "VOIDGLOOM" : null;
            case "Crimson Isle" -> e instanceof Blaze && (sub.equals("Burning Desert") || sub.equals("Smoldering Tomb")) ? "DEMONLORD" : null;
            default -> null;
        };
    }

    private static String name(String type) {
        return switch (type) {
            case "REVENANT" -> "Revenant Horror";
            case "TARANTULA" -> "Tarantula Broodfather";
            case "SVEN" -> "Sven Packmaster";
            case "VOIDGLOOM" -> "Voidgloom Seraph";
            default -> "Inferno Demonlord";
        };
    }

    private static String subArea() {
        for (String line : ScoreboardUtils.getScoreboardLines()) {
            String t = ScoreboardUtils.cleanLine(line);
            if (t.startsWith("⏣") || t.startsWith("ф")) return t.substring(1).trim();
        }
        return "";
    }

    private static void warn(String title, String chat) {
        long now = System.currentTimeMillis();
        if (now - lastWarning < 10_000) return;
        lastWarning = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §e" + chat));
        if (cfg().slayerQuestWarningTitle) {
            mc.gui.hud.setTimes(0, 40, 5);
            mc.gui.hud.setTitle(Component.literal("§e" + title));
        }
    }
}
