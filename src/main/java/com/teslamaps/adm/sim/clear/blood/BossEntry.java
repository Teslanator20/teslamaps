// Ported from ADM's p3sim/clear/blood/BossEntry.kt
package com.teslamaps.adm.sim.clear.blood;

import com.teslamaps.adm.sim.BossBar;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.Spots;
import com.teslamaps.adm.sim.pack.rooms.WorldPos;
import com.teslamaps.adm.sim.run.BossEntryOptions;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.RunBlocks;
import com.teslamaps.adm.sim.run.RunStage;
import com.teslamaps.adm.sim.util.KRandom;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The way from the blood room to Maxor: the strike and the portal, then the one-tick jump of the whole party. */
public final class BossEntry {
    private BossEntry() {}

    public static final float THUNDER_VOLUME = 10000f;
    public static final float EXPLODE_VOLUME = 2f;

    /** Recorded 0.79 to 0.98. */
    public static float thunderPitch(KRandom random) { return 0.79f + random.nextFloat() * 0.19f; }

    /** Vanilla's 0.5 + 0.2 * random. */
    public static float explodePitch(KRandom random) { return 0.5f + random.nextFloat() * 0.2f; }

    /** Where the party lands: the P1 spot, a half block up. */
    public static final Vec3 ARRIVAL = new Vec3(Spots.P1.x, Spots.P1.y + 0.5, Spots.P1.z);

    public static Vec3 blockCentre(double x, double y, double z) { return new Vec3(Math.floor(x) + 0.5, Math.floor(y) + 0.5, Math.floor(z) + 0.5); }

    /** The room's alcove, or the centre of the block the host stands in. */
    public static Vec3 strikePoint(BloodGeometry.ExitSite site) {
        if (site != null) return new Vec3(site.strikeX, site.strikeY, site.strikeZ);
        ServerPlayer from = Sim.host();
        if (from == null) {
            List<ServerPlayer> ps = Sim.players();
            from = ps.isEmpty() ? null : ps.get(0);
        }
        if (from == null) return ARRIVAL;
        return blockCentre(from.getX(), from.getY(), from.getZ());
    }

    /** The lightning bolt is added: the tick before the portal appears. */
    public static void strike(BloodGeometry.ExitSite site) {
        Vec3 at = strikePoint(site);
        LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, Sim.level());
        bolt.setVisualOnly(true);
        bolt.snapTo(at.x, at.y, at.z, 0f, 0f);
        Sim.spawn(bolt);
    }

    /** Thunder and the explosion at the strike, and the portal blocks, all in this tick. */
    public static void openPortal(Run run, BloodGeometry.ExitSite site) {
        KRandom random = run.subRandom("blood-entry");
        Vec3 at = strikePoint(site);
        // a visual-only bolt is silent, so both sounds are sent here
        Sim.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, THUNDER_VOLUME, thunderPitch(random), at, SoundSource.WEATHER);
        Sim.sound(SoundEvents.GENERIC_EXPLODE, EXPLODE_VOLUME, explodePitch(random), at, SoundSource.BLOCKS);
        if (site != null) setPortal(site);
    }

    /** Writes the 12 portal blocks. */
    public static void setPortal(BloodGeometry.ExitSite site) {
        Direction.Axis axis = site.axisX ? Direction.Axis.X : Direction.Axis.Z;
        BlockState state = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);
        for (WorldPos c : site.cells) RunBlocks.set(c.x, c.y, c.z, state);
    }

    /** Makes the alcove an open doorway. */
    public static void clearPortal(BloodGeometry.ExitSite site) {
        for (WorldPos c : site.cells) RunBlocks.set(c.x, c.y, c.z, Blocks.AIR.defaultBlockState());
    }

    /** The jump: Maxor's bar, the whole party to the P1 spot, then the boss stage. */
    public static void enter(Run run) {
        // the Watcher's bar, when it was up, is renamed in place
        BossBar.show("§c§lMaxor", 1f);
        for (ServerPlayer p : Sim.players()) Sim.tp(p, ARRIVAL.x, ARRIVAL.y, ARRIVAL.z);
        run.state.milestones.bossEntryT = run.t();
        run.bus.post(new com.teslamaps.adm.sim.run.BossEntry(run.t()));
        run.requestStage(RunStage.BOSS_ENTRY);
        // the boss kit and loadout replace the clear's at the jump
        run.beginBoss(new BossEntryOptions(true, 2));
    }
}
