package com.teslamaps.screen.customize;

import com.teslamaps.mixin.ModelManagerAccessor;
import com.teslamaps.render.gui.SelectionPopup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Every baked item model, for the model override fields. Ported from Skyblocker. */
public class ModelSelectionPopup extends SelectionPopup<ModelSelectionPopup.Widget> {
    protected ModelSelectionPopup(Screen backgroundScreen, Consumer<@Nullable Identifier> onDone) {
        super(Component.literal("Select Model"), backgroundScreen, opt -> onDone.accept(opt.map(Widget::getValue).orElse(null)), 20);
    }

    private final List<Widget> widgets = ((ModelManagerAccessor) Minecraft.getInstance().getModelManager())
            .getBakedItemStackModels().keySet().stream().sorted().map(Widget::new).toList();

    @Override
    protected Collection<Widget> filterWidgets(String input) {
        String needle = input.replace(' ', '_').toLowerCase(Locale.ENGLISH);
        return widgets.stream().filter(w -> w.value.toString().contains(needle)).toList();
    }

    protected class Widget extends AbstractWidget {
        private final ItemStack icon;
        private final Identifier value;

        public Widget(Identifier identifier) {
            super(0, 0, 20, 20, Component.literal(identifier.toString()));
            this.value = identifier;
            this.icon = new ItemStack(Items.CAT_SPAWN_EGG);
            this.icon.set(DataComponents.ITEM_MODEL, identifier);
            setTooltip(Tooltip.create(getMessage()));
        }

        public Identifier getValue() {
            return value;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.item(icon, getX() + 2, getY() + 2);
            if (selectedItem == this) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x3000FF00);
            if (isHovered()) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            super.onClick(click, doubled);
            ModelSelectionPopup.this.setSelectedItem(this);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
}
