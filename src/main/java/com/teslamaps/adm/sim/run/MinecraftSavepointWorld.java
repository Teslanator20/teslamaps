// Ported from ADM's p3sim/run/MinecraftSavepointWorld.kt
package com.teslamaps.adm.sim.run;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimServer;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class MinecraftSavepointWorld implements SavepointWorld {
    public static final MinecraftSavepointWorld INSTANCE = new MinecraftSavepointWorld();

    private MinecraftSavepointWorld() {}

    private static RegistryOps<JsonElement> ops() {
        ServerLevel level = SimServer.level();
        return level != null ? level.registryAccess().createSerializationContext(JsonOps.INSTANCE) : null;
    }

    @Override
    public JsonArray capturePlayers() {
        JsonArray out = new JsonArray();
        RegistryOps<JsonElement> ops = ops();
        if (ops == null) return out;
        for (ServerPlayer p : Sim.players()) {
            JsonObject o = new JsonObject();
            o.addProperty("uuid", p.getUUID().toString());
            o.addProperty("x", p.getX()); o.addProperty("y", p.getY()); o.addProperty("z", p.getZ());
            o.addProperty("yaw", (double) p.getYRot()); o.addProperty("pitch", (double) p.getXRot());
            o.addProperty("selected", p.getInventory().getSelectedSlot());
            JsonArray items = new JsonArray();
            for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                ItemStack s = p.getInventory().getItem(i);
                if (s.isEmpty()) continue;
                JsonElement enc = ItemStack.OPTIONAL_CODEC.encodeStart(ops, s).result().orElse(null);
                if (enc == null) continue;
                JsonObject e = new JsonObject();
                e.addProperty("slot", i);
                e.add("item", enc);
                items.add(e);
            }
            o.add("items", items);
            out.add(o);
        }
        return out;
    }

    @Override
    public void restorePlayers(JsonArray data) {
        RegistryOps<JsonElement> ops = ops();
        if (ops == null) return;
        for (JsonElement e : data) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            JsonElement ue = o.get("uuid");
            if (ue == null) continue;
            String uuid = ue.getAsString();
            ServerPlayer found = null;
            for (ServerPlayer c : Sim.players()) if (c.getUUID().toString().equals(uuid)) { found = c; break; }
            if (found == null) continue;
            ServerPlayer p = found;
            Sim.asPlayer(p, () -> {
                Sim.tp(p, o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble(), o.get("yaw").getAsFloat(), o.get("pitch").getAsFloat());
                p.getInventory().clearContent();
                JsonArray items = o.getAsJsonArray("items");
                if (items != null) {
                    for (JsonElement it : items) {
                        JsonObject j = it.getAsJsonObject();
                        ItemStack stack = ItemStack.OPTIONAL_CODEC.parse(ops, j.get("item")).result().orElse(null);
                        if (stack == null) Ec.LOG.warn("[ads] a saved item could not be read back (slot " + j.get("slot") + ")");
                        else p.getInventory().setItem(j.get("slot").getAsInt(), stack);
                    }
                }
                JsonElement sel = o.get("selected");
                p.getInventory().setSelectedSlot(sel != null ? sel.getAsInt() : 0);
                p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));
                p.containerMenu.broadcastChanges(); p.inventoryMenu.broadcastChanges();
            });
        }
    }

    @Override
    public int resyncRegions() {
        ServerLevel level = SimServer.level();
        if (level == null) return 0;
        return WorldRegions.resyncAll(level);
    }

    @Override
    public void clearEntities() { Sim.clearEntities(); }
}
