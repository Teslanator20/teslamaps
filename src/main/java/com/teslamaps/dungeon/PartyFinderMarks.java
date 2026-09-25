package com.teslamaps.dungeon;

// SkyHanni DungeonFinderFeatures (member / ineligible patterns), SkyblockExtras "Meets Requirements"
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.mixin.HandledScreenAccessor;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PartyFinderMarks {

    private static final Pattern MEMBER = Pattern.compile("^ (?<name>\\w{1,16}): (?<cls>\\w+) \\(.*?(?<lvl>\\d+).*?\\)$");
    private static final Pattern INELIGIBLE = Pattern.compile("^(Requires .*|You don't meet the requirement!|Complete previous floor first!)$");
    private static final int GREEN = 0x5500FF00, ORANGE = 0x66FF9900, RED = 0x66FF0000;

    private record Mark(int slot, int color, String count) {}

    private static volatile List<Mark> marks = List.of();
    private static Object screenRef;
    private static int tickCounter;

    private PartyFinderMarks() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("PartyFinderMarks").section("Party").when(() -> cfg().partyFinderMarks)
                .tick(PartyFinderMarks::tick);
    }

    private static AbstractContainerScreen<?> finder() {
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs)) return null;
        return StringUtil.strip(cs.getTitle().getString()).trim().equals("Party Finder") ? cs : null;
    }

    private static void tick() {
        if (++tickCounter % 5 != 0) return;
        AbstractContainerScreen<?> cs = finder();
        screenRef = cs;
        if (cs == null) { marks = List.of(); return; }
        int min = cfg().partyFinderMinClass;
        List<Mark> out = new ArrayList<>();
        for (Slot slot : cs.getMenu().slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            ItemStack st = slot.getItem();
            if (st.isEmpty() || !st.is(Items.PLAYER_HEAD)) continue;
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore == null) continue;
            int members = 0;
            boolean under = false, blocked = false, isParty = false;
            for (Component line : lore.lines()) {
                String raw = StringUtil.strip(line.getString());
                String l = raw.trim();
                if (l.startsWith("Members:") || l.startsWith("Dungeon:")) isParty = true;
                if (INELIGIBLE.matcher(l).matches()) blocked = true;
                Matcher m = MEMBER.matcher(raw);
                if (m.matches()) {
                    members++;
                    if (min > 0 && Integer.parseInt(m.group("lvl")) < min) under = true;
                }
            }
            if (!isParty) continue;
            int color = blocked ? RED : under ? ORANGE : GREEN;
            out.add(new Mark(slot.index, color, members > 0 ? String.valueOf(members) : null));
        }
        marks = out;
    }

    public static void render(GuiGraphicsExtractor ctx) {
        TeslaMapsConfig c = cfg();
        if (!c.partyFinderMarks || !c.section("Party")) return;
        List<Mark> l = marks;
        if (l.isEmpty()) return;
        if (!(Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> cs) || cs != screenRef) return;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        var slots = cs.getMenu().slots;
        var font = Minecraft.getInstance().font;
        for (Mark m : l) {
            if (m.slot < 0 || m.slot >= slots.size()) continue;
            Slot s = slots.get(m.slot);
            int sx = acc.getX() + s.x, sy = acc.getY() + s.y;
            ctx.fill(sx, sy, sx + 16, sy + 16, m.color);
            if (c.partyFinderCount && m.count != null)
                ctx.text(font, m.count, sx + 17 - font.width(m.count), sy + 9, 0xFFFFFFFF, true);
        }
    }
}
