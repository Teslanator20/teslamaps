package com.teslamaps.features;

// GUI-022, idea from FloydAddons' FloydFont / NiaMod FontOption: own .ttf in front of the vanilla default font
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import com.mojang.blaze3d.platform.TextureUtil;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.FontSetProvidersAccessor;
import com.teslamaps.registry.Feature;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.FontOption;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import net.minecraft.resources.Identifier;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomFont {

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("fonts");

    private static Map<Identifier, FontSet> fontSets;
    private static GlyphProvider ours;
    private static String loadedKey;

    private CustomFont() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("CustomFont")
                .config("Interface", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Custom Font");
                    b.toggle("Custom Font", () -> c.customFont, v -> { c.customFont = v; apply(); })
                            .desc("Draws all text with a .ttf/.otf from config/teslamaps/fonts. Characters the font lacks "
                                    + "(emojis, SkyBlock symbols, pack glyphs) still come from the normal font.")
                            .tags("font", "ttf", "schrift", "typeface");
                    String[] files = files();
                    b.dropdown("  Font File", files,
                            () -> c.customFontFile.isEmpty() ? files[0] : c.customFontFile,
                            v -> { c.customFontFile = v.startsWith("(") ? "" : v; apply(); });
                    b.slider("  Size", 6f, 16f, () -> c.customFontSize, v -> c.customFontSize = v, 1);
                    b.slider("  Sharpness (Oversample)", 1f, 8f, () -> c.customFontOversample, v -> c.customFontOversample = v, 1)
                            .desc("Higher = sharper at big GUI/HUD scales, more texture memory.");
                    b.slider("  Vertical Shift", -3f, 3f, () -> c.customFontShiftY, v -> c.customFontShiftY = v, 1);
                    b.buttonRow(new String[]{"Apply", "Open Folder"}, new Runnable[]{
                            CustomFont::apply,
                            () -> {
                                try { Files.createDirectories(DIR); } catch (IOException ignored) {}
                                net.minecraft.util.Util.getPlatform().openPath(DIR);
                            }});
                });
    }

    private static String[] files() {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(DIR)) try {
            try (var s = Files.list(DIR)) {
                s.filter(Files::isRegularFile).map(p -> p.getFileName().toString())
                        .filter(n -> { String l = n.toLowerCase(Locale.ROOT); return l.endsWith(".ttf") || l.endsWith(".otf"); })
                        .sorted(String::compareToIgnoreCase).forEach(out::add);
            }
        } catch (IOException ignored) {}
        if (out.isEmpty()) out.add("(no fonts in config/teslamaps/fonts)");
        return out.toArray(new String[0]);
    }

    /** From FontManager.apply: the font sets were rebuilt, ours is gone from them. */
    public static void onFontsLoaded(Map<Identifier, FontSet> sets) {
        fontSets = sets;
        if (ours != null || cfg().customFont) {
            try {
                apply();
            } catch (RuntimeException e) {
                TeslaMaps.LOGGER.warn("custom font: {}", e.toString());
            }
        }
    }

    public static void apply() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) { mc.execute(CustomFont::apply); return; }
        if (fontSets == null) return;
        FontSet set = fontSets.get(Minecraft.DEFAULT_FONT);
        if (set == null) return;
        List<GlyphProvider.Conditional> base = new ArrayList<>();
        for (GlyphProvider.Conditional p : ((FontSetProvidersAccessor) set).teslamaps$getAllProviders()) {
            if (ours == null || p.provider() != ours) base.add(p);
        }
        TeslaMapsConfig c = cfg();
        GlyphProvider old = ours;
        GlyphProvider next = null;
        String key = c.customFontFile + "|" + c.customFontSize + "|" + c.customFontOversample + "|" + c.customFontShiftY;
        if (c.customFont && !c.customFontFile.isEmpty()) {
            if (old != null && key.equals(loadedKey)) {
                next = old;
            } else {
                try {
                    next = load(DIR.resolve(c.customFontFile), c.customFontSize, c.customFontOversample, c.customFontShiftY);
                    loadedKey = key;
                } catch (Exception e) {
                    TeslaMaps.LOGGER.warn("custom font {}: {}", c.customFontFile, e.toString());
                    if (mc.player != null) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§b[TeslaMaps] §cCould not load font " + c.customFontFile + ": " + e.getMessage()));
                }
            }
        }
        List<GlyphProvider.Conditional> list = new ArrayList<>(base.size() + 1);
        if (next != null) list.add(new GlyphProvider.Conditional(next, FontOption.Filter.ALWAYS_PASS));
        list.addAll(base);
        set.reload(list, options(mc));
        ours = next;
        if (next == null) loadedKey = null;
        if (old != null && old != next) old.close();
    }

    private static Set<FontOption> options(Minecraft mc) {
        Set<FontOption> o = EnumSet.noneOf(FontOption.class);
        if (mc.options.forceUnicodeFont().get()) o.add(FontOption.UNIFORM);
        if (mc.options.japaneseGlyphVariants().get()) o.add(FontOption.JAPANESE_VARIANTS);
        return o;
    }

    private static GlyphProvider load(Path file, float size, float oversample, float shiftY) throws IOException {
        ByteBuffer mem;
        try (InputStream in = Files.newInputStream(file)) {
            mem = TextureUtil.readResource(in);
        }
        FT_Face face = null;
        try {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                try (MemoryStack st = MemoryStack.stackPush()) {
                    PointerBuffer pb = st.mallocPointer(1);
                    FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), mem, 0L, pb), "Initializing font face");
                    face = FT_Face.create(pb.get());
                }
                String fmt = FreeType.FT_Get_Font_Format(face);
                if (!"TrueType".equals(fmt) && !"CFF".equals(fmt)) throw new IOException("unsupported format " + fmt);
                FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE), "Find unicode charmap");
                return new TrueTypeGlyphProvider(mem, face, size, oversample, 0f, shiftY, "");
            }
        } catch (IOException | RuntimeException e) {
            synchronized (FreeTypeUtil.LIBRARY_LOCK) {
                if (face != null) FreeType.FT_Done_Face(face);
            }
            MemoryUtil.memFree(mem);
            throw e;
        }
    }
}
