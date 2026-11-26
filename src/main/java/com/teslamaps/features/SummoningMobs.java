package com.teslamaps.features;

// Ported from SkyHanni's SummoningMobManager.kt + SummoningSoulsName.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class SummoningMobs {

    private static final Pattern SPAWN = Pattern.compile("^You have spawned your .+ soul! \\(\\d+ Mana\\)$");
    private static final Pattern DESPAWN = Pattern.compile("^(?:You have despawned your monsters?!|The Seraph recalled your \\d+ summoned allies!)$");
    private static final List<String> SAMPLE = List.of("§aSummoning mobs: 2", "#1 §aTank Zombie §a12k§2/12k§c❤", "#2 §aSuper Tank Zombie §e6k§2/12k§c❤");
    private static final long SOUL_MOB_MS = 3000;

    private static final class Summon {
        final LivingEntity mob;
        final String name;
        ArmorStand tag;
        Summon(LivingEntity mob, String name, ArmorStand tag) { this.mob = mob; this.name = name; this.tag = tag; }
    }

    private record Seen(Vec3 pos, String name, long at) {}

    private static final Int2ObjectOpenHashMap<Summon> summons = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<Seen> seenMobs = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<String> souls = new Int2ObjectOpenHashMap<>();
    private static final List<ArmorStand> soulStands = new ArrayList<>();
    private static volatile java.util.Set<Integer> hiddenTags = java.util.Set.of();

    private static Object lastLevel, soulLevel;
    private static int soulTicks;
    private static long lastDespawnChat;
    private static int ticks;
    private static List<String> lines = List.of();
    private static int width;
    private static HudEditElement hud;

    private SummoningMobs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("summoningMobs", "Necromancer Summon Helper").section("Combat")
                .enabled(() -> cfg().summoningMobs && cfg().summoningMobsHud)
                .size(() -> panelWidth(SAMPLE), () -> SAMPLE.size() * 10 + 6)
                .preview((g, mc, x, y, s) -> draw(g, mc, x, y, s, SAMPLE, panelWidth(SAMPLE)));
        Feature.of("SummoningMobs").section("Combat").when(() -> cfg().summoningMobs)
                .tick(SummoningMobs::tick)
                .serverChat(SummoningMobs::onChat)
                .hudHiddenInInventory("summoningmobs", SummoningMobs::render);
        Feature.of("SummoningSoulNames").section("Combat").when(() -> cfg().summoningSoulNames)
                .tick(SummoningMobs::soulTick)
                .world(SummoningMobs::renderSouls)
                .config("Combat", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Necromancy");
                    b.toggle("Necromancer Summon Helper", () -> c.summoningMobs, v -> c.summoningMobs = v)
                            .desc("Your own Necromancer summons: HUD list with health, highlight, hidden nametags and a message when one dies.")
                            .tags("summon", "summoning", "necromancer", "necromancy", "soul");
                    b.toggle("  HUD List", () -> c.summoningMobsHud, v -> c.summoningMobsHud = v).desc("Position in /tmap gui");
                    b.toggle("  Green Highlight", () -> c.summoningMobsHighlight, v -> c.summoningMobsHighlight = v);
                    b.toggle("  Hide Nametags", () -> c.summoningMobsHideTags, v -> c.summoningMobsHideTags = v);
                    b.toggle("  Death Message + Hide Spawn Chat", () -> c.summoningMobsChat, v -> c.summoningMobsChat = v)
                            .desc("Hides the spawn/despawn lines and prints \"Your Summoning Mob just died!\"");
                    b.toggle("Summoning Soul Names", () -> c.summoningSoulNames, v -> c.summoningSoulNames = v)
                            .desc("Dropped Summoning Souls show the name of the mob they came from.")
                            .tags("summoning", "soul", "necromancer", "name");
                });
    }

    private static void onChat(String text) {
        if (DESPAWN.matcher(StringUtil.stripTrim(text)).matches()) lastDespawnChat = System.currentTimeMillis();
    }

    /** ChatFilter chain: true = hide the line. */
    public static boolean hideChat(String stripped) {
        TeslaMapsConfig c = cfg();
        if (!c.summoningMobs || !c.summoningMobsChat || !c.section("Combat")) return false;
        String t = stripped.trim();
        return SPAWN.matcher(t).matches() || DESPAWN.matcher(t).matches();
    }

    /** EntityRenderDispatcher.shouldRender. */
    public static boolean shouldHide(Entity e) {
        return e instanceof ArmorStand && !hiddenTags.isEmpty() && cfg().summoningMobs && cfg().summoningMobsHideTags && hiddenTags.contains(e.getId());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            summons.clear();
        }
        if (!SkyblockUtils.isInSkyblock()) {
            summons.clear();
            lines = List.of();
            hiddenTags = java.util.Set.of();
            return;
        }
        if (ticks++ % 5 == 0) scan(mc);

        var it = summons.int2ObjectEntrySet().iterator();
        while (it.hasNext()) {
            Summon s = it.next().getValue();
            if (s.mob.isAlive() && !s.mob.isRemoved() && s.mob.getHealth() > 0) continue;
            it.remove();
            if (c.summoningMobsChat && mc.player.distanceToSqr(s.mob) < 48 * 48) {
                com.teslamaps.utils.TickScheduler.schedule(20, () -> {
                    if (System.currentTimeMillis() - lastDespawnChat > 2000 && mc.player != null)
                        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §fYour Summoning Mob just §cdied!"));
                });
            }
        }

        if (c.summoningMobsHighlight) for (Summon s : summons.values()) EntityGlow.mark(s.mob, 0x55FF55);

        java.util.Set<Integer> hide = new java.util.HashSet<>();
        if (c.summoningMobsHideTags) for (Summon s : summons.values()) if (s.tag != null) hide.add(s.tag.getId());
        hiddenTags = hide;

        if (!c.summoningMobsHud || summons.isEmpty()) {
            lines = List.of();
            return;
        }
        List<String> out = new ArrayList<>();
        out.add("§aSummoning mobs: " + summons.size());
        int i = 1;
        for (Summon s : summons.values()) {
            float hp = s.mob.getHealth(), max = s.mob.getMaxHealth();
            String col = hp >= max * 0.75f ? "§a" : hp >= max * 0.5f ? "§e" : hp >= max * 0.25f ? "§6" : "§c";
            out.add("#" + i++ + " §a" + s.name + " " + col + shortNum(hp) + "§2/" + shortNum(max) + "§c❤");
        }
        lines = out;
        width = panelWidth(out);
    }

    private static void scan(Minecraft mc) {
        String me = mc.player.getGameProfile().name();
        for (ArmorStand as : EntityCache.namedArmorStands()) {
            String n = NametagMobs.tagName(as);
            if (n == null || !n.startsWith(me + "'s ")) continue;
            NametagMobs.Info info = NametagMobs.parse(n, false);
            if (info == null || info.kind() != NametagMobs.Kind.SUMMON || !me.equals(info.owner())) continue;
            LivingEntity mob = NametagMobs.mobForTag(as);
            if (mob == null || !mob.isAlive()) continue;
            Summon s = summons.get(mob.getId());
            if (s == null) summons.put(mob.getId(), new Summon(mob, info.name(), as));
            else s.tag = as;
        }
    }

    private static void soulTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != soulLevel) {
            soulLevel = mc.level;
            souls.clear();
            seenMobs.clear();
        }
        if (!SkyblockUtils.isInSkyblock()) {
            souls.clear();
            seenMobs.clear();
            soulStands.clear();
            return;
        }
        long now = System.currentTimeMillis();
        if (soulTicks++ % 5 == 0) {
            for (ArmorStand as : EntityCache.namedArmorStands()) {
                String raw = as.getCustomName().getString();
                if (raw.indexOf('❤') < 0) continue;
                String clean = StringUtil.stripTrim(raw);
                if (clean.contains(" 0❤") || clean.contains(" 0/")) continue;
                LivingEntity mob = NametagMobs.mobForTag(as);
                if (mob == null) continue;
                seenMobs.put(mob.getId(), new Seen(mob.position(), raw, now));
            }
        }

        List<ArmorStand> stands = new ArrayList<>();
        java.util.Set<Integer> present = new java.util.HashSet<>();
        for (ArmorStand as : EntityCache.armorStands()) {
            present.add(as.getId());
            if (as.hasCustomName()) continue;
            String known = souls.get(as.getId());
            if (known == null) {
                if (as.tickCount > 40 || !as.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) continue;
                known = nearestGone(mc, as.position(), now);
                if (known == null) continue;
                souls.put(as.getId(), known);
            }
            stands.add(as);
        }
        souls.keySet().removeIf(id -> !present.contains(id));
        seenMobs.int2ObjectEntrySet().removeIf(en -> now - en.getValue().at > 6 * 60_000);
        soulStands.clear();
        soulStands.addAll(stands);
    }

    private static String nearestGone(Minecraft mc, Vec3 p, long now) {
        String best = null;
        double bestD = 5 * 5;
        for (var en : seenMobs.int2ObjectEntrySet()) {
            Seen s = en.getValue();
            if (now - s.at > SOUL_MOB_MS) continue;
            Entity e = mc.level.getEntity(en.getIntKey());
            if (e != null && e.isAlive() && !(e instanceof LivingEntity le && le.getHealth() <= 0)) continue;
            double d = s.pos.distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = s.name;
            }
        }
        return best;
    }

    private static void renderSouls(PoseStack pose, Vec3 cam) {
        if (soulStands.isEmpty()) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (ArmorStand as : soulStands) {
            String n = souls.get(as.getId());
            if (n != null) ESPRenderer.drawText(pose, n, as.getPosition(pt).add(0, 2.5, 0), 1f, cam, true, false);
        }
    }

    private static String shortNum(float v) {
        if (v >= 1_000_000) return trim(v / 1_000_000f) + "M";
        if (v >= 1_000) return trim(v / 1_000f) + "k";
        return String.valueOf((int) v);
    }

    private static String trim(float v) {
        return v >= 100 ? String.valueOf((int) v) : String.valueOf(Math.round(v * 10) / 10f);
    }

    private static int panelWidth(List<String> ls) {
        Minecraft mc = Minecraft.getInstance();
        int w = 0;
        for (String l : ls) w = Math.max(w, mc.font.width(l));
        return w + 8;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> ls = lines;
        if (ls.isEmpty() || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        draw(g, mc, hud.x(), hud.y(), hud.scale(), ls, width);
    }

    private static void draw(GuiGraphicsExtractor g, Minecraft mc, int x, int y, float scale, List<String> ls, int w) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        HudStyle.panel(g, 0, 0, w, ls.size() * 10 + 6);
        for (int i = 0; i < ls.size(); i++) g.text(mc.font, ls.get(i), 4, 4 + i * 10, 0xFFFFFFFF, true);
        g.pose().popMatrix();
    }
}
