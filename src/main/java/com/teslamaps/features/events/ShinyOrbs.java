package com.teslamaps.features.events;

// Ported from SkyHanni's ShinyOrbTracker.kt, PigFeatures.kt and PigFeaturesApi.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.features.tracker.Tracker;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.ItemPrice;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import com.teslamaps.utils.TickScheduler;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShinyOrbs {

    private record Orb(Pig pig, Vec3 orbPos, long spawnedAt) {}

    private static final Pattern LOOTED = Pattern.compile("^SHINY! You extracted Shiny Token and (.+) from the piglet's orb!$");
    private static final Pattern COINS = Pattern.compile("^\\+([\\d,]+) Coins$");
    private static final Pattern XP = Pattern.compile("^\\+([\\d,]+) (.+) XP$");
    private static final Pattern AMOUNT = Pattern.compile("^(?:([\\d,]+)x )?(.+?)(?: x([\\d,]+))?$");
    private static final double ORB_PRICE = 5000;

    private static Tracker tracker, stats;
    private static HudEditElement hud;
    private static Feature feature;
    private static final List<Orb> orbs = new ArrayList<>();
    private static boolean show;
    private static int ticks;
    private static Object lastLevel;

    private ShinyOrbs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        stats = Tracker.create("shiny_orb_stats", "Shiny Orb Stats").fixedValue("completed", 0);
        tracker = Tracker.create("shiny_orb", "§6§lShiny Orb Profit Tracker")
                .fixedValue("orbs_used", -ORB_PRICE).name("orbs_used", "§6Shiny Orbs Used").coinKey("orbs_used")
                .extra(ShinyOrbs::extraLines);
        hud = HudEditElement.of("shinyOrbTracker", "Shiny Orb Tracker").section("Events")
                .enabled(() -> cfg().shinyOrbTracker)
                .size(() -> Math.max(80, tracker.size(Minecraft.getInstance().font)[0]),
                        () -> Math.max(30, tracker.size(Minecraft.getInstance().font)[1]))
                .preview((g, mc, x, y, s) -> tracker.draw(g, mc, x, y, s));
        feature = Feature.of("ShinyOrbs").section("Events")
                .when(() -> cfg().shinyOrbTracker || cfg().shinyOrbLines)
                .tick(ShinyOrbs::tick)
                .serverChat(ShinyOrbs::onChat)
                .world(ShinyOrbs::render)
                .hudHiddenInInventory("shinyorbtracker", ShinyOrbs::renderHud)
                .config("Events", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Year of the Pig");
                    b.toggle("Shiny Orb Tracker", () -> c.shinyOrbTracker, v -> c.shinyOrbTracker = v)
                            .desc("Loot from Shiny Orbs (items, coins, skill XP) minus 5k per orb used, with orbs charged "
                                    + "and profit per hour. Move it in /tmap gui.")
                            .tags("shiny orb", "shiny pig", "year of the pig", "technoblade");
                    b.toggle("  Only While Holding Orb / Rod", () -> c.shinyOrbHoldingOnly, v -> c.shinyOrbHoldingOnly = v);
                    b.buttonRow(new String[]{"Reset Session", "Reset Total", "Session / Total"},
                            new Runnable[]{
                                    () -> { tracker.resetSession(); stats.resetSession(); },
                                    () -> { tracker.resetTotal(); stats.resetTotal(); },
                                    tracker::toggleView});
                    b.toggle("Shiny Pig Lines", () -> c.shinyOrbLines, v -> c.shinyOrbLines = v)
                            .desc("Lines to the Shiny Pig you used an orb on, and from the pig to its orb")
                            .tags("shiny pig", "shiny orb", "line");
                    b.toggle("  Line To Pig", () -> c.shinyOrbLineToPig, v -> c.shinyOrbLineToPig = v);
                    b.toggle("  Line Pig To Orb", () -> c.shinyOrbLineToOrb, v -> c.shinyOrbLineToOrb = v);
                });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) onClick(entity);
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) onClick(entity);
            return InteractionResult.PASS;
        });
    }

    private static List<String> extraLines(Tracker.View view) {
        List<String> out = new ArrayList<>();
        long used = tracker.count("orbs_used", view);
        long done = stats.count("completed", view);
        if (used > 0) out.add("§7Orbs used: §e" + used + " §7charged: §e" + done);
        for (String k : stats.keys()) {
            if (!k.startsWith("xp_")) continue;
            long n = stats.count(k, view);
            if (n > 0) out.add("§3+" + ItemPrice.format(n) + " " + k.substring(3) + " XP");
        }
        return out;
    }

    private static void onClick(Entity entity) {
        if (feature == null || !feature.active() || !(entity instanceof Pig pig)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ItemUtil.skyblockId(mc.player.getMainHandItem()).equals("SHINY_ORB")) return;
        Vec3 start = pig.position();
        TickScheduler.schedule(20, () -> {
            for (Orb o : orbs) if (o.pig == pig) return;
            ArmorStand orb = findOrb(start);
            if (orb == null) return;
            orbs.add(new Orb(pig, orb.position().add(0, 2, 0), System.currentTimeMillis()));
            if (cfg().shinyOrbTracker) tracker.add("orbs_used", 1);
        });
    }

    private static ArmorStand findOrb(Vec3 at) {
        ArmorStand best = null;
        double bestD = 25;
        for (ArmorStand s : EntityCache.armorStands()) {
            if (s.isRemoved() || !s.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) continue;
            double d = s.position().distanceToSqr(at);
            if (d < bestD) { bestD = d; best = s; }
        }
        return best;
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (!t.startsWith("SHINY! ") && !t.startsWith("Your Shiny Orb")) return;
        if (t.equals("Your Shiny Orb and associated pig expired and disappeared.")) {
            Orb oldest = null;
            for (Orb o : orbs) if (oldest == null || o.spawnedAt < oldest.spawnedAt) oldest = o;
            if (oldest != null && oldest.pig.isRemoved()) orbs.remove(oldest);
            return;
        }
        if (!cfg().shinyOrbTracker) return;
        if (t.equals("SHINY! The orb is charged! Click on it for loot!")) {
            stats.add("completed", 1);
            tracker.markActivity();
            return;
        }
        Matcher m = LOOTED.matcher(t);
        if (!m.matches()) return;
        tracker.add("SHINY_SHARD", 1);
        String reward = m.group(1).trim();
        if ((m = COINS.matcher(reward)).matches()) {
            tracker.addCoins(Long.parseLong(m.group(1).replace(",", "")));
            return;
        }
        if ((m = XP.matcher(reward)).matches()) {
            stats.fixedValue("xp_" + m.group(2), 0);
            stats.add("xp_" + m.group(2), Long.parseLong(m.group(1).replace(",", "")));
            return;
        }
        m = AMOUNT.matcher(reward);
        if (!m.matches()) return;
        String num = m.group(1) != null ? m.group(1) : m.group(3);
        long amount = num == null ? 1 : Long.parseLong(num.replace(",", ""));
        String name = m.group(2);
        String id = PriceManager.idForName(name);
        if (id == null) id = SkyblockItems.idForName(name);
        tracker.add(id != null ? id : name, amount);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            orbs.clear();
        }
        if (++ticks % 20 == 0 && !orbs.isEmpty()) {
            long now = System.currentTimeMillis();
            orbs.removeIf(o -> o.pig.isRemoved() && now - o.spawnedAt > 2000);
        }
        if (!cfg().shinyOrbTracker) {
            show = false;
            return;
        }
        tracker.tick();
        stats.tick();
        if (ticks % 5 != 0) return;
        String held = ItemUtil.skyblockId(mc.player.getMainHandItem());
        boolean holding = held.equals("SHINY_ORB") || held.equals("SHINY_ROD");
        show = (!cfg().shinyOrbHoldingOnly || holding) && tracker.count("orbs_used", tracker.view()) > 0;
    }

    private static void renderHud(GuiGraphicsExtractor g, DeltaTracker d) {
        if (!show || hud == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        tracker.draw(g, mc, hud.x(), hud.y(), hud.scale());
    }

    private static void render(PoseStack pose, Vec3 cam) {
        if (orbs.isEmpty() || !cfg().shinyOrbLines) return;
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (Orb o : orbs) {
            if (o.pig.isRemoved()) continue;
            Vec3 pig = o.pig.getPosition(pt).add(0, 0.54, 0);
            if (cfg().shinyOrbLineToPig) ESPRenderer.drawTracerFromCamera(pose, pig, 0xFFFFAFAF, cam, true, 3f);
            if (cfg().shinyOrbLineToOrb) ESPRenderer.drawLine(pose, pig, o.orbPos.add(0, -0.5, 0), 0xFFFFFF00, 3f, cam, true);
        }
    }
}
