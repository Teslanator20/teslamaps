package com.teslamaps.features;

// Ported from NotEnoughUpdates' OldSkyBlockMenu.kt / SkyHanni's OldSkyblockMenu.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OldSkyblockMenu {

    private record Btn(String cmd, int slot, String name, String texture, boolean cookie, String... desc) {}

    private static final Btn[] BTNS = {
            new Btn("trades", 40, "Trades", null, false,
                    "View your available trades.", "These trades are always", "available and accessible through", "the SkyBlock Menu."),
            new Btn("accessories", 53, "Accessory Bag",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTYxYTkxOGMw"
                            + "YzQ5YmE4ZDA1M2U1MjJjYjkxYWJjNzQ2ODkzNjdiNGQ4YWEwNmJmYzFiYTkxNTQ3MzA5ODVmZiJ9fX0=", true,
                    "A special bag which can hold", "Talismans, Rings, Artifacts, Relics, and", "Orbs within it. All will still", "work while in this bag!"),
            new Btn("potionbag", 52, "Potion Bag",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWY4Yjg"
                            + "yNDI3YjI2MGQwYTYxZTY0ODNmYzNiMmMzNWE1ODU4NTFlMDhhOWE5ZGYzNzI1NDhiNDE2OGNjODE3YyJ9fX0=", true,
                    "A handy bag for holding your", "Potions in."),
            new Btn("quiver", 44, "Quiver",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNiM2FjZ"
                            + "GMxMWNhNzQ3YmY3MTBlNTlmNGM4ZTliM2Q5NDlmZGQzNjRjNjg2OTgzMWNhODc4ZjA3NjNkMTc4NyJ9fX0=", true,
                    "A masterfully crafted Quiver", "which holds any kind of", "projectile you can think of!"),
            new Btn("fishingbag", 43, "Fishing Bag",
                    "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWI4ZT"
                            + "I5N2RmNmI4ZGZmY2YxMzVkYmE4NGVjNzkyZDQyMGFkOGVjYjQ1OGQxNDQyODg1NzJhODQ2MDNiMTYzMSJ9fX0=", true,
                    "A useful bag which can hold all", "types of fish, baits, and fishing", "loot!"),
            new Btn("sacks", 35, "Sack of Sacks",
                    "ewogICJ0aW1lc3RhbXAiIDogMTU5MTMxMDU4NTYwOSwKICAicHJvZmlsZUlkIiA6ICI0MWQzYWJjMmQ3NDk0MDBjOTA5MGQ1NDM0"
                            + "ZDAzODMxYiIsCiAgInByb2ZpbGVOYW1lIiA6ICJNZWdha2xvb24iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVl"
                            + "LAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5l"
                            + "Y3JhZnQubmV0L3RleHR1cmUvODBhMDc3ZTI0OGQxNDI3NzJlYTgwMDg2NGY4YzU3OGI5ZDM2ODg1YjI5ZGFmODM2YjY0"
                            + "YTcwNjg4MmI2ZWMxMCIKICAgIH0KICB9Cn0=", false,
                    "A sack which contains other", "sacks. Sackception!"),
            new Btn("chocolatefactory", 16, "Open Chocolate Factory", null, false,
                    "Opens the Chocolate Factory."),
    };

    private static final ItemStack[] shown = new ItemStack[54];
    private static final Btn[] bySlot = new Btn[54];
    private static int openFor = -1;
    private static int builtMp = -1;
    private static boolean builtCookie = false;
    private static boolean open = false;

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        for (Btn b : BTNS) bySlot[b.slot] = b;
        Feature.of("OldSkyblockMenu").section("Inventory").when(() -> cfg().oldSkyblockMenu)
                .tick(OldSkyblockMenu::tick)
                .config("Inventory", b -> {
                    b.toggle("Old SkyBlock Menu Shortcuts", () -> cfg().oldSkyblockMenu, v -> cfg().oldSkyblockMenu = v)
                            .desc("Empty glass slots in the SkyBlock Menu become Trades, Accessory Bag (with Magical Power), Potion Bag, Quiver, Fishing Bag, Sack of Sacks and Chocolate Factory");
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)
                || !StringUtil.stripTrim(cs.getTitle().getString()).equals("SkyBlock Menu")
                || "The Rift".equals(com.teslamaps.utils.SkyblockUtils.getArea())) {
            open = false;
            openFor = -1;
            return;
        }
        var menu = cs.getMenu();
        boolean cookie = cookieActive();
        Integer mpBox = cfg().magicalPower.get(key());
        int mp = mpBox == null ? 0 : mpBox;
        boolean rebuild = menu.containerId != openFor || mp != builtMp || cookie != builtCookie;
        openFor = menu.containerId;
        builtMp = mp;
        builtCookie = cookie;
        for (Btn b : BTNS) {
            if (b.slot >= menu.slots.size()) { shown[b.slot] = null; continue; }
            Slot s = menu.slots.get(b.slot);
            if (s.container instanceof Inventory || !isPane(s.getItem())) { shown[b.slot] = null; continue; }
            if (rebuild || shown[b.slot] == null) shown[b.slot] = build(b, cookie, mp);
        }
        open = true;
    }

    private static boolean isPane(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("stained_glass_pane");
    }

    private static String key() {
        String k = com.teslamaps.utils.SbProfile.key();
        return k == null ? "" : k;
    }

    /** Tab footer: "Cookie Buff" then "Not active! ..." when there is none. */
    private static boolean cookieActive() {
        String f = StringUtil.strip(com.teslamaps.dungeon.DungeonInfoHud.footer());
        int i = f.indexOf("Cookie Buff");
        if (i < 0) return true;
        return !f.substring(i + 11).trim().startsWith("Not active");
    }

    private static ItemStack build(Btn b, boolean cookie, int mp) {
        ItemStack st = b.texture == null ? new ItemStack(b.slot == 16 ? Items.COCOA_BEANS : Items.EMERALD) : SkyblockItems.headFromTexture(b.texture);
        if (st.isEmpty()) st = new ItemStack(Items.CHEST);
        st.set(DataComponents.CUSTOM_NAME, Component.literal("§a" + b.name).withStyle(s -> s.withItalic(false)));
        List<Component> lore = new ArrayList<>();
        for (String d : b.desc) lore.add(line("§7" + d));
        if (b.slot == 53 && mp > 0) {
            lore.add(line(""));
            lore.add(line("§7Magical Power: §6" + String.format(Locale.ROOT, "%,d", mp)));
        }
        lore.add(line(""));
        if (b.cookie && !cookie) {
            lore.add(line("§cYou need a booster cookie active"));
            lore.add(line("§cto use this shortcut!"));
        } else {
            lore.add(line("§eClick to execute /" + b.cmd));
        }
        st.set(DataComponents.LORE, new ItemLore(lore));
        return st;
    }

    private static Component line(String s) {
        return Component.literal(s).withStyle(st -> st.withItalic(false));
    }

    /** Stack to draw instead of the slot's own, or null. Runs per slot per frame: array reads only. */
    public static ItemStack replacement(Slot slot) {
        if (!open || !cfg().oldSkyblockMenu) return null;
        int i = slot.index;
        if (i < 0 || i >= 54 || bySlot[i] == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || cs.getMenu().containerId != openFor) return null;
        if (slot.container instanceof Inventory) return null;
        return shown[i];
    }

    public static boolean onClick(Slot slot) {
        if (slot == null || replacement(slot) == null) return false;
        Btn b = bySlot[slot.index];
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return true;
        if (b.cookie && !cookieActive()) {
            mc.player.playSound(SoundEvents.VILLAGER_NO, 1f, 1f);
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cRequires a booster cookie!"));
            return true;
        }
        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1f);
        mc.getConnection().sendCommand(b.cmd);
        return true;
    }
}
