// Ported from ADM's p3sim/clear/secrets/SecretWorld.kt
package com.teslamaps.adm.sim.clear.secrets;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Sim;
import com.teslamaps.adm.sim.SimItems;
import com.teslamaps.adm.sim.clear.blood.HeadTextures;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import com.teslamaps.adm.sim.run.RunBlocks;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SecretWorld {
    private SecretWorld() {}

    private static Direction horizontal(String name) {
        if (name == null) return Direction.NORTH;
        Direction d = Direction.byName(name);
        return d != null && d.getAxis().isHorizontal() ? d : Direction.NORTH;
    }

    public static BlockState chestState(SecretSite site) {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, horizontal(site.facing));
    }

    public static BlockState trappedChestState(SecretSite site) {
        return Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, chestState(site).getValue(ChestBlock.FACING));
    }

    public static boolean isChestBlock(BlockState state) { return state != null && (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)); }

    public static boolean isSkullBlock(BlockState state) { return state != null && state.getBlock() instanceof SkullBlock; }

    public static boolean ensureChest(SecretSite site) {
        if (isChestBlock(RunBlocks.get(site.x, site.y, site.z))) return false;
        return RunBlocks.set(site.x, site.y, site.z, chestState(site));
    }

    public static boolean ensureSkull(SecretSite site, SkullTexture tex) { return ensureSkull(site.x, site.y, site.z, site.quarterTurns, tex); }

    public static boolean ensureSkull(int x, int y, int z, int quarterTurns, SkullTexture tex) {
        BlockState existing = RunBlocks.get(x, y, z);
        boolean wrote = false;
        if (!isSkullBlock(existing)) {
            int rot = Math.floorMod(quarterTurns * 4, 16);
            wrote = RunBlocks.set(x, y, z, Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, rot));
        }
        if (tex != null) giveProfile(new BlockPos(x, y, z), tex);
        return wrote;
    }

    public static boolean placeHead(HeadCell head, SkullTexture tex) {
        BlockState state = head.wallFacing == null
                ? Blocks.PLAYER_HEAD.defaultBlockState().setValue(SkullBlock.ROTATION, head.rotation)
                : Blocks.PLAYER_WALL_HEAD.defaultBlockState().setValue(WallSkullBlock.FACING, horizontal(head.wallFacing));
        boolean wrote = RunBlocks.set(head.pos.x, head.pos.y, head.pos.z, state);
        if (tex != null) giveProfile(new BlockPos(head.pos.x, head.pos.y, head.pos.z), tex);
        return wrote;
    }

    private static void giveProfile(BlockPos pos, SkullTexture tex) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        BlockEntity raw = level.getBlockEntity(pos);
        if (!(raw instanceof SkullBlockEntity be)) return;
        UUID id = null;
        if (tex.profileId != null) {
            try { id = UUID.fromString(tex.profileId); } catch (Throwable t) { id = null; }
        }
        if (id == null) id = UUID.nameUUIDFromBytes(tex.id.getBytes(StandardCharsets.UTF_8));
        PropertyMap props = tex.value != null
                ? new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex.value)))
                : new PropertyMap(ImmutableMultimap.of());
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "", props)));
        be.applyComponentsFromItemStack(stack);
        be.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public static final String ESSENCE_SKIN_ROLE = "essence_skin";
    private static final String REVIVE_ROLE = "revive_stone";
    private static final String WEIGHTS_ROLE = "training_weights";

    public static SkullTexture essenceTexture(SkullTexture packEntry, SkullTexture skin) {
        String id = skin != null ? skin.id : (packEntry != null ? packEntry.id : "essence");
        String value = skin != null ? (skin.value != null ? skin.value : HeadTextures.valueFor(skin.id)) : null;
        return new SkullTexture(id, value, packEntry != null ? packEntry.profileId : null, "essence");
    }

    public static SkullTexture textureFor(List<SkullTexture> textures, String role) {
        for (SkullTexture t : textures) if (t.role.equals(role)) return t;
        return null;
    }

    public static void remove(int x, int y, int z) { RunBlocks.set(x, y, z, Blocks.AIR.defaultBlockState()); }

    public static void powerLever(int x, int y, int z) {
        BlockState st = RunBlocks.get(x, y, z);
        if (st == null) return;
        if (st.getBlock() instanceof LeverBlock && !st.getValue(LeverBlock.POWERED)) RunBlocks.set(x, y, z, st.setValue(LeverBlock.POWERED, true));
    }

    public static void resend(ServerPlayer p, int x, int y, int z) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        p.connection.send(new ClientboundBlockUpdatePacket(level, new BlockPos(x, y, z)));
    }

    public static boolean isLever(int x, int y, int z) {
        BlockState st = RunBlocks.get(x, y, z);
        return st != null && st.getBlock() instanceof LeverBlock;
    }

    // ---- entities

    public static UUID spawnBat(SecretSite site) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return null;
        Bat bat = EntityType.BAT.create(level, EntitySpawnReason.EVENT);
        if (bat == null) return null;
        bat.setPos(site.x + 0.5, site.y + 0.5, site.z + 0.5);
        bat.setSilent(true);
        bat.setPersistenceRequired();
        BlockPos above = new BlockPos(site.x, site.y + 1, site.z);
        bat.setResting(level.getBlockState(above).isRedstoneConductor(level, above));
        AttributeInstance max = bat.getAttribute(Attributes.MAX_HEALTH);
        if (max != null) max.setBaseValue(BAT_HEALTH);
        bat.setHealth(BAT_HEALTH);
        bat.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, true));
        Sim.spawn(bat);
        return bat.getUUID();
    }

    public static final float BAT_HEALTH = 100f;

    public static void killBat(Bat bat) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return;
        if (bat.isAlive()) bat.kill(level);
    }

    public static UUID spawnItem(SecretSite site, LootItem item) {
        ServerLevel level = RunBlocks.level();
        if (level == null) return null;
        ItemEntity e = new ItemEntity(level, site.x + 0.5, site.y + 0.25, site.z + 0.5, stack(item, item.floorCount));
        e.setDeltaMovement(0.0, 0.0, 0.0);
        e.setUnlimitedLifetime();
        e.setPickUpDelay(32767); // vanilla never picks it up; the sim does, inside its own box
        Sim.spawn(e);
        return e.getUUID();
    }

    public static boolean pickUp(ItemEntity e, ServerPlayer p) {
        e.setNoPickUpDelay();
        e.playerTouch(p);
        return e.isRemoved() || e.getItem().isEmpty();
    }

    public static void takeBy(ItemEntity e, Entity collector) {
        if (collector != null) {
            ClientboundTakeItemEntityPacket pkt = new ClientboundTakeItemEntityPacket(e.getId(), collector.getId(), e.getItem().getCount());
            for (ServerPlayer p : Sim.players()) p.connection.send(pkt);
        }
        e.discard();
    }

    public static Entity entity(UUID id) {
        ServerLevel level = RunBlocks.level();
        return level != null ? level.getEntity(id) : null;
    }

    // ---- items

    private static final class Look {
        final Item base;
        final String model;
        final boolean glint;
        final boolean unbreakable;
        final String skinRole;

        Look(Item base, String model, boolean glint, boolean unbreakable, String skinRole) {
            this.base = base;
            this.model = model;
            this.glint = glint;
            this.unbreakable = unbreakable;
            this.skinRole = skinRole;
        }

        Look(Item base, String model) { this(base, model, false, false, null); }
    }

    private static Look look(LootItem item) {
        return switch (item) {
            case SUPERBOOM -> new Look(Items.PAPER, "uncategorized/superboom_tnt");
            case REVIVE_STONE -> new Look(Items.PLAYER_HEAD, null, false, false, REVIVE_ROLE);
            case SPIRIT_LEAP -> new Look(Items.ENDER_PEARL, "island_relevant/dungeons/spirit_leap", true, false, null);
            case HEALING_POTION -> new Look(Items.SPLASH_POTION, null);
            case DECOY -> new Look(Items.POLAR_BEAR_SPAWN_EGG, "uncategorized/decoy");
            case INFLATABLE_JERRY -> new Look(Items.POLAR_BEAR_SPAWN_EGG, "uncategorized/inflatable_jerry");
            case TRAINING_WEIGHTS -> new Look(Items.PLAYER_HEAD, null, false, false, WEIGHTS_ROLE);
            case TRAP -> new Look(Items.HEAVY_WEIGHTED_PRESSURE_PLATE, "uncategorized/trap");
            case DEFUSE_KIT -> new Look(Items.SHEARS, null, true, true, null);
            case CANDYCOMB -> new Look(Items.PAPER, "island_relevant/foraging_2/salts/candycomb");
        };
    }

    public static ItemStack stack(LootItem item, int count) { return stack(item, count, List.of()); }

    public static ItemStack stack(LootItem item, int count, List<SkullTexture> textures) {
        if (item == LootItem.SUPERBOOM) return SimItems.SUPERBOOM().copyWithCount(count);
        Look l = look(item);
        ItemStack s = new ItemStack(l.base, count);
        ChatFormatting colour = ChatFormatting.getByCode(item.colour.charAt(1));
        s.set(DataComponents.CUSTOM_NAME, Component.literal(item.displayName).withStyle(st -> {
            Style it = st.withItalic(false);
            return colour != null ? it.withColor(colour) : it;
        }));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", item.id);
        tag.putBoolean("p3sim", true);
        if (item == LootItem.HEALING_POTION) { tag.putInt("splash", 1); tag.putInt("potion_level", 8); }
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (l.glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (l.unbreakable) s.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        if (l.skinRole != null) {
            HeadTextures.Resolved tex = HeadTextures.resolve(l.skinRole, textures);
            if (tex != null) {
                GameProfile profile = new GameProfile(tex.profileId, "", new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex.value))));
                s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
            }
        }
        if (item == LootItem.HEALING_POTION) {
            s.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.<Holder<Potion>>of(Potions.HEALING), Optional.empty(),
                    List.of(new MobEffectInstance(MobEffects.INSTANT_HEALTH, 20, 7)), Optional.empty()));
        }
        return SimItems.hy(s, l.model, item.style, l.unbreakable);
    }

    public static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack) && !stack.isEmpty()) Sim.spawn(new ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), stack));
    }
}
