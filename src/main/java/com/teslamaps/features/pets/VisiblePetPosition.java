package com.teslamaps.features.pets;

// Ported from Skysoft's VisiblePetPosition.kt, entity match simplified from PetEntityMatcher.kt
import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.data.PetIcons;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Keeps your own floating pet at a steady height and optionally shifts it up or down. */
public final class VisiblePetPosition {

    private static final double NAME_OFFSET = 1.45;
    private static final double IDLE_HEAD_Y = 0.85;
    private static final Map<String, String> HASHES = new HashMap<>();

    private static Feature feature;
    private static int headId = Integer.MIN_VALUE, nameId = Integer.MIN_VALUE;
    private static String expectKey;
    private static String expected;
    private static double visualY = Double.NaN;
    private static float lastAge = -1;
    private static boolean idle;
    private static int ticks, lastSeen;

    private VisiblePetPosition() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("VisiblePetPosition").section("Skills").when(() -> cfg().visiblePetPosition)
                .tick(VisiblePetPosition::tick)
                .config("Skills", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Visible Pet Position", () -> c.visiblePetPosition, v -> c.visiblePetPosition = v)
                            .desc("Finds your own floating pet (Pet tab widget needed) and changes where it is drawn. Only you see it")
                            .tags("pet", "bounce", "bouncing", "height");
                    b.toggle("  Stop Bouncing", () -> c.visiblePetStopBouncing, v -> c.visiblePetStopBouncing = v)
                            .desc("Smooths out the up and down bobbing, the pet rests at shoulder height while you stand still");
                    b.slider("  Height Offset", -3f, 3f, () -> c.visiblePetHeightOffset, v -> c.visiblePetHeightOffset = v, 1)
                            .desc("Moves the pet head and its name up or down");
                });
    }

    private static void clear() {
        headId = nameId = Integer.MIN_VALUE;
        visualY = Double.NaN;
        lastAge = -1;
    }

    private static void tick() {
        ticks++;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !PetTracker.hasPet()) { clear(); return; }
        if (ticks % 2 != 0) return;
        String key = PetTracker.internalName() + "|" + PetTracker.skinId();
        if (!key.equals(expectKey)) {
            expectKey = key;
            String skin = PetTracker.skinId();
            ItemStack icon = skin != null ? PetIcons.forSkin(skin) : ItemStack.EMPTY;
            if (icon.isEmpty()) icon = PetIcons.forType(PetTracker.internalName());
            expected = hash(icon);
            clear();
        }
        Entity head = headId == Integer.MIN_VALUE ? null : mc.level.getEntity(headId);
        if (!(head instanceof ArmorStand hs) || !hs.isAlive() || candidateScore(hs, player) < 0) {
            head = find(player);
            if (head == null) {
                if (ticks - lastSeen > 40) clear();
                return;
            }
            if (head.getId() != headId) {
                visualY = Double.NaN;
                lastAge = -1;
            }
            headId = head.getId();
        }
        lastSeen = ticks;
        nameId = findName(head);
    }

    private static Entity find(Player player) {
        ArmorStand best = null;
        int bestScore = -1;
        for (ArmorStand as : EntityCache.armorStands()) {
            int s = candidateScore(as, player);
            if (s > bestScore) { bestScore = s; best = as; }
        }
        return best;
    }

    /** -1 = no candidate. Exact texture wins, otherwise an invisible head-only stand close by. */
    private static int candidateScore(ArmorStand as, Player player) {
        double dy = as.getY() - player.getY();
        if (dy < -1 || dy > 4) return -1;
        double dx = as.getX() - player.getX(), dz = as.getZ() - player.getZ();
        double h = dx * dx + dz * dz;
        if (h > 64) return -1;
        ItemStack headItem = as.getItemBySlot(EquipmentSlot.HEAD);
        if (headItem.isEmpty() || !headItem.is(Items.PLAYER_HEAD)) return -1;
        int others = 0;
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (slot != EquipmentSlot.HEAD && !as.getItemBySlot(slot).isEmpty()) others++;
        }
        boolean exact = expected != null && expected.equals(hash(headItem));
        if (!exact && (others > 0 || h > 12.25 || !as.isInvisible() || !nameNear(as))) return -1;
        int score = (exact ? 10_000 : 0) + (as.isInvisible() ? 500 : 0) + (as.isSmall() ? 100 : 0) - others * 250;
        score += (int) Math.max(0, (8 - Math.sqrt(h + dy * dy)) * 50);
        return score;
    }

    private static boolean nameNear(Entity head) {
        return findName(head) != Integer.MIN_VALUE;
    }

    private static int findName(Entity head) {
        String want = PetTracker.displayName();
        if (want == null || want.isEmpty()) return Integer.MIN_VALUE;
        int best = Integer.MIN_VALUE;
        double bestD = Double.MAX_VALUE;
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            if (as.getId() == head.getId() || as.getCustomName() == null) continue;
            double dy = as.getY() - head.getY();
            if (dy < 0.75 || dy > 2.75) continue;
            double dx = as.getX() - head.getX(), dz = as.getZ() - head.getZ();
            if (dx * dx + dz * dz > 6.25) continue;
            if (!StringUtil.strip(as.getCustomName().getString()).contains(want)) continue;
            double d = Math.abs(dy - NAME_OFFSET);
            if (d < bestD) { bestD = d; best = as.getId(); }
        }
        return best;
    }

    public static void adjust(Entity entity, EntityRenderState state) {
        int id = entity.getId();
        if ((id != headId && id != nameId) || headId == Integer.MIN_VALUE) return;
        if (feature == null || !feature.active()) return;
        TeslaMapsConfig c = cfg();
        double offset = c.visiblePetHeightOffset;
        if (id == headId) {
            Player player = Minecraft.getInstance().player;
            if (player == null) return;
            if (!c.visiblePetStopBouncing) {
                visualY = state.y;
                state.y += offset;
                return;
            }
            state.y = smooth(target(state, player), state.ageInTicks) + offset;
        } else {
            if (!c.visiblePetStopBouncing) {
                state.y += offset;
                return;
            }
            double head = Double.isNaN(visualY) ? state.y - NAME_OFFSET : visualY;
            state.y = head + NAME_OFFSET + offset;
        }
    }

    private static double target(EntityRenderState state, Player player) {
        double rel = state.y - player.getY();
        double dx = state.x - player.getX(), dz = state.z - player.getZ();
        idle = dx * dx + dz * dz <= 6.25 && rel >= -2.35 && rel <= 2.05;
        return idle ? player.getY() + IDLE_HEAD_Y : state.y;
    }

    private static double smooth(double raw, float age) {
        double prev = visualY;
        float prevAge = lastAge;
        lastAge = age;
        if (Double.isNaN(prev) || prevAge < 0 || age < prevAge) {
            visualY = raw;
            return raw;
        }
        double dt = Math.min(1.0, Math.max(0.0, age - prevAge));
        double diff = Math.abs(raw - prev);
        double perTick = diff <= 0.75 ? (idle ? 0.45 : 0.08) : diff <= 2.0 ? 0.35 : 0.65;
        double alpha = 1.0 - Math.pow(1.0 - perTick, dt);
        visualY = prev + (raw - prev) * alpha;
        return visualY;
    }

    private static String hash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return null;
        for (Property p : profile.partialProfile().properties().get("textures")) {
            if (p == null) continue;
            String v = p.value();
            String hit = HASHES.get(v);
            if (hit == null) {
                if (HASHES.size() > 512) HASHES.clear();
                hit = decode(v);
                HASHES.put(v, hit);
            }
            if (!hit.isEmpty()) return hit;
        }
        return null;
    }

    private static String decode(String value) {
        try {
            String s = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            int at = s.indexOf("texture/");
            if (at < 0) return "";
            int end = at + 8;
            while (end < s.length() && Character.isLetterOrDigit(s.charAt(end))) end++;
            return s.substring(at + 8, end);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
