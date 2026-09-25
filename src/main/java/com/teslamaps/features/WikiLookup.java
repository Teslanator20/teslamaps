package com.teslamaps.features;

// Lookups from Skyblocker's wikilookup package (regular, pet, enchanted book, visitor, calendar)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WikiLookup {

    private static final Pattern PET = Pattern.compile("^\\[Lvl \\d+] (?:\\[\\d+✦] )?(?<name>.+)$");
    private static final Pattern LOGBOOK = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Visitor's Logbook$");
    private static final Pattern CALENDAR_EVENT = Pattern.compile("^[0-9a-z ]*(?<event>.+)$");
    private static boolean wasDown;

    private WikiLookup() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("WikiLookup").section("Inventory").when(() -> cfg().wikiLookup)
                .tick(WikiLookup::tick)
                .config("Inventory", b -> {
                    b.toggle("Wiki Lookup Key", () -> cfg().wikiLookup, v -> cfg().wikiLookup = v)
                            .desc("Press the key over an item in any menu to open its page on the Hypixel wiki. Pets, enchanted books, Garden visitors (logbook) and calendar events too.")
                            .tags("wiki", "browser");
                    b.keybind("  Lookup Key", () -> cfg().wikiLookupKey, v -> cfg().wikiLookupKey = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        boolean down = mc.screen instanceof AbstractContainerScreen<?> && KeyNames.isDown(cfg().wikiLookupKey);
        if (down && !wasDown) lookup(mc, (AbstractContainerScreen<?>) mc.screen);
        wasDown = down;
    }

    private static void lookup(Minecraft mc, AbstractContainerScreen<?> screen) {
        Slot slot = ((com.teslamaps.mixin.HandledScreenAccessor) screen).getFocusedSlot();
        if (slot == null || slot.getItem().isEmpty() || mc.player == null) return;
        String page = page(StringUtil.strip(screen.getTitle().getString()), slot);
        if (page == null || page.isBlank()) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cNo wiki page for this item"));
            return;
        }
        String url = "https://wiki.hypixel.net/index.php?search=" + URLEncoder.encode(page, StandardCharsets.UTF_8)
                + "&title=Special%3ASearch&go=Go";
        try {
            net.minecraft.util.Util.getPlatform().openUri(url);
        } catch (Exception e) {
            mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §cCould not open the browser: " + e.getMessage()));
        }
    }

    private static String page(String title, Slot slot) {
        ItemStack stack = slot.getItem();
        String name = StringUtil.strip(stack.getHoverName().getString()).trim();
        boolean pane = stack.is(Items.BLACK_STAINED_GLASS_PANE);

        if (LOGBOOK.matcher(title).matches() && slot.index > 9 && slot.index < 44 && !pane) return name;
        if (title.equals("Calendar and Events") && slot.index > 9 && slot.index < 26 && !pane) {
            Matcher m = CALENDAR_EVENT.matcher(name);
            if (m.matches()) {
                String event = m.group("event").trim();
                if (event.equals("Election Over!") || event.equals("Election Booth Opens")) return "Mayors";
                if (event.startsWith("Bonus ")) return event.substring(6);
                return event;
            }
        }

        Matcher pet = PET.matcher(name);
        if (pet.matches()) return pet.group("name").trim() + " Pet";

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (stack.is(Items.ENCHANTED_BOOK) && data != null) {
            CompoundTag ench = data.copyTag().getCompoundOrEmpty("enchantments");
            if (ench.keySet().size() == 1) {
                String id = ench.keySet().iterator().next().replace("ultimate_", "").replace('_', ' ').trim();
                return capitalize(id) + " Enchantment";
            }
        }

        String id = ItemUtil.skyblockId(stack);
        if (!id.isEmpty()) {
            String known = SkyblockItems.name(id);
            if (known != null) return StringUtil.strip(known);
        }
        return name.isEmpty() ? null : name;
    }

    private static String capitalize(String s) {
        StringBuilder out = new StringBuilder();
        for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
            if (w.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }
}
