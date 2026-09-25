package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.ItemList;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** /tmap items - SkyBlock item list; a click shows the item's recipes, ingredients are clickable. */
public class ItemListScreen extends Screen {

    private static final int CELL = 18, MARGIN = 10;

    private record Entry(String id, String name, String lower) {}

    private static String lastQuery = "";

    public boolean swallowNextChar;
    private EditBox field;
    private List<Entry> all = List.of();
    private List<Entry> shown = List.of();
    private int scroll, cols, rows;
    private int panelX, panelY, panelW, panelH, gridX, gridY;

    private String recipeId;
    private int recipeIndex;
    private final Deque<String> history = new ArrayDeque<>();
    private final List<TeslaButton> recipeButtons = new ArrayList<>();
    private ItemStack fallback;

    public ItemListScreen(String query) {
        super(Component.literal("Item List"));
        if (query != null && !query.isBlank()) lastQuery = query.trim();
    }

    @Override
    protected void init() {
        if (fallback == null) fallback = new ItemStack(Items.PAPER);
        panelW = Math.min(width - MARGIN * 2, 420);
        panelH = Math.min(height - MARGIN * 2, 300);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        gridX = panelX + 10;
        gridY = panelY + 56;
        cols = Math.max(1, (panelW - 20) / CELL);
        rows = Math.max(1, (panelH - 56 - 30) / CELL);

        field = new EditBox(font, panelX + 12, panelY + 32, panelW - 24, 16, Component.literal("Search"));
        field.setHint(Component.literal("Item name or id..."));
        field.setMaxLength(64);
        field.setResponder(q -> {
            lastQuery = q;
            scroll = 0;
            filter();
        });
        field.setValue(lastQuery);
        addRenderableWidget(field);
        setInitialFocus(field);
        addRenderableWidget(new TeslaButton(panelX + panelW - 74, panelY + panelH - 24, 62, 18, "§fClose", this::onClose));

        recipeButtons.clear();
        int by = panelY + panelH - 24;
        recipeButtons.add(addRenderableWidget(new TeslaButton(panelX + 12, by, 50, 18, "§fBack", this::back)));
        recipeButtons.add(addRenderableWidget(new TeslaButton(panelX + 66, by, 20, 18, "§f<", () -> step(-1))));
        recipeButtons.add(addRenderableWidget(new TeslaButton(panelX + 90, by, 20, 18, "§f>", () -> step(1))));
        recipeButtons.add(addRenderableWidget(new TeslaButton(panelX + 114, by, 74, 18, "§fCraft Helper", this::craftHelper)));
        recipeButtons.add(addRenderableWidget(new TeslaButton(panelX + 192, by, 74, 18, "§f/viewrecipe", this::viewRecipe)));
        updateMode();

        buildAll();
        filter();
        ScreenFit.clamp(this);
    }

    private void buildAll() {
        List<Entry> out = new ArrayList<>();
        for (String id : SkyblockItems.allIds()) {
            String name = SkyblockItems.name(id);
            if (name == null) name = id;
            String plain = com.teslamaps.utils.StringUtil.stripTrim(name);
            out.add(new Entry(id, plain, (plain + " " + id).toLowerCase(Locale.ROOT)));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        all = out;
    }

    private void filter() {
        String q = lastQuery == null ? "" : lastQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            shown = all;
            return;
        }
        String[] words = q.split("\\s+");
        List<Entry> out = new ArrayList<>();
        outer:
        for (Entry e : all) {
            for (String w : words) if (!e.lower().contains(w)) continue outer;
            out.add(e);
        }
        shown = out;
    }

    private void updateMode() {
        boolean recipe = recipeId != null;
        if (field != null) {
            field.visible = !recipe;
            if (recipe) field.setFocused(false);
        }
        for (TeslaButton b : recipeButtons) b.visible = recipe;
    }

    private void openRecipe(String id) {
        if (recipeId != null) history.push(recipeId);
        recipeId = id;
        recipeIndex = 0;
        updateMode();
    }

    private void back() {
        recipeId = history.isEmpty() ? null : history.pop();
        recipeIndex = 0;
        updateMode();
    }

    private void step(int d) {
        List<ItemList.Recipe> list = recipeId == null ? null : ItemList.recipesOrNull(recipeId);
        if (list == null || list.isEmpty()) return;
        recipeIndex = Math.floorMod(recipeIndex + d, list.size());
    }

    private void craftHelper() {
        if (recipeId == null) return;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        c.craftHelperTarget = recipeId.replace('-', ':');
        c.craftHelperAmount = 1;
        c.craftHelperShardTree = "";
        TeslaMapsConfig.save();
        if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("§6[TeslaMaps] §7Craft target: "
                + display(recipeId) + (c.craftHelper ? "" : " §c(Craft Helper is off in the settings)")));
    }

    private void viewRecipe() {
        if (recipeId == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().sendCommand("viewrecipe " + recipeId.replace('-', ':'));
        onClose();
    }

    private static String display(String id) {
        String n = SkyblockItems.coloredName(id.replace('-', ':'));
        if (n == null) n = SkyblockItems.coloredName(id);
        return n != null ? n : "§f" + id;
    }

    private ItemStack stack(String id) {
        ItemStack st = SkyblockItems.stackNow(id.replace('-', ':'));
        if (st.isEmpty() && id.indexOf('-') >= 0) st = SkyblockItems.stackNow(id);
        return st.isEmpty() ? fallback : st;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (recipeId != null) return true;
        int totalRows = (shown.size() + cols - 1) / cols;
        scroll = Math.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, totalRows - rows));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (recipeId == null) {
            if (event.button() == 1 && field != null && field.isMouseOver(event.x(), event.y())) {
                field.setValue("");
                field.setFocused(true);
                return true;
            }
            int i = cellAt(event.x(), event.y());
            if (i >= 0) {
                openRecipe(shown.get(i).id());
                return true;
            }
        } else {
            if (event.button() == 1) {
                back();
                return true;
            }
            String id = recipeSlotAt(event.x(), event.y());
            if (id != null && !id.equals(recipeId)) {
                openRecipe(id);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && recipeId != null) {
            back();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && field != null && field.isFocused() && !field.getValue().isEmpty()) {
            field.setValue("");
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (swallowNextChar) {
            swallowNextChar = false;
            return true;
        }
        return super.charTyped(event);
    }

    private int cellAt(double mx, double my) {
        if (mx < gridX || my < gridY) return -1;
        int c = (int) ((mx - gridX) / CELL), r = (int) ((my - gridY) / CELL);
        if (c >= cols || r >= rows) return -1;
        int i = (scroll + r) * cols + c;
        return i < shown.size() ? i : -1;
    }

    // ---------------------------------------------------------------- recipe layout

    private int[] slotPos(ItemList.Recipe r, int i) {
        int x0 = panelX + 30, y0 = panelY + 60;
        if (r.grid()) return new int[]{x0 + (i % 3) * CELL, y0 + (i / 3) * CELL};
        return new int[]{x0 + (i % 4) * CELL, y0 + (i / 4) * CELL};
    }

    private int[] outputPos() {
        return new int[]{panelX + 30 + 5 * CELL + 20, panelY + 60 + CELL};
    }

    private ItemList.Recipe current() {
        List<ItemList.Recipe> list = recipeId == null ? null : ItemList.recipesOrNull(recipeId);
        if (list == null || list.isEmpty()) return null;
        return list.get(Math.min(recipeIndex, list.size() - 1));
    }

    private String recipeSlotAt(double mx, double my) {
        ItemList.Recipe r = current();
        if (r == null) return null;
        for (int i = 0; i < r.inputs().size(); i++) {
            ItemList.Ing ing = r.inputs().get(i);
            if (ing == null) continue;
            int[] p = slotPos(r, i);
            if (mx >= p[0] && mx < p[0] + 16 && my >= p[1] && my < p[1] + 16) return ing.id();
        }
        int[] o = outputPos();
        if (mx >= o[0] && mx < o[0] + 16 && my >= o[1] && my < o[1] + 16) return r.output().id();
        return null;
    }

    // ---------------------------------------------------------------- render

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, panelW, panelH, 8);
        if (recipeId == null) renderList(ctx, mouseX, mouseY, delta);
        else renderRecipe(ctx, mouseX, mouseY, delta);
    }

    private void renderList(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.text(font, "§f§lItem List", panelX + 12, panelY + 12, 0xFFFFFFFF);
        String count = SkyblockItems.loaded() ? "§8" + shown.size() + " items" : "§8loading items...";
        ctx.text(font, count, panelX + panelW - 12 - font.width(count), panelY + 12, 0xFF808080);
        int hovered = cellAt(mouseX, mouseY);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c;
                if (i >= shown.size()) break;
                int x = gridX + c * CELL, y = gridY + r * CELL;
                if (i == hovered) ctx.fill(x, y, x + 16, y + 16, 0x40FFFFFF);
                ctx.item(stack(shown.get(i).id()), x, y);
            }
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (hovered >= 0) {
            Entry e = shown.get(hovered);
            ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(display(e.id())),
                    Component.literal("§8" + e.id()), Component.literal("§eClick for recipes")), mouseX, mouseY);
        }
    }

    private void renderRecipe(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.text(font, display(recipeId), panelX + 12, panelY + 12, 0xFFFFFFFF);
        List<ItemList.Recipe> list = ItemList.recipesOrNull(recipeId);
        String hoverId = null;
        int hoverCount = 0;
        if (list == null) {
            ctx.text(font, "§7Loading recipes...", panelX + 12, panelY + 40, 0xFFAAAAAA);
        } else if (list.isEmpty()) {
            ctx.text(font, "§7No crafting, forge or Kat recipe known for this item.", panelX + 12, panelY + 40, 0xFFAAAAAA);
        } else {
            ItemList.Recipe r = list.get(Math.min(recipeIndex, list.size() - 1));
            String head = "§6" + r.kind() + (list.size() > 1 ? " §8(" + (recipeIndex + 1) + "/" + list.size() + ")" : "");
            ctx.text(font, head, panelX + 12, panelY + 40, 0xFFFFFFFF);
            for (int i = 0; i < r.inputs().size(); i++) {
                int[] p = slotPos(r, i);
                ctx.fill(p[0] - 1, p[1] - 1, p[0] + 17, p[1] + 17, 0xFF2B2B2B);
                ItemList.Ing ing = r.inputs().get(i);
                if (ing == null) continue;
                boolean hover = mouseX >= p[0] && mouseX < p[0] + 16 && mouseY >= p[1] && mouseY < p[1] + 16;
                drawIng(ctx, ing, p[0], p[1], hover);
                if (hover) {
                    hoverId = ing.id();
                    hoverCount = ing.count();
                }
            }
            int[] o = outputPos();
            ctx.text(font, "§f→", o[0] - 14, o[1] + 4, 0xFFFFFFFF);
            ctx.fill(o[0] - 1, o[1] - 1, o[0] + 17, o[1] + 17, 0xFF2B2B2B);
            boolean hover = mouseX >= o[0] && mouseX < o[0] + 16 && mouseY >= o[1] && mouseY < o[1] + 16;
            drawIng(ctx, r.output(), o[0], o[1], hover);
            if (hover) {
                hoverId = r.output().id();
                hoverCount = r.output().count();
            }
            if (r.info() != null) ctx.text(font, r.info(), panelX + 12, panelY + 60 + 4 * CELL + 6, 0xFFFFFFFF);
        }
        super.extractRenderState(ctx, mouseX, mouseY, delta);
        if (hoverId != null) {
            ctx.setComponentTooltipForNextFrame(font, List.of(Component.literal(display(hoverId) + " §8x" + hoverCount),
                    Component.literal("§8" + hoverId), Component.literal("§eClick for its recipe · right-click back")), mouseX, mouseY);
        }
    }

    private void drawIng(GuiGraphicsExtractor ctx, ItemList.Ing ing, int x, int y, boolean hover) {
        ctx.item(stack(ing.id()), x, y);
        if (ing.count() > 1) {
            String t = ing.count() >= 10000 ? com.teslamaps.utils.NumberUtil.formatSuffixed(ing.count()) : String.valueOf(ing.count());
            ctx.text(font, t, x + 17 - font.width(t), y + 9, 0xFFFFFFFF, true);
        }
        if (hover) ctx.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
