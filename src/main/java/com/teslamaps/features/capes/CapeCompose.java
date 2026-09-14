package com.teslamaps.features.capes;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Turns any picture into a cape sheet: the picked crop on the front and on the
 * (mirrored) inner face, a heavily downscaled blur of the source everywhere else.
 * Shared by the crop editor and the gif decoder, so a still and a frame of an
 * animation are composed exactly the same way.
 */
public class CapeCompose {

    /** Where the crop sits, in source fractions - independent of the resolution. */
    public record Crop(double cx, double cy, double zoom) {
        public static final Crop CENTER = new Crop(0.5, 0.5, 1.0);

        public String store() {
            return cx + "," + cy + "," + zoom;
        }

        /** Parses what {@link #store()} wrote; null for anything else. */
        public static Crop parse(String raw) {
            if (raw == null) return null;
            String[] parts = raw.trim().split(",");
            if (parts.length != 3) return null;
            try {
                return new Crop(Double.parseDouble(parts[0]),
                        Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    /** Crop rect in source pixels, 10:16 like the cape front, clamped inside the picture. */
    public static int[] rect(int sw, int sh, Crop crop) {
        double maxH = Math.min(sh, sw * 16.0 / 10.0);
        double ch = maxH / Math.max(1.0, crop.zoom());
        double cw = ch * 10.0 / 16.0;
        double cx = Math.max(cw / 2, Math.min(sw - cw / 2, crop.cx() * sw));
        double cy = Math.max(ch / 2, Math.min(sh - ch / 2, crop.cy() * sh));
        return new int[]{(int) (cx - cw / 2), (int) (cy - ch / 2), (int) cw, (int) ch};
    }

    /**
     * Composes one sheet on a {@code 64*scale x 32*scale} grid. The source is left
     * open, the caller owns it.
     */
    public static NativeImage sheet(NativeImage source, Crop crop, int scale) {
        int[] rect = rect(source.getWidth(), source.getHeight(), crop);
        int frontW = 10 * scale, frontH = 16 * scale;
        NativeImage sheet = new NativeImage(64 * scale, 32 * scale, true);
        try (NativeImage tiny = new NativeImage(16, 8, true);
             NativeImage front = new NativeImage(frontW, frontH, true)) {
            // background: source shrunk to a handful of pixels and blown back up = cheap blur
            source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), tiny);
            tiny.resizeSubRectTo(0, 0, 16, 8, sheet);

            source.resizeSubRectTo(rect[0], rect[1], rect[2], rect[3], front);
            front.copyRect(sheet, 0, 0, scale, scale, frontW, frontH, false, false);
            front.copyRect(sheet, 0, 0, 12 * scale, scale, frontW, frontH, true, false);
        } catch (Throwable t) {
            sheet.close();
            throw t;
        }
        return sheet;
    }
}
