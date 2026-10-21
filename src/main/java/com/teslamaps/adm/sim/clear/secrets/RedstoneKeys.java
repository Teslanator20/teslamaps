// Ported from ADM's p3sim/clear/secrets/RedstoneKeys.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.pack.Pos;
import com.teslamaps.adm.sim.pack.rooms.Placement;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.List;

public final class RedstoneKeys {
    private RedstoneKeys() {}

    private static final List<Pos> CRYPT_DOOR = List.of(new Pos(12, 70, 15), new Pos(13, 70, 15), new Pos(14, 70, 15), new Pos(15, 70, 15),
            new Pos(13, 69, 15), new Pos(14, 69, 15), new Pos(15, 69, 15));

    public static final List<KeyRoomSpec> ROOMS = List.of(
            new KeyRoomSpec("Redstone_Crypt", List.of(), new Pos(26, 72, 6), new Pos(26, 73, 6), null, CRYPT_DOOR, List.of()),
            new KeyRoomSpec(
                    "Redstone_Key", List.of(new Pos(10, 70, 26), new Pos(19, 66, 7)), new Pos(27, 68, 7), new Pos(27, 69, 7), null,
                    List.of(new Pos(18, 68, 23), new Pos(19, 68, 23), new Pos(20, 68, 23), new Pos(21, 68, 23)),
                    List.of(new RevealChestSpec("KC", new Pos(28, 62, 23), "south", true))),
            new KeyRoomSpec(
                    "Golden_Oasis", List.of(), new Pos(10, 71, 3), new Pos(10, 71, 4), "south",
                    List.of(new Pos(26, 70, 25), new Pos(27, 70, 25), new Pos(28, 70, 25)),
                    List.of(new RevealChestSpec("R1", new Pos(12, 63, 23), "north", false), new RevealChestSpec("R2", new Pos(13, 63, 25), "north", false))));

    public static KeyRoomSpec forDesign(String id) {
        if (id == null) return null;
        for (KeyRoomSpec s : ROOMS) if (s.designId.equals(id)) return s;
        return null;
    }

    public static Pos pickKeySpot(KeyRoomSpec spec, KRandom random) {
        return spec.keySpots.isEmpty() ? null : spec.keySpots.get(random.nextInt(spec.keySpots.size()));
    }

    private static Pos world(Placement placement, Pos p) {
        var w = placement.toWorld(p.x, p.y, p.z);
        return new Pos(w.x, w.y, w.z);
    }

    public static SocketInfo resolve(KeyRoomSpec spec, int room, Placement placement) {
        int q = placement.quarterTurns();
        HeadCell head = new HeadCell(world(placement, spec.head), spec.wallFacing != null ? SecretPlan.rotateFacing(spec.wallFacing, q) : null, Math.floorMod(q * 4, 16));
        List<RevealChest> chests = new ArrayList<>();
        for (RevealChestSpec c : spec.chests) {
            Pos w = world(placement, c.pos);
            chests.add(new RevealChest(new SecretSite(room, c.id, SecretKind.CHEST, w.x, w.y, w.z, SecretPlan.rotateFacing(c.facing, q), null, false, q, false, null, true), c.counts));
        }
        List<Pos> opens = new ArrayList<>();
        for (Pos p : spec.opens) opens.add(world(placement, p));
        return new SocketInfo(head, opens, chests);
    }
}
