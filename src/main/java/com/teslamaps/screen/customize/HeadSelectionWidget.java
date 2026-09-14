package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.CustomAnimatedHelmetTextures;
import com.teslamaps.features.customize.CustomHelmetTextures;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.render.gui.SearchableGridWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Grid of every skyblock head skin plus NEU's animated ones. Ported from Skyblocker. */
public class HeadSelectionWidget extends SearchableGridWidget {
    private final List<HeadButton> allButtons = new ArrayList<>();
    private final HeadButton noneButton;

    private @Nullable ItemStack currentItem;
    private HeadButton selectedButton;

    public HeadSelectionWidget(int x, int y, int width, int height) {
        super(x + 2, y + 2, width - 4, height - 4, Component.nullToEmpty("HeadSelection"), 20, true);

        for (CustomHelmetTextures.NamedTexture tex : CustomHelmetTextures.getTextures()) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, CustomHelmetTextures.getProfile(tex.texture()));
            allButtons.add(new HeadButton(tex.name(), tex.texture(), head, this::onClick));
        }

        for (String id : CustomAnimatedHelmetTextures.getAnimatedHeadIds()) {
            allButtons.add(new AnimatedHeadButton(id, this::onClick));
        }

        this.noneButton = new HeadButton("", this::onClick);
        this.selectedButton = this.noneButton;
        this.allButtons.add(this.noneButton);

        setSearch("");
    }

    @Override
    public void setX(int x) {
        super.setX(x + 2);
    }

    @Override
    public void setY(int y) {
        super.setY(y + 2);
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width - 4);
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height - 4);
    }

    private void onClick(HeadButton button) {
        selectedButton = button;
        updateConfig();
        updateButtons();
    }

    private void updateConfig() {
        if (this.currentItem == null) return;
        String uuid = Customize.uuid(this.currentItem);
        if (uuid.isEmpty()) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();

        switch (this.selectedButton) {
            // animated heads carry no texture, so this has to come before the null check
            case AnimatedHeadButton button -> {
                config.customAnimatedHelmetTextures.put(uuid, button.id);
                config.customHelmetTextures.remove(uuid);
            }
            case HeadButton button when button == noneButton || button.texture == null -> {
                config.customHelmetTextures.remove(uuid);
                config.customAnimatedHelmetTextures.remove(uuid);
            }
            case HeadButton button -> {
                config.customHelmetTextures.put(uuid, Objects.requireNonNull(button.texture));
                config.customAnimatedHelmetTextures.remove(uuid);
            }
        }
        CustomizeScreen.updated();
    }

    private void updateButtons() {
        for (HeadButton b : this.allButtons) {
            b.selected = b.equals(this.selectedButton);
        }
    }

    @Override
    protected Collection<? extends AbstractWidget> filterWidgets(String search) {
        setScrollAmount(0);
        updateButtons();
        String s = search.toLowerCase(Locale.ENGLISH);
        return allButtons.stream().filter(b -> b == noneButton || b.name.toLowerCase(Locale.ENGLISH).contains(s)).toList();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CustomizeGui.INNER_SPACE, getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
    }

    public void setCurrentItem(ItemStack item) {
        this.currentItem = item;
        String uuid = Customize.uuid(item);

        String animatedHeadId = TeslaMapsConfig.get().customAnimatedHelmetTextures.get(uuid);
        String customHeadTexture = TeslaMapsConfig.get().customHelmetTextures.get(uuid);
        HeadButton intended;

        if (animatedHeadId != null) {
            intended = this.allButtons.stream()
                    .filter(AnimatedHeadButton.class::isInstance)
                    .map(AnimatedHeadButton.class::cast)
                    .filter(head -> head.id.equals(animatedHeadId))
                    .findFirst()
                    .map(HeadButton.class::cast)
                    .orElse(noneButton);
        } else if (customHeadTexture != null) {
            intended = this.allButtons.stream()
                    .filter(Predicate.not(AnimatedHeadButton.class::isInstance))
                    .filter(head -> head.texture != null && head.texture.equals(customHeadTexture))
                    .findFirst()
                    .orElse(noneButton);
        } else {
            intended = noneButton;
        }

        this.selectedButton = intended;
        updateButtons();
    }

    private static class HeadButton extends AbstractWidget {
        private final String name;
        /** Only null for animated heads and the "none" button. */
        private final @Nullable String texture;
        private final ItemStack head;
        private final Consumer<HeadButton> onPress;
        private boolean selected = false;

        private HeadButton(String name, Consumer<HeadButton> onPress) {
            this(name, null, new ItemStack(Items.BARRIER), onPress);
        }

        private HeadButton(String name, @Nullable String texture, ItemStack head, Consumer<HeadButton> onPress) {
            super(0, 0, 20, 20, Component.empty());
            this.name = name;
            this.texture = texture;
            this.head = head;
            this.onPress = onPress;
            if (!name.isEmpty()) setTooltip(Tooltip.create(Component.nullToEmpty(name)));
        }

        protected ItemStack getHead() {
            return this.head;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.item(this.getHead(), getX() + 2, getY() + 2);
            if (this.selected) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x3000FF00);
            if (this.isHovered()) graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20FFFFFF);
            this.handleCursor(graphics);
        }

        @Override
        public void onClick(MouseButtonEvent click, boolean doubled) {
            this.onPress.accept(this);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }

    private static class AnimatedHeadButton extends HeadButton {
        private final String id;

        private AnimatedHeadButton(String id, Consumer<HeadButton> onPress) {
            super(CustomAnimatedHelmetTextures.formatName(id), onPress);
            this.id = id;
        }

        /** The stack has to change every frame, so it is rebuilt each time. */
        @Override
        protected ItemStack getHead() {
            ResolvableProfile profile = CustomAnimatedHelmetTextures.animateHeadTexture(this.id);
            if (profile == null) return new ItemStack(Items.BARRIER);
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, profile);
            return stack;
        }
    }
}
