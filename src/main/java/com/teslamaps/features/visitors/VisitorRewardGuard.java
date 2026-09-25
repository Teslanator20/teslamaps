package com.teslamaps.features.visitors;

// After SkyHanni's VisitorRewardWarning + VisitorApi.blockReason
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EstimatedValue;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

public final class VisitorRewardGuard {

    enum Reason {
        RARE_REWARD("§aRare visitor reward found", true), NEVER_ACCEPTED("§cNever accepted", true),
        CHEAP_COPPER("§aCheap copper", true), EXPENSIVE_COPPER("§cExpensive copper", false),
        LOW_LOSS("§aLow loss", true), HIGH_LOSS("§cHigh loss", false);

        final String text;
        final boolean blockRefuse;

        Reason(String text, boolean blockRefuse) {
            this.text = text;
            this.blockRefuse = blockRefuse;
        }
    }

    private static Feature feature;

    private VisitorRewardGuard() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("VisitorRewardGuard").section("Garden").when(() -> cfg().visitorRewardGuard)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Visitor Accept/Refuse Guard", () -> c.visitorRewardGuard, v -> c.visitorRewardGuard = v)
                            .desc("Blocks Refuse Offer (or Accept Offer) by the rules below: the blocked button is greyed out, the other one outlined, the tooltip says why. Hold the bypass key to click anyway.")
                            .tags("visitor", "refuse", "accept", "rare reward", "protect");
                    b.toggle("  Block Refusing Rare Rewards", () -> c.visitorGuardRare, v -> c.visitorGuardRare = v);
                    b.toggle("  Block Refusing Never-Accepted Visitors", () -> c.visitorGuardNew, v -> c.visitorGuardNew = v);
                    b.toggle("  Block Refusing Cheap Copper", () -> c.visitorGuardCheapCopper, v -> c.visitorGuardCheapCopper = v);
                    b.toggle("  Block Accepting Expensive Copper", () -> c.visitorGuardExpensiveCopper, v -> c.visitorGuardExpensiveCopper = v);
                    b.slider("  Copper Price Limit (coins)", 500f, 50000f,
                            () -> (float) c.visitorGuardCopperPrice, v -> c.visitorGuardCopperPrice = Math.round(v / 100f) * 100, 0);
                    b.toggle("  Block Refusing Low Loss", () -> c.visitorGuardLowLoss, v -> c.visitorGuardLowLoss = v);
                    b.toggle("  Block Accepting High Loss", () -> c.visitorGuardHighLoss, v -> c.visitorGuardHighLoss = v);
                    b.slider("  Loss Limit (coins)", 0f, 5_000_000f,
                            () -> (float) c.visitorGuardLossLimit, v -> c.visitorGuardLossLimit = Math.round(v / 10000f) * 10000, 0);
                    b.keybind("  Bypass Key", () -> c.visitorGuardBypassKey, v -> c.visitorGuardBypassKey = v);
                    b.toggle("  Outline Recommended Button", () -> c.visitorGuardOutline, v -> c.visitorGuardOutline = v);
                });
        ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (feature == null || !feature.active() || bypass()) return;
            Visitors.Visitor v = Visitors.open();
            Reason r = reason(v);
            if (r == null) return;
            String name = StringUtil.stripTrim(stack.getHoverName().getString());
            if (!name.equals(r.blockRefuse ? "Refuse Offer" : "Accept Offer")) return;
            lines.add(Component.empty());
            lines.add(Component.literal("§c§lBLOCKED §7- " + r.text + detail(v, r)));
            lines.add(Component.literal("  §7(bypass by holding §e" + KeyNames.display(cfg().visitorGuardBypassKey) + "§7)"));
        });
    }

    private static boolean bypass() {
        return KeyNames.isDown(cfg().visitorGuardBypassKey);
    }

    static Reason reason(Visitors.Visitor v) {
        if (v == null || !v.read) return null;
        TeslaMapsConfig c = cfg();
        int ppc = v.pricePerCopper();
        double loss = v.loss();
        if (c.visitorGuardRare && !Visitors.rareRewards(v).isEmpty()) return Reason.RARE_REWARD;
        if (c.visitorGuardNew && v.offersAccepted == 0) return Reason.NEVER_ACCEPTED;
        if (v.copper > 0 && c.visitorGuardCheapCopper && ppc <= c.visitorGuardCopperPrice) return Reason.CHEAP_COPPER;
        if (v.copper > 0 && c.visitorGuardExpensiveCopper && ppc > c.visitorGuardCopperPrice) return Reason.EXPENSIVE_COPPER;
        if (c.visitorGuardLowLoss && loss <= c.visitorGuardLossLimit) return Reason.LOW_LOSS;
        if (c.visitorGuardHighLoss && loss > c.visitorGuardLossLimit) return Reason.HIGH_LOSS;
        return null;
    }

    private static String detail(Visitors.Visitor v, Reason r) {
        return switch (r) {
            case RARE_REWARD -> " §7(§9" + String.join(", ", Visitors.rareRewards(v)) + "§7)";
            case CHEAP_COPPER, EXPENSIVE_COPPER -> " §7(paying §6" + EstimatedValue.fmt(v.pricePerCopper()) + " §7per)";
            case LOW_LOSS, HIGH_LOSS -> " §7(§6" + EstimatedValue.fmt(Math.abs(v.loss())) + " §7" + (v.loss() > 0 ? "loss" : "profit") + ")";
            default -> "";
        };
    }

    /** slotClicked HEAD: true cancels the click. */
    public static boolean blockClick(Slot slot) {
        if (feature == null || !feature.active() || slot == null || bypass()) return false;
        Visitors.Visitor v = Visitors.open();
        Reason r = reason(v);
        if (r == null) return false;
        String name = StringUtil.stripTrim(slot.getItem().getHoverName().getString());
        return name.equals(r.blockRefuse ? "Refuse Offer" : "Accept Offer");
    }

    public static void render(GuiGraphicsExtractor g) {
        if (feature == null || !feature.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) return;
        Reason r = reason(Visitors.open());
        if (r == null) return;
        var slots = cs.getMenu().slots;
        if (slots.size() <= Visitors.REFUSE_SLOT) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot refuse = slots.get(Visitors.REFUSE_SLOT), accept = slots.get(Visitors.ACCEPT_SLOT);
        Slot blocked = r.blockRefuse ? refuse : accept, other = r.blockRefuse ? accept : refuse;
        int ox = acc.getX(), oy = acc.getY();
        if (!bypass()) g.fill(ox + blocked.x, oy + blocked.y, ox + blocked.x + 16, oy + blocked.y + 16, 0xC0303030);
        if (cfg().visitorGuardOutline) {
            int col = r.blockRefuse ? 0xFF55FF55 : 0xFFFF5555;
            int x = ox + other.x - 1, y = oy + other.y - 1;
            g.fill(x, y, x + 18, y + 1, col);
            g.fill(x, y + 17, x + 18, y + 18, col);
            g.fill(x, y, x + 1, y + 18, col);
            g.fill(x + 17, y, x + 18, y + 18, col);
        }
    }
}
