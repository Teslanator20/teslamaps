package com.teslamaps.features;

// Ported from SkyHanni's NoBitsWarning + the bits tracking of its BitsApi (scoreboard, SkyBlock Menu, cookie chat)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SbProfile;
import com.teslamaps.utils.ScoreboardUtils;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BitsWarning {

    private static final Pattern SCOREBOARD = Pattern.compile("^Bits: ([\\d,.]+)");
    private static final Pattern AVAILABLE = Pattern.compile("Bits Available: ([\\d,]+)");
    private static final Pattern FAME_BITS = Pattern.compile("^You gained ([\\d,]+) Bits Available compounded from all your previously eaten cookies!.*");
    private static final long COOKIE_BITS = 4800;

    private static long bits = -1;
    private static String bitsProfile;
    private static Object lastLevel;
    private static long levelAt;
    private static int timer;
    private static int plings;
    private static Object lastScreen;
    private static long openedAt;

    private BitsWarning() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("BitsWarning").when(() -> cfg().noBitsWarning || cfg().bitsGainMessage)
                .tick(BitsWarning::tick)
                .serverChat(BitsWarning::onChat)
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Bits");
                    b.toggle("No Bits Warning", () -> c.noBitsWarning, v -> c.noBitsWarning = v)
                            .desc("Title and a clickable /bz booster cookie line when you earn bits but have none left to claim. Open the SkyBlock Menu once so the mod knows your Bits Available");
                    b.toggle("  Warning Sound", () -> c.noBitsWarningSound, v -> c.noBitsWarningSound = v);
                    b.toggle("Bits Gain Message", () -> c.bitsGainMessage, v -> c.bitsGainMessage = v)
                            .desc("Chat line \"You have gained N Bits.\" when the scoreboard bits go up");
                    b.slider("  Message Threshold", 0, 1000, () -> (float) c.bitsGainThreshold, v -> c.bitsGainThreshold = Math.round(v));
                });
    }

    private static String key() {
        String k = SbProfile.key();
        return k == null || k.isEmpty() ? "default" : k;
    }

    public static long available() {
        return cfg().bitsAvailableByProfile.getOrDefault(key(), -1L);
    }

    public static void setAvailable(long v) {
        Long old = cfg().bitsAvailableByProfile.put(key(), v);
        if (old == null || old != v) TeslaMapsConfig.save();
    }

    private static void onChat(String raw) {
        String t = StringUtil.stripTrim(raw);
        if (t.startsWith("You consumed a Booster Cookie!")) {
            long a = available();
            setAvailable(Math.max(0, a) + COOKIE_BITS);
            return;
        }
        Matcher m = FAME_BITS.matcher(t);
        if (m.matches()) {
            long a = available();
            setAvailable(Math.max(0, a) + parse(m.group(1)));
        }
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (plings > 0 && timer % 2 == 0) {
            plings--;
            LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 0.6f);
        }
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            if (mc.screen != lastScreen) {
                lastScreen = mc.screen;
                openedAt = System.currentTimeMillis();
            }
            if (timer % 10 == 0 && System.currentTimeMillis() - openedAt > 600
                    && StringUtil.stripTrim(cs.getTitle().getString()).equals("SkyBlock Menu")) readMenu(cs);
        } else lastScreen = null;

        if (timer++ % 10 != 0) return;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            levelAt = System.currentTimeMillis();
            bits = -1;
        }
        if (!SkyblockUtils.isInSkyblock() || System.currentTimeMillis() - levelAt < 3000) return;
        String prof = key();
        if (!prof.equals(bitsProfile)) {
            bitsProfile = prof;
            bits = -1;
        }
        long now = -1;
        for (String l : ScoreboardUtils.getScoreboardLines()) {
            Matcher m = SCOREBOARD.matcher(ScoreboardUtils.cleanLine(l));
            if (m.find()) {
                now = parse(m.group(1));
                break;
            }
        }
        if (now < 0) return;
        long prev = bits;
        bits = now;
        if (prev < 0 || now <= prev) return;
        onGain(mc, now - prev);
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        ItemStack cookie = ItemStack.EMPTY;
        boolean any = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            any = true;
            if (StringUtil.stripTrim(st.getHoverName().getString()).equals("Booster Cookie")) cookie = st;
        }
        if (!any) return;
        if (cookie.isEmpty()) {
            setAvailable(0);
            return;
        }
        for (String l : ItemUtil.loreLines(cookie)) {
            Matcher m = AVAILABLE.matcher(l.trim());
            if (m.find()) {
                long v = parse(m.group(1));
                if (v >= 0) setAvailable(v);
                return;
            }
        }
    }

    private static void onGain(Minecraft mc, long diff) {
        TeslaMapsConfig c = cfg();
        long a = available();
        if (a >= 0) {
            a = Math.max(0, a - diff);
            setAvailable(a);
        }
        if (c.noBitsWarning && a == 0) {
            mc.gui.setTimes(0, 40, 10);
            mc.gui.setTitle(Component.literal("§bNo Bits Available"));
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §bNo Bits Available! §eClick to buy booster cookies on the bazaar.")
                        .withStyle(s -> s.withClickEvent(new ClickEvent.RunCommand("/bz booster cookie"))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to run /bz booster cookie!")))));
            }
            Notifications.show("§bNo Bits Available!\n§7Your Booster Cookie bits are used up.", 20);
            ScreenFlash.alert(c.screenFlashBits);
            if (c.noBitsWarningSound) plings = 10;
        }
        if (c.bitsGainMessage && diff >= c.bitsGainThreshold && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7You have gained §b"
                    + String.format(Locale.ROOT, "%,d", diff) + " §7Bits."));
        }
    }
}
