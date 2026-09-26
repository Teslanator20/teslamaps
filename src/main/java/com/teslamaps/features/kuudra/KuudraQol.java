package com.teslamaps.features.kuudra;

// Ported from Shizo's kuudra/general/KuudraInfo.kt (dpsHelper, showCannon, blockMenu, supplysound) and Odin's RemovePerks.kt (perk list)
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.ESPRenderer;
import com.teslamaps.utils.StringUtil;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Small Kuudra helpers: DPS helper, cannon hitbox, P1 menu block, supply sound, perk menu colors. */
public final class KuudraQol {

    private static Feature feature;
    private static boolean hideBoss;
    private static List<Entity> cannons = List.of();
    private static int ticks;

    private KuudraQol() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("KuudraQol").section("Kuudra")
                .when(() -> cfg().kuudraDpsHelper || cfg().kuudraCannonBox || cfg().kuudraMenuBlock
                        || cfg().kuudraSupplySound || cfg().kuudraPerkColors)
                .tick(KuudraQol::tick)
                .world(KuudraQol::render)
                .serverChat(KuudraQol::onChat)
                .config("Kuudra", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Kuudra QoL");
                    b.toggle("DPS Helper", () -> c.kuudraDpsHelper, v -> c.kuudraDpsHelper = v)
                            .desc("Hides Kuudra in phase 3 while you hold an Aspect or an etherwarp item, so it does not block your view");
                    b.toggle("Cannon Hitbox", () -> c.kuudraCannonBox, v -> c.kuudraCannonBox = v)
                            .desc("Frames the LEFT-CLICK cannons in phases 2 and 3");
                    b.toggle("Block Menu In Phase 1", () -> c.kuudraMenuBlock, v -> c.kuudraMenuBlock = v)
                            .desc("Right-clicking the SkyBlock Menu (or Elle's item) in hotbar slot 9 does nothing during phase 1");
                    b.toggle("Supply Taken Sound", () -> c.kuudraSupplySound, v -> c.kuudraSupplySound = v)
                            .desc("Sound when someone else is already picking up the supply you went for");
                    b.toggle("Mark Useless Perks", () -> c.kuudraPerkColors, v -> c.kuudraPerkColors = v)
                            .desc("Perk Menu: red background behind perks you rarely want (Steady Hands, Bomberman, Mining Frenzy, "
                                    + "Elle's Lava Rod / Pickaxe, Auto Revive, Human Cannonball)");
                });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || feature == null || !cfg().kuudraMenuBlock || !feature.active()) return InteractionResult.PASS;
            if (KuudraContext.phase() != KuudraContext.SUPPLIES || player.getInventory().getSelectedSlot() != 8) return InteractionResult.PASS;
            String name = StringUtil.strip(player.getItemInHand(hand).getHoverName().getString());
            return name.contains("SkyBlock Menu") || name.contains("Elle") ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        hideBoss = c.kuudraDpsHelper && KuudraContext.phase() == KuudraContext.STUN && holdsDpsItem(mc.player.getMainHandItem());
        if (++ticks % 10 != 0) return;
        int phase = KuudraContext.phase();
        if (!c.kuudraCannonBox || phase != KuudraContext.BUILD && phase != KuudraContext.STUN) {
            cannons = List.of();
            return;
        }
        List<Entity> found = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand && e.hasCustomName()
                    && e.getName().getString().toUpperCase(java.util.Locale.ROOT).contains("LEFT-CLICK")) found.add(e);
        }
        cannons = found;
    }

    private static boolean holdsDpsItem(ItemStack s) {
        if (s.isEmpty()) return false;
        if (StringUtil.strip(s.getHoverName().getString()).contains("Aspect")) return true;
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        var tag = data.copyTag();
        return tag.getInt("ethermerge").orElse(0) == 1 || "ETHERWARP_CONDUIT".equals(tag.getString("id").orElse(""));
    }

    /** EntityRenderDispatcher.shouldRender hook. */
    public static boolean shouldHide(Entity e) {
        return hideBoss && e == KuudraContext.boss();
    }

    private static void render(PoseStack pose, Vec3 cam) {
        for (int i = 0, n = cannons.size(); i < n; i++) {
            ESPRenderer.drawBoxOutline(pose, cannons.get(i).getBoundingBox().inflate(0.5), 0xFFFFFFFF, 2f, cam, false);
        }
    }

    private static void onChat(String raw) {
        if (!cfg().kuudraSupplySound || !KuudraContext.inKuudra()) return;
        String s = StringUtil.stripTrim(raw);
        if (s.contains("Someone else is currently trying to pick up these supplies!")
                || s.contains("You are already currently picking up some supplies!")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /** SlotBackgrounds hook. */
    public static void drawSlot(GuiGraphicsExtractor g, Slot slot) {
        if (!cfg().kuudraPerkColors || feature == null || !feature.active() || slot.container instanceof Inventory) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> cs) || !"Perk Menu".equals(cs.getTitle().getString())) return;
        ItemStack s = slot.getItem();
        if (s.isEmpty() || !useless(StringUtil.strip(s.getHoverName().getString()))) return;
        g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x88FF4444);
    }

    private static boolean useless(String n) {
        return n.contains("Steady Hands") || n.contains("Bomberman") || n.contains("Mining Frenzy") || n.contains("Human Cannonball")
                || n.equals("Elle's Lava Rod") || n.equals("Elle's Pickaxe") || n.equals("Auto Revive");
    }
}
