package com.teslamaps.slayer;

// SkyblockExtras "Lock Slayer Type"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SlayerTierLock {

    private static final Pattern TIER_ITEM = Pattern.compile(
            "^(Revenant Horror|Tarantula Broodfather|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend) (V|IV|III|II|I)$");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    private static long lastMsg = 0;

    private SlayerTierLock() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("SlayerTierLock").section("Combat").when(() -> cfg().slayerTierLock)
                .tick(SlayerTierLock::tick)
                .config("Combat", b -> b.toggle("Lock Slayer Type / Tier", () -> cfg().slayerTierLock, v -> cfg().slayerTierLock = v)
                        .desc("In Maddox's slayer menu, clicks on a different boss type or tier than your last quest are blocked. "
                                + "Hold Ctrl to start a different one anyway.")
                        .tags("slayer", "maddox", "protect", "misclick"));
    }

    private static void tick() {
        String boss = SlayerQuest.boss();
        int tier = SlayerQuest.tier();
        if (boss == null || tier < 1 || tier > 5) return;
        String key = switch (boss) {
            case "Atoned Horror" -> "Revenant Horror";
            case "Conjoined Brood" -> "Tarantula Broodfather";
            default -> boss;
        } + " " + ROMAN[tier];
        if (!key.equals(cfg().slayerTierLockLast)) {
            cfg().slayerTierLockLast = key;
            TeslaMapsConfig.save();
        }
    }

    public static boolean blockClick(AbstractContainerScreen<?> cs, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.slayerTierLock || slot == null || !slot.hasItem() || !c.section("Combat")) return false;
        if (slot.container instanceof Inventory) return false;
        String last = c.slayerTierLockLast;
        if (last == null || last.isEmpty()) return false;
        Matcher m = TIER_ITEM.matcher(StringUtil.stripTrim(slot.getItem().getHoverName().getString()));
        if (!m.matches()) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.equals("Slayer") && !title.contains(m.group(1))) return false;
        if (m.group(0).equals(last)) return false;
        long w = Minecraft.getInstance().getWindow().handle();
        if (com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL)) return false;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (now - lastMsg > 1000 && mc.player != null) {
            lastMsg = now;
            mc.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §cPrevented the activation of a different slayer tier/type §7(last: "
                    + last + ", hold Ctrl to override)"));
        }
        return true;
    }
}
