package com.teslamaps.features.rift;

// After SkyHanni's VerminHighlighter.kt, head textures from its Skulls.json
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.EspStyle;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

/** Vermin in West Village / Infested House get highlighted while the Turbomax Vacuum is in hand. */
public final class VerminHighlighter {

    private static final String FLY = "7eefcb90c1030cbaff62790e690158fd18639cca5fe1f72442bd9efd5aaf422a";
    private static final String SPIDER = "8fdf62d4e03ca594c8cd21dd175327f1cf77c4bc057a0959603d83a68ba27008";

    private static List<LivingEntity> found = List.of();
    private static int ticks;

    private VerminHighlighter() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("VerminHighlighter").section("Rift").legit().when(() -> cfg().riftVerminHighlight)
                .tick(VerminHighlighter::tick)
                .world(VerminHighlighter::render)
                .config("Rift", b -> {
                    TeslaMapsConfig c = b.config();
                    b.toggle("Vermin Highlighter", () -> c.riftVerminHighlight, v -> c.riftVerminHighlight = v)
                            .desc("Highlights Flies, Spiders and Silverfish in West Village and the Infested House while the Turbomax Vacuum is in hand")
                            .tags("vermin", "vacuum", "turbomax", "west village", "silverfish", "fly", "spider", "esp").legit();
                    b.espStyle("Vermin", () -> c.riftVerminEsp);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks >= 10) {
            ticks = 0;
            found = scan(mc);
        }
        if (found.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().riftVerminEsp);
        for (LivingEntity e : found) if (e.isAlive()) style.tickEntity(e);
    }

    private static List<LivingEntity> scan(Minecraft mc) {
        if (!RiftSpot.inRift()) return List.of();
        if (!"TURBOMAX_VACUUM".equals(ItemUtil.skyblockId(mc.player.getMainHandItem()))) return List.of();
        String a = RiftSpot.sub();
        if (!a.equals("West Village") && !a.equals("Infested House")) return List.of();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Silverfish s && s.isAlive() && s.getMaxHealth() == 8) out.add(s);
            else if (e instanceof ArmorStand st && st.isAlive() && isVerminHead(st.getItemBySlot(EquipmentSlot.HEAD))) out.add(st);
        }
        return out;
    }

    private static boolean isVerminHead(ItemStack head) {
        if (head == null || head.isEmpty()) return false;
        ResolvableProfile rp = head.get(DataComponents.PROFILE);
        if (rp == null) return false;
        Collection<Property> props = rp.partialProfile().properties().get("textures");
        if (props.isEmpty()) return false;
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(props.iterator().next().value()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return decoded.contains(FLY) || decoded.contains(SPIDER);
    }

    private static void render(PoseStack pose, Vec3 cam) {
        List<LivingEntity> l = found;
        if (l.isEmpty()) return;
        EspStyle style = EspStyle.of(cfg().riftVerminEsp);
        float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        for (int i = 0, n = l.size(); i < n; i++) {
            LivingEntity e = l.get(i);
            if (e.isAlive() && !e.isRemoved()) style.drawEntity(pose, e, cam, pt);
        }
    }
}
