package com.teslamaps.features.bazaar;

// After SkyHanni's AuctionOutbidWarning
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.regex.Pattern;

public final class AuctionOutbid {

    private static final Pattern OUTBID = Pattern.compile("^\\[Auction] .+ outbid you by .+ coins for .+");

    private AuctionOutbid() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AuctionOutbid").when(() -> cfg().auctionOutbidWarning)
                .serverChat(AuctionOutbid::onChat)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Auction Outbid Warning", () -> c.auctionOutbidWarning, v -> c.auctionOutbidWarning = v)
                            .desc("Title \"You have been outbid!\" when someone outbids you on the auction house")
                            .tags("auction", "outbid", "bid", "ah");
                    b.toggle("  Sound", () -> c.auctionOutbidSound, v -> c.auctionOutbidSound = v);
                });
    }

    private static void onChat(String raw) {
        String s = StringUtil.stripTrim(raw);
        if (!s.startsWith("[Auction]") || !OUTBID.matcher(s).matches()) return;
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setTimes(0, 40, 10);
        mc.gui.setTitle(Component.literal("§cYou have been outbid!"));
        if (cfg().auctionOutbidSound) BazaarOrderTracker.play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f, 1.0f);
    }
}
