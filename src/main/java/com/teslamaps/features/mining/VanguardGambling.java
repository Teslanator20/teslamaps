package com.teslamaps.features.mining;

// Ported from SkyOcean's VanguardGambling.kt + SlotMachineSpinner.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Slot machine on the VANGUARD CORPSE LOOT block that lands on the best (or a predefined) drop. */
public final class VanguardGambling {

    public static final String[] MODES = {"Most Expensive", "Predefined"};
    private static final Pattern START = Pattern.compile("^ +VANGUARD CORPSE LOOT! ?$");
    private static final Pattern ITEM = Pattern.compile("^ +(.+?)(?: x([\\d,]+))?$");
    private static final Pattern END = Pattern.compile("^▬{64}$");
    private static final Map<String, Integer> VALUABLES = new LinkedHashMap<>();

    static {
        VALUABLES.put("DYE_FROSTBITTEN", 1);
        VALUABLES.put("SHATTERED_PENDANT", 5);
        VALUABLES.put("CAGED_WISP", 10);
    }

    private static boolean parsing;
    private static final Map<String, Integer> loot = new LinkedHashMap<>();

    private VanguardGambling() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VanguardGambling").section("Mining").when(() -> cfg().vanguardGambling)
                .serverChat(VanguardGambling::onChat)
                .config("Mining", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Vanguard Gambling", () -> c.vanguardGambling, v -> c.vanguardGambling = v)
                            .desc("Just for fun: a slot machine spins when you loot a Vanguard corpse and stops on the best drop. Esc closes it. Test: /tmap vanguard test")
                            .tags("vanguard", "corpse", "gambling", "slot machine", "mineshaft");
                    b.dropdown("  Win On", MODES, () -> c.vanguardGamblingMode, v -> c.vanguardGamblingMode = v)
                            .desc("Most Expensive = most valuable drop. Predefined = only Frostbitten Dye, Shattered Pendant or Caged Wisp win, otherwise you lose.");
                    b.toggle("  Hide Chat While Spinning", () -> c.vanguardGamblingHideChat, v -> c.vanguardGamblingHideChat = v)
                            .desc("Covers the screen so the loot message doesn't spoil the result");
                    b.slider("  Spin Time (s)", 1f, 8f, () -> c.vanguardGamblingTime, v -> c.vanguardGamblingTime = v, 1);
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("vanguard")
                        .then(ClientCommands.literal("test").executes(c -> {
                            Map<String, Integer> fake = new LinkedHashMap<>();
                            fake.put("GLACITE_JEWEL", 3);
                            fake.put("SHATTERED_PENDANT", 1);
                            fake.put("UMBER", 64);
                            fake.put("TUNGSTEN", 64);
                            spin(fake);
                            return 1;
                        })))));
    }

    private static void onChat(String text) {
        String plain = StringUtil.strip(text);
        if (START.matcher(plain).matches()) {
            parsing = true;
            loot.clear();
            return;
        }
        if (!parsing) return;
        if (END.matcher(plain.trim()).matches()) {
            parsing = false;
            if (!loot.isEmpty()) spin(new LinkedHashMap<>(loot));
            loot.clear();
            return;
        }
        Matcher m = ITEM.matcher(plain);
        if (!m.matches()) return;
        String id = idFor(m.group(1).trim());
        if (id == null) return;
        int amount = 1;
        if (m.group(2) != null) {
            try { amount = Integer.parseInt(m.group(2).replace(",", "")); } catch (NumberFormatException ignored) {}
        }
        loot.merge(id, amount, Integer::sum);
        SkyblockItems.prefetch(id);
    }

    private static String idFor(String name) {
        String id = PriceManager.idForName(name);
        return id != null ? id : SkyblockItems.idForName(name);
    }

    private static void spin(Map<String, Integer> drops) {
        String winner = null;
        double best = -1;
        boolean predefined = MODES[1].equals(cfg().vanguardGamblingMode);
        for (Map.Entry<String, Integer> e : drops.entrySet()) {
            if (predefined && !VALUABLES.containsKey(e.getKey())) continue;
            double v = PriceManager.getPrice(e.getKey()) * e.getValue();
            if (v > best) {
                best = v;
                winner = e.getKey();
            }
        }
        Map<String, Integer> pool = new LinkedHashMap<>(VALUABLES);
        for (String id : drops.keySet()) pool.putIfAbsent(id, 10);
        for (String id : pool.keySet()) SkyblockItems.prefetch(id);
        String win = winner;
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(new Spinner(pool, win)));
    }

    private static final class Spinner extends Screen {
        private static final int SLOT = 24;
        private final Map<String, Integer> pool;
        private final String winner;
        private final List<List<String>> reels = new ArrayList<>();
        private final int[] lastIndex = {-1, -1, -1};
        private long start;
        private long doneAt;
        private int soundTick;

        Spinner(Map<String, Integer> pool, String winner) {
            super(Component.literal("Vanguard Slots"));
            this.pool = pool;
            this.winner = winner;
        }

        @Override
        protected void init() {
            if (!reels.isEmpty()) return;
            List<String> finals = new ArrayList<>(3);
            if (winner != null) {
                for (int i = 0; i < 3; i++) finals.add(winner);
            } else {
                String a = random(), b = random(), c = random();
                for (int tries = 0; a.equals(b) && b.equals(c) && tries < 20; tries++) c = random();
                finals.add(a);
                finals.add(b);
                finals.add(c);
            }
            for (int i = 0; i < 3; i++) {
                int len = 60 + i * 10;
                List<String> reel = new ArrayList<>(len);
                for (int j = 0; j < len; j++) reel.add(random());
                reel.set(len - 2, finals.get(i));
                reels.add(reel);
            }
            start = System.currentTimeMillis() + 200;
        }

        private String random() {
            int total = 0;
            for (int w : pool.values()) total += w;
            int r = ThreadLocalRandom.current().nextInt(Math.max(1, total));
            for (Map.Entry<String, Integer> e : pool.entrySet()) {
                r -= e.getValue();
                if (r < 0) return e.getKey();
            }
            return pool.keySet().iterator().next();
        }

        private long duration(int reel) {
            return (long) (Math.max(1f, TeslaMapsConfig.get().vanguardGamblingTime) * 1000) + reel * 1000L;
        }

        @Override
        public void tick() {
            soundTick = 0;
            if (doneAt > 0 && System.currentTimeMillis() - doneAt > 4000) onClose();
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

        @Override
        public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            boolean hide = TeslaMapsConfig.get().vanguardGamblingHideChat;
            g.fill(0, 0, width, height, hide ? 0xFF0B0B10 : 0x90000000);
            long elapsed = System.currentTimeMillis() - start;
            int pw = 3 * 40 + 40, ph = SLOT * 3 + 60;
            int px = (width - pw) / 2, py = (height - ph) / 2;
            HudStyle.screenPanel(g, px, py, pw, ph, 8);
            String title = "§b§lVANGUARD §d§lSLOTS";
            g.text(font, title, width / 2 - font.width(title) / 2, py + 8, 0xFFFFFFFF, true);

            boolean allDone = true;
            for (int i = 0; i < 3; i++) {
                int cx = width / 2 + (i - 1) * 40;
                int top = height / 2 - SLOT * 3 / 2 + 4;
                allDone &= drawReel(g, i, cx, top, elapsed);
            }
            int mid = height / 2 + 4;
            g.fill(px + 10, mid - 1, px + 16, mid + 1, 0xFFFF5555);
            g.fill(px + pw - 16, mid - 1, px + pw - 10, mid + 1, 0xFFFF5555);

            if (allDone) {
                if (doneAt == 0) {
                    doneAt = System.currentTimeMillis();
                    if (minecraft != null && minecraft.player != null) {
                        minecraft.player.playSound(winner != null ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VAULT_INSERT_ITEM_FAIL, 1f, 1f);
                    }
                }
                String line = winner != null ? "§a§lWIN! §f" + name(winner) : "§c§lNo luck this time";
                g.text(font, line, width / 2 - font.width(line) / 2, py + ph - 18, 0xFFFFFFFF, true);
            } else {
                String hint = "§7Esc to skip";
                g.text(font, hint, width / 2 - font.width(hint) / 2, py + ph - 18, 0xFFFFFFFF, true);
            }
        }

        private boolean drawReel(GuiGraphicsExtractor g, int index, int cx, int top, long elapsed) {
            List<String> reel = reels.get(index);
            long dur = duration(index);
            float raw = Math.max(0f, Math.min(1f, elapsed / (float) dur));
            float eased = 1 - (1 - raw) * (1 - raw);
            float scroll = (reel.size() - 2) * SLOT * eased;
            int base = (int) (scroll / SLOT);
            int offset = (int) (scroll % SLOT);
            if (raw < 1f && base > lastIndex[index]) {
                if (lastIndex[index] != -1 && soundTick++ == 0 && minecraft != null && minecraft.player != null) {
                    minecraft.player.playSound(ThreadLocalRandom.current().nextBoolean()
                            ? SoundEvents.VAULT_INSERT_ITEM : SoundEvents.VAULT_INSERT_ITEM_FAIL, 0.6f, 2f);
                }
                lastIndex[index] = base;
            }
            g.fill(cx - 16, top, cx + 16, top + SLOT * 3, 0xFF1C1C24);
            g.enableScissor(cx - 16, top, cx + 16, top + SLOT * 3);
            int centerY = top + SLOT + SLOT / 2;
            for (int o = -1; o <= 2; o++) {
                int idx = Math.max(0, Math.min(reel.size() - 1, base + o));
                ItemStack stack = SkyblockItems.stackNow(reel.get(idx));
                int y = centerY + offset - o * SLOT;
                g.pose().pushMatrix();
                g.pose().translate(cx, y);
                g.pose().scale(1.25f, 1.25f);
                if (stack.isEmpty()) g.text(font, "?", -3, -4, 0xFFAAAAAA, true);
                else g.item(stack, -8, -8);
                g.pose().popMatrix();
            }
            g.disableScissor();
            return raw >= 1f;
        }

        private static String name(String id) {
            String n = SkyblockItems.coloredName(id);
            return n != null ? n : id.toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
