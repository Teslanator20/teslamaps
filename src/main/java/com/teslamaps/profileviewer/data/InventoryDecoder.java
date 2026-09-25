package com.teslamaps.profileviewer.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.teslamaps.TeslaMaps;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Hypixel ships every container as gzipped 1.8 item NBT in base64. We only need the
 * skyblock id, the stack size and the display text - the actual stack for rendering comes
 * from SkyblockItems.
 */
public class InventoryDecoder {

    public static class Slot {
        public final String skyblockId;
        public final String name;
        public final List<String> lore;
        public final int count;
        /** Hypixel's whole ExtraAttributes tag - enchants, stars, reforge, hpb, uuid. */
        public final CompoundTag extraAttributes;
        public final boolean enchanted;
        /** Leather dye from display.color, -1 when the piece is not dyed. */
        public final int color;

        Slot(String skyblockId, String name, List<String> lore, int count, CompoundTag extraAttributes) {
            this(skyblockId, name, lore, count, extraAttributes, false, -1);
        }

        Slot(String skyblockId, String name, List<String> lore, int count, CompoundTag extraAttributes,
             boolean enchanted, int color) {
            this.enchanted = enchanted;
            this.color = color;
            this.skyblockId = skyblockId;
            this.name = name;
            this.lore = lore;
            this.count = count;
            this.extraAttributes = extraAttributes;
        }

        public boolean isEmpty() {
            return skyblockId == null && name == null;
        }

        public static final Slot EMPTY = new Slot(null, null, List.of(), 0, null);

        /** A made up slot for things the api ships as plain json (sacks, shards). */
        public static Slot of(String skyblockId, String name, List<String> lore, int count) {
            return new Slot(skyblockId, name, lore, count, null);
        }
    }

    /** Reads inventory.<key>.data, returns an empty list when the player hid their api. */
    public static List<Slot> decode(JsonObject inventory, String path) {
        if (inventory == null) return List.of();

        JsonObject node = inventory;
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length - 1; i++) {
            if (!node.has(parts[i]) || !node.get(parts[i]).isJsonObject()) return List.of();
            node = node.getAsJsonObject(parts[i]);
        }
        String last = parts[parts.length - 1];
        if (!node.has(last)) return List.of();

        JsonElement target = node.get(last);
        String base64 = null;
        if (target.isJsonObject() && target.getAsJsonObject().has("data")) {
            base64 = target.getAsJsonObject().get("data").getAsString();
        } else if (target.isJsonPrimitive()) {
            base64 = target.getAsString();
        }
        return decodeBase64(base64);
    }

    public static List<Slot> decodeBase64(String base64) {
        if (base64 == null || base64.isBlank()) return List.of();

        try {
            return decodeBytes(Base64.getDecoder().decode(base64));
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to decode an inventory: {}", e.toString());
            return List.of();
        }
    }

    /** Gzipped NBT with an "i" item list, the way Hypixel packs bag/backpack contents into item data. */
    public static List<Slot> decodeBytes(byte[] raw) {
        if (raw == null || raw.length == 0) return List.of();
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(raw), NbtAccounter.create(64L << 20));
            ListTag items = root.getList("i").orElse(null);
            if (items == null) return List.of();

            List<Slot> slots = new ArrayList<>(items.size());
            for (int i = 0; i < items.size(); i++) {
                slots.add(readSlot(items.getCompoundOrEmpty(i)));
            }
            return slots;
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Failed to decode an inventory: {}", e.toString());
            return List.of();
        }
    }

    private static Slot readSlot(CompoundTag item) {
        if (item.isEmpty()) return Slot.EMPTY;

        int count = item.getByteOr("Count", (byte) 0);
        CompoundTag tag = item.getCompound("tag").orElse(null);
        if (tag == null) return count > 0 ? new Slot(null, null, List.of(), count, null) : Slot.EMPTY;

        CompoundTag extra = tag.getCompound("ExtraAttributes").orElse(null);
        String id = extra != null ? extra.getString("id").orElse(null) : null;

        String name = null;
        int color = -1;
        List<String> lore = new ArrayList<>();
        CompoundTag display = tag.getCompound("display").orElse(null);
        if (display != null) {
            color = display.getInt("color").orElse(-1);
            name = display.getString("Name").orElse(null);
            ListTag loreTag = display.getList("Lore").orElse(null);
            if (loreTag != null) {
                for (int i = 0; i < loreTag.size(); i++) {
                    if (loreTag.get(i) instanceof StringTag line) lore.add(line.value());
                }
            }
        }

        if (id == null && name == null) return Slot.EMPTY;
        boolean enchanted = tag.getList("ench").map(list -> !list.isEmpty()).orElse(false);
        return new Slot(id, name, lore, Math.max(count, 1), extra, enchanted, color);
    }
}
