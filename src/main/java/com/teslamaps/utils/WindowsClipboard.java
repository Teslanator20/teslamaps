package com.teslamaps.utils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Puts a picture on the Windows clipboard as CF_DIB through Win32, no AWT involved.
 * Ported from Skysoft's features/screenshot/ScreenshotClipboard.
 */
public final class WindowsClipboard {

    private static final int GMEM_MOVEABLE_ZEROINIT = 0x0042;
    private static final int CF_DIB = 8;
    private static final int HEADER = 40;

    private interface User32 extends Library {
        int OpenClipboard(Pointer window);
        int EmptyClipboard();
        Pointer SetClipboardData(int format, Pointer memory);
        int CloseClipboard();
    }

    private interface Kernel32 extends Library {
        Pointer GlobalAlloc(int flags, BaseTSD.SIZE_T bytes);
        Pointer GlobalLock(Pointer memory);
        int GlobalUnlock(Pointer memory);
        Pointer GlobalFree(Pointer memory);
    }

    private static User32 user32;
    private static Kernel32 kernel32;

    private WindowsClipboard() {}

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    /** Pixels in NativeImage order (ABGR ints, row-major, top row first). Throws with the failing step. */
    public static synchronized void copyAbgr(int[] abgr, int width, int height) {
        if (user32 == null) {
            user32 = Native.load("user32", User32.class);
            kernel32 = Native.load("kernel32", Kernel32.class);
        }
        byte[] dib = dib(abgr, width, height);
        Pointer memory = kernel32.GlobalAlloc(GMEM_MOVEABLE_ZEROINIT, new BaseTSD.SIZE_T(dib.length));
        if (memory == null) throw new IllegalStateException("Windows could not allocate clipboard memory");
        boolean handedOver = false;
        try {
            Pointer dst = kernel32.GlobalLock(memory);
            if (dst == null) throw new IllegalStateException("Windows could not lock clipboard memory");
            dst.write(0L, dib, 0, dib.length);
            kernel32.GlobalUnlock(memory);
            if (user32.OpenClipboard(null) == 0) throw new IllegalStateException("Windows could not open the clipboard");
            try {
                if (user32.EmptyClipboard() == 0) throw new IllegalStateException("Windows could not clear the clipboard");
                if (user32.SetClipboardData(CF_DIB, memory) == null) throw new IllegalStateException("Windows could not copy the picture");
                handedOver = true;
            } finally {
                user32.CloseClipboard();
            }
        } finally {
            if (!handedOver) kernel32.GlobalFree(memory);
        }
    }

    private static byte[] dib(int[] abgr, int w, int h) {
        int rowBytes = Math.multiplyExact(w, 3);
        int stride = (rowBytes + 3) / 4 * 4;
        int imageBytes = Math.multiplyExact(stride, h);
        ByteBuffer buf = ByteBuffer.allocate(Math.addExact(HEADER, imageBytes)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(HEADER);
        buf.putInt(w);
        buf.putInt(h);
        buf.putShort((short) 1);
        buf.putShort((short) 24);
        buf.putInt(0);
        buf.putInt(imageBytes);
        for (int i = 0; i < 4; i++) buf.putInt(0);
        byte[] out = buf.array();
        int pos = HEADER;
        for (int y = h - 1; y >= 0; y--) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                int p = abgr[row + x];
                out[pos++] = (byte) (p >> 16);
                out[pos++] = (byte) (p >> 8);
                out[pos++] = (byte) p;
            }
            pos += stride - rowBytes;
        }
        return out;
    }
}
