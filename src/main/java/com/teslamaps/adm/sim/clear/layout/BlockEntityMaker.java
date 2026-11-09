package com.teslamaps.adm.sim.clear.layout;

// Ported from ADM's p3sim/clear/layout/BlockEntityMaker.kt

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.Ec;
import com.teslamaps.adm.sim.pack.rooms.RoomBlockEntity;
import com.teslamaps.adm.sim.pack.rooms.SkullTexture;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class BlockEntityMaker {
    private final List<ResolvableProfile> profiles;

    public BlockEntityMaker(List<SkullTexture> textures) {
        List<ResolvableProfile> list = new ArrayList<>(textures.size());
        for (SkullTexture t : textures) list.add(profileOf(t));
        this.profiles = list;
    }

    private static ResolvableProfile profileOf(SkullTexture t) {
        if (t.value == null) return null;
        try {
            // Odin matches the profile id, so the pack's one is kept
            UUID id = null;
            if (t.profileId != null) {
                try {
                    id = UUID.fromString(t.profileId);
                } catch (Throwable ignored) {
                    id = null;
                }
            }
            if (id == null) id = UUID.nameUUIDFromBytes(("ads-skull:" + t.id).getBytes(StandardCharsets.UTF_8));
            // a name the network codec accepts
            String name = t.id.length() > 12 ? t.id.substring(0, 12) : t.id;
            if (name.isEmpty()) name = "ads";
            PropertyMap map = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", t.value)));
            return ResolvableProfile.createResolved(new GameProfile(id, name, map));
        } catch (Exception e) {
            Ec.LOG.warn("[ads] skull texture " + t.id + " unusable: " + e.getMessage());
            return null;
        }
    }

    public boolean wants(BlockState state) { return state.getBlock() instanceof EntityBlock && !NEVER.contains(state.getBlock()); }

    public boolean forbids(BlockState state) { return state.getBlock() instanceof EntityBlock && NEVER.contains(state.getBlock()); }

    public BlockEntity create(BlockPos pos, BlockState state, RoomBlockEntity record) {
        if (!(state.getBlock() instanceof EntityBlock block)) return null;
        BlockEntity be = block.newBlockEntity(pos, state);
        if (be == null) return null;
        apply(be, record);
        return be;
    }

    public void apply(BlockEntity be, RoomBlockEntity record) {
        if (record == null) return;
        try {
            if (be instanceof SkullBlockEntity skull) {
                Integer tex = record.tex;
                ResolvableProfile profile = tex != null && tex >= 0 && tex < profiles.size() ? profiles.get(tex) : null;
                if (profile != null) {
                    // components set directly, no ItemStack
                    skull.applyComponents(DataComponentMap.builder().set(DataComponents.PROFILE, profile).build(), DataComponentPatch.EMPTY);
                }
            } else if (be instanceof SignBlockEntity sign) {
                List<String> lines = record.text;
                if (lines != null) {
                    Component[] l = new Component[4];
                    for (int i = 0; i < 4; i++) {
                        String s = i < lines.size() ? lines.get(i) : null;
                        l[i] = Component.literal(s != null ? s : "");
                    }
                    try {
                        sign.setText(new SignText(java.util.List.of(l), java.util.List.of(l), DyeColor.BLACK, false), net.minecraft.world.level.block.entity.SignTextSlot.FRONT);
                    } catch (NullPointerException e) {
                        // a sign on a chunk still being generated has no level yet
                        if (sign.getLevel() != null) throw e;
                    }
                }
            }
        } catch (Exception e) {
            Ec.LOG.warn("[ads] block entity at " + be.getBlockPos() + " unusable: " + e.getMessage());
        }
    }

    private static final Set<Block> NEVER = Set.of(
            Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK,
            Blocks.REPEATING_COMMAND_BLOCK, Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW, Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER
    );
}
