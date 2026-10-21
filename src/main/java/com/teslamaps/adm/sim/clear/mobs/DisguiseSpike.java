// Ported from ADM's p3sim/clear/mobs/DisguiseSpike.kt
package com.teslamaps.adm.sim.clear.mobs;

import com.teslamaps.adm.sim.pack.PackSet;
import com.teslamaps.adm.sim.pack.Packs;
import com.teslamaps.adm.sim.pack.mobs.MobArchetype;
import com.teslamaps.adm.sim.pack.mobs.MobPack;
import com.teslamaps.adm.sim.pack.mobs.MobTag;
import com.teslamaps.adm.sim.run.AdsClientContext;
import com.teslamaps.adm.sim.run.AdsCommand;
import com.teslamaps.adm.sim.run.AdsServerContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.function.Consumer;

public final class DisguiseSpike {
    private DisguiseSpike() {}

    private static int wait = -1;
    private static Consumer<String> reply = null;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (wait > 0 && --wait == 0) {
                Consumer<String> r = reply;
                reply = null;
                wait = -1;
                if (r != null) probe(r);
            }
        });
    }

    public static AdsCommand command(MobsModule module) {
        return new AdsCommand(
                "disguise", "/ads dev disguise [clear]",
                "Puts a Crypt Dreadlord in front of you as a real fake player and checks that Odin's and Blade Addons' star ESP accept it.",
                true,
                (AdsClientContext ctx) -> {
                    if (!"clear".equals(ctx.arg(0))) {
                        reply = ctx.reply;
                        wait = 30;
                    }
                },
                (AdsServerContext ctx) -> {
                    if ("clear".equals(ctx.arg(0))) {
                        ctx.reply(MobsModule.spikeClear());
                    } else {
                        PackSet set = Packs.current;
                        MobPack pack = set != null ? set.mobs : null;
                        MobArchetype found = pack != null ? pack.archetypeById("crypt_dreadlord") : null;
                        MobArchetype arch = found != null ? found : builtin();
                        ctx.reply(MobsModule.spikeSpawn(ctx.player, arch, 4.0));
                        if (pack == null) ctx.reply("No mob tables were loaded, so this one has a plain skin and a built-in name tag.");
                    }
                }
        );
    }

    private static MobArchetype builtin() {
        return new MobArchetype(
                0, "crypt_dreadlord", "Crypt Dreadlord", "clear", "minecraft:player", true, null, null, null, Map.of("F7", 840_000L), true,
                new MobTag(1, 2.09, "§2◆§r§6 ✯ §r", "§c", false), "static", null, null, Map.of()
        );
    }

    private static void probe(Consumer<String> say) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer me = mc.player;
        if (level == null || me == null) { say.accept("No client world to look at."); return; }
        Player fake = null;
        float best = Float.MAX_VALUE;
        for (Entity en : level.entitiesForRendering()) {
            if (!(en instanceof Player it)) continue;
            if (it == me || it.getUUID().version() != 2 || !(it.distanceTo(me) < 16f)) continue;
            float d = it.distanceTo(me);
            if (fake == null || d < best) { fake = it; best = d; }
        }
        if (fake == null) { say.accept("§cNo version-2 player appeared within 16 blocks. The client ignored the add (no player-info entry first?) or the entity never reached it."); return; }
        say.accept("Body: " + fake.getClass().getSimpleName() + " (a Player: " + (fake instanceof Player) + "), id " + fake.getId() + ", uuid version " + fake.getUUID().version() + ", "
                + String.format(java.util.Locale.ROOT, "%.1f", fake.distanceTo(me)) + " blocks away.");
        Entity tag = level.getEntity(fake.getId() + 1);
        String tagName = tag instanceof ArmorStand as && as.getCustomName() != null ? as.getCustomName().getString() : null;
        if (!(tag instanceof ArmorStand) || tagName == null) say.accept("§cNo name-tag stand at body id + 1 (found " + (tag != null ? tag.getClass().getSimpleName() : null) + ").");
        else say.accept("Tag at id " + tag.getId() + ": \"" + tagName + "\" matches the star regex: " + MobTags.STARRED_REGEX.matcher(tagName).matches() + ".");
    }
}
