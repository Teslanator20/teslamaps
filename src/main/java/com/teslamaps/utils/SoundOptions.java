package com.teslamaps.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;

/**
 * Shared list of clean/cool sounds for configurable sound options (etherwarp, secret sound,
 * secret chime, …). Display key -> vanilla registry id; resolved via the registry so any sound
 * can be added by just listing its id.
 */
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
    }

    /** All option keys (for config dropdowns). */
    public static String[] keys() {
        return SOUNDS.keySet().toArray(new String[0]);
    }

    /** Resolve a key to a SoundEvent (falls back to the orb pickup if unknown). */
    public static SoundEvent resolve(String key) {
        String id = SOUNDS.getOrDefault(key, "entity.experience_orb.pickup");
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.fromNamespaceAndPath("minecraft", id));
        return sound != null ? sound : SoundEvents.EXPERIENCE_ORB_PICKUP;
    }
}
