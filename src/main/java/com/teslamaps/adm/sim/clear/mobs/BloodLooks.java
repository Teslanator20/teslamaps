// Ported from ADM's p3sim/clear/mobs/BloodLooks.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobSkin;
import com.teslamaps.adm.sim.pack.mobs.MobTag;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public final class BloodLooks {
    private BloodLooks() {}

    public static final int BASE = 100_000;
    public static final String PREFIX = "blood:";

    private static final class Look {
        final String name;
        final Kind kind;
        final String profile;
        final String skin;
        final boolean slim;

        Look(String name, Kind kind, String profile, String skin, boolean slim) {
            this.name = name; this.kind = kind; this.profile = profile; this.skin = skin; this.slim = slim;
        }

        Look(String name, Kind kind, String profile, String skin) { this(name, kind, profile, skin, false); }
    }

    public enum Kind { STANDARD, BOSS, GIANT, SOUL }

    private static final List<Look> LOOKS = List.of(
            new Look("Freak", Kind.STANDARD, "Freak ", "7d12b2ade413a6cd7cca3c95e961ba9f0ae7165fa41fc7b5d5f094a01240c609"),
            new Look("Reaper", Kind.STANDARD, "Reaper ", "a89f6303af85877610912dc04b8b1e89724752f0a7eea05ab6547e228179c06f", true),
            new Look("Tear", Kind.STANDARD, "Tear ", "c919e5b8d56f062a21d224de14af771e2f55d09b59e7b099d09daa57540b79cf", true),
            new Look("Leech", Kind.STANDARD, "Leech ", "5a79860aca799407c0faa10b1bbcf42998fad4ebcf31d7a214180826b4ac94e1"),
            new Look("Mr. Dead", Kind.STANDARD, "Mr. Dead", "fb3973a752b24a2f3abb003427f6dbe6ca3a61db0a1bcf351c6eab27ec27e50"),
            new Look("Putrid", Kind.STANDARD, "Putrid ", "f4624a9a8c69ca204504abb043d47456cd9b09749a36357462303f276a229d4"),
            new Look("Psycho", Kind.STANDARD, "Psycho ", "aa23c8cde2943c84249de8351bc3540be5f8afaaba8b2cb032fc5acad78a269b"),
            new Look("Ooze", Kind.STANDARD, "Ooze ", "9171f35b8f508142bd8c65417d0f324153ab9147739ee4d10dea733cc80eaa20"),
            new Look("Mute", Kind.STANDARD, "Mute ", "5cccd53f5191c29a9dc8f0170fbdc4e59e66476aae33de27b468f1de1b7cf3b2"),
            new Look("Vader", Kind.STANDARD, "Vader ", "ad67f97d7f821729beb34a82c3f13592b40439fe5248e72576fde7aa180bf77"),
            new Look("Parasite", Kind.STANDARD, "Parasite ", "4774871190c878c9a2c4496c1e10257c6c4ea13807d72c15d7ac6ab3a7a9a8dc"),
            new Look("Walker", Kind.STANDARD, "Walker ", "8421ba5b8e3573ef97beb5b40e15d15b20f30631c4c5330c3deda3047df0e92"),
            new Look("Revoker", Kind.STANDARD, "Revoker ", "ff184c19e725623d32828a0a4e741e86f135ac63dbc828ff3c8468338f3683b"),
            new Look("Cannibal", Kind.STANDARD, "Cannibal ", "b5ba76e02cab72fa7d8ac54ceec849976ab0b00a01068d68c266766bf70c3997"),
            new Look("Skull", Kind.STANDARD, "Skull ", "ad22772f769045fdc5be819ad68b01a97ac04c60886d2ca7afee39b282f7a383"),
            new Look("Frost", Kind.STANDARD, "Frost ", "3260325171a7ba8460830c0eea515c757a665e5b16a14207ba1a3182752bee87"),
            new Look("Flamer", Kind.STANDARD, "Flamer ", "67237eddaebdbbdaacfa912885560ccdc65da93b4c3d513532868ec23bb5b448"),
            new Look("Livid", Kind.BOSS, "Livid ", "c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b"),
            new Look("Spirit Bear", Kind.BOSS, "Spirit Bear", "62d8fd3aa5617b1dac0aae9c81f6dd70ad93a59942f460d27e4d55a5cb8918e8"),
            new Look("Bonzo", Kind.BOSS, "Bonzo ", "12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c"),
            new Look("Scarf", Kind.BOSS, "Scarf ", "7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d"),
            new Look("Giant", Kind.GIANT, "", null),
            new Look("Wandering Soul", Kind.SOUL, "WanderingSoul ", "2cf0de45e70ee69c82ec9c29088adc4c48b35356ef5c4d40849f6796cf7f89b6", true)
    );

    private static final String ICON = "";
    private static final String ICON_GIANT = "";
    private static final String ICON_BOSS = "";
    private static final String ICON_BONZO_SCARF = "";
    private static final String ICON_SPIRIT_BEAR = "";

    public static final double NPC_TAG_DY = 2.125;
    public static final double GIANT_TAG_DY = 12.5;

    private static final Map<String, String> WORD_COLOUR = Map.of("Speedy", "b", "Healthy", "a", "Stormy", "1", "Stealth", "7", "Golden", "6", "Boomer", "c");

    private static final List<MobArchetype> archetypes = build();

    private static List<MobArchetype> build() {
        List<MobArchetype> out = new ArrayList<>();
        for (int i = 0; i < LOOKS.size(); i++) {
            Look l = LOOKS.get(i);
            out.add(new MobArchetype(
                    BASE + i, PREFIX + l.name, l.name, "special",
                    l.kind == Kind.GIANT ? "minecraft:giant" : "minecraft:player",
                    l.kind != Kind.GIANT,
                    l.skin != null ? new MobSkin(l.skin, skinValue(l.skin, l.slim), null, l.slim) : null,
                    null, null, Map.of(), false,
                    new MobTag(1, l.kind == Kind.GIANT ? GIANT_TAG_DY : NPC_TAG_DY, "", "", l.kind == Kind.BOSS || l.name.equals("Livid")),
                    l.kind == Kind.GIANT ? "static" : "chase", null, null, Map.of()
            ));
        }
        return out;
    }

    public static MobArchetype forName(String name) {
        if (name == null) return null;
        for (int i = 0; i < LOOKS.size(); i++) if (LOOKS.get(i).name.equals(name)) return archetypes.get(i);
        return null;
    }

    public static MobArchetype archetype(int index) {
        int i = index - BASE;
        return i >= 0 && i < archetypes.size() ? archetypes.get(i) : null;
    }

    public static boolean isBlood(MobArchetype arch) { return arch.id.startsWith(PREFIX); }

    private static Look look(MobArchetype arch) {
        int i = arch.index - BASE;
        return i >= 0 && i < LOOKS.size() ? LOOKS.get(i) : null;
    }

    public static String profileName(MobArchetype arch) {
        Look l = look(arch);
        return l != null ? l.profile : arch.name;
    }

    private static Kind kindOf(MobArchetype arch) {
        Look l = look(arch);
        return l != null ? l.kind : Kind.STANDARD;
    }

    public static String tag(MobArchetype arch, String word, double hp, double maxHp) {
        String head = switch (kindOf(arch)) {
            case GIANT -> "§2" + ICON + "§r§7" + ICON_GIANT + " ";
            case BOSS -> switch (arch.name) {
                case "Spirit Bear" -> "§a" + ICON_SPIRIT_BEAR + "§r§5" + ICON_BOSS + " ";
                case "Livid" -> "§2" + ICON + " ";
                default -> "§e" + ICON_BONZO_SCARF + "§r§5" + ICON_BOSS + " ";
            };
            default -> "§2" + ICON + " ";
        };
        String colour = WORD_COLOUR.getOrDefault(word, "c");
        String start = kindOf(arch) == Kind.GIANT && colour.equals("7") ? "" : "§r§" + colour;
        String bold = arch.tag != null && arch.tag.bold ? "§l" : "";
        String text = word.isEmpty() ? arch.name : word + " " + arch.name;
        if (hp <= 0.0) return head + start + bold + text + "§r §c0❤";
        String hpColour = maxHp > 0 && hp <= 0.5 * maxHp ? "e" : "a";
        return head + start + bold + text + "§r §" + hpColour + MobTags.formatHp(hp) + "§r§c❤";
    }

    public static String skinValue(String id, boolean slim) {
        String meta = slim ? ",\"metadata\":{\"model\":\"slim\"}" : "";
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + id + "\"" + meta + "}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
