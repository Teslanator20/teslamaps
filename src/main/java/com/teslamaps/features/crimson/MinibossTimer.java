package com.teslamaps.features.crimson;

// After SkyHanni's nether/CrimsonMinibossRespawnTimer.kt and GanKura's CrimsonIsleStatusHud
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.EntityCache;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class MinibossTimer {

    static final class Boss {
        final String name, down, color;
        final AABB area;
        final BlockPos spawn;
        final Pattern tag;
        long nextSpawn;
        boolean estimated;
        Boolean spawned;
        String status = "§7Unknown";

        Boss(String name, String color, AABB area, BlockPos spawn) {
            this.name = name;
            this.color = color;
            this.down = name.toUpperCase(java.util.Locale.ROOT) + " DOWN!";
            this.area = area;
            this.spawn = spawn;
            this.tag = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(name) + "\\s+[\\d.,]+[kKmMbB]?(?:/|❤|\\s|$)");
        }

        void reset() {
            nextSpawn = 0;
            estimated = false;
            spawned = null;
        }
    }

    private static final long RESPAWN_MS = 120_000;
    static final Boss[] BOSSES = {
            new Boss("Bladesoul", "§8", new AABB(-330, 80, -545, -257, 107, -486), new BlockPos(-295, 83, -518)),
            new Boss("Mage Outlaw", "§5", new AABB(-200, 98, -878, -162, 116, -843), new BlockPos(-181, 106, -860)),
            new Boss("Barbarian Duke X", "§c", new AABB(-550, 101, -918, -522, 131, -890), new BlockPos(-537, 117, -905)),
            new Boss("Ashfang", "§7", new AABB(-507, 131, -1035, -462, 155, -955), new BlockPos(-485, 137, -1016)),
            new Boss("Magma Boss", "§6", new AABB(-442, 59, -851, -318, 90, -751), new BlockPos(-369, 66, -805)),
    };
    private static final List<String> SAMPLE = List.of("§c§lMiniboss Timers", "§8Bladesoul§7: §aSPAWNED!",
            "§5Mage Outlaw§7: §e1:12", "§cBarbarian Duke X§7: §6Soon!", "§7Ashfang§7: §cKilled", "§6Magma Boss§7: §7Unknown");

    private static HudEditElement hud;
    private static List<String> lines = List.of();
    private static int width, ticks;
    private static Object lastLevel;

    private MinibossTimer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("crimsonMinibossTimer", "Crimson Miniboss Timers").section("Crimson Isle")
                .enabled(() -> cfg().crimsonMinibossTimer)
                .size(() -> lines.isEmpty() ? TextHud.width(SAMPLE) : width, () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        lines.isEmpty() ? TextHud.width(SAMPLE) : width));
        Feature.of("CrimsonMinibossTimer").section("Crimson Isle").when(() -> cfg().crimsonMinibossTimer)
                .tick(MinibossTimer::tick)
                .serverChat(MinibossTimer::onChat)
                .hudHiddenInInventory("crimsonminibosstimer", MinibossTimer::render)
                .world(MinibossTimer::renderWorld)
                .config("Crimson Isle", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Minibosses");
                    b.toggle("Miniboss Respawn Timers", () -> c.crimsonMinibossTimer, v -> c.crimsonMinibossTimer = v)
                            .desc("Bladesoul, Mage Outlaw, Barbarian Duke X, Ashfang and Magma Boss: SPAWNED, 2 min timer after DOWN!, "
                                    + "Soon! on BEWARE, Killed when the spawn is loaded but empty. Move it in /tmap gui")
                            .tags("miniboss", "bladesoul", "ashfang", "magma boss", "duke", "outlaw", "respawn", "crimson");
                    b.toggle("  Labels At Spawn Points", () -> c.crimsonMinibossLabels, v -> c.crimsonMinibossLabels = v);
                });
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.endsWith(" DOWN!")) {
            for (Boss b : BOSSES) {
                if (!t.equals(b.down)) continue;
                b.nextSpawn = System.currentTimeMillis() + RESPAWN_MS;
                b.estimated = false;
                b.spawned = false;
                return;
            }
        } else if (t.startsWith("BEWARE - ") && t.endsWith(" Is Spawning.")) {
            String name = t.substring(9, t.length() - 13);
            for (Boss b : BOSSES) {
                if (!b.name.equalsIgnoreCase(name)) continue;
                b.spawned = true;
                b.nextSpawn = 0;
                return;
            }
        }
    }

    /** Status line of one boss, for the reputation helper. */
    static String status(String name) {
        for (Boss b : BOSSES) if (b.name.equalsIgnoreCase(name)) return b.status;
        return null;
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            for (Boss b : BOSSES) b.reset();
        }
        if (++ticks % 5 != 0) return;
        if (!Crimson.here()) {
            lines = List.of();
            return;
        }
        long now = System.currentTimeMillis();
        if (ticks % 20 == 0) scan(mc, now);
        List<String> out = new ArrayList<>(BOSSES.length + 1);
        out.add("§c§lMiniboss Timers");
        for (Boss b : BOSSES) {
            b.status = status(b, now);
            out.add(b.color + b.name + "§7: " + b.status);
        }
        lines = out;
        width = TextHud.width(out);
    }

    private static void scan(Minecraft mc, long now) {
        Vec3 p = mc.player.position();
        for (Boss b : BOSSES) {
            if (p.distanceToSqr(Vec3.atCenterOf(b.spawn)) > 80 * 80 || !mc.level.isLoaded(b.spawn)) continue;
            boolean found = false;
            for (ArmorStand as : EntityCache.namedArmorStands()) {
                if (as.isRemoved() || !b.area.contains(as.position())) continue;
                if (b.tag.matcher(StringUtil.strip(as.getCustomName().getString())).find()) {
                    found = true;
                    break;
                }
            }
            if (found) {
                b.spawned = true;
                b.nextSpawn = 0;
            } else if (Boolean.TRUE.equals(b.spawned) || b.spawned == null) {
                if (Boolean.TRUE.equals(b.spawned)) {
                    b.nextSpawn = now + RESPAWN_MS;
                    b.estimated = true;
                }
                b.spawned = false;
            }
        }
    }

    private static String status(Boss b, long now) {
        if (Boolean.TRUE.equals(b.spawned)) return "§aSPAWNED!";
        if (b.nextSpawn > 0) {
            long left = b.nextSpawn - now;
            if (left > 0) return (b.estimated ? "§e~" : "§e") + Crimson.mmss(left);
            if (left > -20_000) return "§6Soon!";
        }
        if (Boolean.FALSE.equals(b.spawned)) return "§cKilled";
        return "§7Unknown";
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || lines.isEmpty() || hud == null) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), lines, width);
    }

    private static void renderWorld(PoseStack pose, Vec3 cam) {
        if (!cfg().crimsonMinibossLabels || lines.isEmpty()) return;
        for (Boss b : BOSSES) {
            ESPRenderer.drawText(pose, b.color + b.name, new Vec3(b.spawn.getX() + 0.5, b.spawn.getY() + 4, b.spawn.getZ() + 0.5), 1.5f, cam);
            ESPRenderer.drawText(pose, b.status, new Vec3(b.spawn.getX() + 0.5, b.spawn.getY() + 3.4, b.spawn.getZ() + 0.5), 1.5f, cam);
        }
    }
}
