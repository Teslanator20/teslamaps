package com.teslamaps.features;

// Skyblocker ItemInHandRendererMixin, NoammAddons Tweaks.kt, SkyHanni HideArmor.kt,
// Devonian LowerNametags.kt, Skytils hideJerryRune / low health vignette
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import it.unimi.dsi.fastutil.ints.Int2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class VisualTweaks {

    private static final Int2DoubleOpenHashMap NAMETAG_OFFSET = new Int2DoubleOpenHashMap();
    private static final Int2IntOpenHashMap NAMETAG_GRACE = new Int2IntOpenHashMap();
    private static final IntOpenHashSet NAMETAG_UNKNOWN = new IntOpenHashSet();
    private static ClientLevel nametagLevel;
    private static int lastSlot = -1, slotChangeTicks;

    private VisualTweaks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("NoReequipAnimation").when(() -> cfg().noReequipAnimation)
                .tick(VisualTweaks::tickSlot)
                .config("Hide", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Held Item");
                    b.toggle("No Re-equip Animation On Item Updates", () -> c.noReequipAnimation, v -> c.noReequipAnimation = v)
                            .desc("SkyBlock: the held item no longer dips down and back up when only its lore/data changes (kill counters, drill fuel, cooldowns). Switching slots still animates.")
                            .tags("reequip", "animation", "bobbing", "book of stats");
                });

        HudElementRegistry.replaceElement(VanillaHudElements.HELD_ITEM_TOOLTIP, original ->
                (ctx, delta) -> { if (!hideHotbarName()) original.extractRenderState(ctx, delta); });

        Feature.of("LowHealthVignette").section("Render").when(() -> cfg().lowHealthVignette)
                .hud("lowhealthvignette", VisualTweaks::renderVignette)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Low Health Vignette");
                    b.toggle("Low Health Vignette", () -> c.lowHealthVignette, v -> c.lowHealthVignette = v)
                            .desc("Red screen edges below a health share (absorption included), stronger the lower you get.");
                    b.slider("  Health Threshold", 0.05f, 1.0f,
                            () -> c.lowHealthVignetteThreshold, v -> c.lowHealthVignetteThreshold = v, 2);
                    b.color("  Color", () -> c.lowHealthVignetteColor, v -> c.lowHealthVignetteColor = v);
                });

        Feature.of("LowerNametags").section("Mob Visuals").when(() -> cfg().lowerNametags)
                .tick(VisualTweaks::tickNametags)
                .config("Mob Visuals", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Nametags");
                    b.toggle("Lower Nametags", () -> c.lowerNametags, v -> c.lowerNametags = v)
                            .desc("Health tags of tall mobs (Giants etc.) float at normal height instead of far above.")
                            .tags("giant", "health", "nametag");
                });
    }

    // --- VIS-065

    private static void tickSlot() {
        int slot = Minecraft.getInstance().player.getInventory().getSelectedSlot();
        if (slot != lastSlot) {
            lastSlot = slot;
            slotChangeTicks = 3;
        } else if (slotChangeTicks > 0) {
            slotChangeTicks--;
        }
    }

    /** From ReequipAnimationMixin. */
    public static boolean instantReplace(ItemStack from, ItemStack to) {
        TeslaMapsConfig c = cfg();
        if (!c.noReequipAnimation || from.isEmpty() || from.getItem() != to.getItem() || !SkyblockUtils.isInSkyblock()) return false;
        Player p = Minecraft.getInstance().player;
        return p == null || slotChangeTicks == 0 && p.getInventory().getSelectedSlot() == lastSlot;
    }

    // --- VIS-066

    /** From ItemCooldownMixin: vanilla cooldown shading only, our own cooldown features still draw. */
    public static float vanillaCooldown(float original) {
        return original > 0 && cfg().hideItemCooldownOverlay && SkyblockUtils.isInSkyblock() ? 0f : original;
    }

    private static boolean hideHotbarName() {
        return cfg().hideHotbarItemName && SkyblockUtils.isInSkyblock();
    }

    // --- VIS-068

    private static void renderVignette(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui || !p.isAlive() || p.isCreative() || p.isSpectator()) return;
        float max = p.getMaxHealth();
        if (max <= 0) return;
        float ratio = (p.getHealth() + p.getAbsorptionAmount()) / max;
        float threshold = cfg().lowHealthVignetteThreshold;
        if (ratio >= threshold) return;
        float strength = Math.min(1f, (threshold - ratio) / threshold);
        int color = TeslaMapsConfig.parseColor(cfg().lowHealthVignetteColor);
        int alpha = Math.round(((color >>> 24) & 0xFF) * strength);
        if (alpha <= 0) return;
        int edge = (alpha << 24) | (color & 0xFFFFFF);
        int clear = color & 0xFFFFFF;
        int w = g.guiWidth(), h = g.guiHeight();
        int d = Math.max(8, Math.min(w, h) / 4);
        g.fillGradient(0, 0, w, d, edge, clear);
        g.fillGradient(0, h - d, w, h, clear, edge);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0, h);
        pose.rotate((float) (-Math.PI / 2));
        g.fillGradient(0, 0, h, d, edge, clear);
        pose.popMatrix();
        pose.pushMatrix();
        pose.translate(w, 0);
        pose.rotate((float) (Math.PI / 2));
        g.fillGradient(0, 0, h, d, edge, clear);
        pose.popMatrix();
    }

    // --- VIS-069

    /** From ArmorStandArmorMixin. */
    public static void lowerNametag(ArmorStand stand, ArmorStandRenderState state) {
        if (!cfg().lowerNametags || !cfg().section("Mob Visuals") || !stand.hasCustomName()) return;
        int id = stand.getId();
        if (NAMETAG_OFFSET.containsKey(id)) state.y += NAMETAG_OFFSET.get(id);
        else NAMETAG_UNKNOWN.add(id);
    }

    private static void tickNametags() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != nametagLevel) {
            nametagLevel = level;
            NAMETAG_OFFSET.clear();
            NAMETAG_GRACE.clear();
            NAMETAG_UNKNOWN.clear();
            return;
        }
        if (NAMETAG_UNKNOWN.isEmpty()) return;
        int[] ids = NAMETAG_UNKNOWN.toIntArray();
        NAMETAG_UNKNOWN.clear();
        for (int id : ids) {
            Entity mob = level.getEntity(id - 1);
            if (mob == null) {
                int tries = NAMETAG_GRACE.addTo(id, 1) + 1;
                if (tries >= 5) NAMETAG_OFFSET.put(id, 0.0);
                continue;
            }
            boolean real = mob instanceof LivingEntity && !(mob instanceof ArmorStand) && !(mob instanceof Player);
            NAMETAG_OFFSET.put(id, real ? Math.min(1.8 - mob.getBbHeight(), 0.0) : 0.0);
        }
        if (NAMETAG_OFFSET.size() > 4096) NAMETAG_OFFSET.clear();
    }

    // --- VIS-070

    /** From PlayerArmorHideMixin. */
    public static void stripPlayerArmor(Avatar entity, AvatarRenderState s) {
        TeslaMapsConfig c = cfg();
        if (!c.hidePlayerArmor) return;
        boolean self = entity == Minecraft.getInstance().player;
        switch (c.hidePlayerArmorMode) {
            case "Self" -> { if (!self) return; }
            case "All" -> { }
            default -> { if (self) return; }
        }
        s.headEquipment = ItemStack.EMPTY;
        s.headItem.clear();
        s.wornHeadType = null;
        s.wornHeadProfile = null;
        if (c.hidePlayerArmorHelmetOnly) return;
        s.chestEquipment = ItemStack.EMPTY;
        s.legsEquipment = ItemStack.EMPTY;
        s.feetEquipment = ItemStack.EMPTY;
    }

    // --- VIS-071

    /** From EntityRenderDispatcherMixin. The rune's eggs carry no SkyBlock id, real drops do. */
    public static boolean shouldHide(Entity e) {
        if (!(e instanceof ItemEntity item) || !cfg().hideJerryRune) return false;
        ItemStack stack = item.getItem();
        return stack.is(Items.VILLAGER_SPAWN_EGG) && ItemUtil.skyblockId(stack).isEmpty() && SkyblockUtils.isInSkyblock();
    }
}
