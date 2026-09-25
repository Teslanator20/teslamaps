package com.teslamaps.features.qol;

// Ported from SkyHanni's UpgradeReminder.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UpgradeReminder {

    private static final Pattern CLAIMED = Pattern.compile("^You claimed the (.+) upgrade!$");
    private static final long REPEAT = 30_000L;

    private static int ticks;
    private static long lastReminder;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("UpgradeReminder").section("Timers")
                .when(() -> cfg().upgradeReminder)
                .tick(UpgradeReminder::tick)
                .serverChat(UpgradeReminder::onChat)
                .config("Timers", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Community Shop Upgrade Reminder", () -> c.upgradeReminder, v -> c.upgradeReminder = v)
                            .desc("Every 30s a clickable reminder once a profile or account upgrade can be claimed. Open the Community Shop upgrades page once to read them.");
                });
    }

    private static void onChat(String raw) {
        if (raw.indexOf("You claimed the") < 0) return;
        Matcher m = CLAIMED.matcher(StringUtil.stripTrim(raw));
        if (!m.matches()) return;
        String name = m.group(1);
        QolStore.Data d = QolStore.get();
        if (name.equals(d.profileUpgrade)) { d.profileUpgrade = null; QolStore.save(); }
        QolStore.Account a = QolStore.account();
        if (name.equals(a.accountUpgrade)) { a.accountUpgrade = null; QolStore.saveAccount(); }
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            if (StringUtil.stripTrim(cs.getTitle().getString()).equals("Community Shop")) readMenu(cs);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastReminder < REPEAT || !SkyblockUtils.isInSkyblock() || DungeonManager.isInDungeon()) return;
        QolStore.Data d = QolStore.get();
        QolStore.Account a = QolStore.account();
        boolean sent = false;
        if (d.profileUpgrade != null && now >= d.profileUpgradeEnd) {
            QolStore.chatWarp("§eThe §a" + d.profileUpgrade + " §eupgrade has completed!", "elizabeth");
            sent = true;
        }
        if (a.accountUpgrade != null && now >= a.accountUpgradeEnd) {
            QolStore.chatWarp("§eThe §a" + a.accountUpgrade + " §eupgrade has completed!", "elizabeth");
            sent = true;
        }
        if (sent) lastReminder = now;
    }

    // SkyHanni: the upgrades page has a lime pane at slot 11, profile upgrades in 27-35, account in 36-44
    private static void readMenu(AbstractContainerScreen<?> cs) {
        var slots = cs.getMenu().slots;
        if (slots.size() < 45 || !slots.get(11).getItem().is(Items.LIME_STAINED_GLASS_PANE)) return;
        boolean loaded = false;
        for (int i = 27; i < 45 && !loaded; i++) loaded = !slots.get(i).getItem().isEmpty();
        if (!loaded) return;
        long now = System.currentTimeMillis();
        QolStore.Data d = QolStore.get();
        QolStore.Account a = QolStore.account();

        Found p = scan(cs, 27);
        long pe = p == null ? 0 : p.end(d.profileUpgrade, d.profileUpgradeEnd, now);
        if (p == null ? d.profileUpgrade != null : !p.name.equals(d.profileUpgrade) || pe != d.profileUpgradeEnd) {
            d.profileUpgrade = p == null ? null : p.name;
            d.profileUpgradeEnd = pe;
            QolStore.save();
        }
        Found ac = scan(cs, 36);
        long ae = ac == null ? 0 : ac.end(a.accountUpgrade, a.accountUpgradeEnd, now);
        if (ac == null ? a.accountUpgrade != null : !ac.name.equals(a.accountUpgrade) || ae != a.accountUpgradeEnd) {
            a.accountUpgrade = ac == null ? null : ac.name;
            a.accountUpgradeEnd = ae;
            QolStore.saveAccount();
        }
    }

    private record Found(String name, long duration, boolean done) {
        // keeps the stored end while it is the same upgrade; a new one ends after its full duration
        long end(String storedName, long storedEnd, long now) {
            boolean same = name.equals(storedName);
            if (done) return same && storedEnd <= now ? storedEnd : now;
            if (!same) return now + duration;
            return storedEnd > now ? storedEnd : now + 600_000L;
        }
    }

    private static Found scan(AbstractContainerScreen<?> cs, int from) {
        for (int i = from; i < from + 9; i++) {
            ItemStack st = cs.getMenu().slots.get(i).getItem();
            if (st.isEmpty()) continue;
            List<String> lore = ItemUtil.loreLines(st);
            boolean upgrading = false, done = false;
            long duration = 0;
            for (String l : lore) {
                String t = l.trim();
                if (t.equals("Currently upgrading!")) upgrading = true;
                else if (t.equals("Click to claim!")) done = true;
                else if (t.startsWith("Duration: ")) duration = QolStore.parseDuration(t.substring(10));
            }
            if (upgrading || done) return new Found(StringUtil.stripTrim(st.getHoverName().getString()), duration, done);
        }
        return null;
    }
}
