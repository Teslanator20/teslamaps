package com.teslamaps.features.qol;

// Ported from SkyHanni's EnchantedClockHelper.kt, boosts from SkyHanni-REPO misc/EnchantedClock.json
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnchantedClock {

    // name, color, chat usage string
    public static final String[][] BOOSTS = {
            {"Minions", "§b", "minions"},
            {"Chocolate Factory", "§6", "Chocolate Factory"},
            {"Pet Training", "§d", "training pets"},
            {"Pet Sitter", "§b", "pets being taken care of by Kat"},
            {"Aging Items", "§e", "aging items"},
            {"Forge", "§6", "forges"},
    };
    private static final List<String> NAMES = List.of("Minions", "Chocolate Factory", "Pet Training", "Pet Sitter", "Aging Items", "Forge");
    private static final long COOLDOWN = 48 * 3_600_000L;
    private static final Pattern USED = Pattern.compile("^TIME WARP! You have successfully warped time for your (.+?)!$");
    private static final Pattern STATUS = Pattern.compile("^Status: (READY|CHARGING|PROBLEM)$");
    private static final Pattern COOLDOWN_LINE = Pattern.compile("On cooldown: ([\\d,]+) (hour|minute)s?");

    private static int ticks;
    private static long repeatAt;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("EnchantedClock").section("Timers")
                .when(() -> cfg().enchantedClockReminder)
                .tick(EnchantedClock::tick)
                .serverChat(EnchantedClock::onChat)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("SkyBlock Reminders");
                    b.toggle("Enchanted Time Clock Reminder", () -> c.enchantedClockReminder, v -> c.enchantedClockReminder = v)
                            .desc("Chat + pling when a Time Clock boost is off its 48h cooldown. Open the clock once to read the cooldowns.");
                    b.searchList("  Boosts", () -> NAMES, s -> !c.enchantedClockDisabled.contains(s),
                            s -> { if (!c.enchantedClockDisabled.remove(s)) c.enchantedClockDisabled.add(s); });
                    b.slider("  Repeat Every (min, 0 = once)", 0f, 60f, () -> c.enchantedClockRepeatMin, v -> c.enchantedClockRepeatMin = v, 0);
                });
    }

    private static void onChat(String raw) {
        if (raw.indexOf("TIME WARP") < 0) return;
        Matcher m = USED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        for (String[] b : BOOSTS) {
            if (!b[2].equals(m.group(1))) continue;
            QolStore.Data d = QolStore.get();
            d.clockReady.put(b[0], System.currentTimeMillis() + COOLDOWN);
            d.clockWarned.remove(b[0]);
            QolStore.save();
            return;
        }
    }

    private static void tick() {
        if (++ticks % 20 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs
                && StringUtil.stripTrim(cs.getTitle().getString()).equals("Enchanted Time Clock")) {
            readMenu(cs);
            return;
        }
        if (!SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) return;
        QolStore.Data d = QolStore.get();
        long now = System.currentTimeMillis();
        TeslaMapsConfig c = cfg();
        if (c.enchantedClockRepeatMin > 0 && repeatAt > 0 && now >= repeatAt) {
            repeatAt = 0;
            d.clockWarned.clear();
        }
        List<String> ready = new ArrayList<>();
        for (String[] b : BOOSTS) {
            Long at = d.clockReady.get(b[0]);
            if (at == null || at > now || d.clockWarned.contains(b[0]) || c.enchantedClockDisabled.contains(b[0])) continue;
            d.clockWarned.add(b[0]);
            ready.add(b[1] + b[0]);
        }
        if (ready.isEmpty()) return;
        QolStore.save();
        QolStore.chat("§6§lTIME WARP! §r§aReady: " + String.join("§7, ", ready));
        LoudSound.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f);
        if (c.enchantedClockRepeatMin > 0) repeatAt = now + (long) (c.enchantedClockRepeatMin * 60_000L);
    }

    private static void readMenu(AbstractContainerScreen<?> cs) {
        QolStore.Data d = QolStore.get();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty()) continue;
            String name = StringUtil.stripTrim(st.getHoverName().getString());
            if (!NAMES.contains(name)) continue;
            String state = null;
            long cd = -1;
            for (String l : ItemUtil.loreLines(st)) {
                Matcher s = STATUS.matcher(l.trim());
                if (s.matches()) state = s.group(1);
                Matcher c = COOLDOWN_LINE.matcher(l);
                if (c.find()) {
                    long n = Long.parseLong(c.group(1).replace(",", ""));
                    cd = n * (c.group(2).equals("hour") ? 3_600_000L : 60_000L);
                }
            }
            if (state == null) continue;
            Long old = d.clockReady.get(name);
            if (!state.equals("CHARGING")) {
                if (old == null || old > now) { d.clockReady.put(name, now); d.clockWarned.add(name); changed = true; }
            } else if (cd >= 0) {
                long at = now + cd;
                // hours are rounded, keep a chat-exact time when it is close
                if (old == null || Math.abs(old - at) > 3_600_000L || old <= now) {
                    d.clockReady.put(name, at);
                    d.clockWarned.remove(name);
                    changed = true;
                }
            }
        }
        if (changed) QolStore.save();
    }
}
