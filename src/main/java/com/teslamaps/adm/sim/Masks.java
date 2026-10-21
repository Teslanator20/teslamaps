package com.teslamaps.adm.sim;

// Ported from ADM's p3sim/Masks.kt

import com.teslamaps.adm.sim.util.KRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

public final class Masks {
    private Masks() {}

    private static final class MaskItem {
        final String id;
        final String name;
        final int cooldown;
        final int safe;
        final String line;
        int readyAt = 0;

        MaskItem(String id, String name, int cooldown, int safe, String line) {
            this.id = id;
            this.name = name;
            this.cooldown = cooldown;
            this.safe = safe;
            this.line = line;
        }
    }

    private static final PerPlayer<List<MaskItem>> items = new PerPlayer<>(() -> List.of(
        new MaskItem("SPIRIT_MASK", "Spirit Mask", 600, 60, "§6Second Wind Activated§r§a! Your Spirit Mask saved your life!"),
        new MaskItem("BONZO_MASK", "Bonzo's Mask", 3600, 60, "§aYour §r§9 Bonzo's Mask §r§asaved your life!"),
        new MaskItem("PHOENIX", "Phoenix", 1200, 60, "§eYour §r§cPhoenix Pet §r§esaved you from certain death!")
    ));

    private static final int SAFE = 58;

    private static final String BONZO_TEX = "eyJ0aW1lc3RhbXAiOjE1ODc5MDgzMDU4MjYsInByb2ZpbGVJZCI6IjJkYzc3YWU3OTQ2MzQ4MDI5NDI4MGM4NDIyNzRiNTY3IiwicHJvZmlsZU5hbWUiOiJzYWR5MDYxMCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTI3MTZlY2JmNWI4ZGEwMGIwNWYzMTZlYzZhZjYxZThiZDAyODA1YjIxZWI4ZTQ0MDE1MTQ2OGRjNjU2NTQ5YyJ9fX0=";
    private static final String SPIRIT_TEX = "eyJ0aW1lc3RhbXAiOjE1MDUyMjI5OTg3MzQsInByb2ZpbGVJZCI6IjBiZTU2MmUxNzIyODQ3YmQ5MDY3MWYxNzNjNjA5NmNhIiwicHJvZmlsZU5hbWUiOiJ4Y29vbHgzIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsibWV0YWRhdGEiOnsibW9kZWwiOiJzbGltIn0sInVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWJiZTcyMWQ3YWQ4YWI5NjVmMDhjYmVjMGI4MzRmNzc5YjUxOTdmNzlkYTRhZWEzZDEzZDI1M2VjZTlkZWMyIn19fQ==";

    public static ItemStack SPIRIT_MASK() { return mask(SPIRIT_TEX, "STARRED_SPIRIT_MASK", SimItems.Lore.STARRED_SPIRIT_MASK_NAME, SimItems.Lore.STARRED_SPIRIT_MASK, "mythic"); }

    public static ItemStack BONZO_MASK() { return mask(BONZO_TEX, "STARRED_BONZO_MASK", SimItems.Lore.STARRED_BONZO_MASK_NAME, SimItems.Lore.STARRED_BONZO_MASK, "epic"); }

    private static List<Component> loreLines(List<String> lore) {
        List<Component> out = new ArrayList<>();
        for (String l : lore) out.add(Component.literal(l).withStyle(st -> st.withItalic(false)));
        return out;
    }

    private static ItemStack mask(String tex, String id, String name, List<String> lore, String style) {
        ItemStack s = SimItems.head(tex, name);
        s.set(DataComponents.LORE, new ItemLore(loreLines(lore)));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putBoolean("p3sim", true);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        SimItems.hy(s, null, style);
        return s;
    }

    public static final int SPARE_SLOT = 9;

    private static boolean isMask(String id) { return id != null && id.endsWith("_MASK"); }

    public static void equip(ServerPlayer p) {
        Inventory inv = p.getInventory();
        if (!P3Sim.realMasks()) {
            if (isMask(SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD)))) SimItems.wear(p, EquipmentSlot.HEAD, ItemStack.EMPTY);
            if (isMask(SimItems.idOf(inv.getItem(SPARE_SLOT)))) inv.setItem(SPARE_SLOT, ItemStack.EMPTY);
        } else {
            boolean spirit = P3Sim.wornMask() == 0;
            SimItems.wear(p, EquipmentSlot.HEAD, spirit ? SPIRIT_MASK() : BONZO_MASK());
            inv.setItem(SPARE_SLOT, spirit ? BONZO_MASK() : SPIRIT_MASK());
        }
        p.inventoryMenu.broadcastChanges();
    }

    public static void openStats(ServerPlayer p) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new StatsMenu(id, inv, p), Component.literal("Stats & Equipment")));
    }

    private static final String NECKLACE_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY5MjI5ODE4Nzc4NiwKICAicHJvZmlsZUlkIiA6ICI1MWIyZGY3NWEyYWM0OTA5YmM4YzlkMzM3Y2EwNDNkYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJMaWNvcm5lQXVCZXVycmUiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEzNjFhNjdiNjNkMDQ1YTBhNjNiNTI1YzFhNzAxMjhmNjkwOWVmMWFjN2JjYzZlNDYzMWViODk1ZjA3NTAyZCIKICAgIH0KICB9Cn0=";
    private static final String CLOAK_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY5MjI5ODIwNzA1MywKICAicHJvZmlsZUlkIiA6ICIzZWUxYWRlMzljZDI0ZjFkOWYwODliYjA2ZTkzNTY5YSIsCiAgInByb2ZpbGVOYW1lIiA6ICJSdXNvR01SIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2ZhMjQzMTE0ODU3MmZlZDdiYzFlYWNmMGQyMjlkZGIyMTE1ZDFhMmNhMTgxZDMyM2QzZmNhNTIyNmU1MTZhMWQiCiAgICB9CiAgfQp9";
    private static final String BELT_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY0MzYwMjI5OTA2MSwKICAicHJvZmlsZUlkIiA6ICI0ZTMwZjUwZTdiYWU0M2YzYWZkMmE3NDUyY2ViZTI5YyIsCiAgInByb2ZpbGVOYW1lIiA6ICJfdG9tYXRvel8iLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFkMmIwMzZkZDY2NGJiOTBjOWQ0NDNjMTk5OGZiNTI2Mzk4YWI0ZGRkZWI3OWI4NDAxYjE2YjlhNGQxMGJhMyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
    private static final String GLOVES_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY5MjI5ODIyMjY4MywKICAicHJvZmlsZUlkIiA6ICI4NzE3ZGFhNmM3OTU0NzE2YmJlYWQ0MDRkYzg0NDQzZSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTa3VsbDAwMDAiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTUyMjg2NzcyMTJiZTQzZWFhZDIzZDQ3ZWQ4NDNlMTVmYjFlNjgzODQ1OTRjMDliNThiMjNmODI0MjdlNTQ5YSIKICAgIH0KICB9Cn0=";
    private static final String BLACK_CAT_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTcwODczNzEyMTIzNSwKICAicHJvZmlsZUlkIiA6ICJmY2ZhYTg0MzA0YjE0NDUxOThkNWYxNzQ3ZjI0Y2Q5MCIsCiAgInByb2ZpbGVOYW1lIiA6ICJTdGV3eVdvbGZ5IiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzgyODJiNWE5YmJlMmNkMzIyMzcyNDAyM2NkNGY2YWQ0MTNmNWJiOWUwZWRlZjgxNzAwYjhhZmMzMDcyZDA0YTUiCiAgICB9CiAgfQp9";
    public static final String PHOENIX_TEX = "ewogICJ0aW1lc3RhbXAiIDogMTY0Mjg2NTc3MTM5MSwKICAicHJvZmlsZUlkIiA6ICJiYjdjY2E3MTA0MzQ0NDEyOGQzMDg5ZTEzYmRmYWI1OSIsCiAgInByb2ZpbGVOYW1lIiA6ICJsYXVyZW5jaW8zMDMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjZiMWI1OWJjODkwYzljOTc1Mjc3ODdkZGUyMDYwMGM4Yjg2ZjZiOTkxMmQ1MWE2YmZjZGIwZTRjMmFhM2M5NyIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";

    public static class StatsMenu extends ChestMenu {
        private final ServerPlayer sp;

        private static final Map<Integer, String> REC_NAME = Map.of(10, "§6 Blooming Bone Necklace §6✪✪✪✪✪", 19, "§d Blooming Shadow Assassin Cloak §6✪✪✪✪✪", 28, "§6Blooming Implosion Belt", 37, "§6Blooming Soulweaver Gloves §6✪✪✪✪✪", 47, "§7[Lvl 100] §6Black Cat§5 ✦");
        private static final Map<Integer, List<String>> REC_LORE = recLore();

        private static Map<Integer, List<String>> recLore() {
            Map<Integer, List<String>> m = new LinkedHashMap<>();
            m.put(10, List.of("§7Defense: §a+288", "§7Crit Chance: §9+7.75%", "§7Farming Fortune: §6+32 §9(+5)", "§7Speed: §f+38.4 §9(+6)", "", "§6Ability: Gladiator's Will ", "§7Gain §a+3 Defense §7for each enemy", "§7within §e10 §7blocks up to §a+30 Defense§7.", "§7Range increases to §e30 §7blocks and", "§7the cap increases to §a+60 Defense", "§7when you play as a §aTank §7in dungeons.", "", "§7Increases the range of your", "§aDiversion §7passive by §e15 §7blocks.", "", "§6§l§ka §6§lLEGENDARY DUNGEON NECKLACE §6§l§ka"));
            m.put(19, List.of("§7Strength: §c+160", "§7Farming Fortune: §6+38.4 §9(+6)", "§7Speed: §f+70.4 §9(+6)", "", "§6Piece Bonus: Bloodrush", "§7On teleport: Your next melee hit", "§7within §a5s §7deals §c10% §7more damage.", "§8Cooldown: §a3s", "", "§d§l§ka §d§lMYTHIC DUNGEON CLOAK §d§l§ka"));
            m.put(28, List.of("§7Defense: §a+70", "§7Farming Fortune: §6+5 §9(+5)", "§7Speed: §f+6 §9(+6)", "", "§6Ability: Consolidated ", "§7Increases all explosion damage dealt by §a25%§7.", "", "§6§l§ka §6§lLEGENDARY BELT §6§l§ka"));
            m.put(37, List.of("§7Strength: §c+64", "§7Crit Damage: §9+64%", "§7Farming Fortune: §6+32 §9(+5)", "§7Speed: §f+38.4 §9(+6)", "", "§7While in §cThe Catacombs§7, summon a", "§cHaunted Skull §7that slowly revolves", "§7around you every §a15 §7seconds or", "§7when you kill a mob with melee damage.", "", "§7When an enemy is hit by a §cHaunted", "§cSkull§7, they are stunned for §b2", "§7seconds and deal §c5% §7less damage.", "", "§6§l§ka §6§lLEGENDARY DUNGEON GLOVES §6§l§ka"));
            m.put(14, List.of("§7Stats that influence how much", "§7damage you take and deal when in", "§7combat.", "", " §c Health §f6,870.74", " §a Defense §f1,345.4", " §f True Defense §f8", " §c Strength §f1,301.96", " §9 Crit Chance §f97.75%", " §9 Crit Damage §f972.78%", " §e Attack Speed §f100%", " §c Ferocity §f0", " §e Swing Range §f8.38", " §b Intelligence §f1,074.56", " §c Ability Damage §f36%", " §c Health Regen §f284.34", " §4 Vitality §f122", " §a Mending §f110", "", "§eClick for details!"));
            m.put(15, List.of("§7Stats that influence what you can", "§7break, how quickly you can break it,", "§7and how many drops you receive", "§7when mining.", "", " §2 Breaking Power §f0", " §6 Mining Speed §f256", " §e Mining Spread §f0", " §e Gemstone Spread §f0", " §5 Pristine §f0", " §6 Mining Fortune §f232", " §6 Ore Fortune §f0", " §6 Block Fortune §f0", " §6 Dwarven Metal Fortune §f0", " §6 Gemstone Fortune §f10", "", "§eClick for details!"));
            m.put(16, List.of("§7Stats that influence how many drops", "§7you receive and how many pests", "§7spawn when farming.", "", " §2 Bonus Pest Chance §f80%", " §e Overbloom §f0", " §6 Farming Fortune §f455.8", " §6 Wheat Fortune §f0", " §6 Carrot Fortune §f0", " §6 Potato Fortune §f0", " §6 Pumpkin Fortune §f0", " §6 Sugar Cane Fortune §f0", " §6 Melon Slice Fortune §f0", " §6 Cactus Fortune §f0", " §6 Cocoa Beans Fortune §f25", " §6 Mushroom Fortune §f0", " §6 Nether Wart Fortune §f0", " §6 Sunflower Fortune §f0", " §6 Moonflower Fortune §f0", " §6 Wild Rose Fortune §f0", "", "§eClick for details!"));
            m.put(23, List.of("§7Stat that includes how many drops", "§7you receive when foraging.", "", " §7§m Sweep 0", " §6 Foraging Fortune §f105", " §6 Fig Fortune §f15", " §6 Mangrove Fortune §f15", " §6 Helix Fortune §f0", " §4 Timber §f0%", "", "§eClick for details!"));
            m.put(24, List.of("§7Stats that influence what you catch", "§7and how quickly you catch it while", "§7fishing.", "", " §b Fishing Speed §f15", " §3 Sea Creature Chance §f22.8%", " §9 Double Hook Chance §f0%", " §6 Trophy Chance §f1%", " §6 Treasure Chance §f3.2%", "", "§eClick for details!"));
            m.put(25, List.of("§7Stats that augment various aspects", "§7of your gameplay.", "", " §f Speed §f550", " §b Magic Find §f40", " §d Pet Luck §f66", " §c Heat Resistance §f1", " §b Cold Resistance §f1", " §3 Respiration §f45", " §9 Pressure Resistance §f30", " §5 Fear §f6", " §d Tracking §f0", "", "§eClick for details!"));
            m.put(32, List.of("§7Stats that influence how quickly you", "§7hunt mobs and how many shards you", "§7get for doing so.", "", " §b Pull §f0", " §d Hunting Fortune §f26", " §b❣ Charm Chance §f1.54%", "", "§eClick for details!"));
            m.put(34, List.of("§7Stats that influence how much §3Skill", "§3XP §7you gain.", "", " §3☯ Combat Wisdom §f65.5", " §3☯ Farming Wisdom §f26", " §3☯ Fishing Wisdom §f27", " §3☯ Mining Wisdom §f85", " §3☯ Foraging Wisdom §f26", " §3☯ Enchanting Wisdom §f25", " §3☯ Alchemy Wisdom §f25", " §3☯ Carpentry Wisdom §f25", " §3☯ Runecrafting Wisdom §f29", " §3☯ Taming Wisdom §f25", " §3☯ Social Wisdom §f25", " §3☯ Hunting Wisdom §f25", "", "§eClick for details!"));
            m.put(47, List.of("§8Combat Pet, Ivory Skin", "", "§7Intelligence: §b+100", "§7Speed: §f+125", "§7Magic Find: §b+15", "§7Pet Luck: §d+15", "", "§6Hunter", "§7Increases your §f Speed §7and speed", "§7cap by §a+100§7.", "", "§6Omen", "§7Grants §d+15 Pet Luck§7.", "", "§6Supernatural", "§7Grants §b+15 Magic Find§7.", "", "§6Held Item: §5Unalloyed Speed", "§7Grants §f+50 Max Speed Cap§7.", "", "§b§lMAX LEVEL", "§8▸ 477,309,419 XP", "", "§8Can be upgraded at Kat in The Hub!", "§6§lLEGENDARY"));
            m.put(50, List.of("§7View and manage all of your active", "§7potion effects.", "", "§7Drink Potions or splash them on the", "§7ground to buff yourself!", "", "§7Currently Active: §e3", "", "§8Also accessible via /effects.", "", "§eClick to view!"));
            m.put(51, List.of("§7View the available §eHypixel", "§7achievements for SkyBlock.", "", "§7These achievements reward", "§eAchievement Points§7, which let you", "§7unlock rewards on the Hypixel", "§7Network.", "", "§7Unlocked: §b225§7/§b336 §8(66%§8)", "§7Points: §e2,090§7/§e3,120 §8(66%§8)", "", "§7Legacy Unlocked: §b1", "§7Legacy Points: §e5", "", "§eClick to view achievements!"));
            return m;
        }

        public StatsMenu(int id, Inventory inv, ServerPlayer sp) {
            super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(54), 6);
            this.sp = sp;
            draw();
        }

        private ItemStack rec(int slot, ItemStack s) {
            List<String> l = REC_LORE.get(slot);
            if (l != null) s.set(DataComponents.LORE, new ItemLore(loreLines(l)));
            if (slot >= 14 && slot <= 16) s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            return s;
        }

        private ItemStack worn(EquipmentSlot eq, String empty) {
            ItemStack s = sp.getItemBySlot(eq).copy();
            return !s.isEmpty() ? s : Terminals.named(Items.GRAY_STAINED_GLASS_PANE, "§7Empty " + empty + " Slot");
        }

        private void draw() {
            var c = getContainer();
            for (int i = 0; i < 54; i++) c.setItem(i, Terminals.FILLER());
            c.setItem(2, sp.getMainHandItem().copy());
            c.setItem(10, rec(10, SimItems.head(NECKLACE_TEX, REC_NAME.get(10))));
            c.setItem(19, rec(19, SimItems.head(CLOAK_TEX, REC_NAME.get(19))));
            c.setItem(28, rec(28, SimItems.head(BELT_TEX, REC_NAME.get(28))));
            c.setItem(37, rec(37, SimItems.head(GLOVES_TEX, REC_NAME.get(37))));
            c.setItem(11, worn(EquipmentSlot.HEAD, "Helmet"));
            c.setItem(20, worn(EquipmentSlot.CHEST, "Chestplate"));
            c.setItem(29, worn(EquipmentSlot.LEGS, "Leggings"));
            c.setItem(38, worn(EquipmentSlot.FEET, "Boots"));
            c.setItem(14, rec(14, Terminals.named(Items.STONE_SWORD, "§cCombat Stats")));
            c.setItem(15, rec(15, Terminals.named(Items.STONE_PICKAXE, "§6Mining Stats")));
            c.setItem(16, rec(16, Terminals.named(Items.GOLDEN_HOE, "§aFarming Stats")));
            c.setItem(23, rec(23, Terminals.named(Items.JUNGLE_SAPLING, "§2Foraging Stats")));
            c.setItem(24, rec(24, Terminals.named(Items.FISHING_ROD, "§bFishing Stats")));
            c.setItem(25, rec(25, Terminals.named(Items.CLOCK, "§dMiscellaneous Stats")));
            c.setItem(32, rec(32, Terminals.named(Items.LEAD, "§eHunting Stats")));
            c.setItem(34, rec(34, Terminals.named(Items.BOOK, "§3Wisdom Stats")));
            Supplier<Boolean> phoenix = P3Sim::phoenix;
            boolean ph = Sim.asPlayer(sp, false, phoenix);
            c.setItem(47, ph ? SimItems.head(PHOENIX_TEX, "§7[Lvl 76] §6Phoenix") : rec(47, SimItems.head(BLACK_CAT_TEX, REC_NAME.get(47))));
            c.setItem(49, Terminals.named(Items.BARRIER, "§cClose"));
            c.setItem(50, rec(50, Terminals.named(Items.POTION, "§aActive Effects")));
            c.setItem(51, rec(51, Terminals.named(Items.DIAMOND, "§aSkyBlock Achievements")));
        }

        @Override
        public void clicked(int slot, int button, ContainerInput input, Player p) {
            if (slot == 49) { sp.closeContainer(); return; }
            int index;
            if (slot >= 54 && slot <= 80) index = slot - 45;
            else if (slot >= 81 && slot <= 89) index = slot - 81;
            else index = -1;
            ItemStack clickedItem = index >= 0 ? sp.getInventory().getItem(index) : ItemStack.EMPTY;
            String id = SimItems.idOf(clickedItem);
            if (id != null && Loadouts.isHelmet(clickedItem)) {
                ItemStack worn = sp.getItemBySlot(EquipmentSlot.HEAD).copy();
                sp.setItemSlot(EquipmentSlot.HEAD, clickedItem.copy());
                sp.getInventory().setItem(index, worn);
                if (id.endsWith("_MASK")) Sim.asPlayer(sp, () -> { P3Sim.setWornMask(id.endsWith("SPIRIT_MASK") ? 0 : 1); });
                Fight.applySpeed(sp);
                Sim.sound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 1f, 1f);
                if (!P3Sim.realMasks() && id.endsWith("_MASK")) Sim.chat("§7Turn on §eReal Masks§7 (menu, Settings) for the one you wear to be the one that saves you.");
            }
            draw();
            broadcastFullState();
            sp.inventoryMenu.broadcastChanges();
        }

        @Override
        public ItemStack quickMoveStack(Player p, int i) { return ItemStack.EMPTY; }

        @Override
        public boolean stillValid(Player p) { return true; }
    }

    public static void swapPet(ServerPlayer p) {
        P3Sim.setPhoenix(!P3Sim.phoenix());
        Fight.later(2 + KRandom.DEFAULT.nextInt(2), "pet speed", () -> Fight.applySpeed(p));
        p.getAbilities().setWalkingSpeed(Math.max(Fight.speedStat(p), 100) / 1000f);
        p.onUpdateAbilities();
        Sim.chat(P3Sim.phoenix() ? "§cAutopet §eequipped your §7[Lvl 100] §5Phoenix§e! §a§lVIEW RULE"
            : "§cAutopet §eequipped your §7[Lvl 100] §6Black Cat§5 ✦§e! §a§lVIEW RULE");
    }

    private static final PerPlayer<Integer> safeUntil = new PerPlayer<>(() -> 0);

    private static final SoundEvent PHOENIX_SCREAM = SoundEvent.createVariableRangeEvent(Identifier.parse("minecraft:mob.ghast.affectionate_scream"));

    public static void reset() {
        Sim.forEachPlayer(p -> resetOne());
        PartyClient.send("masksReset");
    }

    public static void stopped() { Sim.forEachPlayer(p -> { if (ghost.get()) endGhost(p); }); }

    public static void leftOne(ServerPlayer p) { Sim.asPlayer(p, () -> resetOne()); }

    private static void resetOne() {
        for (MaskItem it : items.get()) it.readyAt = 0;
        safeUntil.set(0);
        reviveGen.set(reviveGen.get() + 1);
        if (ghost.get()) {
            ghost.set(false);
            saved.set(null);
            ServerPlayer p = Sim.player();
            if (p != null) {
                restoreArmor(p);
                p.removeEffect(MobEffects.INVISIBILITY);
                p.getAbilities().flying = false;
                p.getAbilities().mayfly = false;
                p.onUpdateAbilities();
            }
        }
    }

    private static String worn() {
        ServerPlayer p = Sim.player();
        if (p == null) return null;
        String id = SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD));
        if (id == null) return null;
        return id.startsWith("STARRED_") ? id.substring("STARRED_".length()) : id;
    }

    public static String status() {
        String worn = worn();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (MaskItem it : items.get()) {
            if (!(!it.id.equals("PHOENIX") || P3Sim.phoenix() || !P3Sim.realMasks())) continue;
            if (!first) sb.append(" ");
            first = false;
            int left = it.readyAt - Fight.serverTick;
            String mark = P3Sim.realMasks() && it.id.equals(worn) ? "§e⛑" : "";
            sb.append(mark).append(left <= 0 ? "§a" + it.name : "§c" + it.name + " " + ((left + 19) / 20) + "s");
        }
        return sb.toString();
    }

    public static boolean covered() { return ghost.get() || Fight.serverTick < safeUntil.get(); }

    public static boolean tryProc(ServerPlayer p) {
        int now = Fight.serverTick;
        List<MaskItem> ready = new ArrayList<>();
        for (MaskItem it : items.get()) if (it.readyAt <= now && (!it.id.equals("PHOENIX") || P3Sim.phoenix() || !P3Sim.realMasks())) ready.add(it);
        MaskItem item = null;
        if (!P3Sim.realMasks()) item = ready.isEmpty() ? null : ready.get(0);
        else {
            String head = SimItems.idOf(p.getItemBySlot(EquipmentSlot.HEAD));
            if (head != null && head.startsWith("STARRED_")) head = head.substring("STARRED_".length());
            for (MaskItem it : ready) if (it.id.equals(head)) { item = it; break; }
            if (item == null) for (MaskItem it : ready) if (it.id.equals("PHOENIX")) { item = it; break; }
        }
        if (item == null) return false;
        item.readyAt = now + item.cooldown;
        if (item.id.equals("PHOENIX") && !P3Sim.phoenix()) { P3Sim.setPhoenix(true); Fight.applySpeed(p); }
        safeUntil.set(now + SAFE);
        Sim.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 3, 1.0, 1.0, 1.0, 0.0);
        if (item.id.equals("PHOENIX")) Sim.level().sendParticles(ParticleTypes.LAVA, p.getX(), p.getY(), p.getZ(), 18, 0.1, 0.1, 0.1, 0.08);
        if (item.id.equals("PHOENIX")) {
            Sim.chat(item.line);
            Sim.sound(SoundEvents.LAVA_EXTINGUISH, 1f, 1.492f, null, SoundSource.BLOCKS);
            Sim.sound(SoundEvents.ZOMBIE_INFECT, 1f, 1.19f, null, SoundSource.HOSTILE);
            Sim.sound(SoundEvents.ZOMBIE_INFECT, 1f, 1.19f, null, SoundSource.HOSTILE);
            Sim.sound(PHOENIX_SCREAM, 1f, 1.41f + KRandom.DEFAULT.nextFloat() * 0.15f);
        } else {
            Sim.sound(SoundEvents.GENERIC_EAT, 0.9f, 0.59f, null, SoundSource.PLAYERS);
            Sim.sound(SoundEvents.ZOMBIE_VILLAGER_CURE, 1f, 2f, null, SoundSource.HOSTILE);
            Sim.chat(item.line);
        }
        return true;
    }

    public static void die(ServerPlayer p, String by) { die(p, by, null, false); }

    public static void die(ServerPlayer p, String by, Integer reviveIn) { die(p, by, reviveIn, false); }

    public static void die(ServerPlayer p, String by, Integer reviveIn, boolean crushed) {
        Sim.chat(crushed ? "§c ☠ §r§7You were crushed and became a ghost." : by == null ? "§c ☠ §r§7You died and became a ghost." : "§c ☠ §r§7You were killed by " + by + " and became a ghost.");
        String name = p.getGameProfile().name();
        Sim.asPlayer(p, () -> {
            Sim.chatOthers(crushed ? "§c ☠ §r§7" + name + " was crushed and became a ghost." : by == null ? "§c ☠ §r§7" + name + " died and became a ghost." : "§c ☠ §r§7" + name + " was killed by " + by + " and became a ghost.");
        });
        if (KRandom.DEFAULT.nextInt(100) < 71) Sim.chat("§aYour Revive Stone revived you and broke!");
        becomeGhost(p, reviveIn);
        Sim.sound(SoundEvents.GENERIC_HURT, 1f, 0.889f, null, SoundSource.NEUTRAL);
    }

    public static void reviveGhost(ServerPlayer p, String by) { reviveGhost(p, by, "§b"); }

    public static void reviveGhost(ServerPlayer p, String by, String byColour) {
        if (!ghost.get()) return;
        reviveGen.set(reviveGen.get() + 1);
        endGhost(p);
        Sim.everyone(() -> Sim.chat("§a ❣ §r§b" + p.getGameProfile().name() + "§r§a was revived by §r" + byColour + (by != null ? by : p.getGameProfile().name()) + "§r§a!"));
    }

    public static void hit(ServerPlayer p, String by) { hit(p, by, null); }

    public static void hit(ServerPlayer p, String by, Runnable goldor) {
        if (covered()) { if (goldor != null) goldor.run(); return; }
        if (by != null && SimItems.veilAbsorbs()) return;
        if (tryProc(p)) { if (goldor != null) goldor.run(); return; }
        die(p, by);
        if (goldor != null) goldor.run();
    }

    public static final PerPlayer<Boolean> ghost = new PerPlayer<>(() -> false);
    private static final PerPlayer<List<ItemStack>> saved = new PerPlayer<>(() -> null);
    private static final PerPlayer<Integer> savedSlot = new PerPlayer<>(() -> 0);
    private static final PerPlayer<Map<EquipmentSlot, ItemStack>> savedArmor = new PerPlayer<>(() -> null);
    private static final PerPlayer<Integer> reviveGen = new PerPlayer<>(() -> 0);

    private static final List<EquipmentSlot> ARMOR_SLOTS = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private static void restoreArmor(ServerPlayer p) {
        Map<EquipmentSlot, ItemStack> a = savedArmor.get();
        if (a != null) a.forEach(p::setItemSlot);
        savedArmor.set(null);
    }

    private static ItemStack ghostItem(Item base, String name) { return ghostItem(base, name, 1); }

    private static ItemStack ghostItem(Item base, String name, int count) {
        ItemStack it = new ItemStack(base, count);
        it.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        return it;
    }

    private static void becomeGhost(ServerPlayer p) { becomeGhost(p, null); }

    private static void becomeGhost(ServerPlayer p, Integer reviveIn) {
        ghost.set(true);
        Inventory inv = p.getInventory();
        List<ItemStack> sv = new ArrayList<>();
        for (int i = 0; i < 36; i++) sv.add(inv.getItem(i).copy());
        saved.set(sv);
        savedSlot.set(inv.getSelectedSlot());
        for (int i = 0; i < 36; i++) if (!"HYPERION".equals(SimItems.idOf(inv.getItem(i)))) inv.setItem(i, ItemStack.EMPTY);
        inv.setItem(0, SimItems.ghostStack(Items.PLAYER_HEAD, "HAUNT_ABILITY", "§aHaunt"));
        ItemStack a3, a5;
        DungeonClass cls = P3Sim.myClass();
        if (cls == DungeonClass.MAGE) {
            a3 = SimItems.ghostStack(Items.BRICKS, "MAGE_DUNGEON_ABILITY_1", "§aPop-up Wall");
            a5 = SimItems.ghostStack(Items.BLAZE_POWDER, "MAGE_DUNGEON_ABILITY_3", "§aFireball");
        } else if (cls == DungeonClass.TANK) {
            a3 = SimItems.ghostStack(Items.PLAYER_HEAD, "TANK_DUNGEON_ABILITY_1", "§aStun Potion");
            a5 = SimItems.ghostStack(Items.PLAYER_HEAD, "TANK_DUNGEON_ABILITY_2", "§aAbsorption Potion");
        } else {
            a3 = SimItems.ghostStack(Items.PLAYER_HEAD, "WARRIOR_DUNGEON_ABILITY_1", "§aStrength Potion");
            a5 = SimItems.ghostStack(Items.IRON_AXE, "GHOST_THROWING_AXE", "§aGhost Axe");
        }
        inv.setItem(3, a3);
        inv.setItem(5, a5);
        inv.setItem(8, SimItems.ghostStack(Items.FILLED_MAP, "MAP", "§aMagical Map"));
        inv.setItem(9, SimItems.ghostStack(Items.ARROW, null, "§fGhost Arrow", 10));
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, -1, 0, false, false, false));
        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
        Sim.title("§eYou became a ghost!", "§7Hopefully your teammates will be able to revive you!", 0, 100, 5);
        int delay = reviveIn != null ? reviveIn : (KRandom.DEFAULT.nextInt(10) < 6 ? KRandom.DEFAULT.nextInt(120, 126) : KRandom.DEFAULT.nextInt(119, 138));
        reviveGen.set(reviveGen.get() + 1);
        int gen = reviveGen.get();
        Corpse.spawn(p, delay);
        Map<EquipmentSlot, ItemStack> armor = new LinkedHashMap<>();
        for (EquipmentSlot it : ARMOR_SLOTS) armor.put(it, p.getItemBySlot(it).copy());
        savedArmor.set(armor);
        for (EquipmentSlot it : ARMOR_SLOTS) p.setItemSlot(it, ItemStack.EMPTY);
        for (int k = 0; k < 5; k++) {
            final int kk = k;
            Fight.later(delay - 100 + 20 * kk, "revive title", () -> {
                if (ghost.get() && gen == reviveGen.get()) Sim.title("§e§lBEING REVIVED", "§aYou will be revived in " + (5 - kk) + "s", 0, 30, 0);
            });
        }
        if (reviveIn == null) Fight.later(delay, "revive", () -> { if (ghost.get() && gen == reviveGen.get()) revive(p); });
    }

    private static void revive(ServerPlayer p) {
        Party.Mate bot = null;
        double best = 0;
        for (Party.Mate it : Party.mates(p)) {
            if (!it.here()) continue;
            if (it.player != null) {
                Supplier<Boolean> isGhost = ghost::get;
                if (Sim.asPlayer(it.player, false, isGhost)) continue;
            }
            double d = it.pos().distanceToSqr(p.position());
            if (bot == null || d < best) { bot = it; best = d; }
        }
        endGhost(p);
        String byName = bot != null ? bot.name : p.getGameProfile().name();
        Sim.everyone(() -> Sim.chat("§a ❣ §r§b" + p.getGameProfile().name() + "§r§a was revived by §r§b" + byName + "§r§a!"));
        Sim.chat("§cAutopet rule triggered but couldn't find your pet!");
        if (bot != null) Sim.tp(p, bot.pos().x, bot.pos().y, bot.pos().z, p.getYRot(), p.getXRot());
    }

    private static void endGhost(ServerPlayer p) {
        ghost.set(false);
        p.removeEffect(MobEffects.INVISIBILITY);
        p.getAbilities().flying = false;
        p.getAbilities().mayfly = false;
        p.onUpdateAbilities();
        List<ItemStack> s = saved.get();
        if (s != null) for (int i = 0; i < s.size(); i++) p.getInventory().setItem(i, s.get(i));
        restoreArmor(p);
        saved.set(null);
        int slot = savedSlot.get();
        p.getInventory().setSelectedSlot(slot);
        p.connection.send(new ClientboundSetHeldSlotPacket(slot));
        p.containerMenu.broadcastChanges();
        p.inventoryMenu.broadcastChanges();
    }
}
