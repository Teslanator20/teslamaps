package com.teslamaps.injected;

/** A value derived from one component instance of an ItemStack. */
public record StackCache<V>(Object src, V value) {}
