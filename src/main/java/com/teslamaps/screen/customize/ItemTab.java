package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.screen.customize.name.CustomizeNameWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.List;

/** Name, glint and item model for a single item. Ported from Skyblocker's ItemTab. */
public class ItemTab extends GridLayoutTab {
    private final CustomizeScreen parentScreen;
    private final CustomizeNameWidget nameWidget;
    private final Button glintButton;
    private final net.minecraft.client.gui.components.EditBox glintColorField;
    private boolean loadingColor;
    private final IdentifierTextField modelField;

    private ItemStack currentItem = ItemStack.EMPTY;
    private TriState glintState = TriState.DEFAULT;

    public ItemTab(CustomizeScreen parentScreen) {
        super(Component.translatable("teslamaps.customize.item"));
        layout.spacing(5);
        this.parentScreen = parentScreen;

        glintButton = Button.builder(Component.empty(), b -> {
            TriState[] states = TriState.values();
            glintState = states[(glintState.ordinal() + 1) % states.length];
            b.setMessage(getGlintText());
            String uuid = Customize.uuid(currentItem);
            if (uuid.isEmpty()) return;
            switch (glintState) {
                case DEFAULT -> TeslaMapsConfig.get().customGlint.remove(uuid);
                case TRUE -> TeslaMapsConfig.get().customGlint.put(uuid, true);
                case FALSE -> TeslaMapsConfig.get().customGlint.put(uuid, false);
            }
            CustomizeScreen.updated();
        }).width(Button.SMALL_WIDTH).build();

        glintColorField = new net.minecraft.client.gui.components.EditBox(Minecraft.getInstance().font, 120, 20, Component.empty());
        glintColorField.setMaxLength(7);
        glintColorField.setHint(Component.literal("Glint color (hex)").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        glintColorField.setResponder(text -> {
            String uuid = Customize.uuid(currentItem);
            if (uuid.isEmpty() || loadingColor) return;
            String hex = text.startsWith("#") ? text.substring(1) : text;
            if (hex.isEmpty()) {
                if (TeslaMapsConfig.get().customGlintColor.remove(uuid) != null) CustomizeScreen.updated();
            } else if (hex.matches("[0-9a-fA-F]{6}")) {
                TeslaMapsConfig.get().customGlintColor.put(uuid, hex.toUpperCase(java.util.Locale.ROOT));
                CustomizeScreen.updated();
            }
        });

        modelField = new IdentifierTextField(120, 20, identifier -> {
            String uuid = Customize.uuid(currentItem);
            if (uuid.isEmpty()) return;
            if (identifier == null) TeslaMapsConfig.get().customItemModel.remove(uuid);
            else TeslaMapsConfig.get().customItemModel.put(uuid, identifier);
            CustomizeScreen.updated();
        });
        modelField.setHint(Component.translatable("teslamaps.customize.item.modelOverride")
                .withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        nameWidget = new CustomizeNameWidget(parentScreen);

        layout.addChild(new ItemSelector(), 0, 0, 1, 1);
        layout.addChild(new BackgroundRenderer(), 0, 1);

        LinearLayout column = layout.addChild(LinearLayout.vertical(), 0, 1, p -> p.alignHorizontallyRight().paddingRight(3).paddingVertical(3));
        column.addChild(glintButton);
        if (TeslaMapsConfig.get().customGlintColors) column.addChild(glintColorField, p -> p.paddingTop(4));
        column.addChild(Button.builder(Component.translatable("teslamaps.customize.item.selectModel"), b -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(new ModelSelectionPopup(parentScreen, model -> {
                if (model != null) modelField.setValue(model.toString());
            }));
        }).width(120).build(), p -> p.paddingTop(4));
        column.addChild(modelField);

        layout.addChild(nameWidget, 1, 0, 1, 2);

        ItemStack initial = parentScreen.initialItem();
        if (!initial.isEmpty() && !Customize.uuid(initial).isEmpty()) {
            setCurrentItem(initial);
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack handStack = player.getMainHandItem();
            if (!Customize.uuid(handStack).isEmpty()) {
                setCurrentItem(handStack);
                return;
            }
            for (ItemStack stack : player.getInventory()) {
                if (!Customize.uuid(stack).isEmpty()) {
                    setCurrentItem(stack);
                    return;
                }
            }
        }
        visitChildren(widget -> widget.visible = false);
        layout.addChild(new StringWidget(Component.translatable("teslamaps.customize.nothingCustomizable"), Minecraft.getInstance().font),
                0, 0, 3, 2, p -> p.alignHorizontallyCenter().alignVerticallyMiddle());
    }

    private void setCurrentItem(ItemStack itemStack) {
        this.currentItem = itemStack;
        String uuid = Customize.uuid(currentItem);
        boolean empty = uuid.isEmpty();
        visitChildren(widget -> widget.visible = !empty);
        if (empty) return;
        parentScreen.backupConfigs(itemStack);
        nameWidget.setItem(itemStack);

        Identifier model = TeslaMapsConfig.get().customItemModel.get(uuid);
        modelField.setValue(model == null ? "" : model.toString());

        Boolean glint = TeslaMapsConfig.get().customGlint.get(uuid);
        glintState = glint == null ? TriState.DEFAULT : (glint ? TriState.TRUE : TriState.FALSE);
        glintButton.setMessage(getGlintText());
        String color = TeslaMapsConfig.get().customGlintColor.get(uuid);
        loadingColor = true;
        glintColorField.setValue(color == null ? "" : color);
        loadingColor = false;
    }

    /** The item the reset buttons act on, empty when nothing here can be edited. */
    ItemStack selected() {
        return currentItem;
    }

    void reloadFromConfig() {
        if (!currentItem.isEmpty()) setCurrentItem(currentItem);
    }

    private Component getGlintText() {
        return Component.translatable("teslamaps.customize.item.glint", switch (glintState) {
            case DEFAULT -> Component.translatable("teslamaps.customize.item.glint.default");
            case TRUE -> CommonComponents.OPTION_ON;
            case FALSE -> CommonComponents.OPTION_OFF;
        });
    }

    private class ItemSelector extends AbstractContainerWidget {
        private static final int PADDING = 3;
        // keeps the item lined up with the model field area
        private static final int ADDED_ITEM_OFFSET = 7;

        private final Button selectItemButton;
        private final LinearLayout layout = LinearLayout.vertical().spacing(5);

        private ItemSelector() {
            super(0, 20, 0, 0, Component.literal("Item Selector"), AbstractScrollArea.defaultSettings(8));
            layout.addChild(SpacerElement.height(32 + ADDED_ITEM_OFFSET));
            selectItemButton = layout.addChild(Button.builder(Component.translatable("teslamaps.customize.item.selectItem"), b ->
                    Minecraft.getInstance().setScreen(new ItemSelectPopup(parentScreen, ItemTab.this::setCurrentItem))
            ).width(Button.SMALL_WIDTH).build());
            layout.arrangeElements();
            layout.setPosition(PADDING, PADDING);
            setSize(layout.getWidth() + PADDING * 2, layout.getHeight() + PADDING * 2);
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
        public List<? extends GuiEventListener> children() {
            return List.of(selectItemButton);
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
            Matrix3x2fStack matrices = graphics.pose();
            matrices.pushMatrix();
            float x = layout.getX() + layout.getWidth() / 2f - 16;
            int y = layout.getY() + ADDED_ITEM_OFFSET;
            if (mouseX >= x && mouseX < x + 32 && mouseY >= y && mouseY < y + 32) {
                graphics.setTooltipForNextFrame(currentItem.getHoverName(), mouseX, mouseY);
            }
            matrices.translate(x, y);
            matrices.scale(2);
            graphics.item(currentItem, 0, 0);
            matrices.popMatrix();
            selectItemButton.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }

    private class BackgroundRenderer extends AbstractWidget {
        BackgroundRenderer() {
            super(0, 0, 0, 0, Component.empty());
            active = false;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = glintButton.getX() - 3;
            int y = glintButton.getY() - 3;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CustomizeGui.INNER_SPACE,
                    x, y, modelField.getRight() + 3 - x, modelField.getBottom() + 3 - y);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }
}
