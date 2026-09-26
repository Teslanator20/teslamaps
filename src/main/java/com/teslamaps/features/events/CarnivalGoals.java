package com.teslamaps.features.events;

// Ported from SkyHanni's CarnivalGoal.kt, CarnivalReminder.kt and CarnivalQuickStart.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.hud.TextHud;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class CarnivalGoals {

    private enum Game {
        FRUIT_DIGGING("§6Fruit Digging", Items.SAND),
        CATCH_A_FISH("§3Catch a Fish", Items.COD),
        ZOMBIE_SHOOTOUT("§cZombie Shootout", Items.ARROW);

        final String display;
        final Item item;

        Game(String display, Item item) {
            this.display = display;
            this.item = item;
        }
    }

    private enum Goal {
        FRUIT_DIGGING_PLAY(Game.FRUIT_DIGGING, "Play 3 games of Fruit Digging.", "Play §a3 games"),
        FRUIT_DIGGING_SCORE(Game.FRUIT_DIGGING, "Reach 3,000 score in a single game of Fruit Digging.", "Reach §a3,000 score"),
        DIG_APPLE(Game.FRUIT_DIGGING, "Dig up 3 Apples in a single game of Fruit Digging.", "Dig up §a3 Apples"),
        UNIQUE_FRUIT(Game.FRUIT_DIGGING, "Dig up 5 unique Fruits in a single game of Fruit Digging.", "Dig up §a5 unique Fruits"),
        DRAGONFRUIT(Game.FRUIT_DIGGING, "Dig up a Dragonfruit.", "Dig up a §dDragonfruit"),
        CATCH_A_FISH_PLAY(Game.CATCH_A_FISH, "Play 3 games of Catch a Fish.", "Play §a3 games"),
        CATCH_A_FISH_SCORE(Game.CATCH_A_FISH, "Reach 3,000 score in a single game of Catch a Fish.", "Reach §a3,000 score"),
        CATCH_FISH(Game.CATCH_A_FISH, "Catch 30 Fish in a single game of Catch a Fish.", "Catch §a30 Fish"),
        CATCH_YELLOW_FISH(Game.CATCH_A_FISH, "Catch 5 Yellow Fish in a single game of Catch a Fish.", "Catch §a5 Yellow Fish"),
        CATCH_STREAK(Game.CATCH_A_FISH, "Reach a 5 Catch Streak in a single game of Catch a Fish.", "Reach a §a5 Catch Streak"),
        ZOMBIE_SHOOTOUT_PLAY(Game.ZOMBIE_SHOOTOUT, "Play 3 games of Zombie Shootout.", "Play §a3 games"),
        ZOMBIE_SHOOTOUT_SCORE(Game.ZOMBIE_SHOOTOUT, "Reach 3,000 score in a single game of Zombie Shootout.", "Reach §a3,000 score"),
        SHOOT_ZOMBIE(Game.ZOMBIE_SHOOTOUT, "Shoot 60 Zombies in a single game of Zombie Shootout.", "Shoot §a60 Zombies"),
        SHOOT_DIAMOND_ZOMBIE(Game.ZOMBIE_SHOOTOUT, "Shoot 5 Diamond Zombies in a single game of Zombie Shootout.", "Shoot §a5 Diamond Zombies"),
        SHOOT_LAMPS(Game.ZOMBIE_SHOOTOUT, "Shoot 5 Redstone Lamps in a single game of Zombie Shootout.", "Shoot §a5 Redstone Lamps");

        final Game game;
        final String text;
        final String display;

        Goal(Game game, String text, String display) {
            this.game = game;
            this.text = text;
            this.display = display;
        }
    }

    private static final List<String> SAMPLE = List.of("§6Fruit Digging", " Play §a3 games", "§cZombie Shootout", " Shoot §a60 Zombies");
    private static final long SKYBLOCK_EPOCH = 1_560_275_700_000L;
    private static final long SKYBLOCK_YEAR = 446_400_000L;

    private static List<String> lines = List.of();
    private static boolean dirty = true;
    private static Screen lastScreen;
    private static int screenTicks, ticks;
    private static long nextReminder;
    private static long lastPrompt, lastQuickClick;
    private static HudEditElement hud;
    private static Feature feature;

    private CarnivalGoals() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        hud = HudEditElement.of("carnivalGoals", "Carnival Goals").ctx(HudEditElement.HudCtx.ANY).section(Carnival.CATEGORY)
                .enabled(() -> cfg().carnivalGoals)
                .size(() -> TextHud.width(lines.isEmpty() ? SAMPLE : lines), () -> TextHud.height(lines.isEmpty() ? SAMPLE.size() : lines.size()))
                .preview((g, mc, x, y, s) -> TextHud.draw(g, mc, x, y, s, lines.isEmpty() ? SAMPLE : lines,
                        TextHud.width(lines.isEmpty() ? SAMPLE : lines)));
        feature = Feature.of("CarnivalGoals").section(Carnival.CATEGORY)
                .when(() -> cfg().carnivalGoals || cfg().carnivalTicketReminder || cfg().carnivalQuickStart)
                .tick(CarnivalGoals::tick)
                .serverChat(CarnivalGoals::onChat)
                .hudHiddenInInventory("carnivalgoals", CarnivalGoals::render)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Carnival Goals HUD", () -> c.carnivalGoals, v -> c.carnivalGoals = v)
                            .desc("At the Carnival: the goals you still need in all three games. Open the Carnival Goals menu once to load them. Position in /tmap gui")
                            .tags("carnival", "goals");
                    b.toggle("  Daily Ticket Reminder", () -> c.carnivalTicketReminder, v -> c.carnivalTicketReminder = v)
                            .desc("Chat reminder every 5 minutes while the Carnival is open and today's Carnival Tickets are unclaimed. Click it to warp.");
                    b.toggle("  Double Click To Start", () -> c.carnivalQuickStart, v -> c.carnivalQuickStart = v)
                            .desc("Clicking a Carnival game NPC again while its dialog is open starts the game straight away.");
                });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && feature.active() && cfg().carnivalQuickStart && quickStart(entity)) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
    }

    private static String profile() {
        return Carnival.profile();
    }

    private static List<String> done() {
        long year = (System.currentTimeMillis() - SKYBLOCK_EPOCH) / SKYBLOCK_YEAR + 1;
        String key = profile();
        List<String> list = cfg().carnivalGoalsDone.get(key);
        String stamp = "year:" + year;
        if (list == null || list.isEmpty() || !list.get(0).equals(stamp)) {
            list = new ArrayList<>();
            list.add(stamp);
            cfg().carnivalGoalsDone.put(key, list);
        }
        return list;
    }

    private static void setDone(Goal g, boolean v) {
        List<String> d = done();
        boolean had = d.contains(g.name());
        if (v == had) return;
        if (v) d.add(g.name());
        else d.remove(g.name());
        TeslaMapsConfig.save();
        dirty = true;
    }

    private static void onChat(String raw) {
        TeslaMapsConfig c = cfg();
        String s = StringUtil.stripTrim(raw);
        if (c.carnivalQuickStart && s.startsWith("Select an option:")) lastPrompt = System.currentTimeMillis();
        if (c.carnivalTicketReminder && (s.equals("You claimed Carnival Ticket x25!")
                || s.startsWith("[NPC] Carnival Leader: You've already claimed your Carnival Tickets for today"))) {
            c.carnivalTicketsClaimed.put(profile(), today());
            TeslaMapsConfig.save();
        }
        if (c.carnivalGoals && (s.contains(" of ") || s.contains("Dragonfruit"))) {
            String t = s.startsWith("- ") ? s.substring(2) : s;
            for (Goal g : Goal.values()) if (g.text.equals(t)) setDone(g, true);
        }
    }

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    private static void tick() {
        TeslaMapsConfig c = cfg();
        if (c.carnivalGoals) readMenu();
        if (++ticks % 20 != 0) return;
        if (c.carnivalGoals) {
            if (dirty) rebuild();
        } else if (!lines.isEmpty()) {
            lines = List.of();
        }
        if (c.carnivalTicketReminder) remind(c);
    }

    private static void readMenu() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            lastScreen = null;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            screenTicks = 0;
        }
        if (screenTicks++ % 10 != 5) return;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).equals("Carnival Goals")) return;
        int size = Math.min(cs.getMenu().slots.size() - 36, 54);
        for (int i = 0; i < size; i++) {
            ItemStack stack = cs.getMenu().getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(stack);
            if (lore.isEmpty()) continue;
            String first = lore.get(0).trim();
            if (first.isEmpty()) continue;
            for (Goal g : Goal.values()) {
                if (!stack.is(g.game.item) || !g.text.startsWith(first)) continue;
                setDone(g, lore.get(lore.size() - 1).trim().equals("COMPLETE"));
                break;
            }
        }
    }

    private static void rebuild() {
        dirty = false;
        List<String> d = done();
        List<String> out = new ArrayList<>();
        for (Game game : Game.values()) {
            List<String> open = new ArrayList<>();
            for (Goal g : Goal.values()) if (g.game == game && !d.contains(g.name())) open.add(" " + g.display);
            if (open.isEmpty()) continue;
            out.add(game.display);
            out.addAll(open);
        }
        lines = out;
    }

    private static void remind(TeslaMapsConfig c) {
        long now = System.currentTimeMillis();
        if (now < nextReminder) return;
        if (!SkyblockUtils.isInSkyblock() || !Carnival.perkActive() || DungeonManager.isInDungeon()) return;
        if (today().equals(c.carnivalTicketsClaimed.get(profile()))) return;
        nextReminder = now + 5 * 60_000L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §eCarnival Tickets are ready to be claimed! §7(click to warp)")
                .withStyle(st -> st.withClickEvent(new ClickEvent.RunCommand("/warp carnival"))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("§e/warp carnival")))));
    }

    private static boolean quickStart(Entity entity) {
        if (!Carnival.inArea()) return false;
        long now = System.currentTimeMillis();
        if (now - lastPrompt > 5000 || now - lastQuickClick < 1000) return false;
        String type = npcType(entity);
        if (type == null) return false;
        lastQuickClick = now;
        lastPrompt = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return false;
        mc.getConnection().sendCommand("selectnpcoption " + type + " r_2_1");
        return true;
    }

    private static String npcType(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        List<Entity> near = new ArrayList<>(mc.level.getEntitiesOfClass(ArmorStand.class, entity.getBoundingBox().inflate(0.5, 2.5, 0.5)));
        near.add(entity);
        for (Entity e : near) {
            Component name = e.getCustomName();
            if (name == null) continue;
            String n = StringUtil.stripTrim(name.getString());
            if (n.contains("Carnival Cowboy")) return "carnival_cowboy";
            if (n.contains("Carnival Fisherman")) return "carnival_fisherman";
            if (n.contains("Carnival Pirateman")) return "carnival_pirateman";
        }
        return null;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker d) {
        List<String> l = lines;
        if (l.isEmpty() || hud == null || !cfg().carnivalGoals || !Carnival.inArea()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        TextHud.draw(g, mc, hud.x(), hud.y(), hud.scale(), l, TextHud.width(l));
    }
}
