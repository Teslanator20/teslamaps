package com.teslamaps.features.pets;

// Level hiding ported from SkyHanni's PetNametag.kt, foreign pet names after SkyblockExtras' "Hide Pet Names"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ChatText;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetNametags {

    private static final Pattern TAG = Pattern.compile("^\\[Lv(\\d+)] (.+)$");

    private record Parsed(Component src, boolean pet, String name, Component rewritten) {}

    private static final Int2ObjectOpenHashMap<Parsed> CACHE = new Int2ObjectOpenHashMap<>();

    private static Feature feature;
    private static Object lastLevel;
    private static String ownName = "";
    private static int flags = -1;

    private PetNametags() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("PetNametags").section("Skills")
                .when(() -> cfg().petNametagHideLevel || cfg().petNametagHideMaxLevel || cfg().petNametagHideOthers)
                .tick(PetNametags::tick)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Hide Pet Nametag Level", () -> c.petNametagHideLevel, v -> c.petNametagHideLevel = v)
                            .desc("Drops the \"[Lv100]\" from floating pet names")
                            .tags("pet", "nametag", "level");
                    b.toggle("  Only At Max Level", () -> c.petNametagHideMaxLevel, v -> c.petNametagHideMaxLevel = v)
                            .desc("Only hides it at level 100 / 200, works without the toggle above too");
                    b.toggle("Hide Other Players' Pet Names", () -> c.petNametagHideOthers, v -> c.petNametagHideOthers = v)
                            .desc("Floating pet names are hidden except your own pet's (Pet tab widget needed to know it). "
                                    + "Rat pet sounds: Sounds > Mute Rat Pet")
                            .tags("pet", "nametag", "hide");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        int f = (c.petNametagHideLevel ? 1 : 0) | (c.petNametagHideMaxLevel ? 2 : 0);
        if (mc.level != lastLevel || CACHE.size() > 512 || f != flags) {
            lastLevel = mc.level;
            flags = f;
            CACHE.clear();
        }
        String n = PetTracker.hasPet() ? PetTracker.displayName() : null;
        ownName = n == null ? "" : n;
    }

    public static void adjust(Entity entity, EntityRenderState state) {
        if (state.nameTag == null || !(entity instanceof ArmorStand) || feature == null || !feature.active()) return;
        Component src = entity.getCustomName();
        if (src == null) return;
        Parsed p = CACHE.get(entity.getId());
        if (p == null || p.src() != src) {
            p = parse(src);
            CACHE.put(entity.getId(), p);
        }
        if (!p.pet()) return;
        TeslaMapsConfig c = cfg();
        if (c.petNametagHideOthers && !own(entity, p.name())) {
            state.nameTag = null;
            return;
        }
        if (p.rewritten() != null) state.nameTag = p.rewritten();
    }

    private static boolean own(Entity entity, String name) {
        if (ownName.isEmpty() || !name.startsWith(ownName)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = entity.getX() - mc.player.getX(), dz = entity.getZ() - mc.player.getZ();
        return dx * dx + dz * dz <= 36;
    }

    private static Parsed parse(Component src) {
        String plain = StringUtil.stripTrim(src.getString());
        Matcher m = TAG.matcher(plain);
        if (!m.matches()) return new Parsed(src, false, null, null);
        TeslaMapsConfig c = cfg();
        int lvl;
        try { lvl = Integer.parseInt(m.group(1)); } catch (NumberFormatException e) { lvl = 0; }
        boolean hide = c.petNametagHideLevel || (c.petNametagHideMaxLevel && (lvl == 100 || lvl == 200));
        Component rewritten = null;
        if (hide) {
            String formatted = ChatText.formatted(src).replace("§r", "");
            int at = formatted.indexOf("] ");
            if (at >= 0) rewritten = Component.literal(formatted.substring(at + 2));
        }
        return new Parsed(src, true, m.group(2), rewritten);
    }
}
