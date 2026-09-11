package com.teslamaps.screen.customize;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * One trim pattern or material. Ported from Skyblocker's TrimElementButton; the pattern button
 * draws the smithing template that provides it instead of a rotating 3D armor piece, since
 * 26.1.2 has no way to register a picture-in-picture renderer.
 */
public abstract sealed class TrimElementButton extends AbstractButton permits TrimElementButton.Pattern, TrimElementButton.Material {
    protected static final ItemStack BARRIER = new ItemStack(Items.BARRIER);

    protected final @Nullable Identifier element;
    protected ItemStack stack = BARRIER;
    private final Consumer<TrimElementButton> onPress;

    public TrimElementButton(@Nullable Identifier element, Component name, Consumer<TrimElementButton> onPress) {
        super(0, 0, 20, 20, name);
        this.element = element;
        this.onPress = onPress;
        setTooltip(Tooltip.create(getMessage()));
    }

    public @Nullable Identifier getElement() {
        return element;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractDefaultSprite(graphics);
        graphics.item(stack, getX() + getWidth() / 2 - 8, getY() + getHeight() / 2 - 8);
    }

    public static final class Pattern extends TrimElementButton {
        public Pattern(@Nullable Identifier element, @Nullable TrimPattern pattern, Consumer<TrimElementButton> onPress) {
            super(element, pattern == null ? Component.translatable("gui.none") : pattern.description(), onPress);
            if (element == null) return;
            // 26.1.2 has no PROVIDES_TRIM_PATTERN component; vanilla names the templates after the pattern
            Identifier templateId = Identifier.fromNamespaceAndPath(element.getNamespace(),
                    element.getPath() + "_armor_trim_smithing_template");
            stack = BuiltInRegistries.ITEM.getOptional(templateId).map(ItemStack::new).orElse(BARRIER);
        }

        /** Kept so the caller can stay identical to Skyblocker's; the 2D icon needs no stack. */
        public void setStack(ItemStack newStack) {}
    }

    public static final class Material extends TrimElementButton {
        public Material(Identifier element, TrimMaterial material, Consumer<TrimElementButton> onPress) {
            super(element, material.description(), onPress);
            stack = BuiltInRegistries.ITEM.stream()
                    .filter(item -> Optional.ofNullable(item.components().get(DataComponents.PROVIDES_TRIM_MATERIAL))
                            .map(provided -> provided.is(element))
                            .orElse(false))
                    .findAny()
                    .map(ItemStack::new)
                    .orElse(BARRIER);
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        onPress.accept(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}
}
