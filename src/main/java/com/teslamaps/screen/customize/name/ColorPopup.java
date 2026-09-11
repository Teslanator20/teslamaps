package com.teslamaps.screen.customize.name;

import com.teslamaps.render.gui.ArgbTextInput;
import com.teslamaps.render.gui.ColorPicker;
import com.teslamaps.render.gui.PopupScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/** Single colour or gradient picker for the name editor. Ported from Skyblocker. */
public class ColorPopup extends PopupScreen {

    private final GridLayout layout = new GridLayout();

    private final boolean gradient;
    private final GradientConsumer gradientConsumer;
    private int startColor = -1;
    private int endColor = -1;

    private ColorPopup(Screen backgroundScreen, GradientConsumer gradientConsumer, boolean gradient) {
        super(Component.literal("Color Popup"), backgroundScreen);
        this.gradientConsumer = gradientConsumer;
        this.gradient = gradient;
        layout.defaultCellSetting().alignHorizontallyCenter();
    }

    public static ColorPopup create(Screen backgroundScreen, IntConsumer colorConsumer) {
        return new ColorPopup(backgroundScreen, (start, end) -> colorConsumer.accept(start), false);
    }

    public static ColorPopup createGradient(Screen backgroundScreen, GradientConsumer gradientConsumer) {
        return new ColorPopup(backgroundScreen, gradientConsumer, true);
    }

    @Override
    protected void init() {
        GridLayout.RowHelper adder = layout.createRowHelper(2);
        addRenderableWidget(adder.addChild(new StringWidget(Component.translatable("teslamaps.customize.name.customColorTitle"), font), 2));
        if (gradient) createLayoutGradient(adder);
        else createLayout(adder);
        adder.addChild(SpacerElement.height(15), 2);
        addRenderableWidget(adder.addChild(Button.builder(Component.translatable("gui.cancel"), b -> onClose()).build(),
                LayoutSettings.defaults().alignHorizontallyRight().paddingRight(2)));
        addRenderableWidget(adder.addChild(Button.builder(Component.translatable("gui.done"), b -> {
            gradientConsumer.accept(startColor, endColor);
            onClose();
        }).build(), LayoutSettings.defaults().alignHorizontallyLeft().paddingLeft(2)));
        super.init();
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        layout.arrangeElements();
        layout.setPosition((width - layout.getWidth()) / 2, (height - layout.getHeight()) / 2);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        extractPopupBackground(graphics, layout.getX(), layout.getY(), layout.getWidth(), layout.getHeight());
    }

    private void createLayout(GridLayout.RowHelper adder) {
        ColorPicker colorPicker = new ColorPicker(0, 0, 200, 100);
        ArgbTextInput argb = new ArgbTextInput(0, 0, font, true, false);
        addRenderableWidget(colorPicker);
        addRenderableWidget(argb);

        argb.setOnChange(color -> {
            colorPicker.setARGBColor(color);
            startColor = color;
        });
        colorPicker.setOnColorChange((color, release) -> {
            argb.setARGBColor(color);
            startColor = color;
        });

        adder.addChild(colorPicker, 2);
        adder.addChild(argb, 2);
    }

    private void createLayoutGradient(GridLayout.RowHelper adder) {
        ColorPicker pickerStart = new ColorPicker(0, 0, 200, 100);
        ArgbTextInput argbStart = new ArgbTextInput(0, 0, font, true, false);
        ColorPicker pickerEnd = new ColorPicker(0, 0, 200, 100);
        ArgbTextInput argbEnd = new ArgbTextInput(0, 0, font, true, false);
        addRenderableWidget(pickerStart);
        addRenderableWidget(argbStart);
        addRenderableWidget(pickerEnd);
        addRenderableWidget(argbEnd);

        argbStart.setOnChange(color -> {
            pickerStart.setARGBColor(color);
            startColor = color;
        });
        pickerStart.setOnColorChange((color, release) -> {
            argbStart.setARGBColor(color);
            startColor = color;
        });
        argbEnd.setOnChange(color -> {
            pickerEnd.setARGBColor(color);
            endColor = color;
        });
        pickerEnd.setOnColorChange((color, release) -> {
            argbEnd.setARGBColor(color);
            endColor = color;
        });

        addRenderableWidget(adder.addChild(new StringWidget(Component.translatable("teslamaps.customize.name.gradientStart"), font)));
        addRenderableWidget(adder.addChild(new StringWidget(Component.translatable("teslamaps.customize.name.gradientEnd"), font)));

        adder.addChild(pickerStart);
        adder.addChild(pickerEnd);
        adder.addChild(argbStart);
        adder.addChild(argbEnd);
    }

    @FunctionalInterface
    public interface GradientConsumer {
        void accept(int start, int end);
    }
}
