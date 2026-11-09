package com.teslamaps.render.gui;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.renderer.DynamicGpuDataStorage;
import net.minecraft.client.renderer.DynamicGpuDataStorageMapped;

import java.nio.ByteBuffer;

/** The std140 block the chroma shaders read: size, time, saturation, direction, fade colours. */
public class ChromaUniform implements AutoCloseable {

    private static final int SIZE =
            new Std140SizeCalculator().putFloat().putFloat().putFloat().putInt()
                    .putFloat().putFloat().putVec4().putVec4().putFloat().putFloat().get();

    private final DynamicGpuDataStorage<Value> storage =
            new DynamicGpuDataStorageMapped<>("TeslaMaps Chroma UBO", SIZE, GpuBuffer.USAGE_UNIFORM, 2);

    public GpuBufferSlice writeWith(float chromaSize, float timeOffset, float saturation, int forwardDirection,
                                    float fadeStartX, float fadeWidth, int fadeColorA, int fadeColorB,
                                    float rawTicks, float pxScale) {
        return storage.writeData(new Value(chromaSize, timeOffset, saturation, forwardDirection,
                fadeStartX, fadeWidth, fadeColorA, fadeColorB, rawTicks, pxScale));
    }

    /** Has to run once per frame or the storage grows without bound. */
    public void clear() {
        storage.endFrame();
    }

    @Override
    public void close() {
        storage.close();
    }

    private record Value(float chromaSize, float timeOffset, float saturation, int forwardDirection,
                         float fadeStartX, float fadeWidth, int fadeColorA, int fadeColorB,
                         float rawTicks, float pxScale)
            implements DynamicGpuDataStorage.DynamicGpuData {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(chromaSize)
                    .putFloat(timeOffset)
                    .putFloat(saturation)
                    .putInt(forwardDirection)
                    .putFloat(fadeStartX)
                    .putFloat(fadeWidth)
                    .putVec4(red(fadeColorA), green(fadeColorA), blue(fadeColorA), 1f)
                    .putVec4(red(fadeColorB), green(fadeColorB), blue(fadeColorB), 1f)
                    .putFloat(rawTicks)
                    .putFloat(pxScale);
        }

        private static float red(int rgb) { return ((rgb >> 16) & 0xFF) / 255f; }
        private static float green(int rgb) { return ((rgb >> 8) & 0xFF) / 255f; }
        private static float blue(int rgb) { return (rgb & 0xFF) / 255f; }
    }
}
