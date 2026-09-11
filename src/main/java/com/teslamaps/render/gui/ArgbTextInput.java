package com.teslamaps.render.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.IntConsumer;

/** Hex colour field, one nibble at a time. Ported from Skyblocker's ARGBTextInput. */
public class ArgbTextInput extends AbstractWidget {
    private static final ChatFormatting[] FORMATTINGS = {ChatFormatting.WHITE, ChatFormatting.RED, ChatFormatting.GREEN, ChatFormatting.BLUE};
    private static final String HEXADECIMAL_CHARS = "0123456789aAbBcCdDeEfF";

    private final int length;
    private final boolean drawBackground;
    private final Font textRenderer;
    private final boolean hasAlpha;
    private final int alphaMask;

    private String input;
    private int index = 0;

    private @Nullable IntConsumer onChange = null;

    public ArgbTextInput(int x, int y, Font textRenderer, boolean drawBackground, boolean hasAlpha) {
        super(x, y, textRenderer.width(hasAlpha ? "AAAAAAAA" : "AAAAAA") + (drawBackground ? 6 : 0),
                10 + (drawBackground ? 4 : 0), Component.nullToEmpty("ARGBTextInput"));
        this.drawBackground = drawBackground;
        this.textRenderer = textRenderer;
        this.length = hasAlpha ? 8 : 6;
        this.hasAlpha = hasAlpha;
        this.alphaMask = hasAlpha ? 0 : 0xFF000000;
        this.input = hasAlpha ? "FFFFFFFF" : "FFFFFF";
    }

    public ArgbTextInput(int x, int y, Font textRenderer, boolean drawBackground) {
        this(x, y, textRenderer, drawBackground, false);
    }

    private OptionalInt getOptionalARGBColor(String value) {
        try {
            return OptionalInt.of(alphaMask | Integer.parseUnsignedInt(value, 16));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    public int getARGBColor() {
        return getOptionalARGBColor(input).orElse(CommonColors.WHITE);
    }

    public void setARGBColor(int argb) {
        input = String.format(hasAlpha ? "%08X" : "%06X", argb & (~alphaMask));
    }

    public void setOnChange(@Nullable IntConsumer onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int selectionStart = textRenderer.width(input.substring(0, index));
        int selectionEnd = textRenderer.width(input.substring(0, index + 1));
        int textX = getX() + (drawBackground ? 3 : 0);
        int textY = getY() + (getHeight() - textRenderer.lineHeight) / 2;
        if (drawBackground) {
            context.fill(getX(), getY(), getRight(), getBottom(), isFocused() ? CommonColors.WHITE : CommonColors.GRAY);
            context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1, CommonColors.BLACK);
        }

        if (isFocused()) {
            context.fill(textX + selectionStart, textY, textX + selectionEnd, textY + textRenderer.lineHeight, 0xFF_00_BB_FF);
            context.fill(textX + selectionStart, textY + textRenderer.lineHeight - 1, textX + selectionEnd, textY + textRenderer.lineHeight, CommonColors.WHITE);
        }
        context.text(textRenderer, visitor -> {
            int start = hasAlpha ? 0 : 1;
            for (int i = 0; i < input.length(); i++) {
                if (!visitor.accept(i, isHoveredOrFocused() ? Style.EMPTY.applyFormat(FORMATTINGS[i / 2 + start]) : Style.EMPTY, input.charAt(i))) return false;
            }
            return true;
        }, textX, textY, CommonColors.WHITE, true);

        if (this.isHovered()) {
            context.requestCursor(CursorTypes.IBEAM);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    @Override
    public boolean keyPressed(KeyEvent keyInput) {
        if (!isFocused()) return false;
        boolean handled = switch (keyInput.key()) {
            case InputConstants.KEY_DELETE -> {
                StringBuilder builder = new StringBuilder(input);
                builder.setCharAt(index, '0');
                input = builder.toString();
                yield true;
            }
            case InputConstants.KEY_BACKSPACE -> {
                StringBuilder builder = new StringBuilder(input);
                builder.setCharAt(index, '0');
                input = builder.toString();
                index = Math.max(0, index - 1);
                yield true;
            }
            case InputConstants.KEY_LEFT -> {
                index = Math.max(0, index - 1);
                yield true;
            }
            case InputConstants.KEY_RIGHT -> {
                index = Math.min(length - 1, index + 1);
                yield true;
            }
            default -> false;
        };
        if (handled) {
            callOnChange();
            return true;
        }
        if (keyInput.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(input);
            return true;
        }
        if (keyInput.isPaste()) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard.startsWith("#")) clipboard = clipboard.substring(1);
            String value = clipboard.substring(0, Math.min(hasAlpha ? 8 : 6, clipboard.length()));
            getOptionalARGBColor(value.toUpperCase(Locale.ENGLISH)).ifPresent(color -> {
                setARGBColor(color);
                callOnChange();
            });
            return true;
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (!isFocused()) return false;
        if (HEXADECIMAL_CHARS.contains(input.codepointAsString())) {
            this.input = new StringBuilder(this.input).replace(index, index + 1, input.codepointAsString().toUpperCase(Locale.ENGLISH)).toString();
            index = Math.min(length - 1, index + 1);
            callOnChange();
            return true;
        }
        return super.charTyped(input);
    }

    private void callOnChange() {
        if (onChange != null) onChange.accept(getARGBColor());
    }

    @Override
    public void onClick(MouseButtonEvent click, boolean doubled) {
        super.onClick(click, doubled);
        index = findClickedChar((int) click.x());
    }

    @Override
    protected void onDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        super.onDrag(click, deltaX, deltaY);
        index = findClickedChar((int) click.x());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        int beginIndex = (findClickedChar((int) mouseX) / 2) * 2;
        int prev;
        try {
            prev = Integer.parseUnsignedInt(input.substring(beginIndex, beginIndex + 2), 16);
        } catch (NumberFormatException e) {
            return true;
        }
        int updated = Math.clamp(prev + (int) ((int) Math.signum(verticalAmount) * Math.ceil(Math.abs(verticalAmount))), 0, 255);
        if (updated != prev) {
            input = new StringBuilder(input).replace(beginIndex, beginIndex + 2, String.format("%02X", updated)).toString();
            callOnChange();
        }
        return true;
    }

    private int findClickedChar(int mouseX) {
        return Math.clamp(textRenderer.plainSubstrByWidth(input, mouseX - getX() - (drawBackground ? 3 : 0)).length(), 0, length - 1);
    }
}
