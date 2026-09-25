package com.teslamaps.features;

// Ported from SkyHanni's RestorePieceOfWizardPortalLore.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.CustomData;

public final class WizardPortalLore {

    private WizardPortalLore() {}

    public static void register() {
        Feature feature = Feature.of("WizardPortalLore").when(() -> TeslaMapsConfig.get().wizardPortalLore);
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
            if (!TeslaMapsConfig.get().wizardPortalLore || !feature.active()) return;
            if (!"WIZARD_PORTAL_MEMENTO".equals(ItemUtil.skyblockId(stack))) return;
            for (Component l : lines) if (l.getString().contains("Earned by:")) return;
            CustomData d = stack.get(DataComponents.CUSTOM_DATA);
            if (d == null) return;
            String who = d.copyTag().getStringOr("recipient_name", "");
            if (who.isEmpty()) return;
            lines.add(Math.min(5, lines.size()), Component.literal("§7Earned by: " + who));
        });
    }
}
