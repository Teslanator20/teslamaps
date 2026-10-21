// Ported from ADM's p3sim/clear/secrets/SecretsDev.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsServerContext;
import com.teslamaps.adm.sim.run.BonusKind;
import com.teslamaps.adm.sim.run.RoomRecord;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.SecretKind;
import com.teslamaps.adm.sim.run.SecretLedger;
import com.teslamaps.adm.sim.run.api.Services;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

final class SecretsDev {
    private SecretsDev() {}

    static List<AdsCommand> commands() {
        return List.of(
                new AdsCommand("secrets", "/ads dev secrets [all]", "Lists the secrets of the room you stand in (kind, spot, found or not), or the found/max of every room with 'all'.", true, null, SecretsDev::secrets),
                new AdsCommand("secretgo", "/ads dev secretgo <n>", "Teleports you onto the n-th secret still to find in your room (the numbers /ads dev secrets prints).", true, null, SecretsDev::go),
                new AdsCommand("secretgive", "/ads dev secretgive [n]", "Counts n secrets (default 1) of your room as found, the way a bot would, so you can watch the bars and the room turn green.", true, null, SecretsDev::give),
                new AdsCommand("secretcrypts", "/ads dev secretcrypts", "Lists the crypts and Superboom walls of your room and whether each is destroyed.", true, null, SecretsDev::crypts),
                new AdsCommand("secretboom", "/ads dev secretboom", "Sets off a Superboom at the block you look at (what the Superboom item will do once it is wired in).", true, null, SecretsDev::boom),
                new AdsCommand("secretbonus", "/ads dev secretbonus <mimic|prince|bat>", "Scores a bonus mob as if it had died.", true, null, SecretsDev::bonus));
    }

    private static Integer toIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static void secrets(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        SecretsModule.DevInfo info = SecretsModule.devInfo(run);
        if (info == null) { ctx.reply("The secrets are not set up for this run."); return; }
        SecretLedger led = run.state.secrets;
        if ("all".equals(ctx.arg(0))) {
            ctx.reply("Team: " + led.teamFound + "/" + led.teamMax + " secrets (" + led.percent() + "%), " + led.crypts + " crypts, " + led.witherEssence + " wither essence.");
            for (RoomRecord r : run.state.rooms) {
                RoomSecrets rs = info.plan.room(r.index());
                if (r.secretsMax() == 0) continue;
                ctx.reply("  " + r.index() + " " + r.plan.name + ": " + r.secretsFound + "/" + r.secretsMax() + "  placeable " + (rs != null ? rs.placeable() : 0) + "  " + r.check);
            }
            return;
        }
        RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
        if (room == null) { ctx.reply("You are not in a room."); return; }
        RoomSecrets rs = info.plan.room(room.index());
        ctx.reply(room.plan.name + " (room " + room.index() + "): " + room.secretsFound + "/" + room.secretsMax() + " found, " + (rs != null ? rs.placeable() : 0) + " placeable.");
        if (rs != null) {
            for (int i = 0; i < rs.sites.size(); i++) {
                SecretSite s = rs.sites.get(i);
                String st = info.book.isFound(s) ? "found" : (s.kind == SecretKind.LOCKED_CHEST && !info.unlocked.contains(s.key()) ? "locked" : "to find");
                ctx.reply("  " + i + "  " + s.kind + " at " + s.x + " " + s.y + " " + s.z + "  " + st + (s.baked ? "  (in the room's blocks)" : ""));
            }
        }
    }

    private static void go(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        SecretsModule.DevInfo info = SecretsModule.devInfo(run);
        if (info == null) return;
        RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
        if (room == null) { ctx.reply("You are not in a room."); return; }
        Integer n = toIntOrNull(ctx.arg(0));
        if (n == null) { ctx.reply("Say which secret: /ads dev secretgo <n>."); return; }
        RoomSecrets rs = info.plan.room(room.index());
        SecretSite s = rs != null && n >= 0 && n < rs.sites.size() ? rs.sites.get(n) : null;
        if (s == null) { ctx.reply("This room has no secret number " + n + "."); return; }
        Sim.tp(ctx.player, s.x + 0.5, s.y + 1.0, s.z + 0.5);
        ctx.reply("Moved to " + s.kind + " at " + s.x + " " + s.y + " " + s.z + ".");
    }

    private static void give(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
        if (room == null) { ctx.reply("You are not in a room."); return; }
        Integer a = toIntOrNull(ctx.arg(0));
        int n = Math.clamp(a != null ? a : 1, 1, 50);
        int done = 0;
        for (int k = 0; k < n; k++) {
            List<SecretSite> pending = SecretsModule.pendingSecrets(run, room.index());
            SecretSite s = pending.isEmpty() ? null : pending.get(0);
            boolean ok = s != null
                    ? Services.secrets().found(run, room.index(), s.kind, ctx.player.getUUID(), s.x, s.y, s.z)
                    : Services.secrets().found(run, room.index(), SecretKind.CHEST, ctx.player.getUUID(), 0, 0, 0);
            if (ok) done++;
        }
        ctx.reply("Counted " + done + ". " + room.plan.name + ": " + room.secretsFound + "/" + room.secretsMax() + "; room is " + room.check + ".");
    }

    private static void crypts(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        SecretsModule.DevInfo info = SecretsModule.devInfo(run);
        if (info == null) return;
        RoomRecord room = run.state.roomAt(ctx.player.getX(), ctx.player.getZ());
        if (room == null) { ctx.reply("You are not in a room."); return; }
        List<CellGroup> gs = info.groups.ofRoom(room.index());
        int crypts = 0, walls = 0;
        for (CellGroup g : gs) { if (g.kind == GroupKind.CRYPT) crypts++; if (g.kind == GroupKind.WALL) walls++; }
        ctx.reply(room.plan.name + ": " + crypts + " crypt tombs, " + walls + " secret walls. Team crypts: " + run.state.secrets.crypts + ".");
        for (CellGroup g : gs) ctx.reply("  " + g.kind + " " + g.id + ": " + g.cells.size() + " blocks around " + g.minX + " " + g.minY + " " + g.minZ + "  " + (info.groups.isDestroyed(g) ? "destroyed" : "standing"));
    }

    private static void boom(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        HitResult hit = ctx.player.pick(12.0, 1f, false);
        if (hit.getType() != HitResult.Type.BLOCK) { ctx.reply("Look at a block (within 12 blocks)."); return; }
        BlockPos p = ((BlockHitResult) hit).getBlockPos();
        int n = Services.secrets().onSuperboom(run, ctx.player.getUUID(), p.getX(), p.getY(), p.getZ());
        ctx.reply("Superboom at " + p.getX() + " " + p.getY() + " " + p.getZ() + ": " + n + " blocks destroyed. Crypts: " + run.state.secrets.crypts + ".");
    }

    private static void bonus(AdsServerContext ctx) {
        Run run = ctx.run();
        if (run == null) { ctx.reply("No run is on."); return; }
        String a = ctx.arg(0);
        BonusKind kind = null;
        for (BonusKind k : BonusKind.values()) if (k.name().equalsIgnoreCase(a)) { kind = k; break; }
        if (kind == null) { ctx.reply("Say mimic, prince or bat."); return; }
        Services.secrets().bonus(run, kind, ctx.player.getUUID());
        ctx.reply("Scored the " + kind.name().toLowerCase(Locale.ROOT) + ".");
    }
}
