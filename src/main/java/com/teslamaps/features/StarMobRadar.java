package com.teslamaps.features;

// IllegalMap StarMobStuff.renderRadar / StarMob.js
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class StarMobRadar {

    public record Mob(double x, double z, float yaw, int icon, int color) {}

    public static final int ICON_NONE = 0, ICON_ZOMBIE = 1, ICON_SKELETON = 2, ICON_WITHER = 3;

    private static final List<Mob> mobs = new ArrayList<>();
    private static List<Mob> view = new ArrayList<>();
    private static int tickCounter = 0;
    private static ItemStack[] icons;

    private StarMobRadar() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("StarMobRadar").section("Map").legit().when(() -> cfg().starMobRadar)
                .tick(StarMobRadar::tick);
    }

    public static List<Mob> mobs() { return view; }

    public static ItemStack icon(int idx) {
        if (icons == null) {
            icons = new ItemStack[]{ItemStack.EMPTY, new ItemStack(Items.ZOMBIE_HEAD),
                    new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.WITHER_SKELETON_SKULL)};
        }
        return icons[idx];
    }

    public static boolean toggle() {
        TeslaMapsConfig c = cfg();
        c.starMobRadar = !c.starMobRadar;
        TeslaMapsConfig.save();
        return c.starMobRadar;
    }

    private static void tick() {
        if (++tickCounter % 4 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        mobs.clear();
        if (!DungeonManager.isInDungeon() || DungeonManager.isInBossArea() || mc.level == null) {
            publish();
            return;
        }
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String name = StringUtil.strip(as.getName().getString());
            int star = name.indexOf('✯');
            if (star < 0) continue;
            String mob = name.substring(star + 1);
            Entity body = bodyUnder(mc, as);
            float yaw = body != null ? body.getYRot() : as.getYRot();
            mobs.add(new Mob(as.getX(), as.getZ(), yaw, iconFor(mob), minibossColor(mob)));
        }
        for (Player p : mc.level.players()) {
            if (p == mc.player) continue;
            String n = p.getName().getString();
            int color = minibossColor(n);
            if (color != 0 && (n.equals("Shadow Assassin") || n.equals("Lost Adventurer")
                    || n.equals("Frozen Adventurer") || n.equals("Angry Archaeologist") || n.equals("Diamond Guy"))) {
                if (!alreadyNear(p.getX(), p.getZ())) mobs.add(new Mob(p.getX(), p.getZ(), p.getYRot(), ICON_NONE, color));
            }
        }
        publish();
    }

    private static void publish() {
        view = new ArrayList<>(mobs);
    }

    private static boolean alreadyNear(double x, double z) {
        for (Mob m : mobs) {
            double dx = m.x() - x, dz = m.z() - z;
            if (dx * dx + dz * dz < 1.0) return true;
        }
        return false;
    }

    private static Entity bodyUnder(Minecraft mc, ArmorStand as) {
        Entity best = null;
        double bestD = 9;
        for (Entity e : mc.level.getEntities(as, as.getBoundingBox().inflate(0.5, 3, 0.5))) {
            if (e instanceof ArmorStand || e == mc.player) continue;
            double d = e.distanceToSqr(as);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    private static int iconFor(String name) {
        if (name.contains("Zombie") || name.contains("Crypt Lurker")) return ICON_ZOMBIE;
        if (name.contains("Skeleton") || name.contains("Sniper") || name.contains("Super Archer")) return ICON_SKELETON;
        if (name.contains("Dreadlord") || name.contains("Souleater") || name.contains("Withermancer")) return ICON_WITHER;
        return ICON_NONE;
    }

    private static int minibossColor(String name) {
        if (name.contains("Lost Adventurer")) return 0xFF00FF32;
        if (name.contains("Frozen Adventurer")) return 0xFF2EBDFF;
        if (name.contains("Shadow Assassin")) return 0xFFFF0000;
        if (name.contains("Angry Archaeologist") || name.contains("Angry Archeologist")) return 0xFF00FFAA;
        if (name.contains("Diamond Guy")) return 0xFF55FFFF;
        return 0;
    }
}
