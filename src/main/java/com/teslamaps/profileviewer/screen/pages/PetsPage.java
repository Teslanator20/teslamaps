package com.teslamaps.profileviewer.screen.pages;

import com.teslamaps.profileviewer.data.PetData;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.LegacyText;
import com.teslamaps.profileviewer.screen.ItemGrid;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import com.teslamaps.profileviewer.screen.NeuStyle;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The pet menu the way the game shows it: a slot grid with pages. */
public class PetsPage extends ProfileViewerPage {
    private static final Map<String, ItemStack> petBases = new HashMap<>();
    private static final Map<String, ItemStack> petStacks = new HashMap<>();

    private int page = 0;
    private int pageCount = 1;
    private int lastLeft;
    private int lastTop;
    private int pagerX;
    private int pagerY;
    private int pagerW;

    @Override
    public boolean isFixedLayout() {
        return true;
    }

    @Override
    public String getTabName() {
        return "Pets";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.BONE);
    }

    @Override
    public void onSelected() {
        page = 0;
    }

    private int uniquePets;
    private int petScore;
    private int maxedPets;
    private List<String> belowMax = List.of();
    private String statsLine = "";

    private static final String[] SORT_NAMES = {"Default", "Pet Exp", "A-Z"};
    private static int sortMode = 0;
    private List<PetData> sorted = List.of();
    private int sortX;
    private int sortW;

    @Override
    public void init(com.teslamaps.profileviewer.screen.ProfileViewerScreen parent,
                     com.teslamaps.profileviewer.data.SkyblockProfiles profiles) {
        super.init(parent, profiles);
        computeStats();
        resort();
    }

    private void resort() {
        SkyblockProfile profile = getProfile();
        if (profile == null) {
            sorted = List.of();
            return;
        }
        List<PetData> list = new ArrayList<>(profile.getPets());
        if (sortMode == 1) {
            list.sort((a, b) -> Double.compare(b.getExp(), a.getExp()));
        } else if (sortMode == 2) {
            list.sort((a, b) -> {
                int byName = a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                return byName != 0 ? byName : Integer.compare(b.getRarityOrdinal(), a.getRarityOrdinal());
            });
        }
        sorted = list;
        heldIcons.clear();
        for (PetData pet : list) {
            if (pet.getHeldItem() != null)
                heldIcons.put(pet, com.teslamaps.profileviewer.data.PetIcons.forHeldItem(pet.getHeldItem()));
        }
    }

    private final java.util.Map<PetData, ItemStack> heldIcons = new java.util.IdentityHashMap<>();

    /** Pet score: rarity points per unique pet type, plus one bonus point at max level. */
    private void computeStats() {
        uniquePets = 0;
        petScore = 0;
        maxedPets = 0;
        List<String> missing = new ArrayList<>();

        SkyblockProfile profile = getProfile();
        if (profile == null) {
            belowMax = List.of();
            statsLine = "";
            return;
        }

        Map<String, PetData> best = new java.util.LinkedHashMap<>();
        for (PetData pet : profile.getPets()) {
            PetData known = best.get(pet.getType());
            if (known == null || pet.getRarityOrdinal() > known.getRarityOrdinal()
                    || (pet.getRarityOrdinal() == known.getRarityOrdinal() && pet.getLevel() > known.getLevel())) {
                best.put(pet.getType(), pet);
            }
        }

        uniquePets = best.size();
        for (PetData pet : best.values()) {
            boolean maxed = pet.getLevel() >= pet.getMaxLevel();
            petScore += pet.getRarityOrdinal() + 1 + (maxed ? 1 : 0);
            if (maxed) {
                maxedPets++;
            } else {
                missing.add(pet.getRarityCode() + pet.getDisplayName() + " §7(Lvl " + pet.getLevel()
                        + "/" + pet.getMaxLevel() + ")");
            }
        }
        missing.sort(String::compareTo);
        belowMax = missing;

        double totalXp = 0;
        for (PetData pet : profile.getPets()) totalXp += pet.getExp();
        statsLine = "Unique: " + uniquePets + "  Pet Score: " + petScore
                + "  Maxed: " + maxedPets + "/" + uniquePets + "  XP: " + format(totalXp);
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        Font tr = Minecraft.getInstance().font;
        SkyblockProfile profile = getProfile();
        if (profile == null || profile.getPets().isEmpty()) {
            ctx.text(tr, "No pets", x + 10, y + 10, NeuStyle.TEXT_GRAY);
            return;
        }

        int left = x - 8;
        int top = y - 8;
        lastLeft = left;
        lastTop = top;

        List<PetData> pets = sorted.size() == profile.getPets().size() ? sorted : profile.getPets();

        String title = "Pets (" + pets.size() + ")";
        ctx.text(tr, title, left + 8, top + 6, NeuStyle.TEXT_YELLOW);

        String sortLabel = "Sort: " + SORT_NAMES[sortMode];
        sortX = left + 8 + tr.width(title) + 10;
        sortW = tr.width(sortLabel);
        boolean sortHover = mouseX >= sortX - 3 && mouseX <= sortX + sortW + 3 && mouseY >= top + 3 && mouseY <= top + 16;
        NeuStyle.slimBox(ctx, sortX - 3, top + 3, sortW + 6, 13);
        ctx.text(tr, sortLabel, sortX, top + 6, sortHover ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);

        String stats = statsLine;
        int statsX = left + NeuStyle.PANEL_WIDTH - 8 - tr.width(stats);
        ctx.text(tr, stats, statsX, top + 6, NeuStyle.TEXT_AQUA);

        // hovering the stats lists every pet that is not maxed yet
        if (!belowMax.isEmpty() && mouseX >= statsX && mouseX <= statsX + tr.width(stats)
                && mouseY >= top + 4 && mouseY <= top + 15) {
            List<String> lines = new ArrayList<>();
            lines.add("§eMissing max level (" + belowMax.size() + ")");
            for (int i = 0; i < Math.min(belowMax.size(), 18); i++) lines.add(belowMax.get(i));
            if (belowMax.size() > 18) lines.add("§8... and " + (belowMax.size() - 18) + " more");
            List<net.minecraft.network.chat.Component> parsed = new ArrayList<>(lines.size());
            for (String line : lines) parsed.add(LegacyText.parse(line));
            if (parent != null) parent.setHoveredLines(parsed);
        }

        // the grid is sized for this collection, so a small one gets big slots and no paging
        int areaW = NeuStyle.PANEL_WIDTH - 16;
        int areaY = top + 20;
        int areaH = NeuStyle.PANEL_HEIGHT - 20 - 16;

        int[] fit = ItemGrid.fitGrid(areaW, areaH, pets.size());
        int slotSize = fit[0];
        int columns = fit[1];
        int rows = fit[2];
        int perPage = Math.max(1, columns * rows);

        pageCount = Math.max(1, (pets.size() + perPage - 1) / perPage);
        page = Math.min(page, pageCount - 1);

        int gridX = left + (NeuStyle.PANEL_WIDTH - columns * slotSize) / 2;
        int gridY = areaY + Math.max(0, (areaH - rows * slotSize) / 2);

        ItemStack hovered = ItemStack.EMPTY;

        for (int i = 0; i < perPage; i++) {
            int index = page * perPage + i;
            if (index >= pets.size() && pageCount == 1) break;
            int sx = gridX + (i % columns) * slotSize;
            int sy = gridY + (i / columns) * slotSize;

            ItemGrid.slot(ctx, sx, sy, slotSize);

            if (index >= pets.size()) continue;
            PetData pet = pets.get(index);

            ctx.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1,
                    (pet.getRarityColor() & 0x00FFFFFF) | 0x33000000);
            ItemStack stack = stackFor(pet);
            if (!stack.isEmpty()) ItemGrid.item(ctx, stack, sx, sy, slotSize);
            if (pet.getHeldItem() != null) {
                ItemStack held = heldIcons.get(pet);
                if (held == null) held = com.teslamaps.profileviewer.data.PetIcons.forHeldItem(pet.getHeldItem());
                float factor = 9 / 16f;
                ctx.pose().pushMatrix();
                ctx.pose().translate(sx + slotSize - 10, sy + 1);
                ctx.pose().scale(factor, factor);
                ctx.item(held, 0, 0);
                ctx.pose().popMatrix();
            }
            ItemGrid.smallCount(ctx, String.valueOf(pet.getLevel()), sx, sy, slotSize,
                    pet.getLevel() >= pet.getMaxLevel() ? 0xFFFFAA00 : 0xFFFFFFFF);

            if (pet.isActive()) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 1, sy + slotSize - 1, 0x3055FF55);
            }

            if (mouseX >= sx && mouseX < sx + slotSize - 1 && mouseY >= sy && mouseY < sy + slotSize - 1) {
                ctx.fill(sx + 1, sy + 1, sx + slotSize - 2, sy + slotSize - 2, 0x40FFFFFF);
                hovered = stack;
            }
        }

        if (pageCount > 1) {
            String pager = "< " + (page + 1) + " / " + pageCount + " >";
            pagerX = left + (NeuStyle.PANEL_WIDTH - tr.width(pager)) / 2;
            pagerY = gridY + rows * slotSize + 4;
            pagerW = tr.width(pager);
            ctx.text(tr, pager, pagerX, pagerY, NeuStyle.TEXT);
        } else {
            pagerW = 0;
        }

        hoverStack(hovered);
    }

    /** Builds the pet the way the game shows it: level in the name, rarity colour, lore below. */
    private static ItemStack stackFor(PetData pet) {
        String key = pet.getType() + ";" + pet.getRarityOrdinal() + ";" + pet.getLevel()
                + ";" + pet.getCandyUsed() + ";" + pet.getHeldItem() + ";" + pet.isActive();
        ItemStack cached = petStacks.get(key);
        if (cached != null) return cached;

        // bundled textures first: instant and offline. The NEU one-file-at-a-time fallback fired
        // a hundred raw.githubusercontent requests at once, got rate limited, and the EMPTY
        // sentinel in petBases then kept every pet a bone for the whole session.
        ItemStack base = pet.getSkin() != null
                ? com.teslamaps.profileviewer.data.PetIcons.forSkin(pet.getSkin()) : ItemStack.EMPTY;
        if (base.isEmpty() && pet.getSkin() != null) base = lookup(skinId(pet.getSkin()), key);
        if (base.isEmpty()) base = com.teslamaps.profileviewer.data.PetIcons.forType(pet.getType());

        if (base.isEmpty()) {
            for (int rarity = pet.getRarityOrdinal(); rarity >= 0 && base.isEmpty(); rarity--) {
                base = lookup(pet.getType() + ";" + rarity, key);
            }
            for (int rarity = pet.getRarityOrdinal() + 1; rarity <= 5 && base.isEmpty(); rarity++) {
                base = lookup(pet.getType() + ";" + rarity, key);
            }
        }

        ItemStack stack = (base.isEmpty() ? new ItemStack(Items.BONE) : base.copy());

        net.minecraft.nbt.CompoundTag extra = new net.minecraft.nbt.CompoundTag();
        extra.putString("id", "PET");
        extra.putString("petInfo", "{\"type\":\"" + pet.getType() + "\",\"tier\":\"" + pet.getTier()
                + "\",\"exp\":" + (long) pet.getExp() + ",\"candyUsed\":" + pet.getCandyUsed()
                + (pet.getHeldItem() != null ? ",\"heldItem\":\"" + pet.getHeldItem() + "\"" : "")
                + (pet.getSkin() != null ? ",\"skin\":\"" + pet.getSkin() + "\"" : "")
                + ",\"active\":" + pet.isActive() + "}");
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(extra));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, pet.isActive());

        String color = pet.getRarityCode();
        stack.set(DataComponents.CUSTOM_NAME,
                LegacyText.parse("§7[Lvl " + pet.getLevel() + "] " + color + pet.getDisplayName()));

        List<Component> lore = new ArrayList<>();
        if (pet.getHeldItem() != null) {
            lore.add(LegacyText.parse("§7Held Item: " + com.teslamaps.profileviewer.data.PetIcons.heldItemName(pet.getHeldItem())));
            lore.add(Component.empty());
        }
        if (pet.getLevel() >= pet.getMaxLevel()) {
            lore.add(LegacyText.parse("§bMAX LEVEL"));
        } else {
            int percent = (int) Math.round(pet.getProgress() * 100);
            lore.add(LegacyText.parse("§7Progress to Level " + (pet.getLevel() + 1) + ": §e" + percent + "%"));
            lore.add(LegacyText.parse(bar(pet.getProgress())));
        }
        lore.add(LegacyText.parse("§7Total XP: §e" + format(pet.getExp())));
        if (pet.getCandyUsed() > 0) {
            lore.add(LegacyText.parse("§a(" + pet.getCandyUsed() + "/10) Pet Candy Used"));
        }
        if (pet.getSkin() != null) {
            lore.add(LegacyText.parse("§7Skin: §d" + pretty(pet.getSkin())));
        }
        lore.add(Component.empty());
        lore.add(LegacyText.parse(color + pet.getTier().toUpperCase() + (pet.isActive() ? " PET §a(active)" : " PET")));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // never cache the bone: the repo lookup that fills this pet in may be one another pet
        // started, and then no callback would ever drop this entry again
        if (!base.isEmpty()) petStacks.put(key, stack);
        return stack;
    }

    private static String skinId(String skin) {
        return skin.startsWith("PET_SKIN_") ? skin : "PET_SKIN_" + skin;
    }

    /** Repo lookup that kicks off the async fetch once and drops the cached stack when it lands. */
    private static ItemStack lookup(String repoId, String cacheKey) {
        ItemStack known = petBases.get(repoId);
        if (known != null) return known;

        petBases.put(repoId, ItemStack.EMPTY);
        SkyblockItems.resolve(repoId, stack -> {
            petBases.put(repoId, stack);
            petStacks.remove(cacheKey);
        });
        return ItemStack.EMPTY;
    }

    /** The green progress bar hypixel prints under the percentage. */
    private static String bar(double progress) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, progress)) * 20);
        return "§2" + "-".repeat(filled) + "§f" + "-".repeat(20 - filled);
    }

    private static String format(double value) {
        if (value >= 1_000_000) return String.format("%.1fM", value / 1_000_000);
        if (value >= 1_000) return String.format("%.1fK", value / 1_000);
        return String.valueOf((long) value);
    }

    private static String pretty(String id) {
        String out = id.replace("PET_ITEM_", "").replace("PET_SKIN_", "").replace('_', ' ').toLowerCase();
        return out.isEmpty() ? out : Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (sortW > 0 && mouseX >= sortX - 3 && mouseX <= sortX + sortW + 3
                && mouseY >= lastTop + 3 && mouseY <= lastTop + 16) {
            sortMode = (sortMode + (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT ? SORT_NAMES.length - 1 : 1)) % SORT_NAMES.length;
            resort();
            page = 0;
            return true;
        }
        if (pagerW > 0 && mouseY >= pagerY - 2 && mouseY <= pagerY + 10) {
            if (mouseX >= pagerX - 4 && mouseX <= pagerX + 10) {
                page = (page - 1 + pageCount) % pageCount;
                return true;
            }
            if (mouseX >= pagerX + pagerW - 10 && mouseX <= pagerX + pagerW + 4) {
                page = (page + 1) % pageCount;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (pageCount <= 1) return false;
        page = (int) Math.max(0, Math.min(pageCount - 1, page - amount));
        return true;
    }
}
