package com.teslamaps.features;

import com.mojang.authlib.properties.Property;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.Collection;

public class SoulweaverHider {

    private static final String SOUL_WEAVER =
            "eyJ0aW1lc3RhbXAiOjE1NTk1ODAzNjI1NTMsInByb2ZpbGVJZCI6ImU3NmYwZDlhZjc4MjQyYzM5NDY2ZDY3MjE3MzBmNDUzIiwicHJvZmlsZU5hbWUiOiJLbGxscmFoIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS8yZjI0ZWQ2ODc1MzA0ZmE0YTFmMGM3ODViMmNiNmE2YTcyNTYzZTlmM2UyNGVhNTVlMTgxNzg0NTIxMTlhYTY2In19fQ==";
    private static final String BLESSING =
            "ewogICJ0aW1lc3RhbXAiIDogMTYzNTE0NTU0NzUxMiwKICAicHJvZmlsZUlkIiA6ICJmYzUwMjkzYTVkMGI0NzViYWYwNDJhNzIwMWJhMzBkMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJDVUNGTDE3IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FiNDM2ZWRiN2I4MDVlMTMzZjdkMzQ4OWQ0NmNlMDYzNmY3ZTFjYjM3YjY3Njg5ZmFhMTJlNjk4ZGJiZDdjNjYiCiAgICB9CiAgfQp9";
    private static final String REVIVE_STONE =
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTg1MDM4MjMzNCwKICAicHJvZmlsZUlkIiA6ICIxZDlkYmE3NzdlMWE0NzVkOTQ1ZDYxNmZlYzNiNjhlMCIsCiAgInByb2ZpbGVOYW1lIiA6ICJGcmFzeWRpIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I2YTc2Y2MyMmU3YzJhYjljNTQwZDEyNDRlYWRiYTU4MWY1ZGQ5ZTE4ZjlhZGFjZjA1MjgwYTViNDhiOGY2MTgiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
    private static final String PREMIUM_FLESH =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWE3NWU4YjA0NGM3MjAxYTRiMmU4NTZiZTRmYzMxNmE1YWFlYzY2NTc2MTY5YmFiNTg3MmE4ODUzNGI4MDI1NiJ9fX0K";
    private static final String ABILITY_ORB =
            "ewogICJ0aW1lc3RhbXAiIDogMTYzODUyNDAzODE5OCwKICAicHJvZmlsZUlkIiA6ICIzOWEzOTMzZWE4MjU0OGU3ODQwNzQ1YzBjNGY3MjU2ZCIsCiAgInByb2ZpbGVOYW1lIiA6ICJkZW1pbmVjcmFmdGVybG9sIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzVlZTRiYjQ4MjFkMGY1ZWQ4NjVjMjEwOTBhODBiNWVlN2Q1MjI2ODQ3NmVlMjVkMzg5NzEwZjdjYzlmMTEwZDYiCiAgICB9CiAgfQp9";
    private static final String SUPPORT_ORB =
            "ewogICJ0aW1lc3RhbXAiIDogMTYwNTM1NjUyNzQzOSwKICAicHJvZmlsZUlkIiA6ICJhYTZhNDA5NjU4YTk0MDIwYmU3OGQwN2JkMzVlNTg5MyIsCiAgInByb2ZpbGVOYW1lIiA6ICJiejE0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzE1NzhiNGFmM2ZkZDkxNTFiODUwYjEzYzY3YzQ1ODAyMjRjN2Y2MDA1MjcxM2YyZDE1MWY3YzE1ZGMwZDdiMzQiCiAgICB9CiAgfQp9";
    private static final String DAMAGE_ORB =
            "ewogICJ0aW1lc3RhbXAiIDogMTYwNDY4NDIxNTAyMCwKICAicHJvZmlsZUlkIiA6ICI3NzI3ZDM1NjY5Zjk0MTUxODAyM2Q2MmM2ODE3NTkxOCIsCiAgInByb2ZpbGVOYW1lIiA6ICJsaWJyYXJ5ZnJlYWsiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWI4NmRhMmUyNDNjMDVkYzA4OThiMGNjNWQzZTY0ODc3MTczMTc3ZTBhMjM5NDQyNWNlYzEwMDI1OWNiNDUyNiIKICAgIH0KICB9Cn0=";
    private static final String HEALER_FAIRY =
            "ewogICJ0aW1lc3RhbXAiIDogMTcxOTQ2MzA5MTA0NywKICAicHJvZmlsZUlkIiA6ICIyNjRkYzBlYjVlZGI0ZmI3OTgxNWIyZGY1NGY0OTgyNCIsCiAgInByb2ZpbGVOYW1lIiA6ICJxdWludHVwbGV0IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzJlZWRjZmZjNmExMWEzODM0YTI4ODQ5Y2MzMTZhZjdhMjc1MmEzNzZkNTM2Y2Y4NDAzOWNmNzkxMDhiMTY3YWUiCiAgICB9CiAgfQp9";

    // Compare by the texture URL hash, not the raw base64 — Hypixel may send the same skin with a
    // different JSON formatting (pretty vs minified) or timestamp, which breaks exact base64 matching.
    private static final java.util.Map<String, String> URL_CACHE = new java.util.HashMap<>();
    private static final java.util.Set<String> DBG_SEEN = new java.util.HashSet<>();
    private static final String SOUL_WEAVER_URL = textureUrl(SOUL_WEAVER);
    private static final String BLESSING_URL = textureUrl(BLESSING);
    private static final String REVIVE_STONE_URL = textureUrl(REVIVE_STONE);
    private static final String PREMIUM_FLESH_URL = textureUrl(PREMIUM_FLESH);
    private static final String ABILITY_ORB_URL = textureUrl(ABILITY_ORB);
    private static final String SUPPORT_ORB_URL = textureUrl(SUPPORT_ORB);
    private static final String DAMAGE_ORB_URL = textureUrl(DAMAGE_ORB);
    private static final String HEALER_FAIRY_URL = textureUrl(HEALER_FAIRY);

    private static final java.util.Map<ArmorStand, Long> SKULL_MOVED = new java.util.WeakHashMap<>();
    private static final java.util.Map<ArmorStand, Long> PARTICLE_SOURCES = new java.util.WeakHashMap<>();

    public static void register() {
        com.teslamaps.registry.Feature.of("MovingSkulls")
                .when(() -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    return (c.hideSkeletonSkull && c.showMovingSkulls || c.highlightMovingSkulls) && DungeonManager.isInDungeon();
                })
                .tick(SoulweaverHider::tickSkulls);
    }

    // SkyHanni DungeonHideItems: a skull that moved in the last 100 ms is a thrown one
    private static void tickSkulls() {
        long now = System.currentTimeMillis();
        boolean glow = TeslaMapsConfig.get().highlightMovingSkulls;
        for (ArmorStand s : com.teslamaps.utils.EntityCache.armorStands()) {
            ItemStack head = s.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty() || !isSkeletonSkull(head)) continue;
            if (s.getX() != s.xo || s.getY() != s.yo || s.getZ() != s.zo) SKULL_MOVED.put(s, now);
            Long t = SKULL_MOVED.get(s);
            if (glow && t != null && now - t < 200) com.teslamaps.render.EntityGlow.mark(s, 0xFFAA00);
        }
    }

    /** Main thread. Firework/dust particles right at a hidden Superboom/Revive Stone/orb. */
    public static boolean hideParticle(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket p) {
        if (PARTICLE_SOURCES.isEmpty() || !TeslaMapsConfig.get().hideDungeonItemParticles) return false;
        var type = p.getParticle().getType();
        if (type != net.minecraft.core.particles.ParticleTypes.FIREWORK && type != net.minecraft.core.particles.ParticleTypes.DUST) return false;
        long now = System.currentTimeMillis();
        var it = PARTICLE_SOURCES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now - e.getValue() > 100) { it.remove(); continue; }
            if (e.getKey().distanceToSqr(p.getX(), p.getY(), p.getZ()) < 4) return true;
        }
        return false;
    }

    private static boolean particleSource(ArmorStand stand) {
        if (TeslaMapsConfig.get().hideDungeonItemParticles) PARTICLE_SOURCES.put(stand, System.currentTimeMillis());
        return true;
    }

    public static boolean shouldHide(Entity entity) {
        if (!DungeonManager.isInDungeon()) return false;
        TeslaMapsConfig cfg = TeslaMapsConfig.get();

        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            if (cfg.hideThrownBones && stack.getItem() == Items.BONE) return true;
            if (!cfg.hideReviveStone && !cfg.hideJournalEntry) return false; // skip name parse if nothing to match
            String name = cleanName(stack.getHoverName().getString());
            if (cfg.hideReviveStone && name.equals("Revive Stone")) return true;
            if (cfg.hideJournalEntry && name.equals("Journal Entry")) return true;
            return false;
        }

        if (!(entity instanceof ArmorStand stand)) return false;

        boolean nameOpt = cfg.hideSuperboomTnt || cfg.hideBlessing || cfg.hideReviveStone
                || cfg.hidePremiumFlesh || cfg.hideHealerOrbs;
        boolean texOpt = cfg.hideSoulweaverSkull || cfg.hideBlessing || cfg.hideReviveStone
                || cfg.hidePremiumFlesh || cfg.hideHealerFairy || cfg.hideHealerOrbs;
        if (!nameOpt && !texOpt && !cfg.hideSkeletonSkull) return false; // nothing enabled -> no per-entity work

        if (nameOpt) {
            net.minecraft.network.chat.Component cn = stand.getCustomName(); // named stands only; skips the many unnamed ones
            if (cn != null) {
                String standName = cleanName(cn.getString());
                if (cfg.hideSuperboomTnt && standName.startsWith("Superboom TNT")) return true;
                if (cfg.hideBlessing && standName.startsWith("Blessing of ")) return true;
                if (cfg.hideReviveStone && standName.equals("Revive Stone")) return true;
                if (cfg.hidePremiumFlesh && standName.equals("Premium Flesh")) return particleSource(stand);
                if (cfg.hideHealerOrbs && (standName.startsWith("DAMAGE ")
                        || standName.startsWith("ABILITY DAMAGE ") || standName.startsWith("DEFENSE "))) return true;
            }
        }

        ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty()) return false;

        if (cfg.hideSkeletonSkull && isSkeletonSkull(head)) {
            if (cfg.showMovingSkulls) {
                Long t = SKULL_MOVED.get(stand);
                if (t != null && System.currentTimeMillis() - t < 100) return false;
            }
            return true;
        }
        if (cfg.hideSuperboomTnt && cfg.hideDungeonItemParticles
                && cleanName(head.getHoverName().getString()).equals("Superboom TNT")) return particleSource(stand);

        if (head.getItem() != Items.PLAYER_HEAD || !texOpt) return false;
        String tex = skullTexture(head);
        String url = textureUrl(tex);
        if (cfg.debugMode && DBG_SEEN.add(url == null ? "null:" + tex : url)) {
            com.teslamaps.TeslaMaps.debug("[SkullDbg] armorstand head url={} (soulweaver target={}) raw={}", url, SOUL_WEAVER_URL, tex);
        }
        if (url == null) return false;

        if (cfg.hideSoulweaverSkull && url.equals(SOUL_WEAVER_URL)) return true;
        if (cfg.hideBlessing && url.equals(BLESSING_URL)) return true;
        if (cfg.hideReviveStone && url.equals(REVIVE_STONE_URL)) return particleSource(stand);
        if (cfg.hidePremiumFlesh && url.equals(PREMIUM_FLESH_URL)) return true;
        if (cfg.hideHealerFairy && url.equals(HEALER_FAIRY_URL)) return true;
        if (cfg.hideHealerOrbs && (url.equals(ABILITY_ORB_URL) || url.equals(SUPPORT_ORB_URL) || url.equals(DAMAGE_ORB_URL)))
            return particleSource(stand);

        return false;
    }

    /** @return the texture URL hash of a player head, or null. */
    public static String headTextureUrl(ItemStack head) {
        if (head == null || head.getItem() != Items.PLAYER_HEAD) return null;
        return textureUrl(skullTexture(head));
    }

    // Decodes a skull texture-property base64 to its texture URL hash (cached); null if unparseable.
    private static String textureUrl(String base64) {
        if (base64 == null) return null;
        String cached = URL_CACHE.get(base64);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String url = decodeTextureUrl(base64);
        URL_CACHE.put(base64, url == null ? "" : url);
        return url;
    }

    private static String decodeTextureUrl(String base64) {
        try {
            String json = new String(java.util.Base64.getDecoder().decode(base64), java.nio.charset.StandardCharsets.UTF_8);
            int i = json.indexOf("/texture/");
            if (i < 0) return null;
            int start = i + 9;
            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) end++;
                else break;
            }
            return end > start ? json.substring(start, end) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSkeletonSkull(ItemStack head) {
        if (head.getItem() == Items.SKELETON_SKULL || head.getItem() == Items.WITHER_SKELETON_SKULL) return true;
        return cleanName(head.getHoverName().getString()).equals("Skeleton Skull");
    }

    private static final java.util.regex.Pattern COLOR_CODE = java.util.regex.Pattern.compile("(?i)§[0-9A-FK-OR]");

    private static String cleanName(String s) {
        return s == null ? "" : COLOR_CODE.matcher(s).replaceAll("").trim();
    }

    private static String skullTexture(ItemStack stack) {
        ResolvableProfile rp = stack.get(DataComponents.PROFILE);
        if (rp == null) return null;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return null;
        return props.iterator().next().value();
    }
}
