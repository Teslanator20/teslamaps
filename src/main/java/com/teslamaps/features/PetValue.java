package com.teslamaps.features;

// Pet pricing after SkyHelper-Networth (Skyblocker's networth lib) and GanKura's PetBin
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.croesus.PriceManager;
import com.teslamaps.profileviewer.data.PetData;

import java.util.Locale;

/** Lowest BIN of a pet by type and rarity (NEU key TYPE;tier), from the shared lowest-BIN table. */
public final class PetValue {

    private static final double CANDY_FACTOR = 0.65;
    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    public record Pet(String type, String tier, double exp, int level, int candy, String heldItem, String skin) {}

    private PetValue() {}

    public static boolean enabled() { return TeslaMapsConfig.get().petValue; }

    public static int version() { return PriceManager.version(); }

    /** Lowest BIN of the pet at any level, also used while Pet Value is off. */
    public static Double lvl1Price(String tier, String type) {
        PriceManager.ensureFresh();
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equalsIgnoreCase(tier)) return PriceManager.lowestBinOrNull(type + ";" + i);
        }
        return null;
    }

    /** Reads the item's petInfo json, null when it is not a pet. */
    public static Pet parse(String petInfo) {
        if (petInfo == null || petInfo.isBlank()) return null;
        try {
            JsonObject o = JsonParser.parseString(petInfo).getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : null;
            String tier = o.has("tier") ? o.get("tier").getAsString().toUpperCase(Locale.ROOT) : null;
            if (type == null || tier == null) return null;
            double exp = o.has("exp") ? o.get("exp").getAsDouble() : 0;
            int candy = o.has("candyUsed") ? o.get("candyUsed").getAsInt() : 0;
            String held = o.has("heldItem") && !o.get("heldItem").isJsonNull() ? o.get("heldItem").getAsString() : null;
            String skin = o.has("skin") && !o.get("skin").isJsonNull() ? o.get("skin").getAsString() : null;
            int level = new PetData(type, tier, exp, false, held, skin, candy).getLevel();
            return new Pet(type, tier, exp, level, candy, held, skin);
        } catch (Exception e) {
            return null;
        }
    }

    /** The pet itself without held item or skin, null when no price is known. */
    public static Double basePrice(Pet pet) {
        Double price = lvl1Price(pet.tier(), pet.type());
        if (price == null) return null;
        if (pet.candy() > 0 && pet.level() < 100) price *= CANDY_FACTOR;
        return price;
    }

    /** Skin ids carry the PET_SKIN_ prefix on the market. */
    public static String skinId(String skin) {
        return skin.startsWith("PET_SKIN_") ? skin : "PET_SKIN_" + skin;
    }
}
