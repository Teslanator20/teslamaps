package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.ArmorTrimId;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.utils.RegistryUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Patterns on the left, materials on the right. Ported from Skyblocker. */
public class TrimSelectionWidget extends AbstractContainerWidget {
    private static final int PADDING = 3;
    private static final int MAX_BUTTONS_PER_ROW_PATTERN = 7;
    private static final int MAX_BUTTONS_PER_ROW_MATERIAL = 6;

    private final List<TrimElementButton.Pattern> patternButtons = new ArrayList<>();
    private final List<TrimElementButton> materialButtons = new ArrayList<>();
    private FrameLayout layout;
    private final List<AbstractWidget> children = new ArrayList<>();

    private ItemStack currentItem = null;
    private Identifier selectedPattern = null;
    private Identifier selectedMaterial = null;

    public TrimSelectionWidget(int x, int y, int width, int height) {
        super(x, y, width, height, Component.nullToEmpty("Trim Selection"), AbstractScrollArea.defaultSettings(8));

        TrimElementButton.Pattern noneButton = new TrimElementButton.Pattern(null, null, this::onClickPattern);
        noneButton.setMessage(Component.translatable("gui.none"));
        patternButtons.add(noneButton);

        RegistryUtils.lookup().lookupOrThrow(Registries.TRIM_PATTERN).listElements()
                .sorted(Comparator.comparing(reference -> reference.value().description().getString()))
                .map(reference -> new TrimElementButton.Pattern(reference.key().identifier(), reference.value(), this::onClickPattern))
                .forEachOrdered(patternButtons::add);
        children.addAll(patternButtons);

        RegistryUtils.lookup().lookupOrThrow(Registries.TRIM_MATERIAL).listElements()
                .sorted(Comparator.comparing(reference -> reference.value().description().getString()))
                .map(reference -> new TrimElementButton.Material(reference.key().identifier(), reference.value(), this::onClickMaterial))
                .forEachOrdered(materialButtons::add);
        children.addAll(materialButtons);

        positionButtons(width, height);
        layout.setPosition(x + PADDING, y + PADDING);
    }

    private void positionButtons(int width, int height) {
        // minus 9: 3px left padding, 3px right padding, 3px gap
        int buttonsPerRow = Math.max(2, (width - 9) / 20);
        int patternButtonsPerRow = Math.max(1, Math.min(Math.ceilDiv(buttonsPerRow, 2), MAX_BUTTONS_PER_ROW_PATTERN));
        int materialButtonsPerRow = Math.max(1, Math.min(Math.floorDiv(buttonsPerRow, 2), MAX_BUTTONS_PER_ROW_MATERIAL));

        int maxHeight = getHeight() - PADDING * 2;
        boolean overflow = (patternButtons.size() / patternButtonsPerRow + 1) * 20 > maxHeight
                || (materialButtons.size() / materialButtonsPerRow + 1) * 20 > maxHeight;
        if (overflow) {
            buttonsPerRow = Math.max(2, (width - 15) / 20);
            patternButtonsPerRow = Math.max(1, Math.min(Math.ceilDiv(buttonsPerRow, 2), MAX_BUTTONS_PER_ROW_PATTERN));
            materialButtonsPerRow = Math.max(1, Math.min(Math.floorDiv(buttonsPerRow, 2), MAX_BUTTONS_PER_ROW_MATERIAL));
        }

        GridLayout patternsGrid = new GridLayout();
        GridLayout.RowHelper patternAdder = patternsGrid.createRowHelper(patternButtonsPerRow);
        patternButtons.forEach(patternAdder::addChild);

        GridLayout materialsGrid = new GridLayout();
        for (int i = 0; i < materialButtons.size(); i++) {
            TrimElementButton button = materialButtons.get(i);
            int row = i / materialButtonsPerRow;
            int column = materialButtonsPerRow - (i % materialButtonsPerRow) - 1;
            materialsGrid.addChild(button, row, column);
        }

        layout = new FrameLayout(width - PADDING * 2 - (overflow ? 6 : 0), height - PADDING * 2);
        layout.defaultChildLayoutSetting().alignVerticallyTop();
        layout.addChild(patternsGrid, LayoutSettings::alignHorizontallyLeft);
        layout.addChild(materialsGrid, LayoutSettings::alignHorizontallyRight);
        layout.arrangeElements();
    }

    private void onClickPattern(TrimElementButton button) {
        for (TrimElementButton patternButton : patternButtons) patternButton.active = true;
        button.active = false;
        selectedPattern = button.getElement();
        updateConfig();
    }

    private void onClickMaterial(TrimElementButton button) {
        for (TrimElementButton materialButton : materialButtons) materialButton.active = true;
        button.active = false;
        selectedMaterial = button.getElement();
        updateConfig();
    }

    private void updateConfig() {
        if (currentItem == null) return;
        String itemUuid = Customize.uuid(currentItem);
        if (itemUuid.isEmpty()) return;
        Map<String, ArmorTrimId> trims = TeslaMapsConfig.get().customArmorTrims;
        if (selectedPattern == null) trims.remove(itemUuid);
        else trims.put(itemUuid, new ArmorTrimId(selectedMaterial, selectedPattern));
        CustomizeScreen.updated();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    protected int contentHeight() {
        return layout.getHeight() + PADDING * 2;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        layout.setX(getX() + PADDING);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        layout.setY(getY() + PADDING);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        positionButtons(getWidth(), getHeight());
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        positionButtons(getWidth(), getHeight());
    }

    @Override
    protected double scrollRate() {
        return 10;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return super.mouseClicked(new MouseButtonEvent(click.x(), click.y() + this.scrollAmount(), click.buttonInfo()), doubled);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CustomizeGui.INNER_SPACE, getX(), getY(), getWidth(), getHeight());
        graphics.enableScissor(getX() + 2, getY() + 2, getX() + getWidth() - 2, getY() + getHeight() - 2);

        int scrollY = (int) this.scrollAmount();
        for (AbstractWidget widget : this.children) {
            widget.setY(widget.getY() - scrollY);
            widget.extractRenderState(graphics, mouseX, mouseY, a);
            widget.setY(widget.getY() + scrollY);
        }

        extractScrollbar(graphics, mouseX, mouseY);
        graphics.disableScissor();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
        Map<String, ArmorTrimId> trims = TeslaMapsConfig.get().customArmorTrims;
        String itemUuid = Customize.uuid(currentItem);
        for (TrimElementButton.Pattern button : patternButtons) button.setStack(currentItem);

        if (!trims.containsKey(itemUuid)) {
            selectedPattern = null;
            selectedMaterial = materialButtons.isEmpty() ? null : materialButtons.getFirst().getElement();
            for (int i = 0; i < materialButtons.size(); i++) materialButtons.get(i).active = i != 0;
            for (int i = 0; i < patternButtons.size(); i++) patternButtons.get(i).active = i != 0;
        } else {
            ArmorTrimId id = trims.get(itemUuid);
            selectedMaterial = id.material();
            selectedPattern = id.pattern();
            for (TrimElementButton materialButton : materialButtons) {
                materialButton.active = selectedMaterial == null || !selectedMaterial.equals(materialButton.getElement());
            }
            for (TrimElementButton patternButton : patternButtons) {
                patternButton.active = selectedPattern == null || !selectedPattern.equals(patternButton.getElement());
            }
        }
    }
}
