package com.teslamaps.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LRU map — drops the least recently accessed entry once maxSize is exceeded.
 * Ported from bax's utils/SizeLimitedCache.
 */
public class SizeLimitedCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    public SizeLimitedCache(int maxSize) {
        super(maxSize + 1, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }
}
