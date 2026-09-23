package com.teslamaps.features.pets;

// Ported from Devonian's PetDisplay (menu cache) and Skysoft's PetStorageInventoryReader / PetStorageService
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.ProfiledFile;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Every pet seen in the Pets menu, saved per SkyBlock profile: rarity, skin, held item and exact xp
 * from the item's petInfo. Fills in what the tab widget cannot tell after a relog.
 */
public final class PetStore {

    public static final class Pet {
        String type;
        String rarity;
        double exp;
        String heldItem;
        String skin;
        int level;
    }

    private static final Pattern MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?:: \".*\")?(?: \\(\\d+/\\d+\\))?$");
    private static final Pattern NAME = Pattern.compile("\\[Lvl (\\d+)]");
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<LinkedHashMap<String, Pet>>() {}.getType();
    private static final ProfiledFile FILE = new ProfiledFile("pets.json",
            FabricLoader.getInstance().getConfigDir().resolve("teslamaps").resolve("pets.json"));

    private static final Pattern BEASTMASTER = Pattern.compile("Pet Exp Boost: \\+([\\d.]+)%");

    private static Map<String, Pet> pets = new LinkedHashMap<>();
    private static String activeUuid;
    private static double bagBeastmaster = 1.0;
    private static double invBeastmaster = 0;
    private static boolean loaded;
    private static int ticks;
    private static int invTicks;

    /** Beastmaster Crest pet xp multiplier: the inventory one, else the last Accessory Bag read. */
    static double beastmaster() {
        ensure();
        return invBeastmaster > 0 ? invBeastmaster : bagBeastmaster;
    }

    /** Ported from Skysoft's AccessoryBagData.readBeastmasterMultiplier. */
    private static double crest(ItemStack item) {
        if (item.isEmpty() || !ItemUtil.skyblockId(item).startsWith("BEASTMASTER_CREST_")) return 0;
        for (String line : ItemUtil.loreLines(item)) {
            Matcher m = BEASTMASTER.matcher(StringUtil.strip(line));
            if (m.find()) {
                try { return 1 + Double.parseDouble(m.group(1)) / 100.0; } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    private static void scanInventory(Minecraft mc) {
        if (mc.player == null) return;
        Inventory inv = mc.player.getInventory();
        double best = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) best = Math.max(best, crest(inv.getItem(i)));
        invBeastmaster = best;
    }

    private PetStore() {}

    public static void register() {
        Feature.of("PetStore").section("Skills").when(() -> TeslaMapsConfig.get().petOverlay).tick(PetStore::tick);
    }

    private static void ensure() {
        if (loaded && !FILE.stale()) return;
        loaded = true;
        pets = new LinkedHashMap<>();
        activeUuid = null;
        Path p = FILE.path();
        try {
            if (Files.exists(p)) {
                JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
                if (root.has("active") && !root.get("active").isJsonNull()) activeUuid = root.get("active").getAsString();
                bagBeastmaster = root.has("beastmaster") ? root.get("beastmaster").getAsDouble() : 1.0;
                if (root.has("pets")) {
                    Map<String, Pet> read = GSON.fromJson(root.get("pets"), TYPE);
                    if (read != null) pets = read;
                }
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to load pets.json", e);
        }
    }

    private static void save() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty("active", activeUuid);
            root.addProperty("beastmaster", bagBeastmaster);
            root.add("pets", GSON.toJsonTree(pets, TYPE));
            Path p = FILE.path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(root));
        } catch (Exception e) {
            TeslaMaps.LOGGER.error("Failed to save pets.json", e);
        }
    }

    /**
     * Best stored match for a pet the tab widget or a chat line names: the last active one if it is
     * that type, else the stored pet of that type and rarity whose level is closest.
     */
    static Pet resolve(String internalName, String rarity, int level) {
        ensure();
        if (internalName == null) return null;
        Pet active = activeUuid == null ? null : pets.get(activeUuid);
        if (active != null && internalName.equals(active.type) && (rarity == null || rarity.equals(active.rarity))) {
            return active;
        }
        Pet best = null;
        int bestDiff = Integer.MAX_VALUE;
        for (Pet p : pets.values()) {
            if (!internalName.equals(p.type)) continue;
            if (rarity != null && !rarity.equals(p.rarity)) continue;
            int diff = level > 0 ? Math.abs(p.level - level) : 0;
            if (diff < bestDiff) {
                best = p;
                bestDiff = diff;
            }
        }
        return best;
    }

    static Pet active() {
        ensure();
        return activeUuid == null ? null : pets.get(activeUuid);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++invTicks >= 100) {
            invTicks = 0;
            scanInventory(mc);
        }
        if (++ticks < 10) return;
        ticks = 0;
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        ensure();
        if (title.startsWith("Accessory Bag")) {
            readBag(cs);
            return;
        }
        if (!MENU.matcher(title).matches()) return;

        boolean changed = false;
        Pet selected = null;
        String selectedUuid = null;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            JsonObject info = petInfo(item);
            if (info == null || !info.has("type")) continue;

            Pet p = new Pet();
            p.type = info.get("type").getAsString();
            p.rarity = tier(info);
            p.exp = info.has("exp") ? info.get("exp").getAsDouble() : 0;
            p.heldItem = str(info, "heldItem");
            p.skin = str(info, "skin");
            Matcher lvl = NAME.matcher(StringUtil.strip(item.getHoverName().getString()));
            p.level = lvl.find() ? Integer.parseInt(lvl.group(1)) : PetConstants.xpToLevel(p.type, p.rarity, p.exp);

            String uuid = str(info, "uuid");
            if (uuid == null) uuid = p.type + ";" + p.rarity + ";" + (p.skin == null ? "" : p.skin);
            Pet old = pets.put(uuid, p);
            if (old == null || old.exp != p.exp || old.level != p.level || !java.util.Objects.equals(old.heldItem, p.heldItem)
                    || !java.util.Objects.equals(old.skin, p.skin) || !java.util.Objects.equals(old.rarity, p.rarity)) {
                changed = true;
            }
            boolean isActive = (info.has("active") && info.get("active").getAsBoolean())
                    || ItemUtil.loreContains(item, "Click to despawn!");
            if (isActive) {
                selected = p;
                selectedUuid = uuid;
            }
        }
        if (selected != null) {
            if (!selectedUuid.equals(activeUuid)) changed = true;
            activeUuid = selectedUuid;
            PetTracker.applyStored(selected);
        }
        if (changed) save();
    }

    /** Only pages that show a crest update the stored value; the bag has many pages. */
    private static void readBag(AbstractContainerScreen<?> cs) {
        double best = 0;
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof Inventory) continue;
            best = Math.max(best, crest(slot.getItem()));
        }
        if (best > 0 && best != bagBeastmaster) {
            bagBeastmaster = best;
            save();
        }
    }

    private static JsonObject petInfo(ItemStack item) {
        CustomData d = item.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String raw = d.copyTag().getString("petInfo").orElse(null);
        if (raw == null || raw.isEmpty()) return null;
        try {
            return JsonParser.parseString(raw).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    /** petInfo keeps the base tier, a Tier Boost raises it one step. */
    private static String tier(JsonObject info) {
        String t = str(info, "tier");
        if (t == null) return null;
        t = t.toUpperCase(Locale.ROOT);
        if ("PET_ITEM_TIER_BOOST".equals(str(info, "heldItem"))) {
            for (int i = 0; i < RARITIES.length - 1; i++) if (RARITIES[i].equals(t)) return RARITIES[i + 1];
        }
        return t;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
