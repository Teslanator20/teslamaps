package com.teslamaps.features;

// After SkyOcean's features/misc/fun/animal/PlayerAnimals.kt: players drawn as another mob
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

public final class PlayerModels {

    public static final String[] TYPES = {
            "Cat", "Fox", "Wolf", "Axolotl", "Ocelot", "Rabbit", "Chicken", "Pig", "Cow", "Sheep", "Goat", "Frog",
            "Parrot", "Bee", "Allay", "Armadillo", "Panda", "Polar Bear", "Sniffer", "Turtle", "Villager", "Witch",
            "Piglin", "Zombie", "Skeleton", "Creeper", "Enderman", "Blaze", "Slime", "Iron Golem", "Snow Golem", "Copper Golem"};
    private static final EntityType<?>[] ENTITY_TYPES = {
            EntityType.CAT, EntityType.FOX, EntityType.WOLF, EntityType.AXOLOTL, EntityType.OCELOT, EntityType.RABBIT,
            EntityType.CHICKEN, EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.GOAT, EntityType.FROG,
            EntityType.PARROT, EntityType.BEE, EntityType.ALLAY, EntityType.ARMADILLO, EntityType.PANDA, EntityType.POLAR_BEAR,
            EntityType.SNIFFER, EntityType.TURTLE, EntityType.VILLAGER, EntityType.WITCH, EntityType.PIGLIN, EntityType.ZOMBIE,
            EntityType.SKELETON, EntityType.CREEPER, EntityType.ENDERMAN, EntityType.BLAZE, EntityType.SLIME,
            EntityType.IRON_GOLEM, EntityType.SNOW_GOLEM, EntityType.COPPER_GOLEM};

    private static final RenderStateDataKey<EntityRenderState> KEY = RenderStateDataKey.create(() -> "teslamaps:player_model");
    private static final Map<EntityType<?>, Entity> fakes = new HashMap<>();
    private static Object lastLevel;

    private PlayerModels() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PlayerModel").section("Render").when(() -> cfg().playerModel)
                .tick(PlayerModels::tick)
                .config("Render", b -> {
                    TeslaMapsConfig c = cfg();
                    b.toggle("Player Model", () -> c.playerModel, v -> c.playerModel = v)
                            .desc("Draws players as a mob of your choice. Client-side only.");
                    b.dropdown("  Players", PlayerCosmetics.TARGETS, () -> c.playerModelTarget, v -> c.playerModelTarget = v)
                            .desc("Real Players skips Hypixel NPCs.");
                    b.dropdown("  Mob", TYPES, () -> c.playerModelType, v -> c.playerModelType = v);
                    b.toggle("  Baby", () -> c.playerModelBaby, v -> c.playerModelBaby = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            fakes.clear();
        }
    }

    private static EntityType<?> type(String name) {
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(name)) return ENTITY_TYPES[i];
        return EntityType.CAT;
    }

    private static Entity fake(EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level != lastLevel) return null;
        if (fakes.containsKey(type)) return fakes.get(type);
        Entity e = null;
        try {
            e = type.create(mc.level, EntitySpawnReason.LOAD);
            if (e != null) e.setId(-2_000_000 - fakes.size());
        } catch (Throwable t) {
            e = null;
        }
        fakes.put(type, e);
        return e;
    }

    /** From PlayerCosmeticsMixin at the end of the avatar extraction. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void extract(Avatar avatar, AvatarRenderState state, float partialTick) {
        FabricRenderState fs = (FabricRenderState) state;
        if (fs.getData(KEY) != null) fs.setData(KEY, null);
        TeslaMapsConfig c = cfg();
        if (!c.playerModel || !c.section("Render") || !PlayerCosmetics.matches(avatar.getId(), c.playerModelTarget)) return;
        Entity fake = fake(type(c.playerModelType));
        if (fake == null) return;
        fake.setPos(avatar.getX(), avatar.getY(), avatar.getZ());
        fake.xo = avatar.xo;
        fake.yo = avatar.yo;
        fake.zo = avatar.zo;
        fake.setYRot(avatar.getYRot());
        fake.yRotO = avatar.yRotO;
        fake.setXRot(avatar.getXRot());
        fake.xRotO = avatar.xRotO;
        fake.tickCount = avatar.tickCount;
        fake.setOnGround(avatar.onGround());
        if (fake instanceof LivingEntity le) {
            le.yBodyRot = avatar.yBodyRot;
            le.yBodyRotO = avatar.yBodyRotO;
            le.yHeadRot = avatar.yHeadRot;
            le.yHeadRotO = avatar.yHeadRotO;
        }
        if (fake instanceof AgeableMob a && a.isBaby() != c.playerModelBaby) a.setBaby(c.playerModelBaby);
        EntityRenderState s;
        try {
            EntityRenderer r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(fake);
            s = r.createRenderState(fake, partialTick);
        } catch (Throwable t) {
            return;
        }
        if (s instanceof LivingEntityRenderState ls) {
            ls.walkAnimationPos = state.walkAnimationPos;
            ls.walkAnimationSpeed = state.walkAnimationSpeed;
            ls.bodyRot = state.bodyRot;
            ls.yRot = state.yRot;
            ls.xRot = state.xRot;
            ls.isBaby = c.playerModelBaby;
        }
        fs.setData(KEY, s);
    }

    /** From PlayerModelSubmitMixin: the mob state replaces the player's. */
    public static EntityRenderState swap(EntityRenderState state) {
        if (!(state instanceof AvatarRenderState)) return state;
        EntityRenderState s = ((FabricRenderState) state).getData(KEY);
        if (s == null) return state;
        s.x = state.x;
        s.y = state.y;
        s.z = state.z;
        s.lightCoords = state.lightCoords;
        s.outlineColor = state.outlineColor;
        s.nameTag = state.nameTag;
        s.scoreText = state.scoreText;
        s.distanceToCameraSq = state.distanceToCameraSq;
        s.isInvisible = state.isInvisible;
        s.isDiscrete = state.isDiscrete;
        s.displayFireAnimation = state.displayFireAnimation;
        return s;
    }
}
