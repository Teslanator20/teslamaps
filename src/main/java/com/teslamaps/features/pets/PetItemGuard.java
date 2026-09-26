package com.teslamaps.features.pets;

// After Skytils' PetFeatures petItemConfirmation
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Right-click with a pet item, candy or skin is blocked until the chat line is clicked (5 s). */
public final class PetItemGuard {

    private static final Map<String, Boolean> PET_ITEMS = new HashMap<>();
    private static Feature feature;
    private static long unlockedAt;
    private static long lastWarn;

    private PetItemGuard() {}

    public static void register() {
        feature = Feature.of("PetItemGuard").section("Inventory").when(() -> TeslaMapsConfig.get().petItemConfirm);
        UseItemCallback.EVENT.register(PetItemGuard::onUse);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> onUse(player, level, hand));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("petitemunlock").executes(c -> {
            unlockedAt = System.currentTimeMillis();
            c.getSource().sendFeedback(Component.literal("§a[TeslaMaps] §fPet items unlocked for 5s"));
            return 1;
        });
    }

    static boolean isPetItem(ItemStack stack) {
        String id = ItemUtil.skyblockId(stack);
        if (id.isEmpty() || id.equals("PET")) return false;
        Boolean hit = PET_ITEMS.get(id);
        if (hit != null) return hit;
        boolean pet = (id.contains("PET_ITEM") && !id.endsWith("_DROP")) || id.endsWith("CARROT_CANDY")
                || id.equals("PET_CANDY") || id.startsWith("PET_SKIN_");
        if (!pet) {
            List<String> lore = ItemUtil.loreLines(stack);
            for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 3; i--) {
                if (lore.get(i).contains("PET ITEM")) { pet = true; break; }
            }
        }
        PET_ITEMS.put(id, pet);
        return pet;
    }

    private static InteractionResult onUse(Player player, Level level, InteractionHand hand) {
        if (!level.isClientSide() || feature == null || !feature.active() || !SkyblockUtils.isInSkyblock())
            return InteractionResult.PASS;
        if (!isPetItem(player.getItemInHand(hand))) return InteractionResult.PASS;
        long now = System.currentTimeMillis();
        if (now - unlockedAt < 5000) return InteractionResult.PASS;
        if (now - lastWarn > 1500) {
            lastWarn = now;
            player.sendSystemMessage(Component.literal("§c[TeslaMaps] §fBlocked using a pet item. ")
                    .append(Component.literal("§a§l[Click to allow for 5s]").withStyle(s -> s
                            .withClickEvent(new ClickEvent.RunCommand("/tmap petitemunlock"))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Inventory > Item Protection"))))));
        }
        return InteractionResult.FAIL;
    }
}
