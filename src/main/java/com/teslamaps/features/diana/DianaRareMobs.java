package com.teslamaps.features.diana;

// Ported from SkyHanni's RareMobWaypointShare.kt and Skysoft's DianaRareMobSharing.kt,
// DianaLootshareReadyMessage.kt, DianaLootshareReadyMarkers.kt, DamageSplashAttribution.kt
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.Announce;
import com.teslamaps.features.waypoints.WaypointSet;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.NametagMobs;
import com.teslamaps.utils.NumberUtil;
import com.teslamaps.utils.PlayerChat;
import com.teslamaps.utils.StringUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DianaRareMobs {

    static final List<String> RARE = List.of("Minos Inquisitor", "Manticore", "King Minos", "Sphinx");

    private static final Pattern COORDS = Pattern.compile(
            "^x: (-?\\d+(?:\\.\\d+)?),? y: (-?\\d+(?:\\.\\d+)?),? z: (-?\\d+(?:\\.\\d+)?)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INQUIS = Pattern.compile(
            "^A MINOS INQUISITOR has spawned near \\[.*] at Coords (-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?) (-?\\d+(?:\\.\\d+)?)");
    private static final Pattern FOUND = Pattern.compile("^Found (?:a |an )?(.+?)! @ (-?\\d+) (-?\\d+) (-?\\d+)$");
    private static final Pattern DEAD = Pattern.compile("^(?:Inquisitor|Rare Diana Mob) dead!$|^Defeated (?:a |an )?.+!$");
    private static final Pattern DUG = Pattern.compile("You dug out (?:a |an )?([^!]+)!");
    private static final Pattern HP = Pattern.compile("([\\d.,]+[kKmMbB]?)/([\\d.,]+[kKmMbB]?)❤");
    private static final Pattern SPLASH = Pattern.compile("^[✧✯]?([\\d,.]+[KMBkmb]?)[⚔+✧❤♞☄✷ﬗ✯]*$");
    private static final String SECURED = "Loot share secured!";
    private static final long SHARED_MS = 75_000, CHECK_MS = 75_000;

    private record Shared(String player, String mob, BlockPos pos, long at) {}

    static final class Tracked {
        final ArmorStand tag;
        final String mob;
        LivingEntity entity;
        double current = -1, max;
        long damage, lastAttack, seen;
        boolean own, eligible, sharedDeath;
        String label = "";
        final IntOpenHashSet splashes = new IntOpenHashSet();
        Tracked(ArmorStand tag, String mob) { this.tag = tag; this.mob = mob; }
    }

    private static final Map<String, Shared> shared = new LinkedHashMap<>();
    private static final Map<String, String> sharedText = new HashMap<>();
    private static final Map<Integer, Tracked> tracked = new HashMap<>();
    private static final Map<String, Long> readyPlayers = new HashMap<>();
    private static final List<Player> checkPlayers = new ArrayList<>();

    private static String pendingMob;
    private static long pendingAt, lastShareAt;
    private static Tracked own;
    private static boolean ownShared, keyDown;
    private static int timer;
    private static Object lastLevel;
    private static Feature feature;

    private DianaRareMobs() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("DianaRareMobs").section("Events").when(() -> cfg().dianaRareShare || cfg().dianaLootshare)
                .tick(DianaRareMobs::tick)
                .world(DianaRareMobs::render)
                .chat(DianaRareMobs::onChat)
                .config("Events", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Rare Mob Sharing", () -> c.dianaRareShare, v -> c.dianaRareShare = v)
                            .desc("Dig out an Inquisitor, Manticore, King Minos or Sphinx: its coords go to party chat. Pings from party members show as waypoint with a title.")
                            .tags("diana", "inquisitor", "manticore", "king minos", "sphinx", "share", "party");
                    b.toggle("  Share Instantly", () -> c.dianaRareShareInstant, v -> c.dianaRareShareInstant = v)
                            .desc("Off = a clickable chat line and the share key instead");
                    b.keybind("  Share Key", () -> c.dianaRareShareKey, v -> c.dianaRareShareKey = v);
                    b.toggle("  Announce Death", () -> c.dianaRareShareDeath, v -> c.dianaRareShareDeath = v)
                            .desc("Sends \"Rare Diana Mob dead!\" when your shared mob dies, which clears it for the others");
                    b.searchList("  Don't Share", () -> RARE, c.dianaRareNoShare::contains,
                            n -> { if (!c.dianaRareNoShare.remove(n)) c.dianaRareNoShare.add(n); });
                    b.searchList("  Don't Show", () -> RARE, c.dianaRareNoShow::contains,
                            n -> { if (!c.dianaRareNoShow.remove(n)) c.dianaRareNoShow.add(n); });
                    b.toggle("Lootshare Helper", () -> c.dianaLootshare, v -> c.dianaLootshare = v)
                            .desc("Damage splashes right after your hits count as your damage. Above another player's rare mob: \"Lootshare\" red until you dealt 1% of its health, then green.")
                            .tags("diana", "lootshare", "inquisitor", "damage");
                    b.toggle("  Party Message When Secured", () -> c.dianaLootshareMessage, v -> c.dianaLootshareMessage = v);
                    b.toggle("  Checkmark Over Secured Players", () -> c.dianaLootshareChecks, v -> c.dianaLootshareChecks = v)
                            .desc("Party members who posted \"Loot share secured!\" get a ✓ over their head");
                    b.toggle("  30 Block Radius Circle", () -> c.dianaLootshareCircle, v -> c.dianaLootshareCircle = v);
                });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) onAttack(entity);
            return InteractionResult.PASS;
        });
    }

    private static boolean on() {
        return feature != null && feature.active() && DianaBurrows.onHub();
    }

    /** Newest party ping still alive, for the nearest-warp helper. */
    static Vec3 remoteTarget() {
        if (!cfg().dianaRareShare || !on()) return null;
        Shared last = null;
        for (Shared s : shared.values()) last = s;
        return last == null ? null : Vec3.atCenterOf(last.pos);
    }

    private static String rareIn(String text) {
        for (String r : RARE) if (text.toLowerCase(Locale.ROOT).contains(r.toLowerCase(Locale.ROOT))) return r;
        return null;
    }

    private static void onAttack(Entity e) {
        if (!cfg().dianaLootshare || !on()) return;
        long now = System.currentTimeMillis();
        for (Tracked t : tracked.values()) {
            if (e == t.entity || e == t.tag || (t.entity != null && e.distanceToSqr(t.entity) < 4)) t.lastAttack = now;
        }
    }

    // ---- chat ----

    private static void onChat(String raw) {
        if (!on()) return;
        TeslaMapsConfig c = cfg();
        PlayerChat.Msg m = PlayerChat.parse(raw);
        if (m == null) {
            if (!c.dianaRareShare || !StringUtil.isServerMessage(raw)) return;
            String t = StringUtil.stripTrim(raw);
            if (t.startsWith("RARE DROP!")) return;
            Matcher d = DUG.matcher(t);
            if (d.find()) {
                String mob = rareIn(d.group(1));
                if (mob != null) {
                    pendingMob = mob;
                    pendingAt = System.currentTimeMillis();
                }
            }
            return;
        }
        if (m.channel() != PlayerChat.Channel.PARTY || m.own()) return;
        String body = m.message().trim();
        if (body.equals(SECURED)) {
            if (c.dianaLootshare && c.dianaLootshareChecks) readyPlayers.put(m.author().toLowerCase(Locale.ROOT), System.currentTimeMillis() + CHECK_MS);
            return;
        }
        if (!c.dianaRareShare) return;
        if (DEAD.matcher(body).matches()) {
            shared.remove(m.author().toLowerCase(Locale.ROOT));
            return;
        }
        String mob;
        double x, y, z;
        Matcher k = COORDS.matcher(body);
        if (k.matches()) {
            String rest = k.group(4).trim();
            mob = rest.isEmpty() ? "Rare Mob" : rareIn(rest);
            if (mob == null) return;
            x = Double.parseDouble(k.group(1));
            y = Double.parseDouble(k.group(2));
            z = Double.parseDouble(k.group(3));
        } else if ((k = INQUIS.matcher(body)).find()) {
            mob = "Minos Inquisitor";
            x = Double.parseDouble(k.group(1));
            y = Double.parseDouble(k.group(2));
            z = Double.parseDouble(k.group(3));
        } else if ((k = FOUND.matcher(body)).matches()) {
            mob = rareIn(k.group(1));
            if (mob == null) return;
            x = Double.parseDouble(k.group(2));
            y = Double.parseDouble(k.group(3));
            z = Double.parseDouble(k.group(4));
        } else return;
        if (c.dianaRareNoShow.contains(mob)) return;
        String key = m.author().toLowerCase(Locale.ROOT);
        boolean fresh = !shared.containsKey(key);
        shared.remove(key);
        shared.put(key, new Shared(m.author(), mob, BlockPos.containing(x, y, z), System.currentTimeMillis()));
        if (fresh) {
            DianaBurrows.msg("§b" + m.author() + " §efound " + (mob.startsWith("A") ? "an " : "a ") + "§d" + mob + " §eat §c"
                    + (int) Math.floor(x) + " " + (int) Math.floor(y) + " " + (int) Math.floor(z) + "§e!");
            DianaBurrows.title("§d" + mob + " §efrom §b" + m.author());
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
        }
    }

    // ---- sharing ----

    private static void share() {
        Tracked t = own;
        long now = System.currentTimeMillis();
        if (t == null || ownShared || now - lastShareAt < 5000) return;
        if (cfg().dianaRareNoShare.contains(t.mob)) return;
        if (!t.tag.isAlive()) {
            DianaBurrows.msg("§cRare mob out of range.");
            return;
        }
        ownShared = true;
        lastShareAt = now;
        Announce.party(coords(t));
    }

    private static String coords(Tracked t) {
        Entity e = t.entity != null ? t.entity : t.tag;
        return "x: " + (int) Math.floor(e.getX()) + ", y: " + (int) Math.floor(e.getY()) + ", z: " + (int) Math.floor(e.getZ()) + " | " + t.mob;
    }

    private static void foundOwn(Tracked t) {
        own = t;
        ownShared = false;
        t.own = true;
        pendingMob = null;
        TeslaMapsConfig c = cfg();
        if (c.dianaRareNoShare.contains(t.mob)) return;
        if (c.dianaRareShareInstant) {
            share();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String key = c.dianaRareShareKey != -1 ? " §bor press §c" + KeyNames.display(c.dianaRareShareKey) : "";
        mc.player.sendSystemMessage(Component.literal(DianaBurrows.PREFIX + "§bYou found a §d" + t.mob + "§b! Click §chere" + key + " §bto share it.")
                .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/pc " + coords(t)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eShare with your party")))));
    }

    // ---- tick ----

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            shared.clear();
            tracked.clear();
            readyPlayers.clear();
            own = null;
            pendingMob = null;
        }
        if (!DianaBurrows.onHub()) return;
        TeslaMapsConfig c = cfg();
        long now = System.currentTimeMillis();

        boolean down = mc.screen == null && c.dianaRareShareKey != -1 && KeyNames.isDown(c.dianaRareShareKey);
        if (down && !keyDown && c.dianaRareShare) share();
        keyDown = down;

        if (timer++ % 5 == 0) scan(mc, now);
        if (c.dianaLootshare) splashes(mc, now);

        shared.values().removeIf(s -> now - s.at > SHARED_MS);
        sharedText.clear();
        for (Map.Entry<String, Shared> e : shared.entrySet()) {
            Shared s = e.getValue();
            sharedText.put(e.getKey(), "§d" + s.mob + " §7(" + s.player + ") §e"
                    + Math.round(Math.sqrt(mc.player.distanceToSqr(Vec3.atCenterOf(s.pos)))) + "m");
        }
        readyPlayers.values().removeIf(t -> t < now);
        checkPlayers.clear();
        if (c.dianaLootshare && c.dianaLootshareChecks && !readyPlayers.isEmpty()) {
            for (Player p : mc.level.players()) {
                if (p != mc.player && readyPlayers.containsKey(p.getGameProfile().name().toLowerCase(Locale.ROOT))) checkPlayers.add(p);
            }
        }
    }

    private static void scan(Minecraft mc, long now) {
        for (ArmorStand tag : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
            String name = NametagMobs.tagName(tag);
            if (name == null || name.indexOf('❤') < 0) continue;
            String mob = rareIn(name);
            if (mob == null) continue;
            Tracked t = tracked.computeIfAbsent(tag.getId(), id -> new Tracked(tag, mob));
            t.seen = now;
            if (t.entity == null || !t.entity.isAlive()) t.entity = NametagMobs.mobForTag(tag);
            Matcher hp = HP.matcher(name);
            if (hp.find()) {
                t.current = NumberUtil.parseSuffixed(hp.group(1));
                t.max = Math.max(t.max, NumberUtil.parseSuffixed(hp.group(2)));
            }
            if (pendingMob != null && !t.own && own != t && mob.equals(pendingMob) && now - pendingAt < 3000
                    && mc.player.distanceToSqr(tag) < 225) foundOwn(t);
        }
        if (pendingMob != null && now - pendingAt > 3000) pendingMob = null;
        tracked.values().removeIf(t -> {
            boolean dead = t.current == 0 || (t.entity != null && t.entity.isDeadOrDying());
            if (t == own && dead && ownShared && !t.sharedDeath && cfg().dianaRareShare && cfg().dianaRareShareDeath) {
                t.sharedDeath = true;
                Announce.party("Rare Diana Mob dead!");
            }
            boolean gone = dead || !t.tag.isAlive() || now - t.seen > 2000;
            if (gone && t == own) own = null;
            return gone;
        });
        for (Tracked t : tracked.values()) {
            if (t.own || t.max <= 0) {
                t.label = "";
                continue;
            }
            long need = Math.max(1, (long) (t.max * 0.01));
            t.label = t.eligible ? "§a§lLootshare ✔" : "§c§lLootshare §7" + Math.min(99, t.damage * 100 / need) + "%";
        }
    }

    private static void splashes(Minecraft mc, long now) {
        for (Tracked t : tracked.values()) {
            if (t.own || t.eligible || t.max <= 0 || now - t.lastAttack > 900) continue;
            Entity anchor = t.entity != null ? t.entity : t.tag;
            for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, anchor.getBoundingBox().inflate(5))) {
                if (s == t.tag || !t.splashes.add(s.getId())) continue;
                String name = NametagMobs.tagName(s);
                if (name == null) continue;
                Matcher m = SPLASH.matcher(name.replace(",", ""));
                if (!m.matches()) continue;
                t.damage += (long) NumberUtil.parseSuffixed(m.group(1));
            }
            if (t.damage >= Math.max(1, (long) (t.max * 0.01))) {
                t.eligible = true;
                if (cfg().dianaLootshareMessage) Announce.party(SECURED);
            }
        }
    }

    // ---- render ----

    private static void render(PoseStack pose, Vec3 cam) {
        TeslaMapsConfig c = cfg();
        if (c.dianaRareShare) {
            for (Map.Entry<String, Shared> e : shared.entrySet()) {
                String text = sharedText.get(e.getKey());
                if (text != null) WaypointSet.draw(pose, cam, e.getValue().pos, 0xFFFF55FF, text, true, true, true);
            }
        }
        if (!c.dianaLootshare) return;
        for (Tracked t : tracked.values()) {
            if (t.own) continue;
            Entity e = t.entity != null ? t.entity : t.tag;
            if (!t.label.isEmpty()) WaypointSet.drawLabel(pose, cam, t.label, t.tag.position().add(0, 0.8, 0), 1.2f, true);
            if (c.dianaLootshareCircle) {
                Minecraft mc = Minecraft.getInstance();
                boolean in = mc.player != null && mc.player.distanceToSqr(e) <= 900;
                ESPRenderer.drawCircle(pose, e.getX(), e.getY() + 0.05, e.getZ(), 30, in ? 0xFF55FF55 : 0xFFFFFFFF, 2f, cam, true);
            }
        }
        for (int i = 0, n = checkPlayers.size(); i < n; i++) {
            Player p = checkPlayers.get(i);
            WaypointSet.drawLabel(pose, cam, "§b§l✓", p.position().add(0, p.getBbHeight() + 0.9, 0), 1.5f, true);
        }
    }
}
