package com.teslamaps.features;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// Ported from NEU's AbiphoneFavourites + AbiphoneContactHelper (requirements) and Devonian's FavoriteAbiphone
public final class AbiphoneHelper {

    private static final String REPO =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/constants/abiphone.json";
    private static final int TOGGLE_SLOT = 4;

    private static Feature feature;
    private static Screen lastScreen;
    private static boolean inAbiphone, inDirectory;
    private static boolean[] contact = new boolean[0], favorite = new boolean[0];
    private static ItemStack iconAll, iconFav;
    private static volatile Map<String, List<String>> requirements;
    private static boolean loading;
    private static String pendingRemove;
    private static long pendingRemoveAt;

    private AbiphoneHelper() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("AbiphoneHelper").section("Inventory")
                .when(() -> cfg().abiphoneHelper)
                .tick(AbiphoneHelper::tick)
                .config("Inventory", b -> {
                    b.label("Abiphone");
                    b.toggle("Abiphone Helper", () -> cfg().abiphoneHelper, v -> cfg().abiphoneHelper = v)
                            .desc("Favourite contacts, contact requirements and a remove confirmation")
                            .tags("abiphone", "contacts", "favourite");
                    b.toggle("  Favourite Contacts", () -> cfg().abiphoneFavorites, v -> cfg().abiphoneFavorites = v)
                            .desc("Shift-click a contact to favourite it. The top middle slot switches between all and favourites only.");
                    b.color("  Favourite Color", () -> cfg().abiphoneFavoriteColor, v -> cfg().abiphoneFavoriteColor = v);
                    b.toggle("  Contact Requirements", () -> cfg().abiphoneRequirements, v -> cfg().abiphoneRequirements = v)
                            .desc("Contacts Directory tooltip lists what each NPC needs (NEU repo)");
                    b.toggle("  Confirm Removing Contacts", () -> cfg().abiphoneRemoveConfirm, v -> cfg().abiphoneRemoveConfirm = v)
                            .desc("The first right-click on a contact is blocked; repeat within 3s to remove it");
                    b.info("NPC Finder key over a contact tracks its NPC, shift = warp there");
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature.active() && (inAbiphone || inDirectory) && !lines.isEmpty()) tooltip(stack, lines);
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
            inAbiphone = inDirectory = false;
            lastScreen = null;
            pendingRemove = null;
            return;
        }
        if (cs != lastScreen) {
            lastScreen = cs;
            String title = StringUtil.stripTrim(cs.getTitle().getString());
            inDirectory = title.equals("Contacts Directory");
            inAbiphone = (title.startsWith("Abiphone ") || title.equals("AⒷiphone")) && !title.equals("Abiphone Shop");
            if (inDirectory && cfg().abiphoneRequirements) loadRequirements();
        }
        if (!inAbiphone) return;
        List<Slot> slots = cs.getMenu().slots;
        if (contact.length != slots.size()) {
            contact = new boolean[slots.size()];
            favorite = new boolean[slots.size()];
        }
        boolean phoneSelect = false;
        if (slots.size() > 13) {
            ItemStack s = slots.get(13).getItem();
            phoneSelect = !s.isEmpty() && StringUtil.strip(s.getHoverName().getString()).contains("Abiphone ");
        }
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            boolean c = !phoneSelect && !(slot.container instanceof Inventory) && isContact(slot.getItem());
            contact[i] = c;
            favorite[i] = c && cfg().abiphoneFavoriteList.contains(name(slot.getItem()));
        }
    }

    private static boolean favOn() {
        return inAbiphone && cfg().abiphoneFavorites && feature.active();
    }

    private static boolean isContact(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component l : lore.lines()) {
            String s = StringUtil.stripTrim(l.getString());
            if (s.equals("Left-click to call!") || s.equals("Click to call!")) return true;
        }
        return false;
    }

    private static String name(ItemStack stack) {
        return StringUtil.stripTrim(stack.getHoverName().getString());
    }

    private static boolean isContactSlot(Slot slot) {
        return slot != null && slot.index < contact.length && contact[slot.index];
    }

    private static boolean isToggleSlot(Slot slot) {
        return slot != null && slot.index == TOGGLE_SLOT && !(slot.container instanceof Inventory)
                && slot.getItem().getItem().toString().contains("glass_pane");
    }

    /** Hides non-favourites in favourites-only mode. */
    public static boolean hideSlot(Slot slot) {
        return favOn() && cfg().abiphoneFavOnly && isContactSlot(slot) && !favorite[slot.index];
    }

    public static boolean blockClick(AbstractContainerScreen<?> screen, Slot slot, int button, ContainerInput type) {
        if (!inAbiphone || !feature.active() || slot == null) return false;
        TeslaMapsConfig c = cfg();
        if (favOn() && isToggleSlot(slot)) {
            c.abiphoneFavOnly = !c.abiphoneFavOnly;
            TeslaMapsConfig.save();
            return true;
        }
        if (!isContactSlot(slot)) return false;
        String name = name(slot.getItem());
        if (favOn()) {
            if (c.abiphoneFavOnly && !favorite[slot.index]) return true;
            if (type == ContainerInput.QUICK_MOVE) {
                if (!c.abiphoneFavoriteList.remove(name)) c.abiphoneFavoriteList.add(name);
                favorite[slot.index] = c.abiphoneFavoriteList.contains(name);
                TeslaMapsConfig.save();
                msg((favorite[slot.index] ? "§eAdded §f" : "§eRemoved §f") + name + (favorite[slot.index] ? " §eto" : " §efrom") + " your favourite contacts");
                return true;
            }
        }
        if (c.abiphoneRemoveConfirm && button == 1 && type == ContainerInput.PICKUP) {
            long now = System.currentTimeMillis();
            if (name.equals(pendingRemove) && now - pendingRemoveAt < 3000) {
                pendingRemove = null;
                return false;
            }
            pendingRemove = name;
            pendingRemoveAt = now;
            msg("§cRight-click §f" + name + " §cagain within 3s to remove the contact");
            return true;
        }
        return false;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        if (!favOn()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        List<Slot> slots = cs.getMenu().slots;
        boolean favOnly = cfg().abiphoneFavOnly;
        if (!favOnly) {
            int color = 0x80000000 | (TeslaMapsConfig.parseColor(cfg().abiphoneFavoriteColor) & 0xFFFFFF);
            for (int i = 0; i < slots.size() && i < favorite.length; i++) {
                if (!favorite[i]) continue;
                Slot s = slots.get(i);
                ctx.fill(acc.getX() + s.x, acc.getY() + s.y, acc.getX() + s.x + 16, acc.getY() + s.y + 16, color);
            }
        }
        if (slots.size() > TOGGLE_SLOT && isToggleSlot(slots.get(TOGGLE_SLOT))) {
            if (iconAll == null) {
                iconAll = new ItemStack(Items.EMERALD);
                iconFav = new ItemStack(Items.DIAMOND);
            }
            Slot s = slots.get(TOGGLE_SLOT);
            int x = acc.getX() + s.x, y = acc.getY() + s.y;
            ctx.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
            ctx.item(favOnly ? iconFav : iconAll, x, y);
        }
    }

    private static void tooltip(ItemStack stack, List<Component> lines) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.getItem() != stack) return;
        TeslaMapsConfig c = cfg();

        if (inDirectory && c.abiphoneRequirements) {
            Map<String, List<String>> req = requirements;
            List<String> r = req == null ? null : req.get(name(stack));
            if (r != null && !r.isEmpty()) {
                lines.add(Component.literal(" "));
                lines.add(Component.literal("§e§lRequirements:"));
                for (String s : r) lines.add(Component.literal(s));
            }
            return;
        }
        if (!favOn()) return;
        if (isToggleSlot(hovered)) {
            lines.clear();
            if (c.abiphoneFavOnly) {
                lines.add(Component.literal("§6Showing favourite contacts only"));
                lines.add(Component.literal("§eClick to show all contacts"));
            } else {
                lines.add(Component.literal("§aShowing all contacts"));
                lines.add(Component.literal("§eClick to show favourites only"));
            }
            lines.add(Component.literal("§7Shift-click a contact to (un)favourite it"));
            return;
        }
        if (!isContactSlot(hovered)) return;
        boolean fav = favorite[hovered.index];
        if (c.abiphoneFavOnly && !fav) {
            lines.clear();
            return;
        }
        if (c.abiphoneFavOnly) return;
        if (fav) lines.set(0, lines.get(0).copy().append(Component.literal(" §f- §6Favourite")));
        lines.add(Component.literal(fav ? "§eShift-click to remove from favourites" : "§eShift-click to add to favourites"));
    }

    private static void loadRequirements() {
        if (requirements != null || loading) return;
        loading = true;
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement el = com.teslamaps.utils.RepoCache.fetch("neu-abiphone", REPO, 30);
                Map<String, List<String>> out = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (!o.has("requirement") || !o.get("requirement").isJsonArray()) continue;
                    List<String> r = new ArrayList<>();
                    for (JsonElement s : o.getAsJsonArray("requirement")) r.add(s.getAsString());
                    out.put(e.getKey(), r);
                }
                requirements = out;
            } catch (Exception e) {
                com.teslamaps.TeslaMaps.LOGGER.warn("[Abiphone] failed to load abiphone.json: {}", e.toString());
            } finally {
                loading = false;
            }
        });
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] " + s));
    }
}
