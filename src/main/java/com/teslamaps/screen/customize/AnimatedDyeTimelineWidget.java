package com.teslamaps.screen.customize;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.AnimatedDye;
import com.teslamaps.render.gui.CustomizeGui;
import com.teslamaps.utils.OkLabColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Keyframe strip under the colour picker. Ported from Skyblocker. */
public class AnimatedDyeTimelineWidget extends AbstractContainerWidget implements Closeable {
    private static final Identifier GRADIENT_TEXTURE = Identifier.fromNamespaceAndPath("teslamaps", "generated/dye_gradient");
    private static final int HORIZONTAL_MARGIN = 3;
    private static final int VERTICAL_MARGIN = 1;

    private DynamicTexture gradientTexture;
    private int textureWidth;
    private int textureHeight;
    private final FrameCallback frameCallback;

    private String uuid = "";

    private final ArrayList<KeyframeWidget> keyframes = new ArrayList<>();
    private @Nullable KeyframeWidget focusedFrame = null;
    private int deletedIndex = -1;

    public AnimatedDyeTimelineWidget(int x, int y, int width, int height, FrameCallback frameCallback) {
        super(x, y, width, height, Component.literal("Animated Dye Timeline"), AbstractScrollArea.defaultSettings(4));
        createImage(width, height);
        this.frameCallback = frameCallback;
    }

    private void createImage(int width, int height) {
        gradientTexture = new DynamicTexture("TimelineGradient",
                Math.max(1, width - HORIZONTAL_MARGIN * 2), Math.max(1, height - VERTICAL_MARGIN * 2), true);
        NativeImage pixels = gradientTexture.getPixels();
        textureWidth = pixels == null ? 1 : pixels.getWidth();
        textureHeight = pixels == null ? 1 : pixels.getHeight();
        Minecraft.getInstance().getTextureManager().register(GRADIENT_TEXTURE, gradientTexture);
    }

    /** The screen re-shows after a popup with a released texture, so it has to be remade. */
    public void recreateImage() {
        createImage(width, height);
        createGradientTexture();
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        createImage(width, height);
        createGradientTexture();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return keyframes;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, GRADIENT_TEXTURE,
                getX() + HORIZONTAL_MARGIN, getY() + VERTICAL_MARGIN,
                0, 0,
                getWidth() - HORIZONTAL_MARGIN * 2, getHeight() - VERTICAL_MARGIN * 2,
                textureWidth, textureHeight, textureWidth, textureHeight);
        for (KeyframeWidget frame : keyframes) {
            frame.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        super.setFocused(focused);
        if (focused instanceof KeyframeWidget keyframe) {
            frameCallback.onFrameSelected(keyframe.color, keyframe.time);
            focusedFrame = keyframe;
        }
    }

    public void setAnimatedDye(String uuid) {
        this.uuid = uuid;
        AnimatedDye dye = TeslaMapsConfig.get().customAnimatedDyes.get(uuid);
        if (dye == null) return;
        keyframes.clear();
        keyframes.ensureCapacity(dye.keyframes().size());
        for (int i = 0; i < dye.keyframes().size(); i++) {
            AnimatedDye.Keyframe keyframe = dye.keyframes().get(i);
            keyframes.add(new KeyframeWidget(keyframe.color(), keyframe.time(), i != 0 && i != dye.keyframes().size() - 1));
        }
        setFocused(keyframes.getFirst());
        createGradientTexture();
    }

    private void createGradientTexture() {
        NativeImage image = gradientTexture.getPixels();
        if (image == null) return;
        for (int i = 0; i < keyframes.size() - 1; i++) {
            KeyframeWidget frame = keyframes.get(i);
            KeyframeWidget nextFrame = keyframes.get(i + 1);
            int startX = (int) ((image.getWidth() - 1) * frame.time);
            int endX = (int) ((image.getWidth() - 1) * nextFrame.time);
            int size = endX - startX;
            for (int x = 0; x <= size; x++) {
                int color = OkLabColor.interpolate(frame.color, nextFrame.color, size == 0 ? 0 : (float) x / size);
                for (int y = 0; y < image.getHeight(); y++) {
                    image.setPixel(x + startX, y, color | 0xFF_00_00_00);
                }
            }
        }
        gradientTexture.upload();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            if (deletedIndex != -1) {
                setFocused(keyframes.get(Math.min(deletedIndex, keyframes.size() - 1)));
                deletedIndex = -1;
            }
            return true;
        }
        if (isMouseOver(click.x(), click.y())) {
            double mouseX = click.x() - getX() + HORIZONTAL_MARGIN;
            KeyframeWidget frame = new KeyframeWidget(0xFFFF0000, (float) (mouseX / (getWidth() - HORIZONTAL_MARGIN * 2 - 1)), true);
            keyframes.add(frame);
            setFocused(frame);
            dataChanged();
            return true;
        }
        return false;
    }

    public void setColor(int argb) {
        if (focusedFrame == null) return;
        focusedFrame.color = argb;
        dataChanged();
    }

    private void dataChanged() {
        keyframes.sort(Comparator.comparingDouble(f -> f.time));
        createGradientTexture();
        List<AnimatedDye.Keyframe> configFrames = keyframes.stream()
                .map(keyframe -> new AnimatedDye.Keyframe(keyframe.color, keyframe.time)).toList();
        AnimatedDye dye = TeslaMapsConfig.get().customAnimatedDyes.get(uuid);
        if (dye == null) return;
        TeslaMapsConfig.get().customAnimatedDyes.put(uuid,
                new AnimatedDye(List.copyOf(configFrames), dye.cycleBack(), dye.delay(), dye.duration()));
        CustomizeScreen.updated();
    }

    private class KeyframeWidget extends AbstractWidget {
        int color;
        float time;

        private final boolean draggable;
        private boolean dragging = false;

        private KeyframeWidget(int color, float time, boolean draggable) {
            super(0, 0, 7, AnimatedDyeTimelineWidget.this.getHeight(), Component.literal("Keyframe"));
            this.draggable = draggable;
            this.color = color;
            this.time = time;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);
            CustomizeGui.border(graphics, getX(), getY(), getWidth(), getHeight(), isFocused() ? -1 : CommonColors.GRAY);
        }

        @Override
        public int getX() {
            AnimatedDyeTimelineWidget parent = AnimatedDyeTimelineWidget.this;
            return (int) (parent.getX() + HORIZONTAL_MARGIN + time * (parent.getWidth() - HORIZONTAL_MARGIN * 2 - 1)) - 3;
        }

        @Override
        public int getY() {
            return AnimatedDyeTimelineWidget.this.getY();
        }

        @Override
        protected void onDrag(MouseButtonEvent click, double offsetX, double offsetY) {
            super.onDrag(click, offsetX, offsetY);
            if (!draggable) return;
            AnimatedDyeTimelineWidget parent = AnimatedDyeTimelineWidget.this;
            double mouseX = click.x() - parent.getX() + HORIZONTAL_MARGIN;
            time = Math.clamp((float) (mouseX / (parent.getWidth() - HORIZONTAL_MARGIN * 2 - 1)), 0, 1);
            dragging = true;
        }

        @Override
        public void onRelease(MouseButtonEvent click) {
            super.onRelease(click);
            if (dragging) dataChanged();
        }

        @Override
        public boolean keyPressed(KeyEvent input) {
            if (input.key() == InputConstants.KEY_DELETE) deleteThis(false);
            return super.keyPressed(input);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
            if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT && isMouseOver(click.x(), click.y())) {
                deleteThis(true);
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        private void deleteThis(boolean mouse) {
            if (!draggable) return;
            int i = keyframes.indexOf(this);
            if (i < 0 || i + 1 >= keyframes.size()) return;
            AnimatedDyeTimelineWidget.this.setFocused(keyframes.get(i + 1));
            if (mouse) deletedIndex = i;
            keyframes.remove(this);
            dataChanged();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {}
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {}

    @Override
    protected int contentHeight() {
        return getHeight();
    }

    @Override
    protected double scrollRate() {
        return 0;
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(GRADIENT_TEXTURE);
    }

    public interface FrameCallback {
        void onFrameSelected(int color, float time);
    }
}
