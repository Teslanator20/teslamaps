// Ported from ADM's p3sim/clear/blood/camp/WallLook.kt
package com.teslamaps.adm.sim.clear.blood.camp;

import com.teslamaps.adm.sim.clear.blood.HeadTextures;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Which skull texture (rooms-pack role) each kind of wall head wears. */
public class WallLook {
    private final Set<String> roles;
    private final List<String> decor;

    public WallLook(Set<String> roles) {
        this.roles = roles;
        List<String> d = new ArrayList<>();
        for (String r : roles) if (r != null && r.startsWith(DECOR_PREFIX)) d.add(r);
        d.sort(null);
        this.decor = d;
    }

    /** The key to hand to `BloodCampHost.setHead` for station. */
    public String keyFor(WallStation station, KRandom rng) {
        String role;
        switch (station.cls) {
            case GIANT -> role = GIANT;
            case BOSS -> role = boss(Math.max(WallPlan.WALL_BOSSES.indexOf(station.mobName), 0));
            case WANDERING_SOUL -> role = SOUL;
            case STANDARD -> role = mob(station.mobName);
            case DECOR -> { return decor.isEmpty() ? HeadTextures.PLAIN : decor.get(rng.nextInt(decor.size())); }
            default -> throw new IllegalStateException();
        }
        return roles.contains(role) ? role : HeadTextures.PLAIN;
    }

    public static final String GIANT = "blood_giant";
    public static final String SOUL = "blood_soul";
    public static final String DECOR_PREFIX = "blood_decor_";

    public static String boss(int i) { return "blood_boss_" + (i + 1); }

    public static String mob(String name) { return "blood_mob_" + name; }

    /** The look a rooms pack's texture table gives. */
    public static WallLook of(List<SkullTexture> table) {
        Set<String> s = new HashSet<>();
        for (SkullTexture t : table) s.add(t.role);
        return new WallLook(s);
    }
}
