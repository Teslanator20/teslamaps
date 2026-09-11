package com.teslamaps.screen.customize.name;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.screen.customize.CustomizeScreen;
import com.teslamaps.screen.customize.name.visitor.GetClickedPositionVisitor;
import com.teslamaps.screen.customize.name.visitor.GetRenderWidthVisitor;
import com.teslamaps.screen.customize.name.visitor.GetStyleVisitor;
import com.teslamaps.screen.customize.name.visitor.InsertTextVisitor;
import com.teslamaps.screen.customize.name.visitor.SetStyleVisitor;
import com.teslamaps.utils.OkLabColor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Styled item name editor: formatting buttons, colour swatches, custom colour and gradient. Ported from Skyblocker. */
public class CustomizeNameWidget extends AbstractContainerWidget {
    private static final int PADDING = 3;

    private final Minecraft client = Minecraft.getInstance();
    private final Font textRenderer = client.font;
    private String uuid = "";

    private Component text = Component.empty();
    private String textString = "";

    private final TextField textField;
    private final StringWidget previewWidget;
    private final FormattingButton[] formattingButtons;
    private final List<AbstractWidget> widgets;

    private final GridLayout grid = new GridLayout();

    private int selectionStart;
    private int selectionEnd;

    private @Nullable Style insertAs;

    public CustomizeNameWidget(Screen parent) {
        super(0, 0, 0, 0, Component.literal("Customize Item Name"), AbstractScrollArea.defaultSettings(4));
        ImmutableList.Builder<AbstractWidget> builder = ImmutableList.builder();
        // the gui is a grid of 20 columns at 16 px each
        textField = grid.addChild(new TextField(), 1, 0, 1, 20);
        builder.add(textField);
        formattingButtons = new FormattingButton[]{
                new FormattingButton("B", ChatFormatting.BOLD, Style::isBold),
                new FormattingButton("I", ChatFormatting.ITALIC, Style::isItalic),
                new FormattingButton("U", ChatFormatting.UNDERLINE, Style::isUnderlined),
                new FormattingButton("S", ChatFormatting.STRIKETHROUGH, Style::isStrikethrough),
                new FormattingButton("|||", ChatFormatting.OBFUSCATED, Style::isObfuscated),
        };

        addFormattingButtons(builder);

        builder.add(grid.addChild(Button.builder(Component.translatable("teslamaps.customize.name.customColor"), b ->
                client.setScreen(ColorPopup.create(parent, color -> setStyle(Style.EMPTY.withColor(color))))
        ).size(48, 16).build(), 2, 17, 1, 3));
        builder.add(grid.addChild(Button.builder(Component.translatable("teslamaps.customize.name.gradientColor"), b ->
                client.setScreen(ColorPopup.createGradient(parent, this::createGradient))
        ).size(48, 16).build(), 3, 17, 1, 3));
        builder.add(grid.addChild(new StringWidget(20 * 16, textRenderer.lineHeight,
                        Component.translatable("teslamaps.customize.name.howToRemove").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY), textRenderer),
                4, 0, 1, 20, LayoutSettings.defaults().paddingTop(2)));
        builder.add(previewWidget = grid.addChild(new StringWidget(20 * 16, textRenderer.lineHeight, Component.empty(), textRenderer)
                        .setMaxWidth(20 * 16, StringWidget.TextOverflow.SCROLLING),
                5, 0, 1, 20, LayoutSettings.defaults().paddingVertical(2).alignHorizontallyCenter()));
        widgets = builder.build();
        grid.arrangeElements();
        grid.setPosition(getX() + PADDING, getY() + PADDING);
        setSize(grid.getWidth() + PADDING * 2, grid.getHeight() + PADDING * 2);
        selectionStart = selectionEnd = textString.length();
    }

    private void addFormattingButtons(ImmutableList.Builder<AbstractWidget> builder) {
        for (int i = 0; i < formattingButtons.length; i++) {
            builder.add(grid.addChild(formattingButtons[i], 0, i));
        }

        int colorButtonIndex = 0;
        for (ChatFormatting formatting : ChatFormatting.values()) {
            if (TextColor.fromLegacyFormat(formatting) != null) {
                builder.add(grid.addChild(new ColorButton(formatting), 2, colorButtonIndex++));
            }
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        grid.setX(getX() + PADDING);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        grid.setY(getY() + PADDING);
    }

    public void setItem(ItemStack stack) {
        uuid = Customize.uuid(stack);
        setText(stack.getHoverName().copy(), false);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CustomizeGui.INNER_SPACE, getX(), getY(), getWidth(), getHeight());
        for (AbstractWidget widget : widgets) {
            widget.extractRenderState(graphics, mouseX, mouseY, deltaTicks);
        }
    }

    /** Gradient across the current selection, one keyframe per character. */
    private void createGradient(int startColor, int endColor) {
        int previousSelectionStart = selectionStart;
        int previousSelectionEnd = selectionEnd;

        int selStart = Math.min(selectionStart, selectionEnd);
        int selSize = Math.abs(selectionEnd - selectionStart);
        if (selSize == 0) return;
        if (selSize == 1) {
            setStyle(Style.EMPTY.withColor(startColor));
        } else {
            for (int i = 0; i < selSize; i++) {
                selectionStart = selStart + i;
                selectionEnd = selStart + i + 1;
                setStyle(Style.EMPTY.withColor(OkLabColor.interpolate(startColor, endColor, (float) i / (selSize - 1))));
            }
        }
        selectionStart = previousSelectionStart;
        selectionEnd = previousSelectionEnd;
    }

    /** Applies a style to the selection, or to whatever gets typed next if nothing is selected. */
    private void setStyle(Style style) {
        if (selectionStart == selectionEnd) {
            insertAs = style.applyTo(insertAs == null ? Style.EMPTY : insertAs);
            return;
        }
        SetStyleVisitor visitor = new SetStyleVisitor(style, selectionStart, selectionEnd);
        text.visit(visitor, Style.EMPTY);
        setText(visitor.getNewText());
    }

    private void updateStyleButtons() {
        GetStyleVisitor styleVisitor = new GetStyleVisitor(selectionStart, selectionEnd);
        text.visit(styleVisitor, Style.EMPTY);
        Style style = styleVisitor.getStyle();
        for (FormattingButton button : formattingButtons) {
            button.update(style);
        }
    }

    public void setText(Component text, boolean updateConfig) {
        this.text = text;
        textString = text.getString();
        if (updateConfig && !uuid.isEmpty()) {
            if (textString.isBlank()) {
                TeslaMapsConfig.get().customItemNames.remove(uuid);
            } else {
                TeslaMapsConfig.get().customItemNames.put(uuid,
                        text.copy().setStyle(Style.EMPTY.withItalic(false).withColor(ChatFormatting.WHITE)));
            }
            CustomizeScreen.updated();
        }
        previewWidget.setMessage(text);
        grid.arrangeElements();

        // called before init
        if (textField != null) textField.needsRecalc = true;
    }

    public void setText(Component text) {
        setText(text, true);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return widgets;
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (super.charTyped(input) || textField.isFocused()) return true;
        setFocused(textField);
        return textField.charTyped(input);
    }

    /** Inserts at the cursor or replaces the selection. */
    public void insertText(String str) {
        str = StringUtil.filterText(str);
        if (textString.isEmpty()) {
            setText(Component.literal(str).setStyle(insertAs != null ? insertAs : Style.EMPTY));
        } else {
            InsertTextVisitor visitor = new InsertTextVisitor(str, insertAs, selectionStart, selectionEnd);
            text.visit(visitor, Style.EMPTY);
            setText(visitor.getNewText());
            insertAs = null;
        }

        selectionStart = Math.min(selectionStart, selectionEnd) + str.length();
        selectionEnd = selectionStart;
        updateStyleButtons();
    }

    private void moveCursor(boolean left, boolean shiftHeld, boolean ctrlHeld) {
        if (left && selectionStart == 0 || (!left && selectionStart == textString.length())) return;
        if (ctrlHeld) {
            selectionStart = getWordSkipPosition(left);
        } else {
            selectionStart = offsetByCodepoints(textString, selectionStart, left ? -1 : 1);
        }
        if (!shiftHeld) selectionEnd = selectionStart;
        insertAs = null;
        updateStyleButtons();
    }

    private static int offsetByCodepoints(String string, int index, int delta) {
        try {
            return Math.clamp(string.offsetByCodePoints(index, delta), 0, string.length());
        } catch (IndexOutOfBoundsException e) {
            return Math.clamp(index + delta, 0, string.length());
        }
    }

    private void erase(boolean left, boolean ctrlHeld) {
        if (selectionStart != selectionEnd) {
            insertText("");
            return;
        }
        moveCursor(left, true, ctrlHeld);
        insertText("");
    }

    private int getWordSkipPosition(boolean left) {
        int i = selectionStart;
        if (!left) {
            int l = this.textString.length();
            i = this.textString.indexOf(32, i);
            if (i == -1) {
                i = l;
            } else {
                while (i < l && this.textString.charAt(i) == ' ') i++;
            }
        } else {
            while (i > 0 && this.textString.charAt(i - 1) == ' ') i--;
            while (i > 0 && this.textString.charAt(i - 1) != ' ') i--;
        }
        return i;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) setFocused(null);
    }

    private static Component formattingName(ChatFormatting formatting) {
        String name = formatting.name().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Component.literal(Character.toUpperCase(name.charAt(0)) + name.substring(1));
    }

    private class FormattingButton extends AbstractButton {
        private boolean enabled;
        private final ChatFormatting format;
        private final Predicate<Style> isEnabled;

        private FormattingButton(String str, ChatFormatting format, Predicate<Style> isEnabled) {
            super(0, 0, 16, 16, Component.literal(str).withStyle(format));
            this.format = format;
            this.isEnabled = isEnabled;
            setTooltip(Tooltip.create(formattingName(format)));
        }

        private void update(Style style) {
            setEnabled(isEnabled.test(style));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            setEnabled(!enabled);
            switch (format) {
                case BOLD -> setStyle(Style.EMPTY.withBold(enabled));
                case ITALIC -> setStyle(Style.EMPTY.withItalic(enabled));
                case UNDERLINE -> setStyle(Style.EMPTY.withUnderlined(enabled));
                case STRIKETHROUGH -> setStyle(Style.EMPTY.withStrikethrough(enabled));
                case OBFUSCATED -> setStyle(Style.EMPTY.withObfuscated(enabled));
                default -> throw new IllegalStateException("Unexpected value: " + format);
            }
        }

        private void setEnabled(boolean enabled) {
            this.enabled = enabled;
            setMessage(getMessage().copy().withColor(enabled ? CommonColors.YELLOW : CommonColors.WHITE));
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            this.extractDefaultLabel(graphics.textRenderer());
        }
    }

    private class ColorButton extends AbstractButton {
        private final ChatFormatting color;
        private final int intColor;

        private ColorButton(ChatFormatting format) {
            super(0, 0, 16, 16, formattingName(format));
            setTooltip(Tooltip.create(getMessage()));
            this.color = format;
            TextColor textColor = TextColor.fromLegacyFormat(format);
            this.intColor = ARGB.opaque(textColor == null ? CommonColors.WHITE : textColor.getValue());
        }

        @Override
        public void onPress(InputWithModifiers input) {
            setStyle(Style.EMPTY.withColor(color));
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2, intColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }

    /** Captures input and draws the text; all the logic lives in the outer widget. */
    private class TextField extends AbstractWidget {
        private @Nullable IMEPreeditOverlay preeditOverlay;
        private int renderedSelectionStart;
        private int renderedSelectionEnd;
        private boolean needsRecalc = false;
        private int renderStart;
        private int renderEnd;

        private TextField() {
            super(0, 0, 320, 20, Component.literal("TextField"));
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            if (renderedSelectionStart != selectionStart || renderedSelectionEnd != selectionEnd || needsRecalc) {
                renderedSelectionStart = selectionStart;
                renderedSelectionEnd = selectionEnd;
                needsRecalc = false;
                GetRenderWidthVisitor visitor = new GetRenderWidthVisitor(selectionStart, selectionEnd);
                text.visit(visitor, Style.EMPTY);
                renderStart = visitor.startWidth();
                renderEnd = visitor.endWidth();
            }

            graphics.fill(getX(), getY(), getRight(), getBottom(), CommonColors.BLACK);
            CustomizeGui.border(graphics, getX(), getY(), getWidth(), getHeight(), isFocused() ? CommonColors.WHITE : CommonColors.GRAY);
            int textX = getTextX();
            int textY = getY() + (getHeight() - textRenderer.lineHeight) / 2;

            if (renderStart != renderEnd) {
                graphics.fill(textX + renderStart, textY, textX + renderEnd, textY + textRenderer.lineHeight, CommonColors.BLUE);
            }
            if (this.isFocused()) {
                graphics.verticalLine(textX + (selectionStart < selectionEnd ? renderStart : renderEnd) - 1, textY - 1, textY + textRenderer.lineHeight, CommonColors.WHITE);
            }

            graphics.text(textRenderer, text, textX, textY, -1, false);

            if (this.preeditOverlay != null) {
                this.preeditOverlay.updateInputPosition(textX, textY);
                graphics.setPreeditOverlay(this.preeditOverlay);
            }

            this.handleCursor(graphics);
        }

        @Override
        protected void handleCursor(GuiGraphicsExtractor graphics) {
            if (this.isHovered()) graphics.requestCursor(CursorTypes.IBEAM);
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            boolean captured = true;
            switch (input.key()) {
                case InputConstants.KEY_LEFT -> moveCursor(true, input.hasShiftDown(), input.hasControlDownWithQuirk());
                case InputConstants.KEY_RIGHT -> moveCursor(false, input.hasShiftDown(), input.hasControlDownWithQuirk());
                case InputConstants.KEY_BACKSPACE -> erase(true, input.hasControlDownWithQuirk());
                case InputConstants.KEY_DELETE -> erase(false, input.hasControlDownWithQuirk());
                default -> captured = false;
            }
            if (captured) return true;

            if (input.isSelectAll()) {
                selectionStart = 0;
                selectionEnd = textString.length();
                updateStyleButtons();
                return true;
            }
            if (input.isCopy()) {
                client.keyboardHandler.setClipboard(selectedText());
                return true;
            }
            if (input.isPaste()) {
                String clipboard = client.keyboardHandler.getClipboard();
                if (!clipboard.isEmpty()) insertText(clipboard);
                return true;
            }
            if (input.isCut()) {
                client.keyboardHandler.setClipboard(selectedText());
                insertText("");
                return true;
            }
            return false;
        }

        private String selectedText() {
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            return text.getString().substring(start, end);
        }

        @Override
        public boolean charTyped(CharacterEvent input) {
            if (!active) return false;
            if (input.isAllowedChatCharacter()) {
                insertText(input.codepointAsString());
                return true;
            }
            return false;
        }

        @Override
        public boolean preeditUpdated(@Nullable PreeditEvent event) {
            if (this.isActive()) {
                this.preeditOverlay = event != null ? new IMEPreeditOverlay(event, CustomizeNameWidget.this.textRenderer, 9 + 1) : null;
                return true;
            }
            return super.preeditUpdated(event);
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            GetClickedPositionVisitor visitor = new GetClickedPositionVisitor((int) click.x() - getTextX());
            text.visit(visitor, Style.EMPTY);
            selectionStart = selectionEnd = visitor.getPosition() < 0 ? textString.length() : visitor.getPosition();
            updateStyleButtons();
        }

        @Override
        protected void onDrag(MouseButtonEvent click, double offsetX, double offsetY) {
            GetClickedPositionVisitor visitor = new GetClickedPositionVisitor((int) click.x() - getTextX());
            text.visit(visitor, Style.EMPTY);
            selectionStart = visitor.getPosition() < 0 ? textString.length() : visitor.getPosition();
            updateStyleButtons();
        }

        private int getTextX() {
            return getX() + 2;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            // required for IME support
            if (focused) Minecraft.getInstance().onTextInputFocusChange(this, focused);
        }

        @Override
        public void playDownSound(SoundManager soundManager) {}

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    @Override
    protected int contentHeight() {
        return 0;
    }

    @Override
    protected double scrollRate() {
        return 0;
    }
}
