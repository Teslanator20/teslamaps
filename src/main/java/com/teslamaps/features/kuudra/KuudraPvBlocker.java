package com.teslamaps.features.kuudra;

// Ported from SkyHanni's KuudraProfileViewerBlocker.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Swallows right-clicks on real players in Kuudra so the profile viewer does not pop up. */
public final class KuudraPvBlocker {

    private KuudraPvBlocker() {}

    /** MultiPlayerGameMode.interact hook. */
    public static boolean block(Entity e) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.kuudraPvBlocker || !c.section("Kuudra")) return false;
        if (!(e instanceof Player) || e == Minecraft.getInstance().player) return false;
        if (e.getUUID().version() != 4) return false;
        return KuudraRunInfo.inKuudra() || (c.kuudraPvBlockerDungeon && DungeonManager.isInDungeon());
    }
}
