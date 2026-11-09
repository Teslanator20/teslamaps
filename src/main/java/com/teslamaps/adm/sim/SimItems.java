// Ported from ADM's p3sim/SimItems.kt
package com.teslamaps.adm.sim;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.teslamaps.adm.sim.clear.damage.ClearItems;
import com.teslamaps.adm.sim.clear.damage.DamageModule;
import com.teslamaps.adm.sim.clear.damage.ImplosionRules;
import com.teslamaps.adm.sim.clear.damage.SimPearl;
import com.teslamaps.adm.sim.run.Run;
import com.teslamaps.adm.sim.run.api.LeapTarget;
import com.teslamaps.adm.sim.run.api.Services;
import com.teslamaps.adm.sim.util.KRandom;
import com.teslamaps.adm.sim.util.Pair;
import com.teslamaps.adm.sim.util.Triple;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class SimItems {
    private SimItems() {}

    // ------------------------------------------------------------------ the items

    private static ItemStack item(Item base, String id, String name) { return item(base, id, name, List.of(), false, t -> {}); }

    private static ItemStack item(Item base, String id, String name, List<String> lore) { return item(base, id, name, lore, false, t -> {}); }

    private static ItemStack item(Item base, String id, String name, List<String> lore, boolean glint) { return item(base, id, name, lore, glint, t -> {}); }

    private static ItemStack item(Item base, String id, String name, List<String> lore, boolean glint, Consumer<CompoundTag> extra) {
        ItemStack s = new ItemStack(base);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(it -> it.withItalic(false)));
        if (!lore.isEmpty()) s.set(DataComponents.LORE, new ItemLore(plainLore(lore)));
        if (glint) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        s.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putBoolean("p3sim", true);
        extra.accept(tag);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    private static List<Component> plainLore(List<String> lore) {
        List<Component> out = new ArrayList<>();
        for (String l : lore) out.add(Component.literal(l).withStyle(it -> it.withItalic(false)));
        return out;
    }

    public static final class Lore {
        private Lore() {}

        private static final Pattern GLYPH = Pattern.compile("\\{U([0-9A-F]{4})\\}");

        public static String u(String t) {
            return GLYPH.matcher(t).replaceAll(m -> java.util.regex.Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }

        private static List<String> lines(String raw) {
            int a = 0, b = raw.length();
            while (a < b && raw.charAt(a) == '\n') a++;
            while (b > a && raw.charAt(b - 1) == '\n') b--;
            List<String> out = new ArrayList<>();
            for (String l : raw.substring(a, b).split("\n", -1)) out.add(u(l));
            return out;
        }

        public static final List<String> HIDDEN = List.of("minecraft:jukebox_playable", "minecraft:painting/variant", "minecraft:map_id", "minecraft:fireworks", "minecraft:attribute_modifiers", "minecraft:unbreakable",
            "minecraft:written_book_content", "minecraft:banner_patterns", "minecraft:trim", "minecraft:potion_contents", "minecraft:dyed_color", "minecraft:charged_projectiles");
        public static final List<String> HIDDEN_BREAKER = List.of("minecraft:painting/variant", "minecraft:fireworks", "minecraft:attribute_modifiers", "minecraft:enchantments", "minecraft:stored_enchantments", "minecraft:trim",
            "minecraft:charged_projectiles", "minecraft:jukebox_playable", "minecraft:map_id", "minecraft:unbreakable", "minecraft:written_book_content", "minecraft:banner_patterns", "minecraft:potion_contents", "minecraft:dyed_color");
        public static final String SUPERBOOM_TNT_NAME = u("§9Superboom TNT");
        public static final List<String> SUPERBOOM_TNT = lines("""
§7Blows up cracked brick walls and
§7crypts, which are typically found in
§cDungeons §7and the §5Crystal Hollows§7.

§9§lRARE
""");
        public static final String HYPERION_NAME = u("§dHeroic Hyperion §6✪✪✪✪✪");
        public static final List<String> HYPERION = lines("""
§7Gear Score: §d5000
§7Damage: §c+2,195.2 §e(+30)
§7Strength: §c+1,472 §e(+30) §9(+50)
§7Crit Damage: §9+448%
§7Attack Speed: §e+10.85% §9(+7%)
§7Ferocity: §c+46.5
§7Intelligence: §b+4,064 §9(+125) §d(+60)
§7Gemstones: §6[§b{UE003}§6] §6[§b⚔§6]

§d§lSwarm V, §9Bane of Arthropods VI, §9Champion X
§9Cleave V, §9Critical VI, §9Cubism V
§9Ender Slayer VI, §9Experience IV, §9Fire Aspect III
§9First Strike IV, §9Giant Killer VII, §9Impaling III
§9Lethality VI, §9Life Steal V, §9Looting IV
§9Luck VI, §9Prosecute VI, §9Scavenger V
§9Smite VII, §9Tabasco III, §9Thunderlord VI
§9Vampirism VI, §9Venomous V

§7Deals §c+50% §7damage to §8{UE085} Wither §7mobs.
§7Grants §c+1 §c{UE050} Damage §7and §a+2 §b{UE003}
§bIntelligence §7per §cCatacombs §7level.

§aScroll Abilities:
§b§l⦾ §6Ability: Wither Impact  §e§lRIGHT CLICK
§7Teleport §a10 blocks§7 ahead of you
§7dealing §c1,798,815.7 §7damage to nearby
§7enemies. Also reduces your damage
§7taken and grants an absorption
§7shield for §e5 seconds§7.
§8Mana Cost: §b270{UE003}

§fKills: §61,304,218

§d§la §d§lMYTHIC DUNGEON SWORD §d§la
""");
        public static final String TERMINATOR_NAME = u("§dPrecise Terminator §6✪✪✪✪✪§c➎");
        public static final List<String> TERMINATOR = lines("""
§7Gear Score: §d4947
§7Damage: §c+2,195.2 §e(+30)
§7Strength: §c+761.6 §e(+30) §6[+5] §9(+34)
§7Crit Chance: §9+46.5% §9(+15%)
§7Crit Damage: §9+2,080% §9(+70%)
§7Attack Speed: §e+62%
§7Shot Cooldown: §a0.5s

§d§lSoul Eater V, §9Chance V, §9Cubism V
§9Flame II, §9Gravity VI, §9Impaling V
§9Infinite Quiver X, §9Overload V, §9Piercing I
§9Power VII, §9Snipe IV, §9Tabasco III
§9Toxophilite X

§7Shoots §b3 §7arrows at once.
§7Can damage endermen.

§cDivides your §9{UE02C} Crit Chance §cby 4!

§6Ability: Salvation  §e§lLEFT CLICK
§7Can be cast after landing §63 §7hits.
§7Shoot a beam, penetrating up to §e5
§7enemies.
§7The beam always crits.
§8Soulflow Cost: §31⸎

§dShortbow: Instantly shoots!

§fKills: §6157,045

§9Precise Bonus
§7Deal §a+10% §7extra damage when
§7arrows hit the head of a mob.

§8§l* §8Co-op Soulbound §8§l*
§d§la §d§lMYTHIC DUNGEON BOW §d§la
""");
        public static final String WITHER_CLOAK_NAME = u("§6Odd Wither Cloak Sword");
        public static final List<String> WITHER_CLOAK = lines("""
§7Gear Score: §d4964
§7Defense: §a+1,475
§7Damage: §c+1,121
§7Strength: §c+796.5
§7Crit Chance: §9+26.25% §9(+25%)
§7Crit Damage: §9+177% §9(+30%)
§7Intelligence: §b-295 §9(-50)

§c§l⦾ §6Ability: Creeper Veil  §e§lRIGHT CLICK
§7Spawns a layered veil that negates
§7damage for §a10s§7, as follows:

§8➤ §7Lose a layer on hit, consuming
§430§4{UE028} Vitality §7and block up to §c5,850
§7damage.
§8➤ §7The damage blocked scales with
§7your §5Dungeon Stat Boost §7while in §cThe
§cCatacombs§7.
§8➤ §7When the damage exceeds a
§7layer's limit, another layer is
§7consumed, until all damage is
§7accounted for.
§8➤ §7When you run out of §4{UE028} Vitality§7,
§7consume all §4{UE028} Vitality §7and take slight
§7knockback.
§8➤ §7You cannot attack or regenerate
§4{UE028} Vitality §7while the veil is up.

§e§lRIGHT CLICK §7while active to
§7deactivate. Cooldown is halved on
§7deactivation.
§8Vitality Cost: §430{UE028}
§8Cooldown: §a10s

§cThis item can be reclaimed! Use
§e/reclaim §cto take back what's
§crightfully yours!
§6§la §6§lLEGENDARY DUNGEON SWORD §6§la
""");
        public static final String ASPECT_OF_THE_VOID_NAME = u("§6Heroic Aspect of the Void");
        public static final List<String> ASPECT_OF_THE_VOID = lines("""
§7Damage: §c+120
§7Strength: §c+140 §9(+40)
§7Attack Speed: §e+5% §9(+5%)
§7Intelligence: §b+100 §9(+100)
§7Gemstones: §8[§7{UE003}§8]

§d§lUltimate Wise V
§7Reduces the ability mana cost of this
§7item by §a50%§7.

§b§l⦾ §6Ability: Instant Transmission  §e§lRIGHT CLICK
§7Teleport §a12 blocks§7 ahead of you and
§7gain §a+50 §f{UE022} Speed§7 for §a3 seconds§7.
§8Mana Cost: §b18{UE003}

§6Ability: Ether Transmission  §e§lSNEAK RIGHT CLICK
§7Teleport to your targeted block up
§7to §a61 blocks §7away.
§8Soulflow Cost: §31
§8Mana Cost: §b73{UE003}

§6§la §6§lLEGENDARY SWORD §6§la
""");
        public static final String JERRY_STAFF_NAME = u("§6Heroic Jerry-chine Gun §6✪✪✪✪✪");
        public static final List<String> JERRY_STAFF = lines("""
§7Damage: §c+88
§7Strength: §c+40 §9(+40)
§7Attack Speed: §e+5% §9(+5%)
§7Intelligence: §b+320 §9(+100)

§d§lUltimate Wise V
§7Reduces the ability mana cost of this
§7item by §a50%§7.

§c§l⦾ §6Ability: Rapid-fire  §e§lRIGHT CLICK
§7Shoots a Jerry bullet, dealing
§c1,347.2 §7damage on impact and
§7knocking you back.

§7Each shot costs §3+14 mana §7more than
§7the previous, resetting after §a4s §7of
§7not firing.

§8§l* §8Co-op Soulbound §8§l*
§6§la §6§lLEGENDARY SWORD §6§la
""");
        public static final String MOSQUITO_BOW_NAME = u("§6Mosquito Shortbow");
        public static final List<String> MOSQUITO_BOW = lines("""
§7Damage: §c+319
§7Strength: §c+151
§7Crit Damage: §9+39%
§7Vitality: §4+20
§7Shot Cooldown: §a0.5s

§d§lDuplex I
§7Shoot an extra arrow dealing §c4% §7of the
§7first arrow's damage.
§7Targets hit take §c1.1x §7fire damage
§7for §a60s§7.
§9Flame II
§7Arrows ignite your enemies for §a4s§7,
§7dealing §a6% §7of your damage per
§7second.

§6Ability: Eggsecute\s
§7Arrows fired from this weapon deal
§c3x §7damage to §aEgg Sacs §7spawned by
§7the §cTarantula Broodfather§7!

§6Ability: Nasty Bite  §e§lLEFT CLICK
§7Shoot an enhanced shot, healing you
§7for §c189❤ Health §7on hit.
§8Vitality Cost: §410{UE028}

§6Shortbow: Instantly shoots!

§8This item can be reforged!
§6§lLEGENDARY BOW
""");
        public static final String ENDER_PEARL_NAME = u("§fEnder Pearl");
        public static final List<String> ENDER_PEARL = lines("""
§8Collection Item

§f§lCOMMON
""");
        public static final String SKYBLOCK_MENU_NAME = u("§aSkyBlock Menu §7(Click)");
        public static final List<String> SKYBLOCK_MENU = lines("""
§7View all of your SkyBlock progress,
§7including your Skills, Collections,
§7Recipes, and more!

§eClick to open!
""");
        public static final String INFINITE_SPIRIT_LEAP_NAME = u("§5Infinileap");
        public static final List<String> INFINITE_SPIRIT_LEAP = lines("""
§c§l⦾ §6Ability: Spirit Leap  §e§lRIGHT CLICK
§7Allows you to teleport to any teammate! Grants
§a1 §7second of immunity after teleporting,
§7immunity is cancelled upon dealing damage.
§8Cooldown: §a2s

§cDungeons only!

§5§lEPIC DUNGEON ITEM
""");
        public static final String FISHING_ROD_NAME = u("§fFishing Rod");
        public static final List<String> FISHING_ROD = lines("""
§7Damage: §c+15
§7Strength: §c+15
§7Fishing Speed: §b+5
§7Sea Creature Chance: §3+1%

§9ථ Hook §8§lNONE
§9ꨃ Line §8§lNONE
§9࿉ Sinker §8§lNONE

§7Talk to §2Roddy §7in the §2Backwater
§2Bayou §7to apply parts to this rod.

§8This item can be reforged!
§f§lCOMMON FISHING ROD
""");
        public static final String STARRED_SPIRIT_MASK_NAME = u("§d{UE068} Necrotic Spirit Mask §6✪✪✪✪✪");
        public static final List<String> STARRED_SPIRIT_MASK = lines("""
§7Gear Score: §d2569
§7Health: §c+857.6
§7Defense: §a+480
§7Intelligence: §b+1,440 §9(+200)
§7Health Regen: §c+15.75
§7Speed: §f+160
§7Respiration: §3+384

§d§lWisdom V, §9Hecatomb VIII, §9Respiration IV
§9Respite V, §9Transylvanian V, §9Vampiric Vitality X

§6Ability: Second Wind\s
§7Instead of dying, gain §f+50{UE022} Speed
§7and damage immunity for §a3 §7seconds.
§7Also heals you for §a10% §7of your §c❤
§cHealth §7over §a5 §7seconds.
§8Cooldown: §a30s

§d§la §d§lMYTHIC DUNGEON HELMET §d§la
""");
        public static final String STARRED_BONZO_MASK_NAME = u("§5{UE068} Sunny Bonzo's Mask §6✪✪✪✪✪");
        public static final List<String> STARRED_BONZO_MASK = lines("""
§7Gear Score: §d2730
§7Health: §c+960
§7Defense: §a+640
§7Intelligence: §b+960
§7Health Regen: §c+15.75
§7Speed: §f+38.4 §9(+6)
§7Farming Wisdom: §3+6.2 §9(+4)

§d§lWisdom V, §9Hecatomb V, §9Respite V
§9Transylvanian V, §9Vampiric Vitality X

§6Ability: Clownin' Around\s
§7Instead of dying, gain damage
§7immunity and §a+40 §c{UE00D} Strength §7for §a3s
§7and fully replenish your health. The
§7cooldown and Strength bonus you
§7receive improve based on your
§aDungeoneering Skill §7level. This ability
§7only works while in §cThe Catacombs§7!
§8Cooldown: §a180s

§5§la §5§lEPIC DUNGEON HELMET §5§la
""");
        public static final String TERROR_CHESTPLATE_NAME = u("§6Spiked Terror Chestplate");
        public static final List<String> TERROR_CHESTPLATE = lines("""
§7Health: §c+238 §9(+8)
§7Defense: §a+73 §9(+8)
§7Strength: §c+10 §9(+10)
§7Crit Chance: §9+10% §9(+10%)
§7Crit Damage: §9+60% §9(+10%)
§7Attack Speed: §e+14% §9(+14%)
§7Intelligence: §b+15 §9(+10)
§7Speed: §f+13 §9(+1)
§7Gemstones: §8[§8⚔§8] §8[§8⚔§8]

§7Mana Regeneration II§7, §7Vitality II

§6Tiered Bonus: Hydra Strike ({T}/4)
§7Every §a0.2s§7, arrow attacks grant §c1§7
§7stack of §6⁑ Hydra Strike§7. §8Lose 1 stack
§8after {SEC}s of not gaining a stack.
§7
§7Each stack grants §c+{DMG}% Damage§7 and §b+1%
§bArrow Speed§7.
§7
§7At §c10§7 stacks shoot §a+2§7 arrows that deal §c20%
§cArrow Damage§8.

§6§lLEGENDARY CHESTPLATE
""");
        public static final String TERROR_LEGGINGS_NAME = u("§6Spiked Terror Leggings");
        public static final List<String> TERROR_LEGGINGS = lines("""
§7Health: §c+213 §9(+8)
§7Defense: §a+63 §9(+8)
§7Strength: §c+10 §9(+10)
§7Crit Chance: §9+10% §9(+10%)
§7Crit Damage: §9+60% §9(+10%)
§7Attack Speed: §e+14% §9(+14%)
§7Intelligence: §b+15 §9(+10)
§7Speed: §f+13 §9(+1)
§7Gemstones: §8[§8⚔§8] §8[§8⚔§8]

§7Breeze II§7, §7Speed I

§6Tiered Bonus: Hydra Strike ({T}/4)
§7Every §a0.2s§7, arrow attacks grant §c1§7
§7stack of §6⁑ Hydra Strike§7. §8Lose 1 stack
§8after {SEC}s of not gaining a stack.
§7
§7Each stack grants §c+{DMG}% Damage§7 and §b+1%
§bArrow Speed§7.
§7
§7At §c10§7 stacks shoot §a+2§7 arrows that deal §c20%
§cArrow Damage§8.

§6§lLEGENDARY LEGGINGS
""");
        public static final String TERROR_BOOTS_NAME = u("§6Spiked Terror Boots");
        public static final List<String> TERROR_BOOTS = lines("""
§7Health: §c+138 §9(+8)
§7Defense: §a+48 §9(+8)
§7Strength: §c+10 §9(+10)
§7Crit Chance: §9+10% §9(+10%)
§7Crit Damage: §9+60% §9(+10%)
§7Attack Speed: §e+14% §9(+14%)
§7Intelligence: §b+15 §9(+10)
§7Speed: §f+13 §9(+1)
§7Gemstones: §8[§8⚔§8] §8[§8⚔§8]

§7Blazing Resistance I§7, §7Experience I

§6Tiered Bonus: Hydra Strike ({T}/4)
§7Every §a0.2s§7, arrow attacks grant §c1§7
§7stack of §6⁑ Hydra Strike§7. §8Lose 1 stack
§8after {SEC}s of not gaining a stack.
§7
§7Each stack grants §c+{DMG}% Damage§7 and §b+1%
§bArrow Speed§7.
§7
§7At §c10§7 stacks shoot §a+2§7 arrows that deal §c20%
§cArrow Damage§8.

§6§lLEGENDARY BOOTS
""");
        public static final String SPEED_WITHER_BOOTS_NAME = u("§dAncient Maxor's Boots §6✪✪✪✪✪§c➎");
        public static final List<String> SPEED_WITHER_BOOTS = lines("""
§7Gear Score: §d4935
§7Health: §c+2,188.8 §e(+60) §c[+40] §9(+7)
§7Defense: §a+812.8 §e(+30) §9(+7)
§7Strength: §c+428.8 §9(+35) §d(+32)
§7Crit Chance: §9+23.25% §9(+15%)
§7Crit Damage: §9+608% §9(+50%)
§7Intelligence: §b+224 §9(+25)
§7Health Regen: §c+15.75
§7Speed: §f+230.4
§7Gemstones: §6[§d⚔§6] §6[§d⚔§6]

§d§lLegion V, §9Depth Strider III, §9Feather Falling X
§9Growth VI, §9Protection VI, §9Respite V
§9Sugar Rush III, §9Vivacious Vitality VIII

§7Reduces the damage you take from
§7withers by §c10% §7and increases your
§7arrow damage by §c5%§7.

§6Full Set Bonus: Witherborn §7(0/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§9Ancient Bonus
§7Grants §a+1 §9{UE007} Crit Damage §7per
§cCatacombs §7level.

§d§la §d§lMYTHIC DUNGEON BOOTS §d§la
""");
        public static final String SPEED_WITHER_LEGGINGS_NAME = u("§dNecrotic Maxor's Leggings §6✪✪✪✪✪");
        public static final List<String> SPEED_WITHER_LEGGINGS = lines("""
§7Gear Score: §d4015
§7Health: §c+1,472
§7Defense: §a+672
§7Crit Damage: §9+288%
§7Intelligence: §b+1,888 §9(+200) §d(+60)
§7Health Regen: §c+15.75
§7Speed: §f+192
§7Gemstones: §6[§b⚔§6] §6[§b⚔§6]

§d§lWisdom V, §9Respite V, §9Smarty Pants V
§9Vampiric Vitality V

§7Reduces the damage you take from
§7withers by §c10% §7and increases your
§7arrow damage by §c5%§7.

§6Full Set Bonus: Witherborn §7(0/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§d§la §d§lMYTHIC DUNGEON LEGGINGS §d§la
""");
        public static final String MITHRIL_COAT_NAME = u("§6Necrotic Mithril Coat");
        public static final List<String> MITHRIL_COAT = lines("""
§7Defense: §a+125
§7Intelligence: §b+150 §9(+150)
§7Speed: §f+15

§6Ability: Mithril's Protection\s
§7Any damage taken is max §a40% §7of the wearer's
§c❤ Health§7. Gain §cRegeneration §7when this ability
§7activates.

§6§la §6§lLEGENDARY CHESTPLATE §6§la
""");
        public static final String WISE_WITHER_BOOTS_NAME = u("§dNecrotic Storm's Boots §6✪✪✪✪✪");
        public static final List<String> WISE_WITHER_BOOTS = lines("""
§7Gear Score: §d4175
§7Health: §c+1,408
§7Defense: §a+544
§7Intelligence: §b+3,264 §9(+200) §d(+60)
§7Health Regen: §c+15.75
§7Speed: §f+38.4
§7Gemstones: §6[§b{UE003}§6] §6[§b⚔§6]

§d§lWisdom V, §9Depth Strider III, §9Feather Falling X
§9Growth V, §9Protection V, §9Respite V
§9Sugar Rush III, §9Vampiric Vitality V

§7Reduces the damage you take from
§7withers by §c10%§7.

§6Full Set Bonus: Witherborn §7(3/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§d§la §d§lMYTHIC DUNGEON BOOTS §d§la
""");
        public static final String WISE_WITHER_LEGGINGS_NAME = u("§dNecrotic Storm's Leggings §6✪✪✪✪✪");
        public static final List<String> WISE_WITHER_LEGGINGS = lines("""
§7Gear Score: §d5000
§7Health: §c+2,336 §e(+60)
§7Defense: §a+992 §e(+30)
§7Intelligence: §b+3,424 §9(+200) §d(+60)
§7Health Regen: §c+15.75
§7Gemstones: §6[§b{UE003}§6] §6[§b⚔§6]

§d§lWisdom V, §9Growth V, §9Protection V
§9Respite V, §9Smarty Pants V

§7Reduces the damage you take from
§7withers by §c10%§7.

§6Full Set Bonus: Witherborn §7(3/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§d§la §d§lMYTHIC DUNGEON LEGGINGS §d§la
""");
        public static final String WISE_WITHER_CHESTPLATE_NAME = u("§dLoving Storm's Chestplate §6✪✪✪✪✪");
        public static final List<String> WISE_WITHER_CHESTPLATE = lines("""
§7Gear Score: §d5000
§7Health: §c+2,617.6 §e(+60) §9(+14)
§7Defense: §a+1,177.6 §e(+30) §9(+14)
§7True Defense: §f+15.5
§7Intelligence: §b+2,816 §9(+120) §d(+60)
§7Health Regen: §c+15.75
§7Gemstones: §6[§b{UE003}§6] §6[§b⚔§6]

§d§lWisdom V, §9Growth V, §9Protection V
§9Reflection V, §9Respite V, §9True Protection I

§7Reduces the damage you take from
§7withers by §c10%§7.

§6Full Set Bonus: Witherborn §7(3/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§9Loving Bonus
§7Increases ability damage by §a5%§7.

§d§la §d§lMYTHIC DUNGEON CHESTPLATE §d§la
""");
        public static final String WISE_WITHER_HELMET_NAME = u("§dShiny Ancient Storm's Helmet §6✪✪✪✪✪§c➊");
        public static final List<String> WISE_WITHER_HELMET = lines("""
§7Gear Score: §d5000
§7Health: §c+1,932.8 §9(+7)
§7Defense: §a+684.8 §9(+7)
§7Strength: §c+224 §9(+35)
§7Crit Chance: §9+23.25% §9(+15%)
§7Crit Damage: §9+396.8% §9(+50%) §d(+12%)
§7Intelligence: §b+3,072 §9(+25) §d(+30)
§7Health Regen: §c+5.25
§7Gemstones: §6[§b{UE003}§6] §6[§8⚔§6]

§d§lLegion V, §9Big Brain V, §9Growth V
§9Hecatomb X, §9Protection V, §9Rejuvenate V
§9Strong Vitality V

§7Reduces the damage you take from
§7withers by §c10%§7.

§6Full Set Bonus: Witherborn §7(3/4)
§7Spawns a wither minion every §e30
§7seconds up to a maximum §a1 §7wither.
§7Your withers will travel to and
§7explode on nearby enemies.

§9Ancient Bonus
§7Grants §a+1 §9{UE007} Crit Damage §7per
§cCatacombs §7level.

§d§la §d§lSHINY MYTHIC DUNGEON HELMET §d§la
""");
        public static final String RACING_HELMET_NAME = u("§dRenowned Racing Helmet");
        public static final List<String> RACING_HELMET = lines("""
§7Health: §c+47 §9(+10)
§7Defense: §a+10 §9(+10)
§7Strength: §c+12 §9(+12)
§7Crit Chance: §9+12% §9(+12%)
§7Crit Damage: §9+12% §9(+12%)
§7Attack Speed: §e+15% §9(+15%)
§7Intelligence: §b+12 §9(+12)
§7Speed: §f+400 §9(+1)

§d§lWisdom V
§7Gain §b5 §7Intelligence for every §b5
§7levels of exp you have on you.
§7Capped at §b100 §7Intelligence.
§9Hecatomb IX §894
§7Gain §a+0.92% §cCatacombs §7XP & §a+1.84% §3Class §7XP,
§7doubled §7on §b§lS+ §7runs.
§7Grants §c+7.4{UE010} §7per 10 §cCatacombs §7levels.
§8100 S runs to tier up!

§7Grants §f+100{UE022} Speed Cap§7.

§8When horses are not fast enough,
§8use a Racing Helmet instead.

§7Purchased by: §6[MVP§9++§6] Player§f
§7Purchased for: §64,545,454,545 Coins

§8Auction #18
§8Bid #2
§8September 2025

§9Renowned Bonus
§7Increases all §cCombat §7stats and §b{UE01A}
§bMagic Find §7by §a+1%§7.

§d§la §d§lMYTHIC HELMET §d§la
""");
    }

    public static ItemStack hy(ItemStack s, String model, String style) { return hy(s, model, style, true, Lore.HIDDEN); }

    public static ItemStack hy(ItemStack s, String model, String style, boolean unbreakable) { return hy(s, model, style, unbreakable, Lore.HIDDEN); }

    /** Hypixel's item model, only when the game has it (Hypixel's pack loaded); else the vanilla look instead of a missing texture. */
    static void hyModel(ItemStack s, String path) {
        Identifier id = Identifier.parse("hypixel_skyblock:" + path);
        try {
            var rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
            if (rm.getResource(Identifier.fromNamespaceAndPath(id.getNamespace(), "items/" + id.getPath() + ".json")).isEmpty()) return;
        } catch (Throwable t) {
            return;
        }
        s.set(DataComponents.ITEM_MODEL, id);
    }

    public static ItemStack hy(ItemStack s, String model, String style, boolean unbreakable, List<String> hidden) {
        if (model != null) hyModel(s, "item/" + model);
        s.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:" + style));
        LinkedHashSet<DataComponentType<?>> types = new LinkedHashSet<>();
        for (String h : hidden) {
            DataComponentType<?> t = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(h));
            if (t != null) types.add(t);
        }
        s.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(false, types));
        if (!unbreakable) s.remove(DataComponents.UNBREAKABLE);
        return s;
    }

    public static String rankColour(String name) { int h = Math.floorMod(name.hashCode(), 100); return h < 63 ? "§a" : h < 96 ? "§b" : "§6"; }

    public static int etherPuff(Vec3 at) { return Sim.level().sendParticles(ParticleTypes.WITCH, at.x, at.y + 1.0, at.z, 25, 0.25, 1.0, 0.25, 0.0); }

    public static void gatePuffs(Vec3 centre, AABB box) {
        boolean alongX = box.getXsize() >= box.getZsize();
        int n = 8 + KRandom.DEFAULT.nextInt(8) + KRandom.DEFAULT.nextInt(8);
        for (int i = 0; i < n; i++) {
            int a = alongX ? KRandom.DEFAULT.nextInt((int) box.minX + 2, (int) box.maxX - 2) : KRandom.DEFAULT.nextInt((int) box.minZ + 2, (int) box.maxZ - 2);
            int b = KRandom.DEFAULT.nextInt(-1, 2);
            double x = alongX ? (double) a : Math.floor(centre.x) + b;
            double z = alongX ? Math.floor(centre.z) + b : (double) a;
            Vec3 c = new Vec3(x, Math.floor(centre.y - 3 + KRandom.DEFAULT.nextInt(0, 19)), z);
            Sim.level().sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 3, 1.0, 1.0, 1.0, 0.0);
            Sim.sound(SoundEvents.GENERIC_EXPLODE, 0.5f, 0.49206f, c, SoundSource.BLOCKS);
        }
    }

    public static ItemStack ghostStack(Item base, String id, String name) { return ghostStack(base, id, name, 1); }

    public static ItemStack ghostStack(Item base, String id, String name, int count) {
        ItemStack s;
        if (id != null) s = item(base, id, name);
        else {
            s = new ItemStack(base);
            s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false)));
        }
        s.remove(DataComponents.UNBREAKABLE);
        s.setCount(count);
        return s;
    }

    public static String idOf(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        if (d == null) return null;
        String id = d.copyTag().getStringOr("id", "");
        return id.isEmpty() ? null : id;
    }

    private static final String LEAP_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY1MjE0NjYxMjc0MiwKICAicHJvZmlsZUlkIiA6ICI5ZWU3NTUxOGQyZWE0Y2Q4OGJiNGI1YTZkNmVhNTFjYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJNaWNyb3MxMTgyIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzM3N2Q0YTIwNmQ3NzU3ZjQ3OWYzMzJlYzFhMmJiYmVlNTdjZWY5NzU2OGRkODhkZjgxZjQ4NjRhZWU3ZDNkOTgiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";

    public static ItemStack head(String tex, String name) {
        ItemStack s = new ItemStack(Items.PLAYER_HEAD);
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(tex.getBytes(StandardCharsets.UTF_8)), "p3sim", new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", tex))));
        s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(it -> it.withItalic(false)));
        return s;
    }

    public static ItemStack SUPERBOOM() {
        ItemStack it = item(Items.PAPER, "SUPERBOOM_TNT", Lore.SUPERBOOM_TNT_NAME, Lore.SUPERBOOM_TNT, true);
        it.setCount(64);
        hy(it, "uncategorized/superboom_tnt", "rare", false);
        return it;
    }

    public static ItemStack HYPERION() {
        ItemStack it = item(Items.IRON_SWORD, "HYPERION", Lore.HYPERION_NAME, Lore.HYPERION, true);
        hy(it, "uncategorized/hyperion", "mythic");
        return it;
    }

    public static ItemStack BONZO() {
        ItemStack it = item(Items.BLAZE_ROD, "STARRED_BONZO_STAFF", "§5 Heroic Bonzo's Staff §6✪✪✪✪✪", List.of(
            "§7Gear Score: §d845 §8(1,213)", "§7Damage: §c+250 §8(+1,350)", "§7Strength: §c+185 §8(+550)", "§7Intelligence: §a+300 §8(+700)", "",
            "§9Ferocity: §a+10", "", "§7§8This item can be reforged!", "",
            "§6Ability: Showtime  §e§lRIGHT CLICK", "§7Shoots balloons that create a large explosion", "§7on impact, dealing up to §c16,956.3 §7damage.", "§8Mana Cost: §341", "",
            "§5§lEPIC DUNGEON SWORD"), true);
        hyModel(it, "item/island_relevant/dungeons/bonzos_staff_fragged");
        it.set(DataComponents.TOOLTIP_STYLE, Identifier.parse("hypixel_skyblock:epic"));
        return it;
    }

    public static ItemStack SPIRIT_BOW() { return item(Items.BOW, "ITEM_SPIRIT_BOW", "§5Spirit Shortbow", List.of("§7Shortbow: instantly shoots!"), true); }

    public static ItemStack DUNGEONBREAKER() {
        ItemStack it = item(Items.DIAMOND_PICKAXE, "DUNGEONBREAKER", "§cDungeonbreaker", breakerLore(charges()), true);
        hy(it, null, "special", false, Lore.HIDDEN_BREAKER);
        it.set(DataComponents.TOOL, new Tool(List.of(), 1024f, 1, true));
        ServerLevel level = SimServer.level();
        if (level != null) {
            Registry<Enchantment> reg = level.registryAccess().lookup(Registries.ENCHANTMENT).orElse(null);
            if (reg != null) {
                ItemEnchantments.Mutable e = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                reg.get(Enchantments.EFFICIENCY).ifPresent(h -> e.set(h, 10));
                reg.get(Enchantments.UNBREAKING).ifPresent(h -> e.set(h, 10));
                it.set(DataComponents.ENCHANTMENTS, e.toImmutable());
            }
        }
        return it;
    }

    private static List<String> breakerLore(int n) {
        return List.of("§7Speed: §f+20", "", "§6Ability: Dungeon Breaker §e§lDIG",
            "§7While in §cThe Catacombs§7, consume §e1§c⸕", "§7charge to break a block. §320§7 blocks can", "§7be broken at a time, and re-appear after", "§a10s§7. §e2§c⸕§7 charges are regenerated each", "§7second.", "",
            "§7Charges: §e" + n + "§7/§e" + MAX_CHARGES + "§c⸕", "", "§8§l* §8Co-op Soulbound §8§l*", "§c§lSPECIAL DUNGEON PICKAXE");
    }

    private static void refreshBreakerLore() {
        ServerPlayer p = Sim.player();
        if (p == null) return;
        Inventory inv = p.getInventory();
        ItemLore lore = new ItemLore(plainLore(breakerLore(charges())));
        for (int i = 0; i < inv.getContainerSize(); i++) { ItemStack st = inv.getItem(i); if (!st.isEmpty() && "DUNGEONBREAKER".equals(idOf(st))) st.set(DataComponents.LORE, lore); }
    }

    public static ItemStack PEARLS() {
        ItemStack it = item(Items.ENDER_PEARL, "ENDER_PEARL", Lore.ENDER_PEARL_NAME, Lore.ENDER_PEARL);
        it.setCount(12);
        hy(it, null, "common", false);
        return it;
    }

    public static ItemStack LEAP() {
        ItemStack s = head(LEAP_TEX, "§5Infinileap");
        s.set(DataComponents.LORE, new ItemLore(plainLore(Lore.INFINITE_SPIRIT_LEAP)));
        hy(s, null, "epic", false);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "INFINITE_SPIRIT_LEAP");
        tag.putBoolean("p3sim", true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    public static ItemStack JERRY() {
        ItemStack it = item(Items.GOLDEN_HORSE_ARMOR, "JERRY_STAFF", Lore.JERRY_STAFF_NAME, Lore.JERRY_STAFF, true);
        hy(it, "community_center/mayor/jerry/jerrychine_gun", "legendary", false);
        return it;
    }

    public static ItemStack CLOAK() {
        ItemStack it = item(Items.STONE_SWORD, "WITHER_CLOAK", Lore.WITHER_CLOAK_NAME, Lore.WITHER_CLOAK);
        hy(it, "uncategorized/wither_cloak_sword", "legendary");
        return it;
    }

    public static ItemStack MENU() {
        ItemStack it = item(Items.NETHER_STAR, "SKYBLOCK_MENU", Lore.SKYBLOCK_MENU_NAME, Lore.SKYBLOCK_MENU);
        hy(it, null, "common", false);
        return it;
    }

    // ------------------------------------------------------------------ Haste / Mining Fatigue by held slot

    private static final PerPlayer<Boolean> breakerHeld = new PerPlayer<>(() -> null);

    public static void miningEffects(ServerPlayer p) { miningEffects(p, false); }

    public static void miningEffects(ServerPlayer p, boolean force) {
        boolean held = "DUNGEONBREAKER".equals(idOf(p.getMainHandItem()));
        Boolean was = breakerHeld.get();
        if (!force && was != null && held == was && (held || p.hasEffect(MobEffects.HASTE))) return;
        breakerHeld.set(held);
        if (held) {
            p.removeEffect(MobEffects.HASTE);
            p.removeEffect(MobEffects.MINING_FATIGUE);
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.HASTE, -1, 0, true, true, false));
            p.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, -1, 255, true, true, false));
        }
    }

    // ------------------------------------------------------------------ hotbar slot 9 (index 8)

    private static final int QUIVER_START = 2769;
    private static final PerPlayer<Integer> quiverArrows = new PerPlayer<>(() -> QUIVER_START);

    public static void quiverShot() { if (quiverArrows.get() > 0) quiverArrows.set(quiverArrows.get() - 1); }

    private static ItemStack quiverPreview() {
        ItemStack s = new ItemStack(Items.FEATHER);
        s.setCount(Math.clamp(quiverArrows.get(), 1, 64));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        hyModel(s, "item/uncategorized/flint_arrow");
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Flint Arrow").withStyle(it -> it.withItalic(false).withColor(ChatFormatting.WHITE)));
        List<String> lore = List.of("§7Damage: §c+1", "", "§8Stats added when shot!", "§f§lCOMMON ARROW", "§8§m                                ",
            "§7Arrows Remaining: §a" + String.format(Locale.ROOT, "%,d", quiverArrows.get()), "", "§8This item is a preview of your", "§8currently selected arrow.");
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Sim.legacy(l).copy().withStyle(it -> it.withItalic(false)));
        s.set(DataComponents.LORE, new ItemLore(lines));
        CompoundTag tag = new CompoundTag();
        tag.putString("quiver_arrow", "true");
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    private static ItemStack magicalMap() {
        ItemStack s = new ItemStack(Items.FILLED_MAP);
        s.set(DataComponents.CUSTOM_NAME, Component.literal("Magical Map").withStyle(it -> it.withItalic(false).withColor(ChatFormatting.AQUA)));
        List<Component> lines = new ArrayList<>();
        for (String l : List.of("Shows the layout of the Dungeon as", "it is explored and completed.")) lines.add(Component.literal(l).withStyle(it -> it.withItalic(false).withColor(ChatFormatting.GRAY)));
        s.set(DataComponents.LORE, new ItemLore(lines));
        s.set(DataComponents.MAP_ID, new MapId(1024));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "MAP");
        tag.putInt("dontSaveToProfile", 1);
        tag.putInt("dontUpdateStack", 1);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return s;
    }

    private static boolean isQuiverPreview(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && "true".equals(d.copyTag().getStringOr("quiver_arrow", ""));
    }

    private static boolean isMagicalMap(ItemStack s) {
        if (!"MAP".equals(idOf(s))) return false;
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().contains("dontUpdateStack");
    }

    public static boolean isSlot9(ItemStack s) { return "SKYBLOCK_MENU".equals(idOf(s)) || isQuiverPreview(s) || isMagicalMap(s); }

    private static void tickSlot9(ServerPlayer p) {
        if (Masks.ghost.get()) return;
        Inventory inv = p.getInventory();
        ItemStack cur = inv.getItem(8);
        if (!isSlot9(cur)) return;
        ItemStack held = inv.getItem(inv.getSelectedSlot());
        String heldId = idOf(held);
        ItemStack want;
        if (clearMapShown()) want = magicalMap();
        else if (heldId != null && held.getItem() instanceof BowItem) want = quiverPreview();
        else if ("INFINITE_SPIRIT_LEAP".equals(heldId)) want = magicalMap();
        else {
            if ("SKYBLOCK_MENU".equals(idOf(cur))) return;
            want = MENU();
        }
        if (ItemStack.isSameItemSameComponents(cur, want) && cur.getCount() == want.getCount()) return;
        inv.setItem(8, want);
    }

    public static ItemStack AOTV() {
        ItemStack it = item(Items.DIAMOND_SHOVEL, "ASPECT_OF_THE_VOID", Lore.ASPECT_OF_THE_VOID_NAME, Lore.ASPECT_OF_THE_VOID, true, t -> { t.putInt("ethermerge", 1); t.putInt("tuned_transmission", 4); });
        hy(it, "slayer/enderman/aspect_of_the_void", "legendary");
        return it;
    }

    public static ItemStack PET_ROD() {
        ItemStack it = item(Items.FISHING_ROD, "PET_ROD", Lore.FISHING_ROD_NAME, Lore.FISHING_ROD);
        hy(it, null, "common");
        return it;
    }

    public static ItemStack TERMINATOR() {
        ItemStack it = item(Items.BOW, "TERMINATOR", Lore.TERMINATOR_NAME, Lore.TERMINATOR, true);
        hy(it, "slayer/enderman/weapons/terminator", "mythic");
        return it;
    }

    public static ItemStack MOSQUITO() {
        ItemStack it = item(Items.BOW, "MOSQUITO_BOW", Lore.MOSQUITO_BOW_NAME, Lore.MOSQUITO_BOW, true);
        hy(it, "slayer/spider/weapons/mosquito_shortbow", "legendary");
        return it;
    }

    public static ItemStack LAST_BREATH() {
        return item(Items.BOW, Bows.LAST_BREATH, "§6Last Breath", List.of("§9Duplex I", "§7Shoot an extra arrow dealing §a4%§7 of the",
            "§7first arrow's damage.", "", "§7Draw and release, like a vanilla bow.", "", "§6§lLEGENDARY BOW"), true);
    }

    public static ItemStack EXPLOSIVE_BOW() {
        return item(Items.BOW, Bows.EXPLOSIVE, "§5Explosive Bow", List.of("§7Damage: §c+100", "§7Strength: §c+20", "",
            "§6Ability: Explosive Shot", "§7Your arrows explode on impact, dealing", "§7damage to nearby enemies.", "",
            "§7Draw and release, like a vanilla bow.", "", "§5§lEPIC BOW"));
    }

    public enum ArmorSet { MAXOR, TERROR, WISE }

    private static final String RACING_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY1NTg2ODcxMjQwMCwKICAicHJvZmlsZUlkIiA6ICJmZTYxY2RiMjUyMTA0ODYzYTljY2E2ODAwZDRiMzgzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNeVNoYWRvd3MiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmNlMDc0NmIxMmVlNDA1Mzk1OGUxNDBiYTI5NTkzMjcyYmQ4NGNhMzRiYWY1MGQwZDgwYjViYzNjNjE1ZTljNiIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String WISE_HELM_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTYwNTYyMzMzMzU2MSwKICAicHJvZmlsZUlkIiA6ICJjZGM5MzQ0NDAzODM0ZDdkYmRmOWUyMmVjZmM5MzBiZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJSYXdMb2JzdGVycyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS85Y2E2YWM4Mzk2YmEyZmE2NGIwZjI3MTFkY2EyMDIzMmM3YTUyOTEyNmI5NmRiNmVmYWE4ZDdmMmUxODQwZDEiCiAgICB9CiAgfQp9";

    private static ItemStack piece(Item base, String id, String name, List<String> lore, Integer rgb, String style) {
        ItemStack s = item(base, id, name, lore);
        if (rgb != null) s.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
        hy(s, null, style);
        return s;
    }

    private static ItemStack terror(Item base, String id, int rgb) {
        int n = 3 + (terrorHelmet ? 1 : 0);
        int sec = n >= 4 ? 10 : n == 3 ? 7 : 4;
        int dmg = n >= 4 ? 6 : n == 3 ? 4 : 2;
        List<String> src = switch (id) { case "TERROR_CHESTPLATE" -> Lore.TERROR_CHESTPLATE; case "TERROR_LEGGINGS" -> Lore.TERROR_LEGGINGS; default -> Lore.TERROR_BOOTS; };
        List<String> lore = new ArrayList<>();
        for (String l : src) lore.add(l.replace("{T}", String.valueOf(n)).replace("{SEC}", String.valueOf(sec)).replace("{DMG}", String.valueOf(dmg)));
        String name = switch (id) { case "TERROR_CHESTPLATE" -> Lore.TERROR_CHESTPLATE_NAME; case "TERROR_LEGGINGS" -> Lore.TERROR_LEGGINGS_NAME; default -> Lore.TERROR_BOOTS_NAME; };
        return piece(base, id, name, lore, rgb, "legendary");
    }

    public static void equipArmor(ServerPlayer p) { equipArmor(p, null, null); }

    public static void equipArmor(ServerPlayer p, ArmorSet set) { equipArmor(p, set, null); }

    public static void equipArmor(ServerPlayer p, ArmorSet set, Boolean terrorHelm) {
        terrorHelmet = terrorHelm != null ? terrorHelm : "TERROR_HELMET".equals(idOf(p.getItemBySlot(EquipmentSlot.HEAD)));
        Triple<ItemStack, ItemStack, ItemStack> t = armorStacks(set);
        wear(p, EquipmentSlot.CHEST, t.first);
        wear(p, EquipmentSlot.LEGS, t.second);
        wear(p, EquipmentSlot.FEET, t.third);
        terrorHelmet = false;
    }

    private static boolean terrorHelmet = false;

    public static ItemStack armorPiece(String id) {
        for (ArmorSet set : ArmorSet.values()) {
            Triple<ItemStack, ItemStack, ItemStack> t = armorStacks(set);
            for (ItemStack s : Arrays.asList(t.first, t.second, t.third)) if (Objects.equals(idOf(s), id)) return s;
        }
        return null;
    }

    private static Triple<ItemStack, ItemStack, ItemStack> armorStacks(ArmorSet set) {
        ArmorSet which = set != null ? set : ArmorSet.MAXOR;
        ItemStack chest, legs, feet;
        switch (which) {
            case TERROR -> {
                chest = terror(Items.LEATHER_CHESTPLATE, "TERROR_CHESTPLATE", 4064687);
                legs = terror(Items.LEATHER_LEGGINGS, "TERROR_LEGGINGS", 6104017);
                feet = terror(Items.LEATHER_BOOTS, "TERROR_BOOTS", 8144108);
            }
            case MAXOR -> {
                chest = piece(Items.CHAINMAIL_CHESTPLATE, "MITHRIL_COAT", Lore.MITHRIL_COAT_NAME, Lore.MITHRIL_COAT, null, "legendary");
                legs = piece(Items.LEATHER_LEGGINGS, "SPEED_WITHER_LEGGINGS", Lore.SPEED_WITHER_LEGGINGS_NAME, Lore.SPEED_WITHER_LEGGINGS, 6107065, "mythic");
                feet = piece(Items.LEATHER_BOOTS, "SPEED_WITHER_BOOTS", Lore.SPEED_WITHER_BOOTS_NAME, Lore.SPEED_WITHER_BOOTS, 9005512, "mythic");
            }
            default -> {
                chest = piece(Items.LEATHER_CHESTPLATE, "WISE_WITHER_CHESTPLATE", Lore.WISE_WITHER_CHESTPLATE_NAME, Lore.WISE_WITHER_CHESTPLATE, 1545156, "mythic");
                legs = piece(Items.LEATHER_LEGGINGS, "WISE_WITHER_LEGGINGS", Lore.WISE_WITHER_LEGGINGS_NAME, Lore.WISE_WITHER_LEGGINGS, 1550532, "mythic");
                feet = piece(Items.LEATHER_BOOTS, "WISE_WITHER_BOOTS", Lore.WISE_WITHER_BOOTS_NAME, Lore.WISE_WITHER_BOOTS, 1889508, "mythic");
            }
        }
        return new Triple<>(chest, legs, feet);
    }

    public static void equipHelmet(ServerPlayer p, boolean wise) {
        ItemStack h = wise ? head(WISE_HELM_TEX, Lore.WISE_WITHER_HELMET_NAME) : head(RACING_TEX, Lore.RACING_HELMET_NAME);
        h.set(DataComponents.LORE, new ItemLore(plainLore(wise ? Lore.WISE_WITHER_HELMET : Lore.RACING_HELMET)));
        h.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", wise ? "WISE_WITHER_HELMET" : "RACING_HELMET");
        tag.putBoolean("p3sim", true);
        h.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        hy(h, null, "mythic");
        wear(p, EquipmentSlot.HEAD, h);
    }

    public static void wear(ServerPlayer p, EquipmentSlot slot, ItemStack s) { p.getInventory().setItem(slot.getIndex(36), s); }

    public static void giveHotbar(ServerPlayer p) { giveHotbar(p, true, false); }

    public static void giveHotbar(ServerPlayer p, boolean p3) { giveHotbar(p, p3, false); }

    public static void giveHotbar(ServerPlayer p, boolean p3, boolean start) {
        Inventory inv = p.getInventory();
        HotbarLayout.keep(p);
        boolean pet = P3Sim.phoenix();
        inv.clearContent();
        List<ItemStack> bar = List.of(p3 ? SUPERBOOM() : HYPERION(), BONZO(), TERMINATOR(), DUNGEONBREAKER(), PET_ROD(), LEAP(), JERRY(), CLOAK(), MENU());
        List<Pair<Integer, ItemStack>> extras = List.of(Pair.of(10, p3 ? HYPERION() : SUPERBOOM()), Pair.of(11, AOTV()), Pair.of(12, SPIRIT_BOW()), Pair.of(13, PEARLS()), Pair.of(14, MOSQUITO()), Pair.of(15, LAST_BREATH()), Pair.of(16, EXPLOSIVE_BOW()));
        List<Pair<Integer, ItemStack>> defaults = new ArrayList<>();
        for (int i = 0; i < bar.size(); i++) defaults.add(Pair.of(i, bar.get(i)));
        defaults.addAll(extras);
        Pair<List<Pair<Integer, ItemStack>>, Integer> arranged = HotbarLayout.arrange(defaults, p3);
        int held = arranged.second;
        for (Pair<Integer, ItemStack> e : arranged.first) inv.setItem(e.first, e.second);
        Loadouts.applySaved(p, p3);
        if (!start && P3Sim.phoenix() != pet) { P3Sim.setPhoenix(pet); Fight.applySpeed(p); }
        if (p3 && P3Sim.terrorAtTerms()) Loadouts.wearTerror(p);
        HotbarLayout.gave(p3, held);
        inv.setSelectedSlot(held);
        p.connection.send(new ClientboundSetHeldSlotPacket(held));
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
    }

    // ------------------------------------------------------------------ the clear's kit

    public static ItemStack SPIRIT_SCEPTRE() {
        ItemStack it = item(Items.PAPER, SCEPTRE, "§6 Heroic Spirit Sceptre §6✪✪✪✪✪", List.of(
            "§6Ability: Guided Bat §e§lRIGHT CLICK", "§7Shoots a guided spirit bat, following", "§7your aim and exploding on what it hits.",
            "", "§6§lLEGENDARY DUNGEON SWORD"), true);
        hy(it, "island_relevant/dungeons/spirit_sceptre_fragged", "legendary");
        return it;
    }

    public static final String SCEPTRE = "STARRED_BAT_WAND";

    private static final double SCEPTRE_RANGE = 24.0;

    private static void sceptre(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(SCEPTRE_RANGE));
        BlockHitResult wall = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double reach = wall.getType() == HitResult.Type.MISS ? SCEPTRE_RANGE : wall.getLocation().distanceTo(eye);
        Vec3 at = eye.add(look.scale(reach));
        AABB box = new AABB(eye, at).inflate(1.5);
        Entity mob = null;
        double best = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, box, c -> c instanceof LivingEntity && c.isAlive() && DamageModule.mobFor(c) != null)) {
            double d = e.distanceToSqr(eye);
            if (mob == null || d < best) { mob = e; best = d; }
        }
        if (mob != null) at = mob.position().add(0.0, mob.getBbHeight() / 2.0, 0.0);
        Sim.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        Sim.sound(SoundEvents.GENERIC_EXPLODE, 0.5f, 1.2f, at, SoundSource.PLAYERS);
        DamageModule.blast(at.x, at.y, at.z, com.teslamaps.adm.sim.run.DamageSource.IMPLOSION, p.getUUID());
    }

    private static final int YOUNG_DRAGON_DYE = 0xDDE4F0;
    private static final String YOUNG_DRAGON_HELM_TEX = "a0e81ed07dfb0244d56f4d5f2b37553ec026fb4796f0fb0c57a8eb264983e1e0";

    private static ItemStack youngDragon(Item base, String id, String name) {
        return piece(base, id, "§6" + name + " §6✪✪✪✪✪", List.of("§6Full Set Bonus: Young Blood", "§7Gain §a+70✦ Speed§7 while above §c50%", "§7health.", "", "§6§lLEGENDARY"), YOUNG_DRAGON_DYE, "legendary");
    }

    public static void wearClearArmor(ServerPlayer p) {
        ItemStack helm = head(com.teslamaps.adm.sim.clear.blood.HeadTextures.valueFor(YOUNG_DRAGON_HELM_TEX), "§6Necrotic Young Dragon Helmet §6✪✪✪✪✪");
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "YOUNG_DRAGON_HELMET");
        tag.putBoolean("p3sim", true);
        helm.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        hy(helm, null, "legendary");
        wear(p, EquipmentSlot.HEAD, helm);
        wear(p, EquipmentSlot.CHEST, youngDragon(Items.LEATHER_CHESTPLATE, "YOUNG_DRAGON_CHESTPLATE", "Loving Young Dragon Chestplate"));
        wear(p, EquipmentSlot.LEGS, youngDragon(Items.LEATHER_LEGGINGS, "YOUNG_DRAGON_LEGGINGS", "Necrotic Young Dragon Leggings"));
        wear(p, EquipmentSlot.FEET, youngDragon(Items.LEATHER_BOOTS, "YOUNG_DRAGON_BOOTS", "Necrotic Young Dragon Boots"));
    }

    public static void giveClearKit(ServerPlayer p) {
        Inventory inv = p.getInventory();
        inv.clearContent();
        ItemStack hyperion = HYPERION();
        ItemStack aotv = AOTV();
        ItemStack term = TERMINATOR();
        ItemStack breaker = DUNGEONBREAKER();
        ItemStack pearls = PEARLS();
        pearls.setCount(16);
        ItemStack leap = LEAP();
        ItemStack sceptre = SPIRIT_SCEPTRE();
        ItemStack boom = SUPERBOOM();
        boom.setCount(5);
        List<ItemStack> bar = List.of(hyperion, aotv, term, breaker, pearls, leap, sceptre, boom, MENU());
        for (int i = 0; i < bar.size(); i++) inv.setItem(i, bar.get(i));
        for (Pair<Integer, ItemStack> e : List.of(Pair.of(27, SUPERBOOM()), Pair.of(28, BONZO()), Pair.of(29, PET_ROD()), Pair.of(30, JERRY()))) inv.setItem(e.first, e.second);
        wearClearArmor(p);
        HotbarLayout.forgetGiven();
        inv.setSelectedSlot(0);
        p.connection.send(new ClientboundSetHeldSlotPacket(0));
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
    }

    private static boolean clearMapShown() {
        Run r = DamageModule.activeRun();
        if (r == null) return false;
        return r.stage == com.teslamaps.adm.sim.run.RunStage.CLEAR || r.stage == com.teslamaps.adm.sim.run.RunStage.BLOOD;
    }

    // ------------------------------------------------------------------ hooks

    private static boolean simServer(Level level) {
        return !level.isClientSide() && level instanceof ServerLevel && level.getServer() == SimServer.server && SimServer.server != null;
    }

    private static boolean simClient(Level level) { return level.isClientSide() && P3Sim.inSim(); }

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            ItemStack stack = player.getItemInHand(hand);
            String id = idOf(stack);
            if (id == null) return InteractionResult.PASS;
            if (simClient(level)) {
                if (id.equals("SKYBLOCK_MENU")) { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> mc.gui.setScreen(new SimRestartScreen())); return InteractionResult.FAIL; }
                return InteractionResult.PASS;
            }
            if (!simServer(level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            return Sim.asPlayer(sp, false, () -> {
                Pair<String, Integer> bf = blockFired.get();
                if (bf != null) {
                    int d = Fight.serverTick - bf.second;
                    if (bf.first.equals(id) && d >= 0 && d <= 1) { blockFired.set(null); return InteractionResult.SUCCESS; }
                }
                InteractionResult[] result = { InteractionResult.PASS };
                Ec.safely("p3sim use " + id, () -> result[0] = use(sp, id));
                return result[0];
            });
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            String id = idOf(player.getItemInHand(hand));
            if (simClient(level)) {
                if ("SKYBLOCK_MENU".equals(id)) { Minecraft mc = Minecraft.getInstance(); mc.execute(() -> mc.gui.setScreen(new SimRestartScreen())); return InteractionResult.FAIL; }
                return InteractionResult.PASS;
            }
            if (!simServer(level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            InteractionResult[] result = { InteractionResult.PASS };
            Ec.safely("p3sim use block", () -> result[0] = Sim.asPlayer(sp, false, () -> { return useBlock(sp, hit.getBlockPos(), id, hit.getDirection()); }));
            if (sp.getMainHandItem().getItem() instanceof BlockItem) sp.connection.send(new ClientboundSetPlayerInventoryPacket(sp.getInventory().getSelectedSlot(), sp.getMainHandItem().copy()));
            return result[0];
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (hand != InteractionHand.MAIN_HAND || !simServer(level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            InteractionResult[] result = { InteractionResult.PASS };
            Ec.safely("p3sim use entity", () -> result[0] = Sim.asPlayer(sp, false, () -> { return useEntity(sp, entity); }));
            return result[0];
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (simClient(level)) return entity instanceof ItemFrame ? InteractionResult.FAIL : InteractionResult.PASS;
            if (!simServer(level) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            InteractionResult[] result = { InteractionResult.PASS };
            Ec.safely("p3sim hit entity", () -> result[0] = Sim.asPlayer(sp, false, () -> { return useEntity(sp, entity, true); }));
            return result[0];
        });
    }

    private static BlockPos hitHeld = null;

    private static final HashMap<BlockPos, Pair<Integer, String>> predicted = new HashMap<>();
    private static int clientTicks = 0;

    private static String afterLastDot(String s) { return s.substring(s.lastIndexOf('.') + 1); }

    private static void watchPredicted() {
        clientTicks++;
        if (predicted.isEmpty()) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) { predicted.clear(); return; }
        Iterator<Map.Entry<BlockPos, Pair<Integer, String>>> it = predicted.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Pair<Integer, String>> en = it.next();
            BlockPos pos = en.getKey();
            Pair<Integer, String> v = en.getValue();
            int age = clientTicks - v.first;
            BlockState now = level.getBlockState(pos);
            if (!now.isAir()) {
                Ec.LOG.info("[p3sim] breaker: {},{},{} ({}) back in the game {} ticks after the hit as {}", pos.getX(), pos.getY(), pos.getZ(), v.second, age,
                    afterLastDot(now.getBlock().getDescriptionId()));
                it.remove();
            } else if (age > 40) it.remove();
        }
    }

    public static void clientTick() {
        Ec.safely("p3sim breaker watch", SimItems::watchPredicted);
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyAttack.isDown()) { hitHeld = null; return; }
        LocalPlayer player = mc.player;
        if (player == null) return;
        ClientLevel level = mc.level;
        if (level == null) return;
        if (mc.gui.screen() != null || player.isUsingItem() || !simClient(level) || !"DUNGEONBREAKER".equals(idOf(player.getMainHandItem()))) return;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) player.swing(InteractionHand.MAIN_HAND, false);
    }

    public static boolean clientHitBlock(BlockPos pos, Direction face) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        ClientLevel level = mc.level;
        if (level == null) return false;
        if (!simClient(level)) return false;
        BlockPos at = pos.immutable();
        boolean again = at.equals(hitHeld);
        hitHeld = at;
        String held = idOf(player.getMainHandItem());
        if ("DUNGEONBREAKER".equals(held) && Practice.noBreaking()) return true;
        BlockState state = level.getBlockState(at);
        boolean lever = state.getBlock() instanceof LeverBlock;
        if (!again || "DUNGEONBREAKER".equals(held)) SimNet.send("hit", at.getX() + "," + at.getY() + "," + at.getZ() + "," + face.ordinal() + "," + (again ? 1 : 0));
        if ("SUPERBOOM_TNT".equals(held)) return true;
        if (lever && !"DUNGEONBREAKER".equals(held)) return true;
        if (!"DUNGEONBREAKER".equals(held)) return false;
        if (state.isAir()) return true;
        if (state.getDestroySpeed(level, at) >= 0) { predicted.put(at, Pair.of(clientTicks, afterLastDot(state.getBlock().getDescriptionId()))); level.destroyBlock(at, false); }
        return true;
    }

    private static Integer intOrNull(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    public static void serverHit(ServerPlayer p, String data) {
        String[] v = data.split(",", -1);
        if (v.length < 5) return;
        Integer x = intOrNull(v[0]); if (x == null) return;
        Integer y = intOrNull(v[1]); if (y == null) return;
        Integer z = intOrNull(v[2]); if (z == null) return;
        BlockPos at = new BlockPos(x, y, z);
        Integer fi = intOrNull(v[3]); if (fi == null) return;
        if (fi < 0 || fi >= Direction.values().length) return;
        Direction face = Direction.values()[fi];
        boolean again = v[4].equals("1");
        Blocks.clientHit(at);
        String held = idOf(p.getMainHandItem());
        Runnable sendBack = () -> { if (!p.isRemoved()) p.connection.send(new ClientboundBlockUpdatePacket(Sim.level(), at)); };
        if (p.position().distanceTo(Vec3.atCenterOf(at)) > 12.0) { if ("DUNGEONBREAKER".equals(held)) sendBack.run(); return; }
        Run active = DamageModule.activeRun();
        boolean shut = active != null && Services.doors().touchBlock(active, at.getX(), at.getY(), at.getZ(), p.getUUID());
        if (shut) { Fight.afterPing("hit ignored", sendBack); return; }
        if (Fight.phase instanceof GoldorPhase ph) {
            if (ph.leverAt(at) != null || ph.devices.lights.isLever(at)) { if (!again) ph.devices.leftClick(at); if (!"DUNGEONBREAKER".equals(held)) return; }
        }
        if ("SUPERBOOM_TNT".equals(held)) {
            if (!again) {
                Run run = DamageModule.activeRun();
                Fight.afterPing("superboom", () -> Fight.later(1, "superboom", () -> { if (run != null) superboomRun(run, p, at, face); else superboom(p, at, face); }));
            }
            return;
        }
        if ("DUNGEONBREAKER".equals(held)) mine(p, at);
        else Fight.afterPing("hit ignored", sendBack);
    }

    public static ItemStack quiverArrow(Player p, ItemStack weapon) {
        String id = idOf(weapon);
        if (id == null || !Bows.DRAWN.contains(id)) return null;
        Level level = p.level();
        boolean sim = level.isClientSide() ? P3Sim.inSim() : SimServer.isSimLevel(level);
        return sim ? new ItemStack(Items.ARROW) : null;
    }

    public static Boolean releaseBow(ItemStack stack, Level level, LivingEntity entity, int remainingTime) {
        String bow = idOf(stack);
        if (bow == null || !Bows.DRAWN.contains(bow)) return null;
        if (!simServer(level) || !(entity instanceof ServerPlayer sp)) return null;
        float power = BowItem.getPowerForTime(stack.getUseDuration(sp) - remainingTime);
        if (power < 0.1f) return false;
        Bows.release(sp, bow, power);
        return true;
    }

    public static void clientLeftClick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        ClientLevel level = mc.level;
        if (level == null) return;
        if (!simClient(level)) return;
        String id = idOf(player.getMainHandItem());
        if (id != null || Practice.active() || Practice.clickRestarts()) SimNet.send("left", id != null ? id : "");
    }

    public static void serverLeftClick(ServerPlayer p) { serverLeftClick(p, ""); }

    public static void serverLeftClick(ServerPlayer p, String held) {
        String id = idOf(p.getMainHandItem());
        if (Sim.isHost(p) && (Practice.clickRestarts() || Practice.active() && ("INFINITE_SPIRIT_LEAP".equals(id) || "INFINITE_SPIRIT_LEAP".equals(held)))) { Practice.restart(); return; }
        if (Fight.phase == null && "STARRED_BONZO_STAFF".equals(id)) { boolean sneak = p.isShiftKeyDown(); asClicked(p, "bonzo aotv", false, () -> { if (sneak) etherwarp(p); else blink(p, 12); }); return; }
        if (Mage.isMage() && Mage.beamItem(id)) { Fight.afterPing("mage beam", () -> Mage.click(p)); return; }
        if (id != null && Bows.SHORTBOWS.contains(id)) Bows.click(p, id, true);
    }

    public static boolean clientDrop(boolean fullStack) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        ClientLevel level = mc.level;
        if (level == null) return false;
        if (!simClient(level)) return false;
        SimNet.send("drop", fullStack ? "1" : "0");
        return true;
    }

    public static void serverDrop(ServerPlayer p, boolean fullStack) {
        if (com.teslamaps.adm.sim.clear.damage.ClassAbilities.press(p, !fullStack)) return;
        if (!fullStack) return;
        if (P3Sim.myClass() == DungeonClass.ARCHER) asClicked(p, "archer ability", false, () -> volley(p));
        else if (Mage.isMage()) Fight.afterPing("guided sheep", () -> Mage.ability(p));
    }

    private static void asClicked(ServerPlayer p, String what, boolean prior, Runnable run) {
        float xRot = prior ? Fight.lastRot.get().first : p.getXRot();
        float yRot = prior ? Fight.lastRot.get().second : p.getYRot();
        Vec3 pos = p.position();
        Fight.afterPing(what, () -> {
            if (p.isRemoved()) return;
            float nowX = p.getXRot();
            float nowY = p.getYRot();
            p.setXRot(xRot);
            p.setYRot(yRot);
            clickPos = pos;
            try { run.run(); } finally { p.setXRot(nowX); p.setYRot(nowY); clickPos = null; }
        });
    }

    private static Vec3 clickPos = null;

    private static void tpKeepLook(ServerPlayer p, double x, double y, double z) {
        p.teleportTo(Sim.level(), x, y, z, Relative.ROTATION, 0f, 0f, false);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0;
    }

    public static void reset() {
        Mage.reset(); rapidLast.set(-1000); resetBreaker(); cloakUntil.set(0); cloakReady.set(0); vitality.set(0); discardVeil(); lastHype.set(-100); lastCure.set(-1000); bonzoLast.set(-100); jerryTick.set(-1);
        leapReady.set(0); pearlLeapReady.set(0); volleyReady.set(0); blockFired.set(null); quiverArrows.set(QUIVER_START); breakerHeld.set(null); Bows.reset();
    }

    public static void resetShared() {
        arrows.clear(); lastMotion.clear(); lastPos.clear(); pendingMotion.clear();
        if (!liveRockets.isEmpty()) {
            IntArrayList ids = new IntArrayList(liveRockets);
            for (ServerPlayer sp : Sim.players()) sp.connection.send(new ClientboundRemoveEntitiesPacket(ids));
            liveRockets.clear();
        }
    }

    private static InteractionResult use(ServerPlayer p, String id) { return use(p, id, false); }

    private static InteractionResult use(ServerPlayer p, String id, boolean fromBlock) {
        if (Fight.phase instanceof P1Maxor m && m.usePylon(p.position())) return InteractionResult.SUCCESS;
        if (TRAP_LOCKED.contains(id)) {
            Run r = DamageModule.activeRun();
            if (r != null && Services.traps().abilitiesDisabled(r, p.getUUID())) { Sim.chat(ABILITY_LOCKED); return InteractionResult.SUCCESS; }
        }
        if (id.equals("HYPERION") && Fight.phase instanceof P2Storm s) s.beam();
        if (id.equals("ASPECT_OF_THE_VOID")) { boolean sneak = p.isShiftKeyDown(); asClicked(p, "aotv", false, () -> { if (sneak) etherwarp(p); else blink(p, 12); }); }
        else if (id.equals("HYPERION")) asClicked(p, "hype", false, () -> {
            if (hypeReady()) {
                boolean clear = DamageModule.activeRun() != null;
                if (clear) blink(p, 10);
                implode(p);
            }
        });
        else if (id.equals("ENDER_PEARL")) {
            if (DamageModule.activeRun() != null) asClicked(p, "pearl", false, () -> throwPearl(p));
            else { p.getMainHandItem().shrink(1); Sim.chat("§cA mystical force in this room prevents you from using that ability!"); }
        }
        else if (id.equals("STARRED_BONZO_STAFF")) asClicked(p, "bonzo", false, () -> bonzo(p));
        else if (id.equals("JERRY_STAFF")) asClicked(p, "jerry", !fromBlock, () -> jerry(p));
        else if (id.equals("WITHER_CLOAK")) asClicked(p, "cloak", false, () -> cloak(p));
        else if (id.equals(SCEPTRE)) asClicked(p, "sceptre", false, () -> sceptre(p));
        else if (id.equals(ClearItems.INFINILEAP) || id.equals(ClearItems.SPIRIT_LEAP)) openLeap(p, id);
        else if (ClearItems.INERT.containsKey(id)) inertItem(p, id);
        else if (id.equals("HAUNT_ABILITY")) openHaunt(p);
        else if (id.equals("SUPERBOOM_TNT")) {}
        else if (Bows.SHORTBOWS.contains(id)) Bows.click(p, id, false);
        else if (id.equals("PET_ROD")) Fight.afterPing("pet rod", () -> Fight.later(1, "pet rod cast", () -> { if (!p.isRemoved()) castRod(p); }));
        else return InteractionResult.PASS;
        p.containerMenu.broadcastChanges();
        return InteractionResult.SUCCESS;
    }

    private static void throwPearl(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return;
        Sim.sound(SoundEvents.ARROW_SHOOT, ClearItems.PEARL_VOLUME, ClearItems.pearlPitch(KRandom.DEFAULT.nextFloat()), p.position(), SoundSource.NEUTRAL);
        Projectile.spawnProjectileFromRotation(
            (Projectile.ProjectileFactory<SimPearl>) (l, o, s) -> { SimPearl e = new SimPearl(l, o, s); e.addTag(Sim.TAG); return e; },
            level, held, p, 0f, ClearItems.PEARL_SPEED, ClearItems.PEARL_SPREAD);
        if (!p.isCreative()) held.shrink(1);
    }

    private static void resyncHeld(ServerPlayer p) {
        p.connection.send(new ClientboundContainerSetSlotPacket(0, p.inventoryMenu.incrementStateId(), 36 + p.getInventory().getSelectedSlot(), p.getMainHandItem().copy()));
    }

    private static final PerPlayer<Map<String, Integer>> inertNoteAt = new PerPlayer<>(() -> Map.of());

    private static void inertItem(ServerPlayer p, String id) {
        resyncHeld(p);
        int now = Fight.serverTick;
        Integer last = inertNoteAt.get().get(id);
        if (now - (last != null ? last : -ClearItems.INERT_NOTE_GAP) < ClearItems.INERT_NOTE_GAP) return;
        Map<String, Integer> m = new LinkedHashMap<>(inertNoteAt.get());
        m.put(id, now);
        inertNoteAt.set(m);
        String name = ClearItems.INERT.get(id);
        if (name != null) Sim.note(ClearItems.inertNote(name));
    }

    public static void vanillaLeverToggle(BlockPos pos) {
        BlockState st = Blocks.get(pos);
        if (st == null || !st.hasProperty(LeverBlock.POWERED)) {
            st = Sim.level().getBlockState(pos);
            if (!st.hasProperty(LeverBlock.POWERED)) return;
        }
        boolean on = !st.getValue(LeverBlock.POWERED);
        Blocks.set(pos, st.setValue(LeverBlock.POWERED, on));
        Sim.sound(SoundEvents.LEVER_CLICK, 0.3f, on ? 0.5873016f : 0.4920635f, Vec3.atCenterOf(pos), SoundSource.BLOCKS);
    }

    private static InteractionResult useBlock(ServerPlayer p, BlockPos pos, String id, Direction hitFace) {
        Fight.Phase phase = Fight.phase;
        if (phase instanceof P1Maxor m && m.usePylon(Vec3.atCenterOf(pos))) return InteractionResult.SUCCESS;
        if (phase instanceof GoldorPhase g) {
            Station st = g.leverAt(pos);
            if (st != null) { Fight.afterPing("lever", () -> g.pullLever(st, Sim.me(), false)); return InteractionResult.SUCCESS; }
            if (g.devices.use(pos)) return InteractionResult.SUCCESS;
            if ("SUPERBOOM_TNT".equals(id)) {
                Fight.afterPing("superboom", () -> Fight.later(1, "superboom", () -> superboom(p, pos, hitFace)));
                blockFired.set(Pair.of(id, Fight.serverTick));
                return InteractionResult.SUCCESS;
            }
        }
        if ("SUPERBOOM_TNT".equals(id)) {
            Run run = DamageModule.activeRun();
            if (run != null) {
                Fight.afterPing("superboom", () -> Fight.later(1, "superboom", () -> superboomRun(run, p, pos, hitFace)));
                blockFired.set(Pair.of(id, Fight.serverTick));
                return InteractionResult.SUCCESS;
            }
        }
        BlockState state = Sim.level().getBlockState(pos);
        if (state.getBlock() instanceof LeverBlock) { Fight.afterPing("lever", () -> vanillaLeverToggle(pos)); return InteractionResult.SUCCESS; }
        if (state.getBlock() instanceof ButtonBlock) return InteractionResult.SUCCESS;
        if (id == null) return InteractionResult.SUCCESS;
        InteractionResult r = use(p, id, true);
        if (r != InteractionResult.PASS && !(p.getMainHandItem().getItem() instanceof BlockItem)) blockFired.set(Pair.of(id, Fight.serverTick));
        return r;
    }

    private static final PerPlayer<Pair<String, Integer>> blockFired = new PerPlayer<>(() -> null);

    private static final PerPlayer<WeakHashMap<Station, Integer>> termClickTick = new PerPlayer<>(WeakHashMap::new);

    private static InteractionResult useEntity(ServerPlayer p, Entity e) { return useEntity(p, e, false); }

    private static InteractionResult useEntity(ServerPlayer p, Entity e, boolean left) {
        if (e instanceof EndCrystal) { if (Fight.phase instanceof P1Maxor m) m.useCrystal(e); return InteractionResult.SUCCESS; }
        if (e instanceof ArmorStand && Fight.phase instanceof P1Maxor m && m.useCrystal(e)) return InteractionResult.SUCCESS;
        if (left && "HYPERION".equals(idOf(p.getMainHandItem())) && Fight.phase instanceof P2Storm s) s.beam();
        GoldorPhase phase = Fight.phase instanceof GoldorPhase g ? g : null;
        if (phase != null) {
            if (e instanceof ItemFrame f && phase.devices.arrows.owns(e)) { if (!left) phase.devices.arrows.use(f); return InteractionResult.SUCCESS; }
            if (e instanceof ArmorStand) {
                Station st = null;
                for (Station s : phase.stations) if (s.owns(e)) { st = s; break; }
                if (st != null && st.kind == Station.Kind.TERMINAL) {
                    WeakHashMap<Station, Integer> ticks = termClickTick.get();
                    if (!Objects.equals(ticks.get(st), Fight.serverTick)) {
                        ticks.put(st, Fight.serverTick);
                        Station term = st;
                        Fight.afterPing("terminal", () -> phase.useTerminal(term));
                    }
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.SUCCESS;
            }
        }
        if (com.teslamaps.adm.sim.clear.state.MortNpc.owns(e)) { if (!left) com.teslamaps.adm.sim.clear.state.ReadyUpMenu.open(p); return InteractionResult.SUCCESS; }
        if (e instanceof ItemFrame || e instanceof ArmorStand) return InteractionResult.SUCCESS;
        if (e.entityTags().contains(Sim.TAG)) return InteractionResult.SUCCESS;
        return InteractionResult.PASS;
    }

    // ------------------------------------------------------------------ teleports

    private static Vec3 look(Player p) {
        double yaw = Math.toRadians(p.getYRot());
        double pitch = Math.toRadians(p.getXRot());
        return new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    private static BlockState state(int x, int y, int z) { return Sim.level().getBlockState(new BlockPos(x, y, z)); }

    private static boolean collides(BlockState s, BlockPos pos) { return !s.getCollisionShape(Sim.level(), pos, CollisionContext.empty()).isEmpty(); }

    private static double top(int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        VoxelShape shape = state(x, y, z).getCollisionShape(Sim.level(), pos, CollisionContext.empty());
        if (shape.isEmpty()) return 0.0;
        double best = 0.0;
        for (AABB b : shape.toAabbs()) if (b.minX <= 0.5 && b.maxX >= 0.5 && b.minZ <= 0.5 && b.maxZ >= 0.5) best = Math.max(best, b.maxY);
        return best > 0 ? best : shape.max(Direction.Axis.Y);
    }

    private static boolean passThrough(BlockState s) {
        Block b = s.getBlock();
        return b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof LadderBlock || b instanceof FlowerPotBlock || b instanceof ButtonBlock || b instanceof LeverBlock;
    }

    private static boolean stopsAnyway(BlockState s) {
        Block b = s.getBlock();
        return b instanceof SignBlock || b instanceof AbstractBannerBlock || b instanceof TripWireHookBlock;
    }

    private static boolean rayStops(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (passThrough(s)) return false;
        if (stopsAnyway(s)) return true;
        return collides(s, new BlockPos(x, y, z));
    }

    private static boolean bodyPasses(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (stopsAnyway(s)) return true;
        Block b = s.getBlock();
        if (b instanceof SkullBlock || b instanceof WallSkullBlock || b instanceof LadderBlock || b instanceof FlowerPotBlock) return false;
        return !collides(s, new BlockPos(x, y, z));
    }

    public static void etherwarp(ServerPlayer p) { etherwarp(p, 61.0); }

    public static void etherwarp(ServerPlayer p, double range) {
        Vec3 seen = clickPos != null ? clickPos : p.position();
        Vec3 eye = new Vec3(seen.x, seen.y + 1.27, seen.z);
        Vec3 dir = look(p);
        Triple<Integer, Integer, Integer> hit = dda(eye, dir, range);
        if (hit == null) { return; }
        int x = hit.first, y = hit.second, z = hit.third;
        BlockState s = state(x, y, z);
        BlockState above = state(x, y + 1, z);
        boolean bad = stopsAnyway(s) || s.getBlock() instanceof SkullBlock || s.getBlock() instanceof LadderBlock || s.getBlock() instanceof FlowerPotBlock ||
            above.getBlock() instanceof SkullBlock || above.getBlock() instanceof WallSkullBlock || above.getBlock() instanceof LadderBlock || above.getBlock() instanceof FlowerPotBlock || top(x, y, z) < 0.03;
        int need = top(x, y, z) > 1.0 ? 3 : 2;
        boolean blocked = false;
        if (!bad) for (int i = 1; i <= need; i++) if (!bodyPasses(x, y + i, z)) { blocked = true; break; }
        if (bad || blocked || rayStops((int) Math.floor(eye.x), (int) Math.floor(eye.y), (int) Math.floor(eye.z))) {
            Sim.chat("§cThere are blocks in the way!");
            return;
        }
        etherPuff(p.position());
        tpKeepLook(p, x + 0.5, y + 1.05, z + 0.5);
        Sim.sound(SoundEvents.ENDER_DRAGON_HURT, 1f, 0.54f, p.position(), SoundSource.HOSTILE);
    }

    private static double ddaFirst(double o, int b, int s, double d) { return s > 0 ? (b + 1 - o) * d : s < 0 ? (o - b) * d : Double.MAX_VALUE; }

    private static Triple<Integer, Integer, Integer> dda(Vec3 eye, Vec3 dir, double range) {
        int bx = (int) Math.floor(eye.x), by = (int) Math.floor(eye.y), bz = (int) Math.floor(eye.z);
        int sx = (int) Math.signum(dir.x), sy = (int) Math.signum(dir.y), sz = (int) Math.signum(dir.z);
        double dx = dir.x != 0.0 ? Math.abs(1 / dir.x) : Double.MAX_VALUE;
        double dy = dir.y != 0.0 ? Math.abs(1 / dir.y) : Double.MAX_VALUE;
        double dz = dir.z != 0.0 ? Math.abs(1 / dir.z) : Double.MAX_VALUE;
        double tx = ddaFirst(eye.x, bx, sx, dx), ty = ddaFirst(eye.y, by, sy, dy), tz = ddaFirst(eye.z, bz, sz, dz);
        for (int step = 0; step < 250; step++) {
            double t = Math.min(tx, Math.min(ty, tz));
            if (t > range) return null;
            boolean cx = tx <= t + 1e-4, cy = ty <= t + 1e-4, cz = tz <= t + 1e-4;
            if ((cx ? 1 : 0) + (cy ? 1 : 0) + (cz ? 1 : 0) >= 2) {
                if (cx && rayStops(bx + sx, by, bz)) return new Triple<>(bx + sx, by, bz);
                if (cy && rayStops(bx, by + sy, bz)) return new Triple<>(bx, by + sy, bz);
                if (cz && rayStops(bx, by, bz + sz)) return new Triple<>(bx, by, bz + sz);
            }
            if (cx) { bx += sx; tx += dx; }
            if (cy) { by += sy; ty += dy; }
            if (cz) { bz += sz; tz += dz; }
            if (rayStops(bx, by, bz)) return new Triple<>(bx, by, bz);
        }
        return null;
    }

    private static boolean blinkBlocks(int x, int y, int z) {
        BlockState s = state(x, y, z);
        if (s.getBlock() instanceof SignBlock || s.getBlock() instanceof AbstractBannerBlock) return false;
        if (rayStops(x, y, z) && top(x, y, z) >= 0.5) return true;
        return top(x, y - 1, z) > 1.0;
    }

    private static boolean feetPass(int x, int y, int z) { return (bodyPasses(x, y, z) || top(x, y, z) < 0.5) && top(x, y - 1, z) <= 1.0; }

    private static int blinkFeetAt(Vec3 eye, Vec3 dir, int i) {
        Vec3 c = eye.add(dir.scale(i));
        int cx = (int) Math.floor(c.x), cy = (int) Math.floor(c.y), cz = (int) Math.floor(c.z);
        return feetPass(cx, cy - 1, cz) ? cy - 1 : cy;
    }

    private static boolean blinkRoomy(ServerPlayer p, Vec3 eye, Vec3 dir, int i) {
        Vec3 c = eye.add(dir.scale(i));
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        int f = blinkFeetAt(eye, dir, i);
        return Sim.level().noCollision(p, new AABB(cx + 0.2, f + 1.0, cz + 0.2, cx + 0.8, f + 1.8, cz + 0.8).deflate(1e-3));
    }

    public static void blink(ServerPlayer p, int range) {
        Vec3 eye = (clickPos != null ? clickPos : p.position()).add(0.0, (double) p.getEyeHeight(), 0.0);
        Vec3 dir = look(p);
        int last = 0;
        int px = (int) Math.floor(eye.x), pz = (int) Math.floor(eye.z);
        boolean cut = false;
        loop:
        for (int i = 1; i <= range; i++) {
            for (int k = 1; k <= 4; k++) {
                Vec3 q = eye.add(dir.scale((i - 1) + k * 0.25));
                if (blinkBlocks((int) Math.floor(q.x), (int) Math.floor(q.y), (int) Math.floor(q.z))) { cut = true; break loop; }
            }
            Vec3 c = eye.add(dir.scale(i));
            int cx = (int) Math.floor(c.x), cy = (int) Math.floor(c.y), cz = (int) Math.floor(c.z);
            if (cx != px && cz != pz && blinkBlocks(px, cy, cz) && blinkBlocks(cx, cy, pz)) { cut = true; break; }
            last = i; px = cx; pz = cz;
        }
        while (last > 0 && !blinkRoomy(p, eye, dir, last)) { last--; cut = true; }
        if (last == 0) { Sim.chat("§cThere are blocks in the way!"); return; }
        Vec3 c = eye.add(dir.scale(last));
        int cx = (int) Math.floor(c.x), cz = (int) Math.floor(c.z);
        int feetY = blinkFeetAt(eye, dir, last);
        if (cx == (int) Math.floor(p.getX()) && cz == (int) Math.floor(p.getZ()) && (feetY == (int) Math.floor(p.getY() + 0.05) || new Vec3(cx + 0.5, (double) feetY, cz + 0.5).distanceTo(p.position()) < 1.5)) {
            Sim.chat("§cThere are blocks in the way!"); return;
        }
        if (cut) Sim.chat("§cThere are blocks in the way!");
        tpKeepLook(p, cx + 0.5, (double) feetY, cz + 0.5);
        Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, p.position(), SoundSource.HOSTILE);
    }

    private static final Set<String> TRAP_LOCKED = Set.of("ASPECT_OF_THE_VOID", "HYPERION", "STARRED_BONZO_STAFF", "WITHER_CLOAK", "JERRY_STAFF");

    private static final String ABILITY_LOCKED = "§cYou cannot use abilities in this room!";

    private static final PerPlayer<Integer> lastHype = new PerPlayer<>(() -> -100);

    private static boolean hypeReady() {
        int now = Fight.serverTick;
        if (now == lastHype.get()) return false;
        lastHype.set(now);
        return true;
    }

    private static final double IMPLOSION_DAMAGE = 34_000_000.0;

    private static final PerPlayer<Integer> lastCure = new PerPlayer<>(() -> -1000);

    private static void witherShield(ServerPlayer p) {
        Fight.later(2, "wither shield", () -> {
            if (p.isRemoved()) return;
            if (DamageModule.activeRun() != null) return;
            AttributeInstance a = p.getAttribute(Attributes.MAX_ABSORPTION);
            if (a != null && a.getBaseValue() < 16.0) a.setBaseValue(16.0);
            p.setAbsorptionAmount(16f);
        });
        int now = Fight.serverTick;
        if (now - lastCure.get() < 100) return;
        lastCure.set(now);
        Sim.sound(SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 0.6984127f, p.position(), SoundSource.HOSTILE);
        Vec3 look = p.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        right = right.lengthSqr() < 1e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
        Vec3 up = look.cross(right).normalize();
        Vec3 c = new Vec3(p.getX(), p.getY() + 1.5, p.getZ()).add(look.scale(0.5));
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            Vec3 q = c.add(right.scale(0.7 * Math.cos(a))).add(up.scale(0.7 * Math.sin(a)));
            Fight.later(KRandom.DEFAULT.nextInt(0, 5), "shield ring", () -> Sim.level().sendParticles(ParticleTypes.WITCH, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0));
        }
    }

    private static void implode(ServerPlayer p) {
        Sim.level().sendParticles(ParticleTypes.EXPLOSION, true, true, p.getX(), p.getY(), p.getZ(), 8, 0.0, 0.0, 0.0, 8.0);
        Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, 1f, p.position(), SoundSource.BLOCKS);
        witherShield(p);
        DamageModule.startShield(p);
        Run run = DamageModule.activeRun();
        if (run != null) {
            DamageModule.Cast cast = DamageModule.castImplosion(run, p);
            if (cast.count == 0) return;
            Sim.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.492f, p.position(), SoundSource.PLAYERS);
            Sim.chat(ImplosionRules.line(cast.count, cast.total));
            return;
        }
        if (Fight.phase instanceof GoldorPhase) return;
        AABB box = new AABB(p.getX() - 6, p.getEyeY() - 6, p.getZ() - 6, p.getX() + 6, p.getEyeY() + 7, p.getZ() + 6);
        int n = Sim.level().getEntitiesOfClass(WitherBoss.class, box, w -> w.isAlive()).size();
        if (n == 0) return;
        Sim.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.492f, p.position(), SoundSource.PLAYERS);
        double dmg = 0.0;
        for (int i = 1; i <= n; i++) dmg += IMPLOSION_DAMAGE * (0.9 + KRandom.DEFAULT.nextDouble() * 0.25);
        String shown = String.format(Locale.ROOT, "%,.1f", dmg);
        if (shown.endsWith(".0")) shown = shown.substring(0, shown.length() - 2);
        Sim.chat("§7Your Implosion hit §r§c" + n + " §r§7" + (n == 1 ? "enemy" : "enemies") + " for §r§c" + shown + " §r§7damage.");
    }

    // ------------------------------------------------------------------ movement items

    private static void push(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        pendingMotion.put(p, v);
    }

    private static final LinkedHashMap<ServerPlayer, Vec3> pendingMotion = new LinkedHashMap<>();

    public static void flushMotion() {
        if (pendingMotion.isEmpty()) return;
        pendingMotion.forEach((p, v) -> { if (!p.isRemoved()) p.connection.send(new ClientboundSetEntityMotionPacket(p.getId(), v)); });
        pendingMotion.clear();
    }

    private static void bonzo(ServerPlayer p) {
        int gap = Fight.serverTick - bonzoLast.get();
        if (gap < 4) { Ec.LOG.info("[ec] bonzo: refused, {} ticks after the last (4-tick gate)", gap); return; }
        double resend = gap < 8 ? 0.5 : gap > 10 ? 0.81 : 0.65;
        bonzoLast.set(Fight.serverTick);
        if (KRandom.DEFAULT.nextDouble() < resend) p.connection.send(new ClientboundContainerSetSlotPacket(0, p.inventoryMenu.incrementStateId(), 36 + p.getInventory().getSelectedSlot(), p.getItemInHand(InteractionHand.MAIN_HAND).copy()));
        Vec3 pos = clickPos != null ? clickPos : p.position();
        Vec3 dir = look(p);
        Vec3 o = pos.add(0.0, 1.0, 0.0).add(dir.scale(0.2));
        Vec3 s0 = floor32(pos.add(dir.scale(0.2)).add(0.0, -0.5, 0.0));
        ArmorStand stand = new ArmorStand(Sim.level(), s0.x, s0.y, s0.z);
        stand.setInvisible(true);
        stand.setSilent(true);
        stand.setNoGravity(true);
        Station.setMarker(stand);
        Station.setLeverFlags(stand);
        stand.setYRot(p.getYRot());
        stand.setXRot(p.getXRot());
        stand.setItemSlot(EquipmentSlot.HEAD, balloonHead());
        Sim.spawn(stand);
        Sim.sound(SoundEvents.GHAST_AMBIENT, 1f, (88 + KRandom.DEFAULT.nextInt(26)) / 63f, pos, SoundSource.HOSTILE);
        boolean startsInside = bonzoSolid(o);
        bonzoFly(p, stand, o, dir, startsInside, 1);
    }

    private static void bonzoFly(ServerPlayer p, ArmorStand stand, Vec3 o, Vec3 dir, boolean startsInside, int j) {
        Fight.later(1, "bonzo flight", () -> {
            if (stand.isRemoved()) return;
            int k = j - 1;
            if (k >= 1 && k <= BONZO_STEPS && (startsInside ? k == 2 : bonzoSolid(o.add(dir.scale(BONZO_SPEED * k))))) {
                bonzoBurst(p, o.add(dir.scale(BONZO_SPEED * k)), stand);
                return;
            }
            if (j > BONZO_STEPS) { Ec.LOG.info("[ec] bonzo: no block within reach, no burst"); stand.discard(); return; }
            Vec3 q = o.add(dir.scale(BONZO_SPEED * j));
            stand.setPos(q.x, q.y - 1.5, q.z);
            bonzoFly(p, stand, o, dir, startsInside, j + 1);
        });
    }

    private static String f2(double v) { return String.format(Locale.ROOT, "%.2f", v); }

    private static void bonzoBurst(ServerPlayer p, Vec3 bTrue, ArmorStand stand) {
        Supplier<Integer> colours = () -> KRandom.DEFAULT.nextInt(0x1000000);
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        int c1 = colours.get();
        int c2 = colours.get();
        rocket.set(DataComponents.FIREWORKS, new Fireworks(0, List.of(new FireworkExplosion(
            FireworkExplosion.Shape.SMALL_BALL, new IntArrayList(new int[] { c1, c2 }), new IntArrayList(), false, false))));
        Vec3 b32 = floor32(bTrue);
        FireworkRocketEntity fw = new FireworkRocketEntity(Sim.level(), b32.x, b32.y, b32.z, rocket);
        ServerGamePacketListenerImpl conn = p.connection;
        conn.send(new ClientboundAddEntityPacket(fw.getId(), fw.getUUID(), b32.x, b32.y, b32.z, 0f, 0f,
            EntityTypes.FIREWORK_ROCKET, 0, Vec3.ZERO, 0.0));
        List<SynchedEntityData.DataValue<?>> data = fw.getEntityData().getNonDefaultValues();
        if (data != null) conn.send(new ClientboundSetEntityDataPacket(fw.getId(), data));
        conn.send(new ClientboundEntityEventPacket(fw, (byte) 17));
        liveRockets.add(fw.getId());
        Fight.later(1, "bonzo cleanup", () -> { liveRockets.rem(fw.getId()); conn.send(new ClientboundRemoveEntitiesPacket(fw.getId())); stand.discard(); });
        Vec3 pos = Fight.seenPos(p);
        double dist = bTrue.distanceTo(pos.add(0.0, 1.5, 0.0));
        Ec.LOG.info("[ec] bonzo: burst at {} {} {}, {} from you (seen {} {} {}, live {} {} {}): {}",
            f2(bTrue.x), f2(bTrue.y), f2(bTrue.z), f2(dist),
            f2(pos.x), f2(pos.y), f2(pos.z), f2(p.getX()), f2(p.getY()), f2(p.getZ()),
            dist > 3.5 ? "no boost (over 3.5)" : "boost");
        if (dist > 3.5) return;
        double dx = pos.x - bTrue.x, dz = pos.z - bTrue.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        push(p, h > 0.0 ? new Vec3(1.5 * dx / h, 0.5, 1.5 * dz / h) : new Vec3(0.0, 0.5, 0.0));
    }

    private static Vec3 floor32(Vec3 v) { return new Vec3(Math.floor(v.x * 32) / 32, Math.floor(v.y * 32) / 32, Math.floor(v.z * 32) / 32); }

    private static boolean bonzoSolid(Vec3 at) {
        BlockState s = state((int) Math.floor(at.x), (int) Math.floor(at.y), (int) Math.floor(at.z));
        Block b = s.getBlock();
        if (s.isAir() || b instanceof net.minecraft.world.level.block.LiquidBlock || b instanceof net.minecraft.world.level.block.BaseFireBlock || b instanceof LeverBlock || b instanceof ButtonBlock ||
            b instanceof SignBlock || b instanceof net.minecraft.world.level.block.BaseTorchBlock || b instanceof net.minecraft.world.level.block.TripWireBlock ||
            b instanceof TripWireHookBlock || b instanceof net.minecraft.world.level.block.BasePressurePlateBlock || b instanceof net.minecraft.world.level.block.CarpetBlock ||
            b instanceof net.minecraft.world.level.block.AbstractSkullBlock || b instanceof net.minecraft.world.level.block.TallGrassBlock ||
            b instanceof net.minecraft.world.level.block.LightBlock || b instanceof LadderBlock || b instanceof net.minecraft.world.level.block.VineBlock) return false;
        return true;
    }

    private static boolean isGlassPane(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock && BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().endsWith("glass_pane");
    }

    private static final List<String> BALLOON_HASHES = List.of(
        "f1f99e7c394e13d199d1d22cb082d209068cd7a4de6ca09edff3fc61660ae5cf", "52dd11da04252f76b6934bc26612f54f264f30eed74df89941209e191bebc0a2",
        "7c6cc5aed49056977d89348978c0aaaaa54c968a3b411bbaba5c9970b405297", "7399ff9c40fdfb7b115addddff59c344eaa11acb0c64c6839b5f8407e36d239d",
        "a2dd1389a97fd4c86b11a1a2abad61389404d837d2caf097c91d27768363dc5a", "ac96d34a3cced4e4bba25119f74ea31f50f43f90cca2fbcbc6bae8625d60dede",
        "28c544b288f899d494f953ace7743a15fdb22d24f58f9ca298f35429fdc27b63", "cc5ec2dfbf2cf4e21bd011c784d9fc4ad7a88182bed27afd91b53b4513ce0aba",
        "3491c8fb426979da2d6327b3705a4cb9c91c2d5536223233addde21d5f58c940");

    private static ItemStack balloonHead() {
        String hash = BALLOON_HASHES.get(KRandom.DEFAULT.nextInt(BALLOON_HASHES.size()));
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        ItemStack s = head(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), "Player Head");
        s.remove(DataComponents.CUSTOM_NAME);
        return s;
    }

    private static final PerPlayer<Integer> bonzoLast = new PerPlayer<>(() -> -100);
    private static final IntArrayList liveRockets = new IntArrayList();
    private static final double BONZO_SPEED = 0.75;
    private static final int BONZO_STEPS = 170;

    private static Vec3 jerryBullet(Vec3 o, Vec3 u, int k) { return o.add(u.scale(0.25 + 0.75 * k)); }

    private static void jerry(ServerPlayer p) {
        if (jerryTick.get() != Fight.serverTick) { jerryTick.set(Fight.serverTick); jerryShots.set(0); }
        int shots = jerryShots.get() + 1;
        jerryShots.set(shots);
        if (shots > 3) return;
        Vec3 f = clickPos != null ? clickPos : p.position();
        Vec3 u = look(p);
        Vec3 o = f.add(0.0, 1.1, 0.0);
        JerryStand stand = new JerryStand(Sim.level(), f.x + u.x, f.y + u.y - 0.5, f.z + u.z);
        stand.setInvisible(true);
        stand.setSharedFlagOnFire(true);
        stand.setSilent(true);
        stand.setNoGravity(true);
        Station.setMarker(stand);
        Station.setLeverFlags(stand);
        stand.setYRot(p.getYRot());
        stand.setXRot(p.getXRot());
        stand.setHeadPose(new Rotations(p.getXRot(), 0f, 0f));
        stand.setItemSlot(EquipmentSlot.HEAD, villagerHead());
        Sim.spawn(stand);
        Sim.sound(SoundEvents.VILLAGER_TRADE, 0.5f, (88 + KRandom.DEFAULT.nextInt(26)) / 63f, f, SoundSource.NEUTRAL);
        rapidFire(p);
        jerryFly(p, stand, o, u, 1);
    }

    private static void jerryFly(ServerPlayer p, JerryStand stand, Vec3 o, Vec3 u, int k) {
        Fight.later(1, "jerry bullet", () -> {
            if (p.isRemoved()) { stand.discard(); return; }
            Vec3 b = jerryBullet(o, u, k);
            if (k < JERRY_LIFE && !jerrySolid(b)) {
                Vec3 next = jerryBullet(o, u, k + 1);
                stand.setPos(next.x, next.y - 1.6, next.z);
                jerryFly(p, stand, o, u, k + 1);
                return;
            }
            jerryBurst(p, b);
            Fight.later(1, "jerry stand gone", stand::discard);
        });
    }

    private static void jerryBurst(ServerPlayer p, Vec3 b) {
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.VILLAGER_YES),
            SoundSource.MASTER, Math.floor(b.x) + 0.5, Math.floor(b.y) + 0.5, Math.floor(b.z) + 0.5, 0.35f, 1f, KRandom.DEFAULT.nextLong()));
        Vec3 d = Fight.seenPos(p).add(0.0, 1.62, 0.0).subtract(b);
        double len = d.length();
        if (len <= 4.0 && len > 0.0) push(p, new Vec3(0.5 * d.x / len, 0.6, 0.5 * d.z / len));
        Sim.level().sendParticles(ParticleTypes.POOF, true, true, b.x, b.y, b.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    private static boolean jerrySolid(Vec3 b) {
        BlockPos pos = BlockPos.containing(b);
        BlockState s = Sim.level().getBlockState(pos);
        if (isGlassPane(s)) return true;
        VoxelShape shape = s.getCollisionShape(Sim.level(), pos, CollisionContext.empty());
        if (shape.isEmpty()) return false;
        for (AABB a : shape.toAabbs()) if (a.move(pos).contains(b)) return true;
        return false;
    }

    private static ItemStack villagerHead() {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/41b830eb4082acec836bc835e40a11282bb51193315f91184337e8d3555583\"}}}";
        ItemStack s = head(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)), "Villager");
        s.remove(DataComponents.CUSTOM_NAME);
        return s;
    }

    private static final PerPlayer<Integer> jerryTick = new PerPlayer<>(() -> -1);
    private static final PerPlayer<Integer> jerryShots = new PerPlayer<>(() -> 0);
    private static final int JERRY_LIFE = 30;

    private static final PerPlayer<Integer> rapidShots = new PerPlayer<>(() -> 0);
    private static final PerPlayer<Integer> rapidLast = new PerPlayer<>(() -> -1000);

    private static void rapidFire(ServerPlayer p) {
        int now = Fight.serverTick;
        if (now - rapidLast.get() > 80) rapidShots.set(0);
        rapidShots.set(rapidShots.get() + 1);
        rapidLast.set(now);
        p.connection.send(new ClientboundSystemChatPacket(
            Component.literal("§b-" + (14 * rapidShots.get()) + " Mana (§6Rapid-fire§b)"), true));
    }

    public static class JerryStand extends ArmorStand {
        public JerryStand(Level level, double x, double y, double z) { super(level, x, y, z); }

        @Override
        public void setSharedFlagOnFire(boolean onFire) { super.setSharedFlagOnFire(true); }
    }

    public static final PerPlayer<Integer> cloakUntil = new PerPlayer<>(() -> 0);
    private static final PerPlayer<Integer> cloakReady = new PerPlayer<>(() -> 0);

    public static boolean cloaked() { return Fight.serverTick < cloakUntil.get(); }

    private static final PerPlayer<Integer> vitality = new PerPlayer<>(() -> 0);
    private static final int VEIL_VITALITY = 122;
    private static final int VEIL_HIT = 30;
    private static final int VEIL_COOLDOWN = 100;

    private static final PerPlayer<ArrayList<Creeper>> veil = new PerPlayer<>(ArrayList::new);
    private static final PerPlayer<Double> veilAngle = new PerPlayer<>(() -> 0.0);

    public static class VeilCreeper extends Creeper {
        public VeilCreeper(Level level) { super(EntityTypes.CREEPER, level); }

        @Override public void checkDespawn() {}
        @Override public boolean isPickable() { return false; }
        @Override public boolean isPushable() { return false; }
        @Override public boolean canBeCollidedWith(Entity other) { return false; }
    }

    private static Vec3 veilPos(ServerPlayer p, int i) {
        double a = Math.toRadians(veilAngle.get() + 60.0 * i);
        return new Vec3(p.getX() + 1.5 * Math.cos(a), p.getY(), p.getZ() + 1.5 * Math.sin(a));
    }

    private static void spawnVeil(ServerPlayer p) {
        discardVeil();
        veilAngle.set(0.0);
        for (int i = 0; i < 6; i++) {
            VeilCreeper e = new VeilCreeper(Sim.level());
            e.setNoAi(true); e.setSilent(true); e.setInvulnerable(true); e.setNoGravity(true); e.setInvisible(true);
            e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false));
            e.thunderHit(Sim.level(), new LightningBolt(EntityTypes.LIGHTNING_BOLT, Sim.level()));
            e.clearFire();
            Vec3 g = veilPos(p, i);
            e.snapTo(g.x, g.y, g.z, 0f, 0f);
            veil.get().add(Sim.spawn(e));
        }
    }

    private static void discardVeil() { for (Creeper c : veil.get()) c.discard(); veil.get().clear(); }

    private static void endVeil(ServerPlayer p, String chat) { endVeil(p, chat, false); }

    private static void endVeil(ServerPlayer p, String chat, boolean soundNow) {
        int now = Fight.serverTick;
        cloakUntil.set(now); cloakReady.set(now + VEIL_COOLDOWN); vitality.set(0);
        if (chat != null) Sim.chat(chat);
        Runnable sound = () -> Sim.sound(SoundEvents.SKELETON_HURT, 4f, 1.1904762f, null, SoundSource.HOSTILE);
        if (soundNow) sound.run(); else Fight.later(1, "veil end sound", sound);
        ArrayList<Creeper> gone = new ArrayList<>(veil.get());
        veil.get().clear();
        Fight.later(2, "veil gone", () -> { for (Creeper c : gone) c.discard(); });
    }

    public static boolean veilAbsorbs() {
        if (!cloaked()) return false;
        if (vitality.get() >= VEIL_HIT) { vitality.set(vitality.get() - VEIL_HIT); return true; }
        endVeil(Sim.player(), "§cNot enough vitality! §r§dCreeper Veil §r§cDe-activated!");
        return true;
    }

    private static void tickVeil(ServerPlayer p) {
        if (!cloaked()) {
            ArrayList<Creeper> v = veil.get();
            if (!v.isEmpty()) {
                boolean allGone = true;
                for (Creeper c : v) if (!c.isRemoved()) { allGone = false; break; }
                if (allGone) v.clear();
            }
            return;
        }
        if (Masks.ghost.get()) { endVeil(p, null, true); return; }
        veilAngle.set(veilAngle.get() + 3.0);
        ArrayList<Creeper> v = veil.get();
        for (int i = 0; i < v.size(); i++) { Vec3 g = veilPos(p, i); v.get(i).snapTo(g.x, g.y, g.z, 0f, 0f); }
    }

    private static void cloak(ServerPlayer p) {
        int now = Fight.serverTick;
        if (cloaked()) { endVeil(p, "§dCreeper Veil §r§cDe-activated!"); return; }
        if (now < cloakReady.get()) { Sim.chat("§cThis ability is on cooldown for " + ((cloakReady.get() - now + 19) / 20) + "s."); return; }
        cloakUntil.set(Integer.MAX_VALUE); vitality.set(VEIL_VITALITY);
        Sim.chat("§dCreeper Veil §r§aActivated!");
        spawnVeil(p);
    }

    private static void castRod(ServerPlayer p) {
        FishingHook hook = new FishingHook(p, Sim.level(), 0, 0);
        hook.setDeltaMovement(p.getLookAngle().scale(1.5));
        ArmorStand stand = new ArmorStand(Sim.level(), hook.getX(), hook.getY(), hook.getZ());
        stand.setInvisible(true);
        stand.setSilent(true);
        stand.setNoGravity(true);
        Station.setMarker(stand);
        Sim.spawn(stand);
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.PLAYER_SPLASH),
            SoundSource.PLAYERS, 200.0, 300.0, 400.0, 0f, 0f, KRandom.DEFAULT.nextLong()));
        Masks.swapPet(p);
        Sim.spawn(hook);
        p.connection.send(new ClientboundSetEntityMotionPacket(hook.getId(), hook.getDeltaMovement()));
        Fight.later(6, "pet bobber gone", () -> { hook.discard(); stand.discard(); });
    }

    // ------------------------------------------------------------------ Superboom, Dungeonbreaker, bows

    private static void superboom(ServerPlayer p, BlockPos on, Direction face) {
        if (!(Fight.phase instanceof GoldorPhase phase)) return;
        if (on == null) return;
        Vec3 at = Vec3.atCenterOf(on);
        boomFx(Vec3.atCenterOf(on.relative(face)));
        int gate = phase.gateNear(at, 1.5);
        if (gate > 0) phase.blowGate(gate, Sim.me());
    }

    private static void superboomRun(Run run, ServerPlayer p, BlockPos on, Direction face) {
        if (p.isRemoved()) return;
        if ("SUPERBOOM_TNT".equals(idOf(p.getMainHandItem())) && !p.isCreative()) { p.getMainHandItem().shrink(1); p.containerMenu.broadcastChanges(); }
        boomFx(Vec3.atCenterOf(on.relative(face)));
        Services.secrets().onSuperboom(run, p.getUUID(), on.getX(), on.getY(), on.getZ());
    }

    private static void boomFx(Vec3 at) {
        Sim.level().sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        Sim.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 3, 1.0, 1.0, 1.0, 0.0);
        Sim.sound(SoundEvents.GENERIC_EXPLODE, 1f, (float) (Math.floor((0.81 + KRandom.DEFAULT.nextDouble() * 0.174) * 63) / 63), at);
    }

    // ------------------------------------------------------------------ Dungeonbreaker

    private static final String THAT_BLOCK = "§cA mystical force prevents you from digging that block!";
    private static final String THERE = "§cA mystical force prevents you digging there!";
    private static final String NO_CHARGES = "§cYou don't have enough charges to break this block right now!";
    public static final int MAX_CHARGES = 20;

    private static final AABB CORE_DOOR = new AABB(52.0, 115.0, 54.0, 57.0, 122.0, 55.0);
    private static final AABB S4_DEVICE = new AABB(55.0, 132.0, 142.0, 65.0, 137.0, 148.0);
    private static final AABB SS_DEVICE = new AABB(109.0, 119.0, 90.0, 112.0, 125.0, 96.0);

    private static boolean holdsSomething(BlockPos pos) {
        ServerLevel level = Sim.level();
        if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        EnumProperty<AttachFace> face = FaceAttachedHorizontalDirectionalBlock.FACE;
        EnumProperty<Direction> facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockState up = level.getBlockState(pos.above());
        Block ub = up.getBlock();
        boolean onTop = ub instanceof net.minecraft.world.level.block.CarpetBlock || ub instanceof net.minecraft.world.level.block.BasePressurePlateBlock ||
            (ub instanceof FaceAttachedHorizontalDirectionalBlock && up.getValue(face) == AttachFace.FLOOR) ||
            (ub instanceof net.minecraft.world.level.block.BaseTorchBlock && !(ub instanceof net.minecraft.world.level.block.WallTorchBlock) && !(ub instanceof net.minecraft.world.level.block.RedstoneWallTorchBlock)) ||
            ub instanceof net.minecraft.world.level.block.BaseFireBlock || ub instanceof SkullBlock || ub instanceof net.minecraft.world.level.block.StandingSignBlock ||
            ub instanceof net.minecraft.world.level.block.BannerBlock || ub instanceof net.minecraft.world.level.block.BaseRailBlock || ub instanceof net.minecraft.world.level.block.RedStoneWireBlock ||
            ub instanceof net.minecraft.world.level.block.SnowLayerBlock || ub instanceof net.minecraft.world.level.block.DoorBlock || ub instanceof net.minecraft.world.level.block.TripWireBlock ||
            ub instanceof net.minecraft.world.level.block.VegetationBlock || ub instanceof FlowerPotBlock;
        if (onTop) return true;
        BlockState down = level.getBlockState(pos.below());
        if (down.getBlock() instanceof FaceAttachedHorizontalDirectionalBlock && down.getValue(face) == AttachFace.CEILING) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState n = level.getBlockState(pos.relative(d));
            Block nb = n.getBlock();
            boolean wall = nb instanceof net.minecraft.world.level.block.WallTorchBlock || nb instanceof net.minecraft.world.level.block.RedstoneWallTorchBlock || nb instanceof WallSkullBlock ||
                nb instanceof net.minecraft.world.level.block.WallSignBlock || nb instanceof net.minecraft.world.level.block.WallBannerBlock || nb instanceof LadderBlock ||
                nb instanceof TripWireHookBlock ||
                (nb instanceof FaceAttachedHorizontalDirectionalBlock && n.getValue(face) == AttachFace.WALL);
            if (wall && n.hasProperty(facing) && n.getValue(facing) == d) return true;
            BooleanProperty side = PipeBlock.PROPERTY_BY_DIRECTION.get(d.getOpposite());
            if ((nb instanceof net.minecraft.world.level.block.FireBlock || nb instanceof net.minecraft.world.level.block.VineBlock) && side != null && n.hasProperty(side) && n.getValue(side)) return true;
        }
        return false;
    }

    private static String refusal(ServerPlayer p, BlockPos pos, BlockState s) {
        Block b = s.getBlock();
        Vec3 c = Vec3.atCenterOf(pos);
        Run run = DamageModule.activeRun();
        if (run != null) {
            if (run.t0Tick == null || run.t() < 0) return "";
            if (com.teslamaps.adm.sim.clear.secrets.BreakerRules.inGrid(pos.getX(), pos.getZ()) && com.teslamaps.adm.sim.clear.secrets.BreakerRules.isBorderOrOutside(run.plan, pos.getX(), pos.getZ()))
                return p.getEyePosition().distanceTo(c) > 5.2 ? "" : THERE;
        }
        if (SS_DEVICE.contains(c) && (b instanceof ButtonBlock || b == net.minecraft.world.level.block.Blocks.EMERALD_BLOCK || b == net.minecraft.world.level.block.Blocks.REDSTONE_LAMP || b == net.minecraft.world.level.block.Blocks.SEA_LANTERN)) return THERE;
        if (S4_DEVICE.contains(c) && (b instanceof LeverBlock || b instanceof ButtonBlock || b == net.minecraft.world.level.block.Blocks.REDSTONE_LAMP || b == net.minecraft.world.level.block.Blocks.BEDROCK)) return THERE;
        if (s.getDestroySpeed(Sim.level(), pos) < 0) return THAT_BLOCK;
        if (b == net.minecraft.world.level.block.Blocks.BARRIER || b == net.minecraft.world.level.block.Blocks.BEDROCK) return THAT_BLOCK;
        if (b instanceof net.minecraft.world.level.block.CommandBlock) return THAT_BLOCK;
        if (b == net.minecraft.world.level.block.Blocks.GOLD_BLOCK && !CORE_DOOR.contains(c)) return THAT_BLOCK;
        if (b instanceof LeverBlock || b instanceof ButtonBlock) return THAT_BLOCK;
        if (b instanceof net.minecraft.world.level.block.piston.PistonBaseBlock || b instanceof net.minecraft.world.level.block.piston.PistonHeadBlock || b instanceof net.minecraft.world.level.block.piston.MovingPistonBlock) return THAT_BLOCK;
        if (b == net.minecraft.world.level.block.Blocks.GRANITE || b == net.minecraft.world.level.block.Blocks.POLISHED_GRANITE) return THAT_BLOCK;
        if (holdsSomething(pos)) return THAT_BLOCK;
        if (b == net.minecraft.world.level.block.Blocks.REDSTONE_LAMP) return THERE;
        if (b == net.minecraft.world.level.block.Blocks.EMERALD_BLOCK) return THAT_BLOCK;
        if (p.getEyePosition().distanceTo(c) > 5.2) return "";
        Run r2 = DamageModule.activeRun();
        if (r2 != null) {
            String why = Services.secrets().breakerRefusal(r2, pos.getX(), pos.getY(), pos.getZ());
            if (why != null) return why;
        }
        return null;
    }

    private static final PerPlayer<Integer> chargesNow = new PerPlayer<>(() -> MAX_CHARGES);

    public static int charges() { return chargesNow.get(); }

    private static void setCharges(int v) { if (chargesNow.get() != v) { chargesNow.set(v); refreshBreakerLore(); } }

    private static final PerPlayer<Integer> refillStep = new PerPlayer<>(() -> 0);
    private static final java.util.Random refillRng = new java.util.Random();

    private static final class Broken {
        final BlockPos pos;
        final BlockState state;
        final int at;
        int restoreAt = Integer.MAX_VALUE;

        Broken(BlockPos pos, BlockState state, int at) { this.pos = pos; this.state = state; this.at = at; }
    }

    private static final PerPlayer<ArrayDeque<Broken>> brokenBy = new PerPlayer<>(ArrayDeque::new);

    private static ArrayDeque<Broken> broken() { return brokenBy.get(); }

    private static final PerPlayer<ArrayDeque<Broken>> window = new PerPlayer<>(ArrayDeque::new);
    private static final PerPlayer<Integer> refusedSaidAt = new PerPlayer<>(() -> -100);

    private static void resetBreaker() {
        setCharges(MAX_CHARGES); refillStep.set(0); window.get().clear(); refusedSaidAt.set(-100);
        if (!P3Sim.breakerPerma()) broken().clear();
    }

    public static void rebreak() {
        if (!P3Sim.breakerPerma()) return;
        for (ArrayDeque<Broken> l : brokenBy.values()) for (Broken b : l) Blocks.set(b.pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    public static void unbreakAll() {
        if (P3Sim.breakerPerma()) return;
        for (ArrayDeque<Broken> l : brokenBy.values()) { for (Broken b : l) restore(b); l.clear(); }
        Sim.forEachPlayer(pl -> window.get().clear());
    }

    public static void unPerma() { for (Broken b : broken()) restore(b); broken().clear(); window.get().clear(); }

    private static void mine(ServerPlayer p, BlockPos pos) {
        ServerLevel level = Sim.level();
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return;
        if (Practice.noBreaking()) { p.connection.send(new ClientboundBlockUpdatePacket(level, pos)); return; }
        int now = Fight.serverTick;
        String why = refusal(p, pos, s);
        String what;
        if (why != null) what = "refused (" + (why.isEmpty() ? "quietly" : why) + ")";
        else if (charges() <= 0) what = "refused (no charges)";
        else what = "broken, " + (charges() - (P3Sim.breakerInfinite() ? 0 : 1)) + " charges left";
        Ec.LOG.info("[p3sim] breaker: server hit {},{},{} ({}) at tick {}: {}", pos.getX(), pos.getY(), pos.getZ(), afterLastDot(s.getBlock().getDescriptionId()), now, what);
        if (why != null || charges() <= 0) {
            p.connection.send(new ClientboundBlockUpdatePacket(level, pos));
            if (why == null) { if (now - refusedSaidAt.get() >= 20) { refusedSaidAt.set(now); Sim.chat(NO_CHARGES); } }
            else if (!why.isEmpty() && now - refusedSaidAt.get() >= 20) { refusedSaidAt.set(now); Sim.chat(why); }
            return;
        }
        if (!P3Sim.breakerInfinite()) setCharges(charges() - 1);
        breakerSet(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        Run run = DamageModule.activeRun();
        if (run != null) Services.secrets().onBreakerBreak(run, p.getUUID(), pos.getX(), pos.getY(), pos.getZ());
        broken().addLast(new Broken(pos, s, now));
        Broken b = broken().getLast();
        window.get().addLast(b);
        if (window.get().size() > MAX_CHARGES) { Broken o = window.get().removeFirst(); if (!P3Sim.breakerPerma()) o.restoreAt = Math.min(o.restoreAt, now + 41); }
    }

    private static void restore(Broken b) {
        Ec.LOG.info("[p3sim] breaker: {},{},{} back on the server (regen) at tick {}", b.pos.getX(), b.pos.getY(), b.pos.getZ(), Fight.serverTick);
        breakerSet(b.pos, b.state);
    }

    private static void breakerSet(BlockPos pos, BlockState state) {
        if (DamageModule.activeRun() != null) com.teslamaps.adm.sim.run.RunBlocks.set(pos, state); else Blocks.set(pos, state);
    }

    private static void restoreBroken() {
        if (P3Sim.breakerPerma()) return;
        int now = Fight.serverTick;
        int regen = (int) Math.round(P3Sim.breakerRegen() * 20);
        for (ArrayDeque<Broken> l : brokenBy.values()) l.removeIf(b -> { if (now - b.at >= regen || now >= b.restoreAt) { restore(b); return true; } return false; });
    }

    public static void addBreakerCharges(int n) { setCharges(Math.min(charges() + n, MAX_CHARGES)); }

    private static void tickBreaker() {
        int now = Fight.serverTick;
        if (charges() >= MAX_CHARGES) refillStep.set(0);
        else {
            if (refillStep.get() == 0) refillStep.set(now + 1 + refillRng.nextInt(2 * Math.max(1, (36 / P3Sim.breakerRefill())) - 1));
            if (now >= refillStep.get()) {
                setCharges(Math.min(charges() + (refillRng.nextInt(5) == 0 ? 1 : 2), MAX_CHARGES));
                refillStep.set(0);
            }
        }
    }

    private static final PerPlayer<Integer> volleyReady = new PerPlayer<>(() -> 0);

    private static void volley(ServerPlayer p) {
        int now = Fight.serverTick;
        if (now < volleyReady.get()) { Sim.chat("§cYour Regular Ability is currently on cooldown for " + ((volleyReady.get() - now + 19) / 20) + " more seconds."); return; }
        volleyReady.set(now + 440);
        volleyArrows(p);
        Sim.chat("§aUsed §6Explosive Shot§a!");
        Fight.later(1, "explosive shot summary", () -> Sim.chat("§7Your Explosive Shot hit §c0 §7enemies for §c0 §7damage."));
    }

    public static void volleyArrows(ServerPlayer p) {
        Vec3 mid = new Vec3(p.getX(), p.getY() + p.getBbHeight() / 2, p.getZ());
        for (float dy : new float[] { -5.5f, 0f, 5.5f }) {
            double yaw = Math.toRadians((double) (p.getYRot() + dy));
            double pitch = Math.toRadians((double) p.getXRot());
            Vec3 v = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch)).scale(3.0);
            SimArrow a = Bows.launch(mid, mid, v, p, h -> {
                if (h instanceof BlockHitResult bh) {
                    Vec3 at = bh.getLocation();
                    boomFx(Vec3.atCenterOf(bh.getBlockPos().relative(bh.getDirection())));
                    if (Fight.phase instanceof GoldorPhase g) {
                        int gate = g.gateNear(at, 2.0);
                        if (gate > 0) g.blowGate(gate, Sim.me());
                    }
                }
            });
            if (a != null) a.setCritArrow(true);
        }
        Sim.sound(SoundEvents.ARROW_SHOOT, 1f, 0.8f, p.position());
    }

    private static final ArrayList<AbstractArrow> arrows = new ArrayList<>();
    private static final HashMap<AbstractArrow, Vec3> lastMotion = new HashMap<>();

    public static void tick() {
        ServerLevel level = SimServer.level();
        if (level == null) return;
        Sim.forEachPlayer(p -> {
            tickBreaker();
            Ec.safely("p3sim veil", () -> tickVeil(p));
            Ec.safely("p3sim mage", Mage::tick);
            Ec.safely("p3sim slot 9", () -> tickSlot9(p));
            Ec.safely("p3sim mining effects", () -> miningEffects(p));
        });
        restoreBroken();
        for (AbstractArrow a : level.getEntitiesOfClass(AbstractArrow.class, new AABB(-20.0, 0.0, -20.0, 160.0, 256.0, 160.0), c -> c.getOwner() instanceof Player && !(c instanceof SimArrow) && !arrows.contains(c))) arrows.add(a);
        Iterator<AbstractArrow> it = arrows.iterator();
        while (it.hasNext()) {
            AbstractArrow a = it.next();
            if (a.isRemoved()) { it.remove(); lastMotion.remove(a); lastPos.remove(a); continue; }
            Vec3 v = a.getDeltaMovement();
            if (v.lengthSqr() > 1e-3) lastMotion.put(a, v);
            Vec3 lm = lastMotion.get(a);
            Vec3 raw = lm != null ? lm : v;
            Vec3 dir = raw.lengthSqr() < 1e-6 ? Vec3.ZERO : raw.normalize();
            Vec3 lp = lastPos.get(a);
            Vec3 from = lp != null ? lp : a.position().subtract(dir);
            Vec3 to = a.position().add(dir.scale(0.6));
            lastPos.put(a, a.position());
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a));
            if (hit.getType() == HitResult.Type.BLOCK) {
                if (Fight.phase instanceof GoldorPhase g && g.devices != null && g.devices.target != null) g.devices.target.hit(hit.getBlockPos());
                a.discard();
                it.remove(); lastMotion.remove(a); lastPos.remove(a);
                continue;
            }
            if (a.tickCount > 100 || v.lengthSqr() < 1e-3) { a.discard(); it.remove(); lastMotion.remove(a); lastPos.remove(a); }
        }
    }

    private static final HashMap<AbstractArrow, Vec3> lastPos = new HashMap<>();

    // ------------------------------------------------------------------ Spirit Leap

    private static final PerPlayer<Integer> leapReady = new PerPlayer<>(() -> 0);

    private static final PerPlayer<Integer> pearlLeapReady = new PerPlayer<>(() -> 0);

    private static int leapReadyAt(String id) { return id.equals(ClearItems.SPIRIT_LEAP) ? pearlLeapReady.get() : leapReady.get(); }

    private static void openLeap(ServerPlayer p) { openLeap(p, ClearItems.INFINILEAP); }

    private static void openLeap(ServerPlayer p, String id) {
        if (id.equals(ClearItems.SPIRIT_LEAP)) resyncHeld(p);
        int now = Fight.serverTick;
        int ready = leapReadyAt(id);
        if (now < ready) {
            Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 8f, 0f, p.position(), SoundSource.HOSTILE);
            Sim.chat("§cThis ability is on cooldown for " + ((ready - now + 19) / 20) + "s."); return;
        }
        List<LeapRow> rows = runRows(p);
        if (rows == null) {
            rows = new ArrayList<>();
            for (Party.Mate m : Party.mates(p)) if (Practice.active() || m.here()) rows.add(LeapRow.of(m));
        }
        List<LeapRow> mates = rows;
        Fight.afterPing("leapOpen", () -> p.openMenu(new SimpleMenuProvider((windowId, inv, pl) -> new LeapMenu(windowId, inv, mates, id), Component.literal("Spirit Leap"))));
    }

    public static class LeapRow {
        public final String name;
        public final DungeonClass clazz;
        public final GameProfile profile;
        public final Supplier<Vec3> posNow;
        public final float yaw;
        public final float pitch;
        public final String colour;

        public LeapRow(String name, DungeonClass clazz, GameProfile profile, Supplier<Vec3> posNow, float yaw, float pitch) { this(name, clazz, profile, posNow, yaw, pitch, null); }

        public LeapRow(String name, DungeonClass clazz, GameProfile profile, Supplier<Vec3> posNow, float yaw, float pitch, String colour) {
            this.name = name;
            this.clazz = clazz;
            this.profile = profile;
            this.posNow = posNow;
            this.yaw = yaw;
            this.pitch = pitch;
            this.colour = colour;
        }

        public Vec3 pos() { return posNow.get(); }

        public String nameColour() { return colour != null ? colour : rankColour(name); }

        public static LeapRow of(Party.Mate m) { return new LeapRow(m.name, m.clazz, m.profile(), () -> m.pos(), m.yaw(), m.pitch()); }
    }

    private static List<LeapRow> runRows(ServerPlayer p) {
        Run run = com.teslamaps.adm.sim.run.Runs.current;
        if (run == null || run.isOver() || !run.stage.inDungeon()) return null;
        Map<UUID, ServerPlayer> onlines = new LinkedHashMap<>();
        for (ServerPlayer sp : Sim.players()) onlines.put(sp.getUUID(), sp);
        List<LeapRow> out = new ArrayList<>();
        for (LeapTarget t : Services.bots().leapTargets(run, p.getUUID())) {
            if (!t.here) continue;
            ServerPlayer online = onlines.get(t.uuid);
            GameProfile profile = online != null ? online.getGameProfile() : new GameProfile(t.uuid, t.name);
            DungeonClass clazz = null;
            for (DungeonClass c : DungeonClass.values()) if (c.name().equalsIgnoreCase(t.dungeonClass)) { clazz = c; break; }
            com.teslamaps.adm.sim.run.PlayerRecord rec = run.state.players.get(t.uuid);
            Object colour = rec != null ? rec.extras.get("nameColour") : null;
            out.add(new LeapRow(
                t.name, clazz, profile, () -> { com.teslamaps.adm.sim.run.PlayerRecord r = run.state.players.get(t.uuid); return r != null ? new Vec3(r.x, r.y, r.z) : new Vec3(t.x, t.y, t.z); }, t.yaw, t.pitch,
                colour instanceof String s ? s : null));
        }
        return out;
    }

    private static void openHaunt(ServerPlayer p) {
        List<LeapRow> rows = runRows(p);
        if (rows == null) {
            rows = new ArrayList<>();
            for (Party.Mate m : Party.mates(p)) if (m.here()) rows.add(LeapRow.of(m));
        }
        List<LeapRow> mates = rows;
        Fight.afterPing("hauntOpen", () -> { if (Masks.ghost.get()) p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new HauntMenu(id, inv, mates), Component.literal("Teleport to Player"))); });
    }

    private static int headSlot(int i) { List<Integer> slots = List.of(11, 12, 14, 15); return i < slots.size() ? slots.get(i) : 16; }

    public static class HauntMenu extends ChestMenu {
        public final List<LeapRow> bots;

        public HauntMenu(int id, Inventory inv, List<LeapRow> bots) {
            super(MenuType.GENERIC_9x4, id, inv, new SimpleContainer(36), 4);
            this.bots = bots;
            for (int i = 0; i < 36; i++) getContainer().setItem(i, ItemStack.EMPTY);
            for (int i = 0; i < bots.size(); i++) {
                LeapRow b = bots.get(i);
                ItemStack h = new ItemStack(Items.PLAYER_HEAD);
                h.set(DataComponents.PROFILE, ResolvableProfile.createResolved(b.profile));
                h.set(DataComponents.CUSTOM_NAME, Component.literal(b.nameColour() + b.name).withStyle(it -> it.withItalic(false)));
                List<Component> lore = new ArrayList<>();
                for (String l : List.of("§7Click to teleport to this player!", "", "§7Health: §a100%", "", "§eClick to teleport!")) lore.add(Component.literal(l).withStyle(st -> st.withItalic(false)));
                h.set(DataComponents.LORE, new ItemLore(lore));
                getContainer().setItem(headSlot(i), h);
            }
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (slot < 11 || slot > 16) return;
            String name = ChatFormatting.stripFormatting(getContainer().getItem(slot).getHoverName().getString());
            LeapRow bot = null;
            for (LeapRow b : bots) if (Objects.equals(b.name, name)) { bot = b; break; }
            if (bot == null) return;
            LeapRow target = bot;
            ServerPlayer sp = (ServerPlayer) p;
            Fight.afterPing("haunt", () -> {
                sp.closeContainer();
                Vec3 e = target.pos();
                Sim.tp(sp, e.x, e.y, e.z, target.yaw, target.pitch);
                Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1.095f, sp.position(), SoundSource.HOSTILE);
                Sim.chatStyled("§aTeleported you to " + target.name + "!");
            });
        }

        @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player p) { return true; }
    }

    public static class LeapMenu extends ChestMenu {
        public final List<LeapRow> bots;
        private final String item;
        private final Player owner;

        public LeapMenu(int id, Inventory inv, List<LeapRow> bots) { this(id, inv, bots, ClearItems.INFINILEAP); }

        public LeapMenu(int id, Inventory inv, List<LeapRow> bots, String item) {
            super(MenuType.GENERIC_9x4, id, inv, new SimpleContainer(36), 4);
            this.bots = bots;
            this.item = item;
            this.owner = inv.player;
            for (int i = 0; i < 36; i++) getContainer().setItem(i, Terminals.FILLER());
            for (int i = 0; i < bots.size(); i++) {
                LeapRow b = bots.get(i);
                ItemStack h = new ItemStack(Items.PLAYER_HEAD);
                h.set(DataComponents.PROFILE, ResolvableProfile.createResolved(b.profile));
                ChatFormatting byCode = ChatFormatting.getByCode(b.nameColour().charAt(1));
                ChatFormatting colour = byCode != null ? byCode : ChatFormatting.GREEN;
                h.set(DataComponents.CUSTOM_NAME, Component.literal(b.name).withStyle(it -> it.withItalic(false).withColor(colour)));
                h.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Click to teleport!").withStyle(it -> it.withItalic(false).withColor(ChatFormatting.YELLOW)))));
                getContainer().setItem(headSlot(i), h);
            }
        }

        @Override
        public void setSynchronizer(ContainerSynchronizer synchronizer) {
            super.setSynchronizer(owner instanceof ServerPlayer sp ? new PerSlotSync(synchronizer, sp) : synchronizer);
        }

        private boolean inClick = false;

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (slot < 11 || slot > 16) return;
            String name = ChatFormatting.stripFormatting(getContainer().getItem(slot).getHoverName().getString());
            LeapRow bot = null;
            for (LeapRow b : bots) if (Objects.equals(b.name, name)) { bot = b; break; }
            if (bot == null) return;
            LeapRow target = bot;
            ServerPlayer sp = (ServerPlayer) p;
            inClick = true;
            try {
                Fight.afterPing("leap", () -> {
                    if (inClick) Fight.later(0, "leap", () -> leap(sp, target)); else leap(sp, target);
                });
            } finally { inClick = false; }
        }

        private void leap(ServerPlayer sp, LeapRow bot) {
            if (sp.isRemoved() || sp.containerMenu != this) return;
            sp.closeContainer();
            Vec3 e = bot.pos();
            int cooldownEnd = Fight.serverTick + ClearItems.leapCooldownTicks(item);
            if (item.equals(ClearItems.SPIRIT_LEAP)) pearlLeapReady.set(cooldownEnd); else leapReady.set(cooldownEnd);
            if (ClearItems.leapConsumes(item)) useUpLeapItem(sp);
            Spots.Spot ps = null;
            if (Practice.active() && bot.clazz != null) {
                ps = Practice.leapWaypoint(P3Plan.botOrder().indexOf(bot.clazz));
                if (ps == null) ps = Practice.leapSpot(bot.clazz);
            }
            if (ps != null) Sim.tp(sp, ps.x, ps.y, ps.z, ps.yaw, ps.pitch);
            else Sim.tp(sp, e.x, e.y, e.z, bot.yaw, bot.pitch);
            Sim.sound(SoundEvents.ENDERMAN_TELEPORT, 1f, 1f, sp.position(), SoundSource.HOSTILE);
            Sim.chatStyled("§aYou have teleported to §r" + bot.nameColour() + bot.name + "§r§a!");
            sp.connection.send(new ClientboundContainerClosePacket(0));
            if (Sim.isHost(sp)) GhostCapture.event("leap", bot.clazz != null ? bot.clazz.name() : bot.name);
        }

        @Override public ItemStack quickMoveStack(Player p, int slot) { return ItemStack.EMPTY; }
        @Override public boolean stillValid(Player p) { return true; }
    }

    private static void useUpLeapItem(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        Integer slot = null;
        for (int i = 0; i < 9 && slot == null; i++) if (ClearItems.SPIRIT_LEAP.equals(idOf(inv.getItem(i)))) slot = i;
        for (int i = 9; i < inv.getContainerSize() && slot == null; i++) if (ClearItems.SPIRIT_LEAP.equals(idOf(inv.getItem(i)))) slot = i;
        if (slot == null) return;
        inv.getItem(slot).shrink(1);
        sp.inventoryMenu.broadcastChanges();
    }

    public static class PerSlotSync implements ContainerSynchronizer {
        private final ContainerSynchronizer base;
        private final ServerPlayer sp;

        public PerSlotSync(ContainerSynchronizer base, ServerPlayer sp) { this.base = base; this.sp = sp; }

        @Override
        public void sendInitialData(AbstractContainerMenu container, List<ItemStack> slotItems, ItemStack carried, int[] dataSlots) {
            for (int i = 0; i < slotItems.size(); i++) sp.connection.send(new ClientboundContainerSetSlotPacket(container.containerId, container.incrementStateId(), i, slotItems.get(i)));
            for (int i = 0; i < dataSlots.length; i++) base.sendDataChange(container, i, dataSlots[i]);
        }

        @Override public void sendSlotChange(AbstractContainerMenu container, int slot, ItemStack stack) { base.sendSlotChange(container, slot, stack); }
        @Override public void sendCarriedChange(AbstractContainerMenu container, ItemStack stack) { base.sendCarriedChange(container, stack); }
        @Override public void sendDataChange(AbstractContainerMenu container, int id, int value) { base.sendDataChange(container, id, value); }
        @Override public RemoteSlot createSlot() { return base.createSlot(); }
    }
}
