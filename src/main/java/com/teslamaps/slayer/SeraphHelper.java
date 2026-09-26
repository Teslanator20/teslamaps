package com.teslamaps.slayer;

// Voidgloom HUD after SkyblockExtras' "Voidgloom Display", Nukekubi glow from Skyblocker's SlayerGlowAdder,
// laser hider from Zen's HideEndermanLaser, summon hider from Oringo's RemoveAnnoyingMobs
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SoulweaverHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.EspStyle;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SeraphHelper {

    private static final String NUKEKUBI = "eb07594e2df273921a77c101d0bfdfa1115abed5b9b2029eb496ceba9bdbb4b3";
    private static final Pattern HITS = Pattern.compile("(\\d+) Hits?\\b");
    private static final Pattern HP = Pattern.compile("([\\d.,]+[kKmMbB]?)❤");
    private static final Pattern TIER = Pattern.compile("Voidgloom Seraph (V|IV|III|II|I)\\b");
    public static final String[] LASER_MODES = {"All bosses", "Carries", "Mine", "Mine and carries", "Not mine/carries"};
    private static final List<String> SAMPLE = List.of("§5§lVoidgloom Seraph IV", "§fHit Phase: §d15 Hits",
            "§fHolding Beacon: §7No", "§fBeacon Placed: §c3.2s", "§fSkulls Nearby: §62");

    private record Seraph(ArmorStand tag, Entity body, String owner) {}

    private static List<Seraph> seraphs = List.of();
    private static List<ArmorStand> skulls = List.of();
    private static final List<BlockPos> beacons = new ArrayList<>();
    private static final List<Long> beaconSince = new ArrayList<>();
    private static Set<String> customers = Set.of();
    private static List<String> lines = List.of();
    private static boolean inEnd = false;
    private static int ticks = 0;
    private static HudEditElement hud;

    private SeraphHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    private static boolean any() {
        TeslaMapsConfig c = cfg();
        return c.voidgloomHud || c.voidgloomNukekubiGlow || c.voidgloomHideLaser || c.voidgloomHideSummons;
    }

    public static void register() {
        hud = HudEditElement.of("voidgloomHud", "Voidgloom HUD").ctx(HudEditElement.HudCtx.THE_END).section("Combat")
                .enabled(() -> cfg().voidgloomHud)
                .size(() -> width(lines.isEmpty() ? SAMPLE : lines), () -> height(lines.isEmpty() ? SAMPLE : lines))
                .scaleRange(0.5f, 4.0f)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines));
        Feature.of("SeraphHelper").section("Combat").when(SeraphHelper::any)
                .tick(SeraphHelper::tick)
                .world(SeraphHelper::renderWorld)
                .hud("voidgloomhud", SeraphHelper::renderHud)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Voidgloom Seraph");
                    b.toggle("Voidgloom HUD", () -> c.voidgloomHud, v -> c.voidgloomHud = v)
                            .desc("Boss HP or hits left in the hit phase, whether it holds or placed a beacon (with timer) "
                                    + "and the Nukekubi skulls nearby. Move it with /tmap gui.")
                            .tags("slayer", "enderman", "eman", "beacon", "hits");
                    b.toggle("  Only Own Boss", () -> c.voidgloomHudOnlyOwn, v -> c.voidgloomHudOnlyOwn = v);
                    b.toggle("Nukekubi Skull Highlight", () -> c.voidgloomNukekubiGlow, v -> c.voidgloomNukekubiGlow = v)
                            .desc("Nukekubi skulls near a Voidgloom fight glow so you can shoot them down.")
                            .tags("slayer", "enderman", "skull", "glow");
                    b.espStyle("Skull", () -> c.voidgloomNukekubiStyle);
                    b.toggle("Hide Voidgloom Laser", () -> c.voidgloomHideLaser, v -> c.voidgloomHideLaser = v)
                            .desc("Hides the guardian beam of Voidgloom bosses picked below. \"Not mine/carries\" hides other players' Broken Heart Radiation.")
                            .tags("slayer", "enderman", "beam", "guardian");
                    b.dropdown("  Hide For", LASER_MODES, () -> c.voidgloomHideLaserFor, v -> c.voidgloomHideLaserFor = v);
                    b.toggle("Hide Mobs at the Seraph", () -> c.voidgloomHideSummons, v -> c.voidgloomHideSummons = v)
                            .desc("Summons, pets and other players within 5 blocks of a Voidgloom Seraph are not drawn.")
                            .tags("slayer", "enderman", "summons", "hide");
                    VoidgloomLaser.config(b);
                    VoidgloomBeacon.config(b);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        ticks++;
        if (ticks % 20 == 1) {
            inEnd = SkyblockUtils.isInArea("The End");
            Set<String> cs = new HashSet<>();
            for (TeslaMapsConfig.SlayerCarry sc : c.slayerCarries) {
                if (sc != null && sc.customer != null) cs.add(sc.customer.toLowerCase(Locale.ROOT));
            }
            customers = cs;
        }
        if (!inEnd) {
            if (!seraphs.isEmpty() || !skulls.isEmpty() || !lines.isEmpty() || !beacons.isEmpty()) clear();
            return;
        }
        if (c.voidgloomNukekubiGlow) {
            EspStyle st = EspStyle.of(c.voidgloomNukekubiStyle);
            for (ArmorStand s : skulls) if (s.isAlive()) st.tickEntity(s);
        }
        if (ticks % 2 != 0) return;

        List<Seraph> found = new ArrayList<>();
        for (ArmorStand s : EntityCache.namedArmorStands()) {
            if (s.distanceToSqr(mc.player) > 48 * 48) continue;
            String name = s.getName().getString();
            if (!name.contains("Voidgloom Seraph")) continue;
            Entity body = NametagMobs.mobForTag(EntityType.ENDERMAN, s);
            found.add(new Seraph(s, body, SlayerHUD.ownerOf(s)));
        }
        seraphs = found;

        if (ticks % 10 == 0) {
            List<ArmorStand> sk = new ArrayList<>();
            if (!found.isEmpty() && (c.voidgloomNukekubiGlow || c.voidgloomHud)) {
                for (ArmorStand s : EntityCache.armorStands()) {
                    if (s.distanceToSqr(mc.player) > 30 * 30) continue;
                    if (NUKEKUBI.equals(SoulweaverHider.headTextureUrl(s.getItemBySlot(EquipmentSlot.HEAD)))) sk.add(s);
                }
            }
            skulls = sk;
        }

        if (!c.voidgloomHud) {
            lines = List.of();
            return;
        }
        Seraph pick = pick(mc, found, c.voidgloomHudOnlyOwn);
        if (pick == null) {
            lines = List.of();
            beacons.clear();
            beaconSince.clear();
            return;
        }
        if (ticks % 10 == 0) scanBeacons(mc, pick);
        lines = buildLines(pick);
    }

    private static Seraph pick(Minecraft mc, List<Seraph> found, boolean onlyOwn) {
        String me = mc.getUser().getName();
        Seraph nearest = null;
        double best = Double.MAX_VALUE;
        for (Seraph s : found) {
            if (s.owner() != null && s.owner().equalsIgnoreCase(me)) return s;
            double d = s.tag().distanceToSqr(mc.player);
            if (d < best) {
                best = d;
                nearest = s;
            }
        }
        return onlyOwn ? null : nearest;
    }

    private static void scanBeacons(Minecraft mc, Seraph s) {
        BlockPos center = (s.body() != null ? s.body() : s.tag()).blockPosition();
        long now = System.currentTimeMillis();
        List<BlockPos> seen = new ArrayList<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = -4; dy <= 3; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (mc.level.getBlockState(p).is(Blocks.BEACON)) seen.add(p.immutable());
                }
            }
        }
        for (int i = beacons.size() - 1; i >= 0; i--) {
            if (!seen.contains(beacons.get(i))) {
                beacons.remove(i);
                beaconSince.remove(i);
            }
        }
        for (BlockPos b : seen) {
            if (!beacons.contains(b)) {
                beacons.add(b);
                beaconSince.add(now);
            }
        }
    }

    private static List<String> buildLines(Seraph s) {
        List<String> out = new ArrayList<>(5);
        String name = StringUtil.strip(s.tag().getName().getString());
        Matcher t = TIER.matcher(name);
        out.add("§5§lVoidgloom Seraph" + (t.find() ? " " + t.group(1) : ""));
        Matcher hits = HITS.matcher(name);
        if (hits.find()) {
            out.add("§fHit Phase: §d" + hits.group(1) + " Hits");
        } else {
            Matcher hp = HP.matcher(name);
            out.add("§fHP: §c" + (hp.find() ? hp.group(1) + "❤" : "?"));
        }
        boolean holding = s.body() instanceof EnderMan em && em.getCarriedBlock() != null
                && em.getCarriedBlock().is(Blocks.BEACON);
        out.add("§fHolding Beacon: " + (holding ? "§cYes" : "§7No"));
        if (beacons.isEmpty()) {
            out.add("§fBeacon Placed: §7No");
        } else {
            long oldest = Long.MAX_VALUE;
            for (long l : beaconSince) oldest = Math.min(oldest, l);
            long left = Math.max(0, 5000 - (System.currentTimeMillis() - oldest));
            out.add("§fBeacon Placed: §c" + (left / 1000) + "." + (left % 1000 / 100) + "s");
        }
        out.add("§fSkulls Nearby: §6" + skulls.size());
        if (s.owner() != null) out.add("§7Spawned by " + s.owner());
        return out;
    }

    private static void clear() {
        seraphs = List.of();
        skulls = List.of();
        lines = List.of();
        beacons.clear();
        beaconSince.clear();
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (!cfg().voidgloomNukekubiGlow || skulls.isEmpty()) return;
        EspStyle st = EspStyle.of(cfg().voidgloomNukekubiStyle);
        if (st.glow() && !st.tracer) return;
        for (ArmorStand s : skulls) {
            if (!s.isAlive()) continue;
            Vec3 p = s.getPosition(1f);
            AABB head = new AABB(p.x - 0.35, p.y + 1.35, p.z - 0.35, p.x + 0.35, p.y + 2.05, p.z + 0.35);
            if (st.glow()) {
                com.teslamaps.render.ESPRenderer.drawTracerFromCamera(pose, head.getCenter(), st.argb() | 0xFF000000, cam, st.throughWalls);
            } else {
                st.drawBox(pose, head, cam);
            }
        }
    }

    /** GuardianBeamMixin: hide this guardian's beam. */
    public static boolean hideLaser(Guardian g) {
        List<Seraph> list = seraphs;
        if (list.isEmpty()) return false;
        TeslaMapsConfig c = cfg();
        if (!c.voidgloomHideLaser || !c.section("Combat")) return false;
        Seraph near = null;
        double best = 20 * 20;
        for (Seraph s : list) {
            Entity e = s.body() != null ? s.body() : s.tag();
            double d = e.distanceToSqr(g);
            if (d < best) {
                best = d;
                near = s;
            }
        }
        if (near == null) return false;
        String mode = c.voidgloomHideLaserFor;
        if (mode == null || mode.equals(LASER_MODES[0])) return true;
        if (near.owner() == null) return false;
        boolean mine = near.owner().equalsIgnoreCase(Minecraft.getInstance().getUser().getName());
        boolean carry = customers.contains(near.owner().toLowerCase(Locale.ROOT));
        return switch (mode) {
            case "Carries" -> carry;
            case "Mine" -> mine;
            case "Mine and carries" -> mine || carry;
            case "Not mine/carries" -> !mine && !carry;
            default -> false;
        };
    }

    /** EntityRenderDispatcherMixin: summons and other mobs right at a Seraph. */
    public static boolean shouldHide(Entity e) {
        List<Seraph> list = seraphs;
        if (list.isEmpty() || !(e instanceof LivingEntity)) return false;
        if (e instanceof ArmorStand || e instanceof EnderMan || e instanceof Guardian) return false;
        TeslaMapsConfig c = cfg();
        if (!c.voidgloomHideSummons || !c.section("Combat")) return false;
        if (e == Minecraft.getInstance().player) return false;
        for (Seraph s : list) {
            Entity body = s.body() != null ? s.body() : s.tag();
            if (body.distanceToSqr(e) < 25) return true;
        }
        return false;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), l);
    }

    private static int width(List<String> l) {
        int w = 0;
        for (String s : l) w = Math.max(w, Minecraft.getInstance().font.width(s));
        return w + 6;
    }

    private static int height(List<String> l) {
        return l.size() * 10 + 4;
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> l) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, width(l), height(l));
        for (int i = 0; i < l.size(); i++) g.text(mc.font, l.get(i), 3, 3 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
