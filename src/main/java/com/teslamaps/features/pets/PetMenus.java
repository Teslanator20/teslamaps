package com.teslamaps.features.pets;

// Slot level after Skyblocker's PetLevelAdder / ChoosePetLevelAdder, tooltip after SkyHanni's PetExpTooltip +
// Devonian's PetXP, favorites after Skytils' FavoritePets, Kat level after NEU's KatSitterOverlay,
// inventory panel after NEU's PetInfoOverlay
import com.google.gson.JsonObject;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SlotText;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.profileviewer.data.PetIcons;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.TeslaGui;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PetMenus {

    private static final Pattern LEVEL = Pattern.compile("⭐? ?\\[Lvl (\\d+)].*");
    private static final Pattern EQUIP = Pattern.compile("Equip: ⭐? ?\\[Lvl (\\d+)].*");
    private static final Pattern CHOOSE = Pattern.compile("^(\\(\\d+/\\d+\\) )?Choose Pet$");
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    /** Per-stack info, "" = nothing to draw. */
    private record Info(String level, String pct, String id) {}
    private static final Info NONE = new Info("", "", null);

    private static final Map<ItemStack, Info> cache = new HashMap<>();
    private static Screen cachedScreen;
    private static boolean petsMenu, chooseMenu;

    private static Feature slotFeature, favFeature, tipFeature, panelFeature, katFeature;

    private PetMenus() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        slotFeature = Feature.of("PetSlotLevel").section("Skills").when(() -> cfg().petSlotLevel);
        favFeature = Feature.of("PetFavorites").section("Skills").when(() -> cfg().petFavorites);
        tipFeature = Feature.of("PetExpTooltip").section("Skills").when(() -> cfg().petExpTooltip);
        panelFeature = Feature.of("PetInventoryPanel").section("Skills").when(() -> cfg().petInventoryPanel);
        katFeature = Feature.of("PetKatLevel").section("Skills").when(() -> cfg().petKatLevel);
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> onTooltip(stack, lines));
    }

    private static void screen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == cachedScreen) return;
        cachedScreen = mc.screen;
        cache.clear();
        petsMenu = chooseMenu = false;
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            String t = StringUtil.stripTrim(cs.getTitle().getString());
            petsMenu = PetStore.isPetsMenu(t);
            chooseMenu = CHOOSE.matcher(t).matches();
        }
    }

    private static Info info(Slot slot) {
        ItemStack item = slot.getItem();
        if (item.isEmpty()) return NONE;
        Info i = cache.get(item);
        if (i != null) return i;
        i = build(slot, item);
        if (cache.size() > 512) cache.clear();
        cache.put(item, i);
        return i;
    }

    private static Info build(Slot slot, ItemStack item) {
        boolean choose = chooseMenu && !(slot.container instanceof Inventory);
        JsonObject pi = PetStore.petInfo(item);
        if (pi == null && !choose) return NONE;
        String level = "", pct = "", id = null;
        String name = StringUtil.strip(item.getHoverName().getString());
        Matcher m = null;
        if (choose && !ItemUtil.loreContains(item, "Except if:")) {
            for (String line : ItemUtil.loreLines(item)) {
                Matcher e = EQUIP.matcher(StringUtil.stripTrim(line));
                if (e.matches()) { m = e; break; }
            }
        }
        if (m == null) {
            Matcher n = LEVEL.matcher(name);
            if (n.matches()) m = n;
        }
        if (m != null) level = m.group(1);
        if (pi != null && pi.has("type")) {
            String type = pi.get("type").getAsString();
            String rarity = PetStore.tier(pi);
            id = PetStore.str(pi, "uuid");
            if (id == null) id = type + ";" + rarity + ";" + (PetStore.str(pi, "skin") == null ? "" : PetStore.str(pi, "skin"));
            if (!level.isEmpty() && pi.has("exp")) {
                int lvl = Integer.parseInt(level);
                double exp = pi.get("exp").getAsDouble();
                double cost = PetConstants.costForLevel(type, rarity, lvl);
                if (cost > 0) {
                    double into = exp - PetConstants.levelToXp(type, rarity, lvl);
                    pct = Math.max(0, Math.min(99, (int) (into / cost * 100))) + "%";
                }
                if (lvl >= PetConstants.maxLevel(type) && cfg().petSlotLevelHideMax) level = "";
            }
        } else if (!level.isEmpty() && cfg().petSlotLevelHideMax
                && (level.equals("200") || level.equals("100") && (!name.contains("Dragon") || name.contains("Ender Dragon")))) {
            level = "";
        }
        return level.isEmpty() && id == null ? NONE : new Info(level, pct, id);
    }

    // ---------------------------------------------------------------- slot text (PET-007)

    public static void drawSlot(GuiGraphicsExtractor ctx, Slot slot) {
        if (slotFeature == null || !cfg().petSlotLevel || !slotFeature.active()) return;
        screen();
        Info i = info(slot);
        if (i == NONE || i.level.isEmpty() || !SlotText.shown(SlotText.PET_LEVEL)) return;
        var font = Minecraft.getInstance().font;
        SlotText.draw(ctx, font, i.level, slot.x, slot.y, 0, 0xFFE9D8A6);
        if (cfg().petSlotLevelPercent && !i.pct.isEmpty()) SlotText.draw(ctx, font, i.pct, slot.x, slot.y, 2, 0xFF55FFFF);
    }

    // ---------------------------------------------------------------- favorites (PET-010)

    public static void drawFavorite(GuiGraphicsExtractor ctx, Slot slot) {
        if (favFeature == null || !cfg().petFavorites || !favFeature.active()) return;
        screen();
        if (!petsMenu || slot.container instanceof Inventory) return;
        Info i = info(slot);
        if (i.id == null || !cfg().petFavoriteIds.contains(i.id)) return;
        ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color());
    }

    private static String colorSrc;
    private static int colorVal;

    private static int color() {
        String s = cfg().petFavoriteColor;
        if (!java.util.Objects.equals(s, colorSrc)) {
            colorSrc = s;
            try {
                long v = Long.parseLong(s == null ? "" : s, 16);
                colorVal = s.length() <= 6 ? (int) (0xFF000000L | v) : (int) v;
            } catch (NumberFormatException e) {
                colorVal = 0x80FFAA00;
            }
        }
        return colorVal;
    }

    public static boolean isFavorite(ItemStack stack) {
        JsonObject pi = PetStore.petInfo(stack);
        if (pi == null || !pi.has("type")) return false;
        String id = PetStore.str(pi, "uuid");
        if (id == null) {
            String skin = PetStore.str(pi, "skin");
            id = pi.get("type").getAsString() + ";" + PetStore.tier(pi) + ";" + (skin == null ? "" : skin);
        }
        return cfg().petFavoriteIds.contains(id);
    }

    /** Favorite key over a pet in the Pets menu toggles it. */
    static boolean toggleFavorite(AbstractContainerScreen<?> cs, int bind) {
        TeslaMapsConfig c = cfg();
        if (!c.petFavorites || bind == -1 || bind != c.petFavoriteKey || favFeature == null || !favFeature.active()) return false;
        Slot hovered = ((HandledScreenAccessor) cs).getFocusedSlot();
        if (hovered == null || hovered.container instanceof Inventory) return true;
        screen();
        Info i = info(hovered);
        if (i.id == null) return true;
        if (!c.petFavoriteIds.remove(i.id)) c.petFavoriteIds.add(i.id);
        TeslaMapsConfig.save();
        return true;
    }

    // ---------------------------------------------------------------- tooltip (PET-009)

    private static ItemStack tipStack;
    private static List<Component> tipLines = List.of();
    private static int tipAnchor;

    private static void onTooltip(ItemStack stack, List<Component> lines) {
        if (tipFeature == null || !cfg().petExpTooltip || !tipFeature.active()) return;
        if (!cfg().petExpTooltipAlways && !Minecraft.getInstance().hasShiftDown()) return;
        if (stack != tipStack) {
            tipStack = stack;
            tipLines = buildTip(stack);
        }
        if (tipLines.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < lines.size(); i++) {
            String s = lines.get(i).getString();
            if (s.contains("MAX LEVEL")) { at = i + 2; break; }
            if (s.contains("Progress to Level")) { at = i + 3; break; }
        }
        if (at < 0 || at > lines.size()) at = lines.size();
        lines.addAll(at, tipLines);
    }

    private static List<Component> buildTip(ItemStack stack) {
        JsonObject pi = PetStore.petInfo(stack);
        if (pi == null || !pi.has("type") || !pi.has("exp")) return List.of();
        String type = pi.get("type").getAsString();
        String rarity = PetStore.tier(pi);
        double exp = pi.get("exp").getAsDouble();
        int max = PetConstants.maxLevel(type);
        double maxXp = PetConstants.levelToXp(type, rarity, max);
        List<Component> out = new java.util.ArrayList<>(4);
        if (maxXp > 0 && exp < maxXp) {
            double pct = exp / maxXp;
            out.add(Component.literal("§7Progress to Level " + max + ": §e" + String.format(Locale.ROOT, "%.1f", pct * 100) + "%"));
            int bars = 20, lit = (int) Math.round(pct * bars);
            out.add(Component.literal("§2" + "-".repeat(lit) + "§f" + "-".repeat(bars - lit)
                    + " §e" + String.format(Locale.US, "%,d", (long) exp) + "§6/§e" + shortFormat(maxXp)));
        }
        out.add(Component.literal("§7Total pet exp: §e" + String.format(Locale.US, "%,d", (long) exp)));
        out.add(Component.empty());
        return out;
    }

    private static String shortFormat(double v) {
        if (v >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", v / 1_000_000);
        if (v >= 1_000) return String.format(Locale.ROOT, "%.1fk", v / 1_000);
        return String.valueOf((long) v);
    }

    // ---------------------------------------------------------------- Kat + inventory panel (PET-002)

    private static ItemStack katStack;
    private static String[] katLines;

    private static final String CLICK_HINT = "§8Click: /pets";
    private static int panelX, panelY, panelW, panelH;

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        panelW = 0;
        if (mc.screen instanceof InventoryScreen) renderPanel(ctx, mc, cs);
        else renderKat(ctx, mc, cs);
    }

    private static void renderPanel(GuiGraphicsExtractor ctx, Minecraft mc, AbstractContainerScreen<?> cs) {
        if (panelFeature == null || !cfg().petInventoryPanel || !panelFeature.active()) return;
        if (!SkyblockUtils.isInSkyblock() || !PetTracker.hasPet()) return;
        String name = "§7[Lvl " + PetTracker.level() + "] " + PetTracker.rarityColor() + PetTracker.displayName();
        int w = Math.max(mc.font.width(name), mc.font.width(CLICK_HINT)) + 22;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int x = acc.getX(), y = acc.getY() - 26;
        TeslaGui.backdropRect(ctx, x, y, w + 4, 22);
        ItemStack icon = PetIcons.forType(PetTracker.internalName());
        if (!icon.isEmpty()) ctx.item(icon, x + 3, y + 3);
        ctx.text(mc.font, name, x + 22, y + 3, 0xFFFFFFFF, true);
        ctx.text(mc.font, CLICK_HINT, x + 22, y + 12, 0xFFFFFFFF, true);
        panelX = x;
        panelY = y;
        panelW = w + 4;
        panelH = 22;
    }

    /** Click on the inventory panel sends /pets. */
    public static boolean click(AbstractContainerScreen<?> cs, double mx, double my, int button) {
        if (panelW <= 0 || button != 0 || !(cs instanceof InventoryScreen)) return false;
        if (mx < panelX || mx >= panelX + panelW || my < panelY || my >= panelY + panelH) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) mc.getConnection().sendCommand("pets");
        return true;
    }

    private static void renderKat(GuiGraphicsExtractor ctx, Minecraft mc, AbstractContainerScreen<?> cs) {
        if (katFeature == null || !cfg().petKatLevel || !katFeature.active()) return;
        String t = StringUtil.stripTrim(cs.getTitle().getString());
        if (!t.contains("Pet Sitter") && !t.equals("Kat")) return;
        ItemStack pet = ItemStack.EMPTY;
        for (Slot s : cs.getMenu().slots) {
            if (s.container instanceof Inventory || s.getItem().isEmpty()) continue;
            if (PetStore.petInfo(s.getItem()) != null) { pet = s.getItem(); break; }
        }
        if (pet.isEmpty()) return;
        if (pet != katStack) {
            katStack = pet;
            katLines = katLines(pet);
        }
        if (katLines == null) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        int x = acc.getX() + acc.getImageWidth() + 4, y = acc.getY();
        int w = 0;
        for (String l : katLines) w = Math.max(w, mc.font.width(l));
        TeslaGui.backdropRect(ctx, x, y, w + 8, katLines.length * 10 + 6);
        for (int i = 0; i < katLines.length; i++) ctx.text(mc.font, katLines[i], x + 4, y + 4 + i * 10, 0xFFFFFFFF, true);
    }

    private static String[] katLines(ItemStack pet) {
        JsonObject pi = PetStore.petInfo(pet);
        if (pi == null || !pi.has("type")) return null;
        String type = pi.get("type").getAsString();
        String rarity = PetStore.str(pi, "tier");
        if (rarity == null) return null;
        rarity = rarity.toUpperCase(Locale.ROOT);
        double exp = pi.has("exp") ? pi.get("exp").getAsDouble() : 0;
        int idx = -1;
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) idx = i;
        if (idx < 0 || idx + 1 >= RARITIES.length) return new String[]{"§eKat Upgrade", "§7No higher rarity"};
        String next = RARITIES[idx + 1];
        int now = PetConstants.xpToLevel(type, rarity, exp);
        int after = PetConstants.xpToLevel(type, next, exp);
        return new String[]{
                "§eKat Upgrade",
                "§7Now: [Lvl " + now + "] " + code(rarity) + cap(rarity),
                "§7After: [Lvl " + after + "] " + code(next) + cap(next),
        };
    }

    private static String cap(String r) {
        return r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String code(String rarity) {
        return switch (rarity) {
            case "COMMON" -> "§f";
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            default -> "§7";
        };
    }
}
