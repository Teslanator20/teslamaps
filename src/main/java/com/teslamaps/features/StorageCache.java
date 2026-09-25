package com.teslamaps.features;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.utils.ProfiledFile;
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
    private static final ProfiledFile FILE = new ProfiledFile("storage.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("storage.json"));
    private static Path path;

    private static Map<String, Entry> data = new HashMap<>();
    private static final Map<String, LinkedHashMap<Integer, ItemStack>> decoded = new HashMap<>();
    private static final Map<String, ItemStack> decodedIcon = new HashMap<>();
    private static boolean loaded = false;
    private static boolean dirty = false;

    private static void ensure() {
        if (!loaded) load();
    }

    static {
        com.teslamaps.utils.SbProfile.onChange(() -> {
            if (loaded && FILE.stale()) { save(); load(); }
        });
    }

    public static void load() {
        data = new HashMap<>();
        dirty = false;
        decoded.clear();
        decodedIcon.clear();
        path = FILE.path();
        try {
            if (Files.exists(path)) {
                Map<String, Entry> d = GSON.fromJson(Files.readString(path), TYPE);
                if (d != null) {
                    boolean changed = false;
                    var it = d.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry e = it.next().getValue();
                        if (e == null || e.slots == null || (e.size <= 0 && e.icon == null)) { it.remove(); changed = true; }
                    }
                    data = d;
                    if (changed) { dirty = true; save(); }
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load storage.json", e);
        }
        loaded = true;
    }

    public static void save() {
        if (!dirty || path == null) return;
        dirty = false;
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(data, TYPE));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save storage.json", e);
        }
    }

    /** Every stored slot as (page key, snbt). */
    public static void forEachStored(java.util.function.BiConsumer<String, String> out) {
        ensure();
        for (Map.Entry<String, Entry> e : data.entrySet()) for (String snbt : e.getValue().slots.values()) out.accept(e.getKey(), snbt);
    }

    public static void put(String key, int size, LinkedHashMap<Integer, String> slots) {
        ensure();
        Entry e = data.computeIfAbsent(key, k -> new Entry());
        e.size = size;
        e.slots = slots;
        decoded.remove(key);
        dirty = true;
    }

    public static void remove(String key) {
        ensure();
        if (data.remove(key) == null) return;
        decoded.remove(key);
        decodedIcon.remove(key);
        dirty = true;
        save();
    }

    public static void putIcon(String key, String iconSnbt) {
        ensure();
        if (iconSnbt == null) return;
        Entry e = data.computeIfAbsent(key, k -> new Entry());
        if (iconSnbt.equals(e.icon)) return;
        e.icon = iconSnbt;
        dirty = true;
        decodedIcon.remove(key);
    }

    public static ItemStack icon(String key) {
        ensure();
        if (decodedIcon.containsKey(key)) return decodedIcon.get(key);
        Entry e = data.get(key);
        ItemStack st = (e == null || e.icon == null) ? null : decode(e.icon);
        decodedIcon.put(key, st);   // cache decoded icon so we don't re-parse SNBT every frame
        return st;
    }

    public static boolean has(String key) {
        ensure();
        return data.containsKey(key);
    }

    public static int size(String key) {
        ensure();
        Entry e = data.get(key);
        return e == null ? 0 : e.size;
    }

    public static LinkedHashMap<Integer, ItemStack> items(String key) {
        ensure();
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

    /** Public entry point for other features that persist stacks as SNBT. */
    public static ItemStack decodeStack(String snbt) {
        return snbt == null ? ItemStack.EMPTY : decode(snbt);
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
