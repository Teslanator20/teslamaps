package com.teslamaps.features;

// Ported from SkyBack's PaperItemConverter.java / SkullManager.java / PackState.java, data from its item_map.json + skull_map.json
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.Customize;
import com.teslamaps.injected.CustomizableStack;
import com.teslamaps.registry.Feature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Renders SkyBlock items with their pre-resource-pack vanilla item / old player head. Render stack only. */
public final class OldItemTextures {

    private static final String PACK_NAMESPACE = "hypixel_skyblock";
    private static final Map<String, Item> BY_ID = new HashMap<>();
    private static final Map<String, Item> BY_MODEL = new HashMap<>();
    private static final Map<String, String> HEADS = new HashMap<>();
    private static final Map<String, ItemStack> HEAD_CACHE = new HashMap<>();
    private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();

    private record Cached(Object data, Object model, boolean heads, boolean keep, ItemStack out) {}

    private static Feature feature;
    private static boolean loaded;
    private static boolean packLoaded;
    private static int ticks;

    private OldItemTextures() {}

    public static void register() {
        feature = Feature.of("OldItemTextures").section("Inventory").when(() -> TeslaMapsConfig.get().oldItemTextures)
                .tickAlways(OldItemTextures::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Old Textures");
                    b.toggle("Old Item Textures", () -> c.oldItemTextures, v -> { c.oldItemTextures = v; CACHE.clear(); })
                            .desc("Shows SkyBlock items as the vanilla item they used to be, before the resource pack. Only the look changes.");
                    b.toggle("  Old Player Heads", () -> c.oldItemTexturesHeads, v -> { c.oldItemTexturesHeads = v; CACHE.clear(); })
                            .desc("Accessories, pet items etc. get their old head texture back.");
                    b.toggle("  Keep Pack Texture If Unknown", () -> c.oldItemTexturesKeepPack, v -> { c.oldItemTexturesKeepPack = v; CACHE.clear(); })
                            .desc("Items without a known old look keep the resource pack texture while the pack is loaded.");
                });
    }

    private static void tick() {
        if (++ticks < 20) return;
        ticks = 0;
        Minecraft mc = Minecraft.getInstance();
        boolean now = mc.getResourceManager() != null && mc.getResourceManager().getNamespaces().contains(PACK_NAMESPACE);
        if (now != packLoaded) {
            packLoaded = now;
            CACHE.clear();
        }
    }

    private static void load() {
        loaded = true;
        read("/assets/teslamaps/data/old_items.txt", (k, v) -> {
            Identifier id = Identifier.tryParse(v.contains(":") ? v : "minecraft:" + v);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (k.startsWith("@")) BY_MODEL.put(k.substring(1), item);
            else BY_ID.put(normalize(k), item);
        });
        read("/assets/teslamaps/data/old_heads.txt", (k, v) -> HEADS.put(normalize(k), v));
        TeslaMaps.LOGGER.info("[OldItemTextures] {} items, {} heads", BY_ID.size() + BY_MODEL.size(), HEADS.size());
    }

    private static void read(String path, java.util.function.BiConsumer<String, String> out) {
        try (InputStream in = OldItemTextures.class.getResourceAsStream(path)) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                int sp = line.indexOf(' ');
                if (sp > 0) out.accept(line.substring(0, sp), line.substring(sp + 1).trim());
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("[OldItemTextures] failed to read {}", path, e);
        }
    }

    private static String normalize(String id) {
        String s = id.toUpperCase();
        return s.startsWith("STARRED_") ? s.substring(8) : s;
    }

    /** From OldItemTexturesMixin, every item render. Never mutates the real stack. */
    public static ItemStack forRender(ItemStack stack) {
        if (feature == null || !TeslaMapsConfig.get().oldItemTextures || stack == null || stack.isEmpty() || !feature.active()) return stack;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        Object data = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        Object model = stack.getComponents().get(DataComponents.ITEM_MODEL);
        if (data == null && model == null) return stack;
        Cached hit = CACHE.get(stack);
        if (hit != null && hit.data == data && hit.model == model && hit.heads == c.oldItemTexturesHeads && hit.keep == c.oldItemTexturesKeepPack) {
            return hit.out == null ? stack : hit.out;
        }
        if (!loaded) load();
        ItemStack out = convert(stack, c);
        CACHE.put(stack, new Cached(data, model, c.oldItemTexturesHeads, c.oldItemTexturesKeepPack, out == stack ? null : out));
        return out;
    }

    private static ItemStack convert(ItemStack stack, TeslaMapsConfig c) {
        CustomizableStack cs = (CustomizableStack) (Object) stack;
        if (Customize.active()) {
            String key = cs.teslamaps$customKey();
            if (!key.isEmpty() && (c.customItemModel.containsKey(key) || c.customHelmetTextures.containsKey(key))) return stack;
        }
        String id = cs.teslamaps$skyblockId();
        if (id.equals("PET")) id = petId(stack);
        String norm = id.isEmpty() ? "" : normalize(id);

        if (!norm.isEmpty() && c.oldItemTexturesHeads && stack.getComponents().get(DataComponents.PROFILE) == null) {
            ItemStack head = head(norm);
            if (head != null) return head;
        }

        Item target = norm.isEmpty() ? null : BY_ID.get(norm);
        Identifier model = stack.getComponents().get(DataComponents.ITEM_MODEL);
        if (target == null && model != null) target = BY_MODEL.get(model.toString());
        if (target == null && c.oldItemTexturesKeepPack && packLoaded) return stack;

        boolean remap = target != null && stack.getItem() != target;
        boolean strip = model != null && !model.getNamespace().equals("minecraft");
        if (!remap && !strip) return stack;
        ItemStack out = remap ? stack.transmuteCopy(target) : stack.copy();
        out.set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(out.getItem()));
        return out;
    }

    private static String petId(ItemStack stack) {
        CustomData cd = stack.getComponents().get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "PET";
        CompoundTag tag = cd.copyTag();
        String info = tag.getStringOr("petInfo", "");
        int i = info.indexOf("\"type\":\"");
        if (i < 0) return "PET";
        int s = i + 8, e = info.indexOf('"', s);
        return e > s ? "PET_" + info.substring(s, e) : "PET";
    }

    private static ItemStack head(String norm) {
        ItemStack cached = HEAD_CACHE.get(norm);
        if (cached != null) return cached;
        String hash = HEADS.get(norm);
        if (hash == null) return null;
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        String value = java.util.Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        com.google.common.collect.Multimap<String, Property> props = com.google.common.collect.LinkedHashMultimap.create();
        props.put("textures", new Property("textures", value));
        GameProfile gp = new GameProfile(UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8)), "teslamaps",
                new com.mojang.authlib.properties.PropertyMap(props));
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(gp));
        HEAD_CACHE.put(norm, head);
        return head;
    }
}
