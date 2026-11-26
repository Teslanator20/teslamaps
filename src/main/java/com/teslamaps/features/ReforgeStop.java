package com.teslamaps.features;

// SkyblockAddons' reforge filter (GuiChestHook) + SkyHanni's ReforgeHelper rare-reforge block
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.LoudSound;
import com.teslamaps.utils.SbRepo;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Blocks the reforge button once the item already has a wanted reforge. */
public final class ReforgeStop {

    private static final List<String> BASIC = List.of(
            "Gentle", "Odd", "Fast", "Fair", "Epic", "Sharp", "Heroic", "Spicy", "Legendary",
            "Deadly", "Fine", "Grand", "Hasty", "Neat", "Rapid", "Unreal", "Awkward", "Rich",
            "Clean", "Fierce", "Heavy", "Light", "Mythic", "Pure", "Smart", "Titanic", "Wise",
            "Bizarre", "Itchy", "Ominous", "Pleasant", "Pretty", "Shiny", "Simple", "Strange", "Vivid",
            "Godly", "Demonic", "Forceful", "Hurtful", "Keen", "Strong", "Superior", "Unpleasant", "Zealous",
            "Silky", "Bloody", "Shaded", "Sweet",
            "Double-Bit", "Lumberjack's", "Great", "Rugged", "Lush", "Green Thumb", "Peasant's", "Robust", "Zooming",
            "Unyielding", "Prospector's", "Excellent", "Sturdy", "Fortunate",
            "Salty", "Treacherous", "Lucky", "Stiff", "Pitchin'");

    private static long lastWarn;

    private ReforgeStop() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ReforgeStop").section("Inventory")
                .config("Inventory", b -> {
                    b.label("Stop At Wanted Reforge");
                    b.toggle("Stop At Wanted Reforge", () -> cfg().reforgeStop, v -> cfg().reforgeStop = v)
                            .desc("Reforge menu / Hex: the reforge button does nothing once the item has a wanted reforge");
                    b.searchList("  Wanted Reforges", ReforgeStop::options,
                            id -> cfg().reforgeStopList.contains(id),
                            id -> { if (!cfg().reforgeStopList.remove(id)) cfg().reforgeStopList.add(id); });
                    b.toggle("  Protect Stone Reforges", () -> cfg().reforgeStopStones, v -> cfg().reforgeStopStones = v)
                            .desc("Never roll over a reforge that came from a reforge stone. Hold ctrl to override");
                });
    }

    private static List<String> options() {
        Set<String> out = new LinkedHashSet<>(cfg().reforgeStopList);
        out.addAll(BASIC);
        List<String> stones = new ArrayList<>(SbRepo.reforgeNames());
        stones.sort(String.CASE_INSENSITIVE_ORDER);
        out.addAll(stones);
        return new ArrayList<>(out);
    }

    /** True = swallow this click. */
    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.reforgeStop || slot == null || !c.section("Inventory") || !SkyblockUtils.isInSkyblock()) return false;
        String title = StringUtil.stripTrim(screen.getTitle().getString());
        boolean hex = title.equals("The Hex ➜ Reforges");
        if (!hex && !title.startsWith("Reforge Item")) return false;
        if (slot.container == Minecraft.getInstance().player.getInventory()) return false;
        int button = hex ? 48 : 22;
        int itemSlot = hex ? 19 : 13;
        if (slot.index != button) return false;
        List<Slot> slots = screen.getMenu().slots;
        if (itemSlot >= slots.size()) return false;
        ItemStack item = slots.get(itemSlot).getItem();
        if (item.isEmpty()) return false;
        String modifier = modifier(item);
        if (modifier.isEmpty()) return false;

        String name = StringUtil.stripTrim(item.getHoverName().getString());
        for (String wanted : c.reforgeStopList) {
            if (matches(wanted, modifier, name)) {
                warn("§aGot §e" + wanted + "§a, reforge button blocked");
                return true;
            }
        }
        if (c.reforgeStopStones && !ctrl()) {
            SbRepo.Reforge r = SbRepo.reforgeByModifier(modifier);
            if (r != null && r.stoneId() != null) {
                warn("§cBlocked: §e" + (r.name() != null ? r.name() : modifier) + " §cis a stone reforge §7(hold ctrl)");
                return true;
            }
        }
        return false;
    }

    private static String modifier(ItemStack item) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr("modifier", "");
    }

    private static boolean matches(String wanted, String modifier, String name) {
        if (norm(wanted).equals(norm(modifier))) return true;
        int i = 0;
        while (i < name.length() && !Character.isLetter(name.charAt(i))) i++;
        return name.substring(i).toLowerCase(Locale.ROOT).startsWith(wanted.toLowerCase(Locale.ROOT) + " ");
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) if (ch >= 'a' && ch <= 'z') sb.append(ch);
        return sb.toString();
    }

    private static boolean ctrl() {
        long h = Minecraft.getInstance().getWindow().handle();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LCONTROL)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RCONTROL);
    }

    private static void warn(String msg) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (mc.player == null || now - lastWarn < 800) return;
        lastWarn = now;
        mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + msg));
        LoudSound.play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
    }
}
