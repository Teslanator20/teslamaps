package com.teslamaps.screen;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.SearchOverlay;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.utils.SkyblockItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The textbox that stands in for the auction house / bazaar search sign. */
public class SearchOverlayScreen extends Screen {

    private static final int ROW_H = 18;
    private static final int PANEL_W = 260;

    private static final Map<String, ItemStack> icons = new ConcurrentHashMap<>();

    private final SignBlockEntity sign;
    private final String[] originalText;
    private final SearchOverlay.Location location;
    private final String startText;

    private EditBox field;
    private List<String> suggestions = List.of();
    private List<String> history = List.of();
    private int selected = -1;
    private boolean pushed = false;

    private int panelX, panelY, panelH, listY, historyY;

    public SearchOverlayScreen(SignBlockEntity sign, String[] originalText,
                               SearchOverlay.Location location, String startText) {
        super(Component.literal("Search"));
        this.sign = sign;
        this.originalText = originalText;
        this.location = location;
        this.startText = startText;
    }

    @Override
    protected void init() {
        history = new ArrayList<>(SearchOverlay.history(location));
        if (history.size() > 5) history = history.subList(0, 5);

        int rows = Math.max(0, Math.min(TeslaMapsConfig.get().searchOverlaySuggestions, 12));
        panelH = 62 + rows * ROW_H + (history.isEmpty() ? 0 : 14 + history.size() * ROW_H) + 30;
        panelX = (width - PANEL_W) / 2;
        panelY = Math.max(8, (height - panelH) / 2);
        listY = panelY + 52;
        historyY = listY + rows * ROW_H + 8;

        field = new EditBox(font, panelX + 12, panelY + 28, PANEL_W - 24, 16, Component.literal("Search"));
        field.setMaxLength(64);
        field.setHint(Component.literal(switch (location) {
            case BAZAAR -> "Bazaar item…";
            case RECIPE -> "Recipe…";
            case MUSEUM -> "Museum item…";
            case OTHER -> "Search…";
            default -> "Auction item…";
        }));
        field.setResponder(text -> {
            selected = -1;
            rebuild(text);
        });
        field.setValue(startText);
        field.moveCursorToEnd(false);
        field.setHighlightPos(0);
        addRenderableWidget(field);
        setInitialFocus(field);

        int by = panelY + panelH - 24;
        addRenderableWidget(new TeslaButton(panelX + 12, by, 90, 18, "§fSearch", this::push));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 74, by, 62, 18, "§fCancel", this::cancel));
        if (location == SearchOverlay.Location.AUCTION && TeslaMapsConfig.get().searchOverlayFilters) {
            com.teslamaps.profileviewer.data.PetRepo.load();
            TeslaButton[] pet = new TeslaButton[1];
            pet[0] = addRenderableWidget(new TeslaButton(panelX + 106, by, 60, 18, petLabel(), () -> {
                SearchOverlay.maxPetLevel = !SearchOverlay.maxPetLevel;
                pet[0].setMessage(Component.literal(petLabel()));
            }));
            TeslaButton[] star = new TeslaButton[1];
            star[0] = addRenderableWidget(new TeslaButton(panelX + 170, by, 36, 18, starLabel(), () -> {
                SearchOverlay.dungeonStars = (SearchOverlay.dungeonStars + 1) % 11;
                star[0].setMessage(Component.literal(starLabel()));
            }));
        }

        rebuild(startText);
        ScreenFit.clamp(this);
    }

    private static String petLabel() {
        return SearchOverlay.maxPetLevel ? "§aMax Lvl" : "§7Max Lvl";
    }

    private static String starLabel() {
        int n = SearchOverlay.dungeonStars;
        return n == 0 ? "§7✪ 0" : n <= 5 ? "§6✪ " + n : "§c✪ " + n;
    }

    private void rebuild(String query) {
        int max = Math.max(0, Math.min(TeslaMapsConfig.get().searchOverlaySuggestions, 12));
        suggestions = SearchOverlay.suggestions(query, location, max);
        for (String name : suggestions) icon(name);
    }

    private ItemStack icon(String name) {
        ItemStack cached = icons.get(name);
        if (cached != null) return cached;
        String id = SkyblockItems.idForName(name);
        if (id == null) return ItemStack.EMPTY;
        icons.put(name, ItemStack.EMPTY);
        SkyblockItems.resolve(id, stack -> icons.put(name, stack));
        return ItemStack.EMPTY;
    }

    private void push() {
        onClose();
    }

    private void cancel() {
        if (field != null) field.setValue("");
        onClose();
    }

    private void search(String name) {
        field.setValue(name);
        push();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 1 && field != null
                && event.x() >= panelX + 12 && event.x() < panelX + PANEL_W - 12
                && event.y() >= panelY + 28 && event.y() < panelY + 44) {
            field.setValue("");
            field.setFocused(true);
            return true;
        }
        int index = rowAt(event.x(), event.y(), listY, suggestions.size());
        if (index >= 0) {
            search(suggestions.get(index));
            return true;
        }
        int historyIndex = rowAt(event.x(), event.y(), historyY, history.size());
        if (historyIndex >= 0) {
            if (event.button() == 1) {
                SearchOverlay.removeHistory(location, history.get(historyIndex));
                history = new ArrayList<>(SearchOverlay.history(location));
                if (history.size() > 5) history = history.subList(0, 5);
            } else {
                search(history.get(historyIndex));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (selected >= 0 && selected < suggestions.size()) field.setValue(suggestions.get(selected));
            push();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB && !suggestions.isEmpty()) {
            int next = Math.max(selected, 0);
            field.setValue(suggestions.get(next));
            field.moveCursorToEnd(false);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DOWN && !suggestions.isEmpty()) {
            selected = Math.min(selected + 1, suggestions.size() - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP && !suggestions.isEmpty()) {
            selected = Math.max(selected - 1, -1);
            return true;
        }
        return super.keyPressed(event);
    }

    private int rowAt(double mx, double my, int top, int count) {
        if (count == 0) return -1;
        if (mx < panelX + 8 || mx > panelX + PANEL_W - 8) return -1;
        if (my < top || my >= top + count * ROW_H) return -1;
        return (int) ((my - top) / ROW_H);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, panelH, 8);

        String title = switch (location) {
            case BAZAAR -> "Bazaar Search";
            case RECIPE -> "Recipe Search";
            case MUSEUM -> "Museum Search";
            case OTHER -> "Search";
            default -> "Auction Search";
        };
        ctx.text(font, "§f§l" + title, panelX + 12, panelY + 12, 0xFFFFFFFF);

        int hovered = rowAt(mouseX, mouseY, listY, suggestions.size());
        for (int i = 0; i < suggestions.size(); i++) {
            String name = suggestions.get(i);
            int y = listY + i * ROW_H;
            if (i == hovered || i == selected) ctx.fill(panelX + 8, y, panelX + PANEL_W - 8, y + ROW_H, 0x30FFFFFF);
            ItemStack stack = icons.getOrDefault(name, ItemStack.EMPTY);
            if (!stack.isEmpty()) ctx.item(stack, panelX + 10, y + 1);
            String id = SkyblockItems.idForName(name);
            String colored = id == null ? null : SkyblockItems.coloredName(id);
            ctx.text(font, colored != null ? colored : "§f" + name, panelX + 30, y + 5, 0xFFFFFFFF);
        }

        if (!history.isEmpty()) {
            ctx.text(font, "§8Recent", panelX + 12, historyY - 11, 0xFF808080);
            int historyHovered = rowAt(mouseX, mouseY, historyY, history.size());
            for (int i = 0; i < history.size(); i++) {
                int y = historyY + i * ROW_H;
                if (i == historyHovered) ctx.fill(panelX + 8, y, panelX + PANEL_W - 8, y + ROW_H, 0x30FFFFFF);
                ctx.text(font, "§7" + history.get(i), panelX + 12, y + 5, 0xFFAAAAAA);
                if (i == historyHovered) {
                    String hint = "§8right click to remove";
                    ctx.text(font, hint, panelX + PANEL_W - 12 - font.width(hint), y + 5, 0xFF808080);
                }
            }
        }

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // the sign is the search: closing has to hand it back, empty or not
        if (!pushed) {
            pushed = true;
            SearchOverlay.push(sign, originalText, location, field == null ? "" : field.getValue());
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
