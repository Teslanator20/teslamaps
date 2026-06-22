/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.features;

import com.mojang.blaze3d.platform.NativeImage;

import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

public class AutoCopyScreenshot {

    public static void copy(NativeImage image) {
        try {
            int w = image.getWidth(), h = image.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = image.getPixel(x, y);
                    int a = (abgr >>> 24) & 0xFF, b = (abgr >>> 16) & 0xFF, g = (abgr >>> 8) & 0xFF, r = abgr & 0xFF;
                    bi.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            cb.setContents(new ImageTransferable(bi), null);
        } catch (Throwable ignored) {
        }
    }

    private record ImageTransferable(Image image) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!DataFlavor.imageFlavor.equals(f)) throw new UnsupportedFlavorException(f);
            return image;
        }
    }
}
