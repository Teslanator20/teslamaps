package com.teslamaps.screen.customize;

import com.teslamaps.features.customize.AnimatedDye;
import com.teslamaps.features.customize.RepoDyeColors;
import com.teslamaps.render.gui.PopupScreen;
import com.teslamaps.utils.OkLabColor;
import com.teslamaps.utils.SkyblockItems;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** The real skyblock dyes as a list to pick from. Ported from Skyblocker. */
public class DyeSelectPopup extends PopupScreen {

    private static final int LIST_WIDTH = 150 + net.minecraft.client.gui.components.AbstractScrollArea.SCROLLBAR_WIDTH;

    private DyeListWidget list;
    private StringWidget titleWidget;
    private Button closeButton;

    private final BiConsumer<Button, Integer> updateStatic;
    private final AnimatedDyeConsumer updateAnimated;

    protected DyeSelectPopup(Screen backgroundScreen, BiConsumer<Button, Integer> updateStatic, AnimatedDyeConsumer updateAnimated) {
        super(Component.translatable("teslamaps.customize.armor.pickDye"), backgroundScreen);
        this.updateStatic = updateStatic;
        this.updateAnimated = updateAnimated;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        extractPopupBackground(graphics, list.getX(), list.getY(), list.getWidth(), list.getHeight());
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        if (list == null) return;
        list.setX((width - list.getWidth()) / 2);
        list.setY((height - list.getHeight()) / 2);

        closeButton.setPosition((width - closeButton.getWidth()) / 2, list.getBottom() + 20);
        titleWidget.setPosition((width - titleWidget.getWidth()) / 2, list.getY() - 30);
    }

    @Override
    protected void init() {
        List<AbstractWidget> rows = new ArrayList<>();
        rows.add(new DyeListWidget.Header(Component.translatable("teslamaps.customize.armor.pickDye.dyes",
                Component.translatable("teslamaps.customize.armor.pickDye.dyes.static"))));
        RepoDyeColors.STATIC_DYES.entrySet().stream()
                .sorted(java.util.Comparator.comparing(
                        e -> displayName(e.getKey()), String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> rows.add(new StaticDyeButton(e.getKey(), e.getValue(),
                        button -> selectStaticDye(button, e.getValue()))));

        rows.add(new DyeListWidget.Header(Component.translatable("teslamaps.customize.armor.pickDye.dyes",
                Component.translatable("teslamaps.customize.armor.pickDye.dyes.animated"))));
        RepoDyeColors.ANIMATED_DYES.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("FAIRY"))
                .sorted(java.util.Comparator.comparing(
                        e -> displayName(e.getKey()), String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> rows.add(new AnimatedDyeButton(e.getKey(), e.getValue(),
                        button -> selectAnimatedDye(button, e.getValue()))));

        // whole rows only, and leave room for the title above and the cancel button below
        int visibleRows = Math.clamp((height - 100) / DyeListWidget.ROW, 3, Math.max(3, rows.size()));
        list = new DyeListWidget(LIST_WIDTH, visibleRows, rows);
        addRenderableWidget(list);

        titleWidget = new StringWidget(Component.translatable("teslamaps.customize.armor.pickDye.title"), font);
        closeButton = Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).width(75).build();
        addRenderableWidget(titleWidget);
        addRenderableWidget(closeButton);

        super.init();
        repositionElements();
    }

    private void selectStaticDye(Button button, int dyeColor) {
        this.onClose();
        this.updateStatic.accept(button, dyeColor);
    }

    private void selectAnimatedDye(Button button, List<Integer> colors) {
        boolean cycleBack = colors.size() % 2 == 0;
        int max = cycleBack ? colors.size() / 2 : colors.size();
        List<AnimatedDye.Keyframe> keyFrames = new ArrayList<>(colors.size());

        if (Minecraft.getInstance().hasShiftDown() || max < 3) {
            // every frame verbatim
            int i = 0;
            for (int color : colors) {
                keyFrames.add(new AnimatedDye.Keyframe(color, (float) i / max));
                i += 1;
                if (cycleBack && i >= max) break;
            }
        } else {
            // only keep the frames where the gradient changes direction in OkLab
            keyFrames.add(new AnimatedDye.Keyframe(colors.getFirst(), 0));
            Vec3 next = toLab(colors.get(1));
            Vec3 current = toLab(colors.getFirst());
            Vec3 direction = next.subtract(current).normalize();
            for (int i = 1; i < max - 1; i++) {
                current = next;
                next = toLab(colors.get(i + 1));
                Vec3 newDirection = next.subtract(current).normalize();
                if (newDirection.dot(direction) < 0.75) {
                    keyFrames.add(new AnimatedDye.Keyframe(colors.get(i), (float) i / max));
                    direction = newDirection;
                }
            }
            keyFrames.add(new AnimatedDye.Keyframe(colors.get(max - 1), 1));
        }

        this.onClose();
        this.updateAnimated.accept(button, keyFrames, cycleBack);
    }

    private static Vec3 toLab(int color) {
        return OkLabColor.linearSRGB2OkLab(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color)).toVec3();
    }

    /** "Dye Aquamarine" / "Aquamarine Dye" both read as "Aquamarine" - the list is all dyes. */
    private static String displayName(String dyeId) {
        // dyes are not in Hypixel's item table, so this is usually the prettified id
        String display = SkyblockItems.coloredName(dyeId);
        String name = (display == null || display.isBlank()
                ? com.teslamaps.features.customize.CustomAnimatedHelmetTextures.formatName(dyeId)
                : StringUtil.strip(display)).trim();
        if (name.regionMatches(true, 0, "dye ", 0, 4)) name = name.substring(4).trim();
        if (name.length() > 4 && name.regionMatches(true, name.length() - 4, " dye", 0, 4)) {
            name = name.substring(0, name.length() - 4).trim();
        }
        return name.isEmpty() ? dyeId : name;
    }

    private static class StaticDyeButton extends Button.Plain {
        private static final int TEXT_OFFSET = 10;

        String name;
        final ItemStack dyeStack;

        protected StaticDyeButton(String dyeId, int color, OnPress onPress) {
            super(0, 0, 150, 20, Component.empty(), onPress, b -> Component.empty());
            name = displayName(dyeId);
            // a leather piece tinted with the dye reads better than the real (async) repo icon
            dyeStack = new ItemStack(Items.LEATHER_CHESTPLATE);
            dyeStack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
            setMessage(Component.literal(name).withColor(color));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            graphics.item(dyeStack, this.getX() + TEXT_OFFSET, this.getY() + 1);
            extractName(graphics, a);
        }

        protected void extractName(GuiGraphicsExtractor graphics, float a) {
            graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE)
                    .acceptScrollingWithDefaultCenter(getMessage(), getX() + 2 + TEXT_OFFSET + 16,
                            getRight() - 2, getY() + 2, getBottom() - 2);
        }
    }

    private static class AnimatedDyeButton extends StaticDyeButton {
        List<Component> animatedNames = new ArrayList<>();
        int index = 0;
        float lastChange = 0;

        protected AnimatedDyeButton(String dyeId, List<Integer> colors, OnPress onPress) {
            super(dyeId, colors.getFirst(), onPress);
            for (int color : colors) {
                animatedNames.add(Component.literal(name).withColor(color));
            }
        }

        @Override
        protected void extractName(GuiGraphicsExtractor graphics, float a) {
            lastChange += a;
            if (lastChange > 2) {
                lastChange = 0;
                index = Math.min(index + 1, animatedNames.size() - 1);
                setMessage(animatedNames.get(index));
                if (index == animatedNames.size() - 1) index = 0;
            }
            super.extractName(graphics, a);
        }
    }

    public interface AnimatedDyeConsumer {
        void accept(Button button, List<AnimatedDye.Keyframe> keyframes, boolean cycleBack);
    }
}
