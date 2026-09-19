package com.teslamaps.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;

public class SoundOptions {
    private static final LinkedHashMap<String, String> SOUNDS = new LinkedHashMap<>();
    static {
        SOUNDS.put("EXPERIENCE_ORB",   "entity.experience_orb.pickup");
        SOUNDS.put("NOTE_PLING",       "block.note_block.pling");
        SOUNDS.put("NOTE_BELL",        "block.note_block.bell");
        SOUNDS.put("NOTE_CHIME",       "block.note_block.chime");
        SOUNDS.put("NOTE_HARP",        "block.note_block.harp");
        SOUNDS.put("NOTE_FLUTE",       "block.note_block.flute");
        SOUNDS.put("NOTE_XYLOPHONE",   "block.note_block.xylophone");
        SOUNDS.put("NOTE_IRON_XYLO",   "block.note_block.iron_xylophone");
        SOUNDS.put("NOTE_BIT",         "block.note_block.bit");
        SOUNDS.put("NOTE_BANJO",       "block.note_block.banjo");
        SOUNDS.put("NOTE_GUITAR",      "block.note_block.guitar");
        SOUNDS.put("NOTE_COWBELL",     "block.note_block.cow_bell");
        SOUNDS.put("NOTE_DIDGERIDOO",  "block.note_block.didgeridoo");
        SOUNDS.put("AMETHYST_CHIME",   "block.amethyst_block.chime");
        SOUNDS.put("AMETHYST_HIT",     "block.amethyst_block.hit");
        SOUNDS.put("AMETHYST_RESONATE","block.amethyst_block.resonate");
        SOUNDS.put("LEVEL_UP",         "entity.player.levelup");
        SOUNDS.put("BELL",             "block.bell.use");
        SOUNDS.put("BUTTON",           "ui.button.click");
        SOUNDS.put("ARROW_HIT",        "entity.arrow.hit_player");
        SOUNDS.put("CRIT",             "entity.player.attack.crit");
        SOUNDS.put("ITEM_PICKUP",      "entity.item.pickup");
        SOUNDS.put("BEACON",           "block.beacon.activate");
        SOUNDS.put("BEACON_SELECT",    "block.beacon.power_select");
        SOUNDS.put("CONDUIT",          "block.conduit.activate");
        SOUNDS.put("ENCHANT",          "block.enchantment_table.use");
        SOUNDS.put("TRIDENT_RETURN",   "item.trident.return");
        SOUNDS.put("FIREWORK",         "entity.firework_rocket.launch");
        SOUNDS.put("ALLAY_GIVE",       "entity.allay.item_given");
        SOUNDS.put("BOOKSHELF",        "block.chiseled_bookshelf.pickup");
        SOUNDS.put("DOLPHIN",          "entity.dolphin.play");
        // referenced by the pickers but previously missing, so they silently fell back
        SOUNDS.put("ANVIL_LAND",       "block.anvil.land");
        SOUNDS.put("BLAZE_DEATH",      "entity.blaze.death");
        SOUNDS.put("GHAST_SHOOT",      "entity.ghast.shoot");
        SOUNDS.put("WITHER_SPAWN",     "entity.wither.spawn");
        SOUNDS.put("ENDER_DRAGON_GROWL", "entity.ender_dragon.growl");
        // clean, short, cut through the noise
        SOUNDS.put("AMETHYST_BREAK",   "block.amethyst_block.break");
        SOUNDS.put("AMETHYST_STEP",    "block.amethyst_cluster.step");
        SOUNDS.put("SCULK_CHARGE",     "block.sculk.charge");
        SOUNDS.put("SCULK_SENSOR",     "block.sculk_sensor.clicking");
        SOUNDS.put("SCULK_SHRIEK",     "block.sculk_shrieker.shriek");
        SOUNDS.put("COPPER_BULB",      "block.copper_bulb.turn_on");
        SOUNDS.put("TRIAL_SPAWNER",    "block.trial_spawner.detect_player");
        SOUNDS.put("VAULT_INSERT",     "block.vault.insert_item");
        SOUNDS.put("VAULT_OPEN",       "block.vault.open_shutter");
        SOUNDS.put("CRAFTER_CRAFT",    "block.crafter.craft");
        SOUNDS.put("DECORATED_POT",    "block.decorated_pot.insert");
        SOUNDS.put("LODESTONE",        "item.lodestone_compass.lock");
        SOUNDS.put("SPYGLASS",         "item.spyglass.use");
        SOUNDS.put("BOTTLE_FILL",      "item.bottle.fill_dragonbreath");
        SOUNDS.put("ARROW_HIT_NOTE",   "block.note_block.chime");
        SOUNDS.put("EXP_LEVEL",        "entity.player.levelup");
        SOUNDS.put("ALLAY_AMBIENT",    "entity.allay.ambient_without_item");
        SOUNDS.put("FROG_LONG",        "entity.frog.long_jump");
        SOUNDS.put("BREEZE_IDLE",      "entity.breeze.idle_air");
        SOUNDS.put("PORTAL_TRAVEL",    "block.portal.travel");
        SOUNDS.put("CHORUS_TELEPORT",  "item.chorus_fruit.teleport");
        SOUNDS.put("TOTEM",            "item.totem.use");
        SOUNDS.put("ANVIL_USE",        "block.anvil.use");
        SOUNDS.put("PLING_HIGH",       "block.note_block.pling");
        SOUNDS.put("UI_TOAST_IN",      "ui.toast.in");
        SOUNDS.put("UI_TOAST_OUT",     "ui.toast.out");
        SOUNDS.put("UI_TOAST_CHALLENGE", "ui.toast.challenge_complete");
        SOUNDS.put("BOAT_PADDLE",      "entity.boat.paddle_water");
        SOUNDS.put("BUCKET_FILL_FISH", "item.bucket.fill_fish");
        SOUNDS.put("FISHING_SPLASH",   "entity.fishing_bobber.splash");
        SOUNDS.put("FISHING_THROW",    "entity.fishing_bobber.throw");
        SOUNDS.put("FISHING_RETRIEVE", "entity.fishing_bobber.retrieve");
    }

    // Bundled files under assets/teslamaps/sounds/, declared in sounds.json. Not in the
    // registry - createVariableRangeEvent is enough for playing them client side, so a build
    // without the audio files just stays silent instead of failing to load.
    private static final LinkedHashMap<String, String> CUSTOM = new LinkedHashMap<>();
    static {
        CUSTOM.put("TM_QUEEN_ANT",  "queen_ant_found");
    }

    public static String[] keys() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(CUSTOM.keySet());
        out.addAll(SOUNDS.keySet());
        return out.toArray(new String[0]);
    }

    public static SoundEvent resolve(String key) {
        String custom = CUSTOM.get(key);
        if (custom != null) {
            return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("teslamaps", custom));
        }
        String id = SOUNDS.getOrDefault(key, "entity.experience_orb.pickup");
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
        return sound != null ? sound : SoundEvents.EXPERIENCE_ORB_PICKUP;
    }
}
