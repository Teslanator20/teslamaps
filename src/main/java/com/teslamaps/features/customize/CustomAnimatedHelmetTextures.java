package com.teslamaps.features.customize;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.TeslaMaps;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** NEU's animated skulls: a list of frames plus how many ticks each one is held. Ported from Skyblocker. */
public class CustomAnimatedHelmetTextures {
    private static final Map<String, AnimatedHead> ANIMATED_HEADS = new LinkedHashMap<>();
    private static final Map<AnimatedHead, StateTracker> STATE_TRACKERS = new java.util.HashMap<>();
    private static int ticks = 0;
    private static boolean requested = false;

    public static void init() {
        if (requested) return;
        requested = true;
        CustomizeRepo.load("animatedskulls", CustomAnimatedHelmetTextures::parse);
    }

    public static void tick() {
        ticks++;
    }

    private static void parse(JsonElement root) {
        Map<String, AnimatedHead> heads = new LinkedHashMap<>();
        try {
            JsonObject skins = root.getAsJsonObject().getAsJsonObject("skins");
            for (Map.Entry<String, JsonElement> entry : skins.entrySet()) {
                JsonObject head = entry.getValue().getAsJsonObject();
                int tickThreshold = Math.max(1, head.get("ticks").getAsInt());
                List<ResolvableProfile> frames = new ArrayList<>();
                for (JsonElement texture : head.getAsJsonArray("textures")) {
                    ResolvableProfile profile = fromString(texture.getAsString());
                    if (profile != null) frames.add(profile);
                }
                if (!frames.isEmpty()) heads.put(entry.getKey(), new AnimatedHead(tickThreshold, frames));
            }
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("[Customize] malformed animatedskulls.json: {}", e.toString());
            return;
        }
        Minecraft.getInstance().execute(() -> {
            ANIMATED_HEADS.clear();
            ANIMATED_HEADS.putAll(heads);
            STATE_TRACKERS.clear();
            TeslaMaps.LOGGER.info("[Customize] {} animated heads", ANIMATED_HEADS.size());
        });
    }

    /** Frames are stored as {@code <uuid>:<texture>}. */
    private static @Nullable ResolvableProfile fromString(String string) {
        try {
            int split = string.indexOf(':');
            if (split < 0) return null;
            UUID uuid = UUID.fromString(string.substring(0, split));
            com.google.common.collect.Multimap<String, Property> properties = com.google.common.collect.LinkedHashMultimap.create();
            properties.put("textures", new Property("textures", string.substring(split + 1)));
            return ResolvableProfile.createResolved(new GameProfile(uuid, "custom", new PropertyMap(properties)));
        } catch (Exception e) {
            return null;
        }
    }

    public static Set<String> getAnimatedHeadIds() {
        return ANIMATED_HEADS.keySet();
    }

    /** What NEU does with these ids too. */
    public static String formatName(String id) {
        String[] words = id.replace('_', ' ').toLowerCase(Locale.ENGLISH).split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }

    public static @Nullable ResolvableProfile animateHeadTexture(String id) {
        AnimatedHead head = ANIMATED_HEADS.get(id);
        if (head == null) return null;

        StateTracker tracker = STATE_TRACKERS.computeIfAbsent(head, StateTracker::new);
        if (tracker.lastRecordedTick != ticks) {
            tracker.advanceTo(ticks);
            tracker.lastRecordedTick = ticks;
        }
        return tracker.currentFrame;
    }

    private record AnimatedHead(int tickThreshold, List<ResolvableProfile> frames) {}

    private static class StateTracker {
        private final AnimatedHead head;
        private int lastRecordedTick = 0;
        private ResolvableProfile currentFrame;

        private StateTracker(AnimatedHead head) {
            this.head = head;
            this.currentFrame = head.frames().getFirst();
        }

        private void advanceTo(int ticks) {
            int index = Math.floorMod(ticks / head.tickThreshold(), head.frames().size());
            this.currentFrame = head.frames().get(index);
        }
    }
}
