package com.teslamaps.adm.practice;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.LegitMode;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.render.EspStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Ported from ADM's OdinHighlightLook.kt ("Blade" look): each kind of dungeon mob in its own colour,
// found from its name tag by entity id. Replaces our starred mob ESP while on.
public final class BladeHighlight {
    private BladeHighlight() {}

    private enum Kind { STAR, TANK, MINI, FEL, ASSASSIN, MIMIC }

    private static final List<String> TANKS = List.of("Zombie Commander", "Zombie Lord", "Skeleton Lord", "Withermancer", "Super Archer");
    private static final List<String> MINIS = List.of("Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer");

    private record Found(Entity entity, Kind kind) {}
    private static final Map<Integer, Found> found = new HashMap<>();
    private static final Set<Integer> seenTags = new HashSet<>();
    private static Object lastLevel;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static EspStyle defaultStar() {
        EspStyle s = new EspStyle("FFFFFFFF");
        s.throughWalls = false;
        return s;
    }

    private static EspStyle star() {
        if (cfg().admBladeStar == null) cfg().admBladeStar = defaultStar();
        return cfg().admBladeStar;
    }

    public static void register() {
        Feature.of("AdmBladeHighlight").section(Adm.SECTION).legit().when(() -> cfg().admBladeHighlight)
                .tick(BladeHighlight::tick)
                .world(BladeHighlight::render)
                .config("ADM Highlight & BR", b -> {
                    b.label("Mob Highlight");
                    b.toggle("Blade Highlight", () -> cfg().admBladeHighlight, v -> cfg().admBladeHighlight = v).cheat()
                            .desc("Blade Addons' look: each kind of mob in its own colour (starred, tanks, minibosses, Fels, Shadow Assassins, Mimics), each mob found from its name tag exactly. Replaces the Starred Mob ESP while on.");
                    b.espStyle("Starred", BladeHighlight::star);
                    b.color("  Tank Color", () -> cfg().admBladeTankColor, v -> cfg().admBladeTankColor = v)
                            .desc("Zombie Commanders and Lords, Skeleton Lords, Withermancers and Super Archers.");
                    b.color("  Miniboss Color", () -> cfg().admBladeMiniColor, v -> cfg().admBladeMiniColor = v)
                            .desc("Lost Adventurers, Angry Archaeologists, Frozen Adventurers and King Midas.");
                    b.color("  Fel Color", () -> cfg().admBladeFelColor, v -> cfg().admBladeFelColor = v)
                            .desc("Starred Fels.");
                    b.color("  Shadow Assassin Color", () -> cfg().admBladeAssassinColor, v -> cfg().admBladeAssassinColor = v)
                            .desc("Shadow Assassins.");
                    b.color("  Mimic Color", () -> cfg().admBladeMimicColor, v -> cfg().admBladeMimicColor = v)
                            .desc("Mimics.");
                    b.toggle("  Hide Invisible", () -> cfg().admBladeHideInvisible, v -> cfg().admBladeHideInvisible = v)
                            .desc("No box on an invisible Shadow Assassin, and only the head of an invisible Fel.");
                    b.toggle("  Not While Blind", () -> cfg().admBladeNotWhileBlind, v -> cfg().admBladeNotWhileBlind = v)
                            .desc("No boxes while you have Blindness.");
                });
    }

    /** Read by our starred mob ESP: it stands down while this is on. */
    public static boolean replacesOurs() {
        return Adm.overrides(cfg().admBladeHighlight) && !LegitMode.blocksCheats();
    }

    private static boolean inClear() {
        return DungeonManager.isInDungeon() && !DungeonManager.isInBossArea();
    }

    private static Kind kindOf(String name) {
        if (name.contains("King Midas")) return Kind.MINI;
        if (name.contains("Mimic")) return Kind.MIMIC;
        if (!name.contains("✯ ")) return null;
        if (name.contains("Fel")) return Kind.FEL;
        for (String m : MINIS) if (name.contains(m)) return Kind.MINI;
        for (String t : TANKS) if (name.contains(t)) return Kind.TANK;
        return Kind.STAR;
    }

    private static boolean isShadowAssassin(Player p) {
        if (p.getUUID().version() != 2) return false;
        Component n = p.getMainHandItem().get(DataComponents.CUSTOM_NAME);
        return n != null && n.getString().equals("Silent Death") && p.getItemBySlot(EquipmentSlot.FEET).is(Items.LEATHER_BOOTS);
    }

    private static int color(Kind kind) {
        String c = switch (kind) {
            case STAR -> star().color;
            case TANK -> cfg().admBladeTankColor;
            case MINI -> cfg().admBladeMiniColor;
            case FEL -> cfg().admBladeFelColor;
            case ASSASSIN -> cfg().admBladeAssassinColor;
            case MIMIC -> cfg().admBladeMimicColor;
        };
        return TeslaMapsConfig.parseColor(c);
    }

    private static AABB box(Entity e) {
        AABB box = ESPRenderer.lerpBox(e);
        if (e instanceof EnderMan && e.isInvisible() && cfg().admBladeHideInvisible) box = box.inflate(0.0, -1.8, 0.0).move(0.0, -1.2, 0.0);
        if (e instanceof Zombie z && z.isBaby()) box = box.inflate(0.15, 0.2, 0.15);
        return box;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) { lastLevel = level; found.clear(); seenTags.clear(); }
        if (level == null || !inClear()) return;
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ArmorStand) {
                if (seenTags.contains(e.getId())) continue;
                Component cn = e.getCustomName();
                if (cn == null) continue;
                String name = cn.getString();
                seenTags.add(e.getId());
                Kind kind = kindOf(name);
                if (kind == null) continue;
                int offset = name.toLowerCase().contains("withermancer") ? 3 : 1;
                Entity mob = level.getEntity(e.getId() - offset);
                if (mob == null || mob instanceof ArmorStand || !mob.isAlive()) continue;
                found.put(mob.getId(), new Found(mob, kind));
            } else if (e instanceof Player p && !found.containsKey(p.getId()) && isShadowAssassin(p)) {
                found.put(p.getId(), new Found(p, Kind.ASSASSIN));
            }
        }
        found.values().removeIf(f -> !f.entity.isAlive() || f.entity.isRemoved());
        if (star().glow() && !blind()) {
            for (Found f : found.values()) {
                if (hidden(f)) continue;
                EntityGlow.mark(f.entity, color(f.kind) & 0xFFFFFF);
            }
        }
    }

    private static boolean blind() {
        Player me = Minecraft.getInstance().player;
        return cfg().admBladeNotWhileBlind && me != null && me.hasEffect(MobEffects.BLINDNESS);
    }

    private static boolean hidden(Found f) {
        return !f.entity.isAlive() || (cfg().admBladeHideInvisible && f.entity instanceof Player && f.entity.isInvisible());
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (!inClear() || blind()) return;
        EspStyle s = star();
        if (s.glow()) return;
        boolean filled = EspStyle.FILLED.equals(s.mode) || EspStyle.BOTH.equals(s.mode);
        boolean outline = !EspStyle.FILLED.equals(s.mode);
        for (Found f : found.values()) {
            if (hidden(f)) continue;
            AABB b = box(f.entity);
            int c = color(f.kind);
            if (filled) ESPRenderer.drawFilledBox(pose, b, (c & 0xFFFFFF) | 0x40000000, cam, s.throughWalls);
            if (outline) ESPRenderer.drawBoxOutline(pose, b, c | 0xFF000000, s.lineWidth, cam, s.throughWalls);
            if (s.tracer) ESPRenderer.drawTracerFromCamera(pose, b.getCenter(), c | 0xFF000000, cam, s.throughWalls);
        }
    }
}
