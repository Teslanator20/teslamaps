package com.teslamaps.debug;

import com.teslamaps.features.EnchantColors;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** /tmap debug tooltiptest - enchant lore with mixed chroma/normal levels, no server needed. */
public class TooltipTestScreen extends Screen {

    private static final String[] RAW = {
            "§7Damage: §c+260",
            "§7Strength: §c+100",
            "§7Sea Creature Chance: §c+4%",
            "",
            "§9Angler V§9, Blessing V§9, Caster VI§9, Charm V§9, Chumming III§9, Expertise X",
            "§9Frail VI, §9Looting V, §9Luck Of The Sea VII, §9Lure VI, §9Magnet VI",
            "§9Piscary V, §9Spiked Hook VI, §9Sharpness VII, §9Looting IV, §9Smite VII",
            "§9Cubism VI, §9Ender Slayer VII, §9Execute V",
            "§9Power VI, §9Snipe IV, §9Impaling III",
            "",
            "§6Ability: Rod Of The Sea §e§lRIGHT CLICK",
            "§7Deals §c10,000 §7damage to all",
            "§7creatures nearby.",
            "§8Mana Cost: §3100",
            "",
            "§6§lLEGENDARY FISHING ROD",
    };

    private static boolean opened;

    /** -Dteslamaps.tooltiptest=true opens it once on the first tick with no screen up. */
    public static void autoOpen(net.minecraft.client.Minecraft client) {
        if (opened || !Boolean.getBoolean("teslamaps.tooltiptest")) return;
        if (client.screen != null || client.level == null) return;
        opened = true;
        client.setScreen(new TooltipTestScreen());
    }

    private final ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
    private List<Component> lore;

    public TooltipTestScreen() {
        super(Component.literal("Tooltip test"));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "TEST_ITEM");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    protected void init() {
        lore = new ArrayList<>();
        lore.add(Component.literal("§6Test Item"));
        for (String raw : RAW) lore.add(Component.literal(raw));
        EnchantColors.onTooltip(stack, lore);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // fake container: item elements and quads mixed in, like a real inventory behind a tooltip
        for (int i = 0; i < 45; i++) {
            int x = 10 + (i % 9) * 18;
            int y = 10 + (i / 9) * 18;
            ctx.fill(x, y, x + 16, y + 16, 0x60FFFFFF);
            ctx.item(i % 2 == 0 ? stack : new ItemStack(Items.ENCHANTED_BOOK), x, y);
        }

        int y = 120;
        for (Component line : lore) {
            ctx.text(this.font, line, 10, y, 0xFFFFFFFF);
            y += 12;
        }
        ctx.setTooltipForNextFrame(this.font, lore, Optional.empty(), 200, 40);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
