package com.teslamaps.render.gui;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import net.minecraft.client.renderer.DynamicUniformStorage;

import java.nio.ByteBuffer;

/** The std140 block the chroma shaders read: size, time, saturation, direction. */
public class ChromaUniform implements AutoCloseable {

    private static final int SIZE =
            new Std140SizeCalculator().putFloat().putFloat().putFloat().putInt().get();

    private final DynamicUniformStorage<Value> storage =
            new DynamicUniformStorage<>("TeslaMaps Chroma UBO", SIZE, 2);

    public GpuBufferSlice writeWith(float chromaSize, float timeOffset, float saturation, int forwardDirection) {
        return storage.writeUniform(new Value(chromaSize, timeOffset, saturation, forwardDirection));
    }

    /** Has to run once per frame or the storage grows without bound. */
    public void clear() {
        storage.endFrame();
    }

    @Override
    public void close() {
        storage.close();
    }

    private record Value(float chromaSize, float timeOffset, float saturation, int forwardDirection)
            implements DynamicUniformStorage.DynamicUniform {
        @Override
        public void write(ByteBuffer buffer) {
            Std140Builder.intoBuffer(buffer)
                    .putFloat(chromaSize)
                    .putFloat(timeOffset)
                    .putFloat(saturation)
                    .putInt(forwardDirection);
        }
    }
}
