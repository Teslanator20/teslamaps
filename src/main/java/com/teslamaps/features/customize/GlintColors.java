package com.teslamaps.features.customize;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Per-item glint colour from {@code /tmap customize}. The colour rides from model extraction
 * (layer state) through the item submit to ItemFeatureRenderer, which swaps the vanilla glint
 * render type for one with a tinted copy of the glint texture.
 */
public final class GlintColors {

    /** Set while ItemModelResolver builds a stack's layers. */
    public static int extracting;
    /** Set while a layer submits its item node. */
    public static int submitting;
    /** Set while ItemFeatureRenderer draws one item submit. */
    public static int rendering;

    private static final Map<Long, RenderType> TYPES = new HashMap<>();
    private static final Map<Integer, Identifier> TEXTURES = new HashMap<>();

    private GlintColors() {}

    /** 0xRRGGBB | 0xFF000000, or 0 when the stack keeps the vanilla glint. */
    public static int colorFor(ItemStack stack) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.customGlintColors || c.customGlintColor.isEmpty() || !Customize.active()) return 0;
        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty()) return 0;
        String hex = c.customGlintColor.get(uuid);
        if (hex == null) return 0;
        try {
            return 0xFF000000 | Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static RenderType colored(RenderType base, int color) {
        int kind = base == RenderTypes.glint() ? 0 : base == RenderTypes.glintTranslucent() ? 1
                : base == RenderTypes.entityGlint() ? 2 : -1;
        if (kind < 0) return base;
        long key = ((long) kind << 32) | (color & 0xFFFFFFFFL);
        RenderType t = TYPES.get(key);
        if (t != null) return t;
        Identifier tex = texture(color);
        if (tex == null) return base;
        RenderSetup.RenderSetupBuilder b = RenderSetup.builder(RenderPipelines.GLINT)
                .withTexture("Sampler0", tex, () -> RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR))
                .setTextureTransform(kind == 2 ? TextureTransform.ENTITY_GLINT_TEXTURING : TextureTransform.GLINT_TEXTURING);
        if (kind == 1) b.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
        t = RenderType.create("teslamaps_glint_" + kind + "_" + Integer.toHexString(color & 0xFFFFFF), b.createRenderSetup());
        TYPES.put(key, t);
        return t;
    }

    private static Identifier texture(int color) {
        if (TEXTURES.containsKey(color)) return TEXTURES.get(color);
        Identifier id;
        Minecraft mc = Minecraft.getInstance();
        try (InputStream in = mc.getResourceManager().open(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)) {
            NativeImage img = NativeImage.read(in);
            int w = img.getWidth(), h = img.getHeight();
            int max = 1;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) max = Math.max(max, lum(img.getPixel(x, y)));
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, bl = color & 0xFF;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int p = img.getPixel(x, y);
                    float l = lum(p) / (float) max;
                    img.setPixel(x, y, (p & 0xFF000000) | ((int) (r * l) << 16) | ((int) (g * l) << 8) | (int) (bl * l));
                }
            }
            id = Identifier.fromNamespaceAndPath("teslamaps", "glint/" + Integer.toHexString(color & 0xFFFFFF));
            Identifier finalId = id;
            mc.getTextureManager().register(id, new DynamicTexture(finalId::toString, img));
            TEXTURES.put(color, id);
            return id;
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("glint texture failed", e);
            TEXTURES.put(color, null);
            return null;
        }
    }

    private static int lum(int argb) {
        return (((argb >> 16) & 0xFF) * 3 + ((argb >> 8) & 0xFF) * 6 + (argb & 0xFF)) / 10;
    }
}
