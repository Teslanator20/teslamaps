package com.teslamaps.features.events;

// Ported from SkyHanni's Year400Features.kt (team finder) and CenturyPartyInvitation.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.DeadMobHider;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EntityGlow;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CenturyHighlights {

    private static final Pattern LEVEL = Pattern.compile("^\\[(\\d+)]");
    private static final Pattern RANGE = Pattern.compile("^\\[(\\d+)](\\+)?(?: - \\[(\\d+)])?\\s+\\S");
    private static final Pattern PARTY = Pattern.compile("SkyBlock level color is \\[(\\d+)]");

    private enum Cake {
        PINK("SLICE_OF_STRAWBERRY_SHORTCAKE", ChatFormatting.LIGHT_PURPLE),
        BLUE("SLICE_OF_BLUEBERRY_CAKE", ChatFormatting.BLUE),
        YELLOW("SLICE_OF_CHEESECAKE", ChatFormatting.YELLOW),
        GREEN("SLICE_OF_GREEN_VELVET_CAKE", ChatFormatting.GREEN),
        RED("SLICE_OF_RED_VELVET_CAKE", ChatFormatting.RED);

        final String id;
        final ChatFormatting color;

        Cake(String id, ChatFormatting color) {
            this.id = id;
            this.color = color;
        }
    }

    private static final Cake NO_TEAM = null;
    private static final Map<UUID, Optional<Cake>> teams = new HashMap<>();
    private static final Map<UUID, Integer> levels = new HashMap<>();
    private static UUID lastPunched;
    private static long lastPunchAt;
    private static Cake inHand;
    private static List<int[]> missing = List.of();
    private static ItemStack lastInvite;
    private static Object lastLevel;
    private static int ticks;
    private static Feature feature;

    private CenturyHighlights() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("CenturyHighlights").section(Carnival.CATEGORY)
                .when(() -> cfg().centuryTeamFinder || cfg().centuryPartyInvite)
                .tick(CenturyHighlights::tick)
                .serverChat(CenturyHighlights::onChat)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Century Celebration");
                    b.toggle("Cake Team Finder", () -> c.centuryTeamFinder, v -> c.centuryTeamFinder = v)
                            .desc("Holding a Slice of Cake: players of that cake's team glow in the team colour, everyone else in the wrong-team colour. \"Too much cake\" answers are remembered.")
                            .tags("century", "cake", "team", "raffle", "anniversary");
                    b.color("  Wrong Team Color", () -> c.colorCenturyTeamWrong, v -> c.colorCenturyTeamWrong = v);
                    b.toggle("Party Invitation Helper", () -> c.centuryPartyInvite, v -> c.centuryPartyInvite = v)
                            .desc("Holding the Century Party Invitation: players whose SkyBlock level colour is still missing glow green, the rest red.")
                            .tags("century", "party", "invitation", "level color");
                    b.color("  Invitable Color", () -> c.colorCenturyInviteYes, v -> c.colorCenturyInviteYes = v);
                    b.color("  Not Needed Color", () -> c.colorCenturyInviteNo, v -> c.colorCenturyInviteNo = v);
                });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            punched(level.isClientSide(), entity);
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            punched(level.isClientSide(), entity);
            return InteractionResult.PASS;
        });
    }

    private static void punched(boolean client, Entity entity) {
        if (!client || inHand == null || feature == null || !feature.active() || !(entity instanceof Player)) return;
        lastPunched = entity.getUUID();
        lastPunchAt = System.currentTimeMillis();
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        String s = StringUtil.stripTrim(raw);
        if (c.centuryTeamFinder && s.equals("This person has had too much cake today!")) {
            if (lastPunched != null && System.currentTimeMillis() - lastPunchAt < 500) teams.put(lastPunched, Optional.empty());
            lastPunched = null;
            return;
        }
        if (!c.centuryPartyInvite) return;
        if (s.startsWith("PARTY! ")) {
            Matcher m = PARTY.matcher(s);
            if (m.find()) {
                int min = Integer.parseInt(m.group(1));
                List<int[]> next = new ArrayList<>(missing);
                next.removeIf(r -> r[0] == min);
                missing = next;
            }
        } else if (s.startsWith("You had already gained the bonus, so... at least everyone is now invited!")) {
            lastInvite = null;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            teams.clear();
            levels.clear();
        }
        if (mc.player == null) return;
        TeslaMapsConfig c = cfg();
        ItemStack hand = mc.player.getMainHandItem();
        String id = ItemUtil.skyblockId(hand);

        inHand = null;
        if (c.centuryTeamFinder) for (Cake k : Cake.values()) if (k.id.equals(id)) inHand = k;
        boolean invite = c.centuryPartyInvite && "CENTURY_PARTY_INVITATION".equals(id);
        if (invite && hand != lastInvite) {
            lastInvite = hand;
            missing = readMissing(hand);
        }
        if (inHand == null && !invite) return;

        boolean scan = ++ticks % 10 == 0;
        int wrong = TeslaMapsConfig.parseColor(c.colorCenturyTeamWrong) & 0xFFFFFF;
        int yes = TeslaMapsConfig.parseColor(c.colorCenturyInviteYes) & 0xFFFFFF;
        int no = TeslaMapsConfig.parseColor(c.colorCenturyInviteNo) & 0xFFFFFF;
        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().version() == 2 || !DeadMobHider.isRealPlayer(p)) continue;
            if (scan || !levels.containsKey(p.getUUID())) read(p);
            if (inHand != null) {
                Optional<Cake> team = teams.get(p.getUUID());
                boolean right = team != null && team.orElse(NO_TEAM) == inHand;
                EntityGlow.mark(p, right ? inHand.color.getColor() : wrong);
            } else {
                Integer lvl = levels.get(p.getUUID());
                if (lvl == null || lvl < 0) continue;
                EntityGlow.mark(p, inRange(lvl) ? yes : no);
            }
        }
    }

    private static boolean inRange(int lvl) {
        for (int[] r : missing) if (lvl >= r[0] && lvl <= r[1]) return true;
        return false;
    }

    private static void read(Player p) {
        UUID id = p.getUUID();
        String name = StringUtil.stripTrim(p.getDisplayName().getString());
        Matcher m = LEVEL.matcher(name);
        levels.put(id, m.find() ? Integer.parseInt(m.group(1)) : -1);
        Optional<Cake> known = teams.get(id);
        if (known != null && known.isEmpty()) return;
        Optional<ChatFormatting> emblem = p.getDisplayName().visit((Style style, String text) -> {
            if (text.indexOf('⛃') < 0) return Optional.empty();
            TextColor tc = style.getColor();
            if (tc == null) return Optional.empty();
            for (ChatFormatting f : ChatFormatting.values()) {
                if (f.isColor() && f.getColor() != null && f.getColor() == tc.getValue()) return Optional.of(f);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (emblem.isEmpty() || emblem.get() == ChatFormatting.GOLD) return;
        for (Cake k : Cake.values()) {
            if (k.color == emblem.get()) {
                teams.put(id, Optional.of(k));
                return;
            }
        }
    }

    private static List<int[]> readMissing(ItemStack stack) {
        List<int[]> out = new ArrayList<>();
        boolean after = false;
        for (String line : ItemUtil.loreLines(stack)) {
            String l = line.trim();
            if (l.startsWith("Level colors missing:") || l.startsWith("Level color missing:")) {
                after = true;
                continue;
            }
            if (!after) continue;
            Matcher m = RANGE.matcher(l);
            if (!m.find()) continue;
            int min = Integer.parseInt(m.group(1));
            int max = m.group(2) != null ? Integer.MAX_VALUE : m.group(3) != null ? Integer.parseInt(m.group(3)) : min;
            out.add(new int[]{min, max});
        }
        return out;
    }
}
