package com.teslamaps.features;

// Ported from Skyblocker's HubGlowAdder / CrimsonGlowAdder (ZombieShootout, CatchAFish, DojoManager, Force/DisciplineTestHelper)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CarnivalDojoGlow {

    private static final AABB SHOOTOUT = new AABB(-102, 70, 43, -99, 76, 46);
    private static final AABB FISH = new AABB(-87, 65, 25, -68, 85, 53);
    private static final String YELLOW_FISH = "360f4f99c78adddeab276ebdf6ab9a0fcffabd49f0b543e1981ac7bd3d55b18a";
    private static final String DOJO_START = "[NPC] Master Tao: Ahhh, here we go! Let's get you into the Arena.";
    private static final Pattern TEST_OF = Pattern.compile("^Test of (\\w+) OBJECTIVES$");
    private static final Pattern FINISHED = Pattern.compile("^CHALLENGE (?:COMPLETED|FAILED)");
    private static final int WOOD = 0xA52A2A, IRON = 0xC0C0C0, GOLD = 0xFFD700, DIAMOND = 0x00FFFF;

    private static boolean inArena;
    private static String challenge = "";
    private static int ticks;
    private static boolean hub, crimson;

    private CarnivalDojoGlow() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CarnivalDojoGlow").section("ESP").legit().when(() -> cfg().carnivalDojoGlow)
                .tick(CarnivalDojoGlow::tick)
                .serverChat(CarnivalDojoGlow::onChat)
                .config("ESP", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Carnival & Dojo");
                    b.toggle("Carnival & Dojo Glow", () -> c.carnivalDojoGlow, v -> c.carnivalDojoGlow = v)
                            .desc("Glow for carnival and dojo targets.").legit();
                    b.toggle("  Zombie Shootout", () -> c.carnivalShootoutGlow, v -> c.carnivalShootoutGlow = v)
                            .desc("Zombies glow by chestplate: diamond cyan, gold yellow, iron silver, leather brown.");
                    b.toggle("  Catch a Fish", () -> c.carnivalFishGlow, v -> c.carnivalFishGlow = v)
                            .desc("Yellow fish glow.");
                    b.toggle("  Dojo", () -> c.dojoGlow, v -> c.dojoGlow = v)
                            .desc("Force: negative zombies glow red. Discipline: zombies matching your held sword glow in its color.");
                });
    }

    private static void onChat(String text) {
        String t = StringUtil.stripTrim(text);
        if (t.equals(DOJO_START)) {
            inArena = true;
            challenge = "";
            return;
        }
        if (!inArena) return;
        if (FINISHED.matcher(t).find()) {
            inArena = false;
            challenge = "";
            return;
        }
        if (!challenge.isEmpty()) return;
        Matcher m = TEST_OF.matcher(t);
        if (m.matches()) challenge = m.group(1);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks % 20 == 0) {
            String a = SkyblockUtils.getArea();
            hub = a.equalsIgnoreCase("Hub");
            crimson = a.equalsIgnoreCase("Crimson Isle");
            if (!crimson) { inArena = false; challenge = ""; }
        }
        TeslaMapsConfig c = cfg();
        BlockPos pp = mc.player.blockPosition();
        if (hub) {
            if (c.carnivalShootoutGlow && SHOOTOUT.contains(pp.getX() + 0.5, pp.getY() + 0.5, pp.getZ() + 0.5)) {
                for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(64))) {
                    int col = shootoutColor(z);
                    if (col != 0) EntityGlow.mark(z, col);
                }
            }
            if (c.carnivalFishGlow && FISH.contains(pp.getX() + 0.5, pp.getY() + 0.5, pp.getZ() + 0.5)) {
                for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, FISH)) {
                    if (YELLOW_FISH.equals(SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD)))) EntityGlow.mark(s, 0xFFFF00);
                }
            }
        }
        if (crimson && inArena && c.dojoGlow && (challenge.equals("Force") || challenge.equals("Discipline"))) {
            int held = challenge.equals("Discipline") ? swordColor(ItemUtil.skyblockId(mc.player.getMainHandItem())) : 0;
            String heldName = challenge.equals("Discipline") ? swordName(held) : null;
            if (challenge.equals("Discipline") && heldName == null) return;
            for (Zombie z : mc.level.getEntitiesOfClass(Zombie.class, mc.player.getBoundingBox().inflate(40))) {
                String name = nametag(z);
                if (name.isEmpty()) continue;
                if (challenge.equals("Force")) {
                    if (name.contains("-")) EntityGlow.mark(z, 0xFF0000);
                } else if (name.equals(heldName) || name.endsWith(" " + heldName)) {
                    EntityGlow.mark(z, held);
                }
            }
        }
    }

    private static String nametag(Zombie z) {
        List<ArmorStand> stands = z.level().getEntitiesOfClass(ArmorStand.class, z.getBoundingBox().inflate(0, 2, 0),
                e -> e.hasCustomName() && !e.isPassenger());
        if (stands.isEmpty()) return "";
        Entity e = stands.getFirst();
        return StringUtil.stripTrim(e.getCustomName().getString());
    }

    private static int shootoutColor(Zombie z) {
        Item chest = z.getItemBySlot(EquipmentSlot.CHEST).getItem();
        if (chest == Items.DIAMOND_CHESTPLATE) return DIAMOND;
        if (chest == Items.GOLDEN_CHESTPLATE) return GOLD;
        if (chest == Items.IRON_CHESTPLATE) return IRON;
        if (chest == Items.LEATHER_CHESTPLATE) return WOOD;
        return 0;
    }

    private static int swordColor(String id) {
        return switch (id) {
            case "WOOD_SWORD" -> WOOD;
            case "IRON_SWORD" -> IRON;
            case "GOLD_SWORD" -> GOLD;
            case "DIAMOND_SWORD" -> DIAMOND;
            default -> 0;
        };
    }

    private static String swordName(int color) {
        return switch (color) {
            case WOOD -> "Wood";
            case IRON -> "Iron";
            case GOLD -> "Gold";
            case DIAMOND -> "Diamond";
            default -> null;
        };
    }
}
