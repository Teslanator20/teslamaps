package com.teslamaps.adm.sim;
// Ported from ADM's p3sim/Station.kt

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

public final class Corpse {
    private Corpse() {}

    public static void spawn(ServerPlayer p, int ticks) {
        ArrayList<Entity> made = new ArrayList<>();
        Ec.safely("p3sim corpse", () -> {
            Mannequin m = new Mannequin(EntityTypes.MANNEQUIN, Sim.level());
            m.setComponent(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
            m.setInvulnerable(true);
            m.setNoGravity(true);
            m.setSilent(true);
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET))
                m.setItemSlot(slot, p.getItemBySlot(slot).copy());
            m.setPose(Pose.SLEEPING);
            m.snapTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), 0f);
            made.add(Sim.spawn(m));
            stand(made, p, p.getX(), p.getY() - 1.28, "§a" + p.getGameProfile().name(), null, true);
            stand(made, p, p.getX(), p.getY() - 1.66, "§c§lDEAD", null, true);
            stand(made, p, p.getX() - 1.0, p.getY(), null, new ItemStack(Items.PLAYER_HEAD), false);
        });
        Fight.later(ticks, "corpse gone", () -> made.forEach(Entity::discard));
    }

    private static void stand(List<Entity> made, ServerPlayer p, double x, double y, String name, ItemStack head, boolean invisible) {
        SimStand s = new SimStand(Sim.level());
        s.snapTo(x, y, p.getZ(), 0f, 0f);
        s.setInvisible(invisible);
        s.setInvulnerable(true);
        s.setSilent(true);
        Station.setMarker(s);
        if (name != null) { s.setCustomName(Sim.legacy(name)); s.setCustomNameVisible(true); }
        if (head != null) s.setItemSlot(EquipmentSlot.HEAD, head);
        made.add(Sim.spawn(s));
    }
}
