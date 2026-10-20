package com.teslamaps.adm.misc;

import com.teslamaps.adm.Adm;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.DungeonManager;
import com.teslamaps.features.TimerTriggers;
import com.teslamaps.registry.Feature;
import com.teslamaps.registry.HudEditElement;
import com.teslamaps.utils.ItemUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Ported from Odin's InvincibilityTimer + ADM's misc/OdinMasksUsed.kt (Only Used) and InvincibilityPhoenixMixin (Phoenix 3 s)
public final class AdmInvincibility {
    private static final String[] SHOW = {"Always", "Any", "When Active", "On Cooldown"};
    private static final Pattern COOLDOWN = Pattern.compile("^Cooldown: (\\d+)s$");

    private enum Type {
        SPIRIT("^Second Wind Activated! Your Spirit Mask saved your life!$", 60, 30, 1),
        BONZO("^Your (?:. )?Bonzo's Mask saved your life!$", 60, 180, 0),
        PHOENIX("^Your Phoenix Pet saved you from certain death!$", 60, 60, 2);

        final Pattern regex;
        final int maxInvincibility, maxCooldown, icon;
        int activeTime, currentCooldown;
        boolean used;

        Type(String regex, int inv, int cd, int icon) {
            this.regex = Pattern.compile(regex);
            this.maxInvincibility = inv;
            this.maxCooldown = cd;
            this.icon = icon;
        }

        void proc(Integer seconds) {
            activeTime = maxInvincibility;
            currentCooldown = (seconds == null ? maxCooldown : seconds) * 20;
            used = true;
        }

        void tick() {
            if (currentCooldown > 0) currentCooldown--;
            if (activeTime > 0) activeTime--;
        }

        void reset() {
            activeTime = 0;
            currentCooldown = 0;
        }

        String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    private static final AdmMisc.TickPump pump = new AdmMisc.TickPump();
    private static HudEditElement hud;

    private AdmInvincibility() {}

    private static TeslaMapsConfig cfg() {
        return TeslaMapsConfig.get();
    }

    /** ADM's Invincibility Timer replaces ours (HUD, item overlay, announce). */
    public static boolean overrides() {
        return Adm.overrides(cfg().admInvincibility);
    }

    static void register() {
        AdmCore.onWorldLoad(() -> {
            for (Type t : Type.values()) { t.reset(); t.used = false; }
        });
        hud = HudEditElement.of("admInvHud", "ADM Invincibility Timer").section(Adm.SECTION)
                .enabled(() -> cfg().admInvincibility)
                .size(() -> 50, () -> 42)
                .scaleRange(0.5f, 5f)
                .preview((g, mc, x, y, s) -> draw(g, x, y, s, true));
        Feature.of("AdmInvincibility").section(Adm.SECTION).when(() -> cfg().admInvincibility)
                .tick(AdmInvincibility::tick)
                .serverChat(AdmInvincibility::onChat)
                .hud("adminvincibility", (g, d) -> {
                    if (Minecraft.getInstance().options.hideGui) return;
                    draw(g, hud.x(), hud.y(), hud.scale(), false);
                })
                .config("ADM Timers", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Invincibility Timer");
                    b.toggle("Invincibility Timer", () -> c.admInvincibility, v -> c.admInvincibility = v)
                            .desc("Odin's Invincibility Timer with ADM's additions. Replaces our own Invincibility Timer while on.")
                            .tags("mask", "bonzo", "spirit", "phoenix");
                    b.toggle("  Invincibility Alert", () -> c.admInvAlert, v -> c.admInvAlert = v)
                            .desc("Plays a sound when you get invincibility.");
                    b.toggle("  Announce Invincibility", () -> c.admInvAnnounce, v -> c.admInvAnnounce = v)
                            .desc("Announces when you get invincibility.");
                    b.dropdown("  Show", SHOW, () -> c.admInvShow, v -> c.admInvShow = v)
                            .desc("Controls when invincibility items are shown.");
                    b.color("  Equipped Mask", () -> c.admInvEquippedColor, v -> c.admInvEquippedColor = v)
                            .desc("Color of the equipped mask in the HUD. (Bonzo/Spirit)");
                    b.toggle("  Show Spirit Mask", () -> c.admInvShowSpirit, v -> c.admInvShowSpirit = v);
                    b.toggle("  Show Bonzo Mask", () -> c.admInvShowBonzo, v -> c.admInvShowBonzo = v);
                    b.toggle("  Show Phoenix Pet", () -> c.admInvShowPhoenix, v -> c.admInvShowPhoenix = v);
                    b.toggle("  Only Used", () -> c.admInvOnlyUsed, v -> c.admInvOnlyUsed = v)
                            .desc("Only show the masks and Phoenix that have saved you this run.");
                    b.toggle("  Only In Dungeons", () -> c.admInvOnlyDungeons, v -> c.admInvOnlyDungeons = v)
                            .desc("Only proc in dungeons.");
                    b.toggle("  Show In Boss", () -> c.admInvShowInBoss, v -> c.admInvShowInBoss = v)
                            .desc("Only shows invincibility timers during dungeon boss fights.");
                    b.toggle("  Show On Item", () -> c.admInvShowOnItem, v -> c.admInvShowOnItem = v)
                            .desc("Renders the cooldown on the spirit mask and bonzo mask items.");
                    b.toggle("    Phoenix As Top Left", () -> c.admInvPhoenixTopLeft, v -> c.admInvPhoenixTopLeft = v)
                            .desc("Displays the cooldown for phoenix pet on the top-left most slot in your inventory.");
                    b.toggle("    Display As Durability", () -> c.admInvDurability, v -> c.admInvDurability = v)
                            .desc("On: durability bar. Off: colored vertical slide.");
                    b.color("    Cooldown Color", () -> c.admInvCooldownColor, v -> c.admInvCooldownColor = v)
                            .desc("Color of the cooldown.");
                });
    }

    /** P3 Sim restarting its masks: everything ready again, nothing used this run. */
    public static void resetTimers() {
        for (Type t : Type.values()) { t.reset(); t.used = false; }
    }

    private static void tick() {
        int n = pump.poll();
        for (int i = 0; i < n; i++) for (Type t : Type.values()) t.tick();
    }

    private static void onChat(String raw) {
        if (cfg().admInvOnlyDungeons && !DungeonManager.isInDungeon()) return;
        String msg = AdmMisc.strip(raw);
        for (Type t : Type.values()) {
            if (!t.regex.matcher(msg).matches()) continue;
            t.proc(t == Type.BONZO ? helmetCooldown() : null);
            int usedMasks = 0;
            for (Type o : Type.values()) if (o.currentCooldown > 0) usedMasks++;
            if (cfg().admInvAnnounce) com.teslamaps.features.ChatQueue.command("pc " + t.label() + " Procced! (" + usedMasks + "/" + Type.values().length + ")");
            if (cfg().admInvAlert) AdmMisc.alert(t.label(), true);
            return;
        }
    }

    private static Integer helmetCooldown() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        ItemLore lore = p.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.LORE);
        if (lore == null) return null;
        List<Component> lines = lore.lines();
        for (int i = lines.size() - 1; i >= 0; i--) {
            Matcher m = COOLDOWN.matcher(com.teslamaps.utils.StringUtil.strip(lines.get(i).getString()).trim());
            if (m.matches()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static boolean wearing(String id) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        String worn = ItemUtil.skyblockId(p.getItemBySlot(EquipmentSlot.HEAD));
        return worn.equals(id) || worn.equals("STARRED_" + id);
    }

    private static boolean shows(Type t) {
        TeslaMapsConfig c = cfg();
        boolean setting = switch (t) {
            case SPIRIT -> c.admInvShowSpirit;
            case BONZO -> c.admInvShowBonzo;
            case PHOENIX -> c.admInvShowPhoenix;
        };
        return setting && (!c.admInvOnlyUsed || t.used);
    }

    private static void draw(GuiGraphicsExtractor g, int x, int y, float scale, boolean example) {
        TeslaMapsConfig c = cfg();
        if (!example && (!DungeonManager.isInDungeon() || (c.admInvShowInBoss && !DungeonManager.isInBossArea()))) return;
        List<Type> visible = new ArrayList<>();
        for (Type t : Type.values()) {
            boolean when = switch (c.admInvShow == null ? "Always" : c.admInvShow) {
                case "Any" -> t.activeTime > 0 || t.currentCooldown > 0;
                case "When Active" -> t.activeTime > 0;
                case "On Cooldown" -> t.currentCooldown > 0;
                default -> true;
            };
            if ((shows(t) || example) && (when || example)) visible.add(t);
        }
        if (visible.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int equipped = TeslaMapsConfig.parseColor(c.admInvEquippedColor) | 0xFF000000;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        for (int i = 0; i < visible.size(); i++) {
            Type t = visible.get(i);
            ItemStack icon = TimerTriggers.maskIcon(t.icon);
            if (icon != null) g.item(icon, 0, -1 + i * 14);
            int ty = i * 14 + 3;
            if ((t == Type.BONZO && wearing("BONZO_MASK")) || (t == Type.SPIRIT && wearing("SPIRIT_MASK"))) g.fill(14, ty, 15, ty + 9, equipped);
            String text;
            int col;
            if (t.activeTime > 0) { text = String.format(Locale.ROOT, "%.2fs", t.activeTime / 20f); col = 0xFFFFAA00; }
            else if (t.currentCooldown > 0) { text = String.format(Locale.ROOT, "%.2fs", t.currentCooldown / 20f); col = 0xFFFF5555; }
            else { text = "✔"; col = 0xFF55FF55; }
            g.text(mc.font, text, 16, ty, col, true);
        }
        g.pose().popMatrix();
    }

    /** Container slot hook (via TimerTriggers.drawSlot): the cooldown over the mask items. */
    public static void drawSlot(GuiGraphicsExtractor g, Slot slot) {
        TeslaMapsConfig c = cfg();
        if (!c.admInvShowOnItem) return;
        ItemStack st = slot.getItem();
        String id = st.isEmpty() ? "" : ItemUtil.skyblockId(st);
        double percent;
        if (id.equals("BONZO_MASK") || id.equals("STARRED_BONZO_MASK")) percent = Type.BONZO.currentCooldown / (double) (Type.BONZO.maxCooldown * 20);
        else if (id.equals("SPIRIT_MASK") || id.equals("STARRED_SPIRIT_MASK")) percent = Type.SPIRIT.currentCooldown / (double) (Type.SPIRIT.maxCooldown * 20);
        else {
            if (slot.getContainerSlot() != 9 || !c.admInvPhoenixTopLeft || !(slot.container instanceof net.minecraft.world.entity.player.Inventory)) return;
            percent = Type.PHOENIX.currentCooldown / (double) (Type.PHOENIX.maxCooldown * 20);
        }
        if (percent <= 0 || percent > 1) return;
        int col = TeslaMapsConfig.parseColor(c.admInvCooldownColor);
        int x = slot.x, y = slot.y;
        if (c.admInvDurability) {
            g.fill(x + 2, y + 13, x + 14, y + 15, 0xFF000000);
            g.fill(x + 2, y + 13, x + 14 - (int) ((1 - percent) * 12), y + 14, col);
        } else {
            g.fill(x, y + (int) ((1 - percent) * 16), x + 16, y + 16, col);
        }
    }
}
