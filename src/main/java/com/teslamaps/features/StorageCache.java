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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StorageCache {

    public static class Entry {
        public int size;
        public LinkedHashMap<Integer, String> slots = new LinkedHashMap<>();
        public String icon;
    }

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type TYPE = new TypeToken<Map<String, Entry>>() {}.getType();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("storage.json");

    private static Map<String, Entry> data = new HashMap<>();
    private static final Map<String, LinkedHashMap<Integer, ItemStack>> decoded = new HashMap<>();
    private static final Map<String, ItemStack> decodedIcon = new HashMap<>();
    private static boolean loaded = false;

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Map<String, Entry> d = GSON.fromJson(Files.readString(PATH), TYPE);
                if (d != null) {
                    boolean changed = false;
                    var it = d.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry e = it.next().getValue();
                        if (e == null || e.slots == null || (e.size <= 0 && e.icon == null)) { it.remove(); changed = true; }
                    }
                    data = d;
                    if (changed) save();
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load storage.json", e);
        }
        loaded = true;
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(data, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save storage.json", e);
        }
    }

    public static void put(String key, int size, LinkedHashMap<Integer, String> slots) {
        if (!loaded) load();
        Entry e = data.computeIfAbsent(key, k -> new Entry());
        e.size = size;
        e.slots = slots;
        decoded.remove(key);
    }

    public static void putIcon(String key, String iconSnbt) {
        if (!loaded) load();
        if (iconSnbt == null) return;
        data.computeIfAbsent(key, k -> new Entry()).icon = iconSnbt;
        decodedIcon.remove(key);
    }

    public static ItemStack icon(String key) {
        if (!loaded) load();
        if (decodedIcon.containsKey(key)) return decodedIcon.get(key);
        Entry e = data.get(key);
        ItemStack st = (e == null || e.icon == null) ? null : decode(e.icon);
        decodedIcon.put(key, st);   // cache decoded icon so we don't re-parse SNBT every frame
        return st;
    }

    public static boolean has(String key) {
        if (!loaded) load();
        return data.containsKey(key);
    }

    public static int size(String key) {
        if (!loaded) load();
        Entry e = data.get(key);
        return e == null ? 0 : e.size;
    }

    public static LinkedHashMap<Integer, ItemStack> items(String key) {
        if (!loaded) load();
        LinkedHashMap<Integer, ItemStack> d = decoded.get(key);
        if (d != null) return d;
        Entry e = data.get(key);
        if (e == null || e.slots == null) return null;
        d = new LinkedHashMap<>();
        for (Map.Entry<Integer, String> s : e.slots.entrySet()) d.put(s.getKey(), decode(s.getValue()));
        decoded.put(key, d);
        return d;
    }

    private static RegistryOps<Tag> ops() {
        return RegistryOps.create(NbtOps.INSTANCE, Minecraft.getInstance().level.registryAccess());
    }

    public static String encode(ItemStack stack) {
        try {
            Tag t = ItemStack.CODEC.encodeStart(ops(), stack).result().orElse(null);
            return t == null ? null : t.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static ItemStack decode(String snbt) {
        try {
            CompoundTag c = TagParser.parseCompoundFully(snbt);
            return ItemStack.CODEC.parse(ops(), c).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }
}
