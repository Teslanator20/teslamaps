package com.teslamaps.features;

// Ported from SkyHanni's ChatSoundResponse.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Plays the animal sound when someone writes meow, woof, moo, ... in chat. */
public final class ChatSoundResponse {

    private record Type(String name, String sound, Pattern pattern) {}

    private static final List<Type> TYPES = new ArrayList<>();
    private static final List<String> NAMES = new ArrayList<>();

    private static void type(String name, String sound, String... words) {
        Pattern p = Pattern.compile("(?:^|.* )(?:" + String.join("|", words) + ")[ !?.]*(?:$| .*$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        TYPES.add(new Type(name, sound, p));
        NAMES.add(name);
    }

    static {
        type("Cat Meow", "entity.cat.ambient", "m+e*o*w+", "m+e*a+o+w+");
        type("Cat Purr", "entity.cat.purr", "p+u*rr+");
        type("Cat Purreow", "entity.cat.purreow", "m+r+e*o*w+", "m+r+e*a+o+w+");
        type("Cat Hiss", "entity.cat.hiss", "h+i+ss+");
        type("Dog Bark", "entity.wolf.ambient", "bark", "a*w*r+u*f+", "w+oo+f+");
        type("Dog Growl", "entity.wolf.growl", "g+rr+");
        type("Dog Howl", "entity.wolf.death", "a+w+oo+");
        type("Sheep", "entity.sheep.ambient", "baa+h*");
        type("Cow", "entity.cow.ambient", "moo+");
        type("Pig", "entity.pig.ambient", "o+i+n+k+");
        type("Chicken", "entity.chicken.ambient", "cl+u+c+k+");
        type("Frog", "entity.frog.hurt", "ribbit");
        type("Horse", "entity.horse.angry", "neigh");
        type("Goat", "entity.goat.screaming.ambient", "bleat");
    }

    private ChatSoundResponse() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static List<String> names() {
        return NAMES;
    }

    public static void register() {
        Feature.of("ChatSoundResponse").section("Chat").when(() -> cfg().chatSoundResponses)
                .chat(ChatSoundResponse::onChat);
    }

    private static void onChat(String raw) {
        String t = StringUtil.strip(raw);
        if (t == null || t.isEmpty()) return;
        List<String> off = cfg().chatSoundResponsesOff;
        for (Type type : TYPES) {
            if (off.contains(type.name)) continue;
            if (!type.pattern.matcher(t).matches()) continue;
            SoundEvent ev = SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", type.sound));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ev, 1f, 1f));
            return;
        }
    }
}
