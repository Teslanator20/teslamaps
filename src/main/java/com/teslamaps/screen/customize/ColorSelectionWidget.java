package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.AnimatedDye;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.mixin.CheckboxAccessor;
import com.teslamaps.mixin.EntityRenderDispatcherAccessor;
import com.teslamaps.render.gui.ArgbTextInput;
import com.teslamaps.render.gui.ColorPicker;
import com.teslamaps.render.gui.CustomizeGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import java.io.Closeable;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Static and animated dye colour picking. Ported from Skyblocker. */
public class ColorSelectionWidget extends AbstractContainerWidget implements Closeable {
    private static final int PADDING = 3;
    private static final NumberFormat DOUBLE_NUMBERS = NumberFormat.getInstance(Locale.US);

    static {
        DOUBLE_NUMBERS.setMaximumFractionDigits(2);
    }

    private static final Component RESET_COLOR_TEXT = Component.translatable("teslamaps.customize.armor.resetColor");
    private static final Component PICK_DYE_COLOR_TEXT = Component.translatable("teslamaps.customize.armor.pickDye");
    private static final Component CANNOT_CUSTOMIZE_COLOR_TEXT = Component.translatable("teslamaps.customize.armor.cannotCustomizeColor");
    private static final Component ANIMATED_TEXT = Component.translatable("teslamaps.customize.armor.animated");
    private static final Component CYCLE_BACK_TEXT = Component.translatable("teslamaps.customize.armor.cycleBack");
    private static final Component DURATION_TOOLTIP_TEXT = Component.translatable("teslamaps.customize.armor.durationTooltip");
    private static final Component DELAY_TOOLTIP_TEXT = Component.translatable("teslamaps.customize.armor.delayTooltip");
    private static final String DURATION_TEXT = "teslamaps.customize.armor.duration";
    private static final String DELAY_TEXT = "teslamaps.customize.armor.delay";

    private final ColorPicker colorPicker;
    private final ArgbTextInput argbTextInput;

    private final AnimatedDyeTimelineWidget timelineWidget;
    private final Checkbox cycleBackCheckbox;
    private final Slider delaySlider;
    private final Slider durationSlider;

    private final Button resetColorButton;
    private final Button pickDyeButton;
    private final Checkbox animatedCheckbox;
    private final StringWidget notCustomizableText;

    private final FrameLayout layout;

    private ItemStack currentItem = ItemStack.EMPTY;
    private boolean animated;
    private boolean customizable = false;

    private final List<AbstractWidget> children;

    public ColorSelectionWidget(int x, int y, int width, int height, Font textRenderer) {
        super(x, y, width, height, Component.nullToEmpty("ColorSelectionWidget"), AbstractScrollArea.defaultSettings(4));
        int pickerHeight = Math.max(20, Math.min(Math.min(2 * height / 3, width / 5), height - 40));

        colorPicker = new ColorPicker(0, 0, pickerHeight * 2, pickerHeight);
        colorPicker.setOnColorChange(this::onPickerColorChanged);
        argbTextInput = new ArgbTextInput(0, 0, textRenderer, true);
        argbTextInput.setOnChange(this::onTextInputColorChanged);
        timelineWidget = new AnimatedDyeTimelineWidget(0, 0, getWidth() - 6, 15, this::onTimelineFrameSelected);

        resetColorButton = Button.builder(RESET_COLOR_TEXT, this::onRemoveCustomColor).width(75).build();
        pickDyeButton = Button.builder(PICK_DYE_COLOR_TEXT, this::onClickPickDye).width(75).build();

        notCustomizableText = new StringWidget(CANNOT_CUSTOMIZE_COLOR_TEXT, textRenderer);
        FrameLayout.centerInRectangle(notCustomizableText, getX(), getY(), getWidth(), getHeight());

        animatedCheckbox = Checkbox.builder(ANIMATED_TEXT, textRenderer)
                .pos(colorPicker.getRight() + 5, resetColorButton.getBottom())
                .maxWidth(80)
                .onValueChange(this::onAnimatedCheckbox)
                .build();
        cycleBackCheckbox = Checkbox.builder(CYCLE_BACK_TEXT, textRenderer)
                .pos(colorPicker.getRight() + 5, animatedCheckbox.getBottom() + 3)
                .maxWidth(80)
                .onValueChange(this::onCycleBackCheckbox)
                .build();

        int sliderWidth = (int) (width * 0.35f);
        delaySlider = new Slider(0, 0, sliderWidth, 0.0f, 2.0f, 0.02f, DELAY_TEXT, value -> {
            String itemUuid = Customize.uuid(currentItem);
            AnimatedDye dye = TeslaMapsConfig.get().customAnimatedDyes.get(itemUuid);
            if (dye == null) return;
            TeslaMapsConfig.get().customAnimatedDyes.put(itemUuid,
                    new AnimatedDye(dye.keyframes(), dye.cycleBack(), value, dye.duration()));
            CustomizeScreen.updated();
        });
        delaySlider.setTooltip(Tooltip.create(DELAY_TOOLTIP_TEXT));

        durationSlider = new Slider(0, 0, sliderWidth, 0.1f, 10.0f, 0.1f, DURATION_TEXT, value -> {
            String itemUuid = Customize.uuid(currentItem);
            AnimatedDye dye = TeslaMapsConfig.get().customAnimatedDyes.get(itemUuid);
            if (dye == null) return;
            TeslaMapsConfig.get().customAnimatedDyes.put(itemUuid,
                    new AnimatedDye(dye.keyframes(), dye.cycleBack(), dye.delay(), value));
            CustomizeScreen.updated();
        });
        durationSlider.setTooltip(Tooltip.create(DURATION_TOOLTIP_TEXT));

        children = List.of(colorPicker, argbTextInput, timelineWidget, resetColorButton, pickDyeButton,
                animatedCheckbox, notCustomizableText, cycleBackCheckbox, delaySlider, durationSlider);

        layout = new FrameLayout(getWidth() - PADDING * 2, getHeight() - PADDING * 2);
        layout.addChild(timelineWidget, LayoutSettings::alignVerticallyBottom);

        GridLayout grid = new GridLayout().spacing(3);
        grid.addChild(argbTextInput, 0, 1);
        grid.addChild(resetColorButton, 0, 1, 1, 3, LayoutSettings::alignHorizontallyRight);
        grid.addChild(pickDyeButton, 0, 2, 1, 4, LayoutSettings::alignHorizontallyRight);
        grid.addChild(animatedCheckbox, 1, 1, 1, 2);
        grid.addChild(delaySlider, 1, 3, 1, 2, LayoutSettings::alignHorizontallyRight);
        grid.addChild(cycleBackCheckbox, 2, 1, 1, 2);
        grid.addChild(durationSlider, 2, 3, 1, 2, LayoutSettings::alignHorizontallyRight);
        grid.addChild(colorPicker, 0, 0, 3, 1);
        layout.addChild(grid, LayoutSettings::alignVerticallyTop);
        layout.addChild(notCustomizableText);
        updateWidgetDimensions();
    }

    private void updateWidgetDimensions() {
        int w = getWidth() - PADDING * 2;
        int h = getHeight() - PADDING * 2;
        timelineWidget.setWidth(w);
        colorPicker.setHeight(Math.max(20, Math.min(h - timelineWidget.getHeight() - 5, w / 3 / 2)));
        colorPicker.setWidth(colorPicker.getHeight() * 2);
        delaySlider.setWidth((int) (w * 0.35f));
        durationSlider.setWidth((int) (w * 0.35f));
        layout.arrangeElements();
        layout.setPosition(getX() + PADDING, getY() + PADDING);
        width = layout.getWidth() + PADDING * 2;
        height = layout.getHeight() + PADDING * 2;
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
        updateWidgetDimensions();
    }

    public AnimatedDyeTimelineWidget getTimelineWidget() {
        return timelineWidget;
    }

    private void onPickerColorChanged(int argb, boolean release) {
        argbTextInput.setARGBColor(argb);
        if (!animated) {
            TeslaMapsConfig.get().customDyeColors.put(Customize.uuid(currentItem), ARGB.opaque(argb));
            CustomizeScreen.updated();
        } else if (release) {
            timelineWidget.setColor(argb);
        }
    }

    private void onTextInputColorChanged(int argb) {
        colorPicker.setARGBColor(argb);
        if (animated) {
            timelineWidget.setColor(argb);
        } else {
            TeslaMapsConfig.get().customDyeColors.put(Customize.uuid(currentItem), ARGB.opaque(argb));
            CustomizeScreen.updated();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return getChildAt(mouseX, mouseY).filter(element -> element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)).isPresent()
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void onTimelineFrameSelected(int color, float time) {
        argbTextInput.setARGBColor(color);
        colorPicker.setARGBColor(color);
    }

    private void onRemoveCustomColor(Button button) {
        animated = false;
        ((CheckboxAccessor) animatedCheckbox).setSelected(false);
        changeVisibilities();

        String itemUuid = Customize.uuid(currentItem);
        TeslaMapsConfig.get().customDyeColors.remove(itemUuid);
        TeslaMapsConfig.get().customAnimatedDyes.remove(itemUuid);
        CustomizeScreen.updated();

        int color = DyedItemColor.getOrDefault(currentItem, -1);
        argbTextInput.setARGBColor(color);
        colorPicker.setARGBColor(color);
    }

    private void onAnimatedCheckbox(Checkbox checkbox, boolean checked) {
        animated = checked;
        changeVisibilities();
        String itemUuid = Customize.uuid(currentItem);
        if (animated) {
            TeslaMapsConfig.get().customAnimatedDyes.put(itemUuid, new AnimatedDye(
                    List.of(new AnimatedDye.Keyframe(CommonColors.RED, 0), new AnimatedDye.Keyframe(CommonColors.BLUE, 1)),
                    true, 0, 1.f));
            CustomizeScreen.updated();
            timelineWidget.setAnimatedDye(itemUuid);
            delaySlider.setValue(0);
            durationSlider.setValue(1);
            ((CheckboxAccessor) cycleBackCheckbox).setSelected(true);
        } else {
            Integer stored = TeslaMapsConfig.get().customDyeColors.get(itemUuid);
            int color = stored != null ? stored : DyedItemColor.getOrDefault(currentItem, -1);
            colorPicker.setARGBColor(color);
            argbTextInput.setARGBColor(color);
            TeslaMapsConfig.get().customAnimatedDyes.remove(itemUuid);
            CustomizeScreen.updated();
        }
    }

    private void onCycleBackCheckbox(Checkbox checkbox, boolean checked) {
        String itemUuid = Customize.uuid(currentItem);
        AnimatedDye dye = TeslaMapsConfig.get().customAnimatedDyes.get(itemUuid);
        if (dye == null) return;
        TeslaMapsConfig.get().customAnimatedDyes.put(itemUuid,
                new AnimatedDye(dye.keyframes(), checked, dye.delay(), dye.duration()));
        CustomizeScreen.updated();
    }

    private void onClickPickDye(Button button) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() == null) return;
        client.gui.setScreen(new DyeSelectPopup(client.gui.screen(), this::setFromSolidDye, this::setFromAnimatedDye));
    }

    private void setFromSolidDye(Button button, int hex) {
        this.onRemoveCustomColor(button);
        this.onPickerColorChanged(hex, true);
    }

    private void setFromAnimatedDye(Button button, List<AnimatedDye.Keyframe> keyFrames, boolean cycleBack) {
        this.onRemoveCustomColor(button);
        this.animated = true;
        ((CheckboxAccessor) animatedCheckbox).setSelected(true);
        ((CheckboxAccessor) cycleBackCheckbox).setSelected(cycleBack);
        durationSlider.setValue(10);
        changeVisibilities();

        String itemUuid = Customize.uuid(currentItem);
        TeslaMapsConfig.get().customAnimatedDyes.put(itemUuid, new AnimatedDye(keyFrames, cycleBack, 0, 10));
        CustomizeScreen.updated();
        timelineWidget.setAnimatedDye(itemUuid);
        timelineWidget.recreateImage();
    }

    private void changeVisibilities() {
        colorPicker.visible = customizable;
        argbTextInput.visible = customizable;

        timelineWidget.visible = customizable && animated;
        cycleBackCheckbox.visible = customizable && animated;
        delaySlider.visible = customizable && animated;
        durationSlider.visible = customizable && animated;

        resetColorButton.visible = customizable;
        pickDyeButton.visible = customizable;
        animatedCheckbox.visible = customizable;
        notCustomizableText.visible = !customizable;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return children;
    }

    @Override
    protected int contentHeight() {
        return 0;
    }

    @Override
    protected double scrollRate() {
        return 0;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CustomizeGui.INNER_SPACE, getX(), getY(), getWidth(), getHeight());
        for (AbstractWidget child : children) {
            child.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!super.mouseClicked(click, doubled)) {
            setFocused(null);
            return false;
        }
        return true;
    }

    @Override
    public void close() {
        timelineWidget.close();
    }

    public void setCurrentItem(ItemStack currentItem) {
        this.currentItem = currentItem;
        refresh();
    }

    public void refresh() {
        String itemUuid = Customize.uuid(currentItem);
        ResourceKey<EquipmentAsset> key = null;
        if (TeslaMapsConfig.get().customArmorModel.containsKey(itemUuid)) {
            key = ResourceKey.create(EquipmentAssets.ROOT_ID, TeslaMapsConfig.get().customArmorModel.get(itemUuid));
        } else if (currentItem.has(DataComponents.EQUIPPABLE)) {
            Equippable component = currentItem.get(DataComponents.EQUIPPABLE);
            key = component == null ? null : component.assetId().orElse(null);
        }
        if (key == null) {
            customizable = false;
        } else {
            EquipmentClientInfo model = ((EntityRenderDispatcherAccessor) Minecraft.getInstance().getEntityRenderDispatcher())
                    .getEquipmentAssets().get(key);
            // non-dyeable body armor still counts: it renders as dyed leather, like on hypixel
            customizable = !model.getLayers(EquipmentClientInfo.LayerType.HUMANOID).isEmpty()
                    || !model.getLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS).isEmpty()
                    || Stream.of(EquipmentClientInfo.LayerType.WINGS)
                    .flatMap(l -> model.getLayers(l).stream())
                    .anyMatch(layer -> layer.dyeable().isPresent());
        }
        if (!customizable) {
            animated = false;
            ((CheckboxAccessor) animatedCheckbox).setSelected(false);
            changeVisibilities();
            return;
        }

        AnimatedDye animatedDye = TeslaMapsConfig.get().customAnimatedDyes.get(itemUuid);
        Integer stored = TeslaMapsConfig.get().customDyeColors.get(itemUuid);
        if (animatedDye != null) {
            animated = true;
            ((CheckboxAccessor) cycleBackCheckbox).setSelected(animatedDye.cycleBack());
            delaySlider.setValue(animatedDye.delay());
            durationSlider.setValue(animatedDye.duration());
            timelineWidget.setAnimatedDye(itemUuid);
        } else {
            animated = false;
            int color = stored != null ? stored : DyedItemColor.getOrDefault(currentItem, -1);
            argbTextInput.setARGBColor(color);
            colorPicker.setARGBColor(color);
        }
        changeVisibilities();
        ((CheckboxAccessor) animatedCheckbox).setSelected(animated);
    }

    private static class Slider extends AbstractSliderButton {
        private final float minValue;
        private final float maxValue;
        private final float step;
        private final String translatable;
        private final Consumer<Float> onValueChanged;

        private boolean clicked = false;

        private Slider(int x, int y, int width, float min, float max, float step, String translatable, Consumer<Float> onValueChanged) {
            super(x, y, width, 15, Component.empty(), 0);
            this.minValue = min;
            this.maxValue = max;
            this.step = step;
            this.translatable = translatable;
            this.onValueChanged = onValueChanged;
            updateMessage();
        }

        private float trueValue() {
            return roundToStep(value * (maxValue - minValue));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(translatable, DOUBLE_NUMBERS.format(trueValue())));
        }

        private void setValue(float val) {
            value = (val - minValue) / (maxValue - minValue);
            updateMessage();
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            super.onClick(click, doubled);
            clicked = true;
        }

        @Override
        public void onRelease(MouseButtonEvent click) {
            super.onRelease(click);
            if (clicked) {
                onValueChanged.accept(trueValue());
                clicked = false;
            }
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (super.keyPressed(input)) {
                onValueChanged.accept(trueValue());
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (verticalAmount == 0) return false;
            float offset = verticalAmount > 0 ? step : -step;
            setValue(Math.clamp(trueValue() + offset, minValue, maxValue));
            onValueChanged.accept(trueValue());
            return true;
        }

        // not used, the value is only pushed on release so it does not write on every drag tick
        @Override
        protected void applyValue() {}

        private float roundToStep(double value) {
            return Math.clamp(minValue + step * Math.round(value / step), minValue, maxValue);
        }
    }
}
