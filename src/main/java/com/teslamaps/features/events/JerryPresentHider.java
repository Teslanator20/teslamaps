package com.teslamaps.features.events;

// Concept from SkyblockAddons' JerryPresent.java
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Jerry's Workshop: hides gift presents (and their name tags) that are neither for nor from you. */
public final class JerryPresentHider {

    private static final Pattern FROM_TO = Pattern.compile("^(?:From:|To:) (?:\\[.*?] )?(\\w{1,16})");

    private static Set<Entity> hidden = Set.of();
    private static boolean active;
    private static int ticks;

    private JerryPresentHider() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("JerryPresentHider").section(Carnival.CATEGORY)
                .when(() -> cfg().hideOtherJerryPresents)
                .tick(JerryPresentHider::tick)
                .config(Carnival.CATEGORY, b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Season of Jerry");
                    b.toggle("Hide Other Players' Presents", () -> c.hideOtherJerryPresents, v -> c.hideOtherJerryPresents = v)
                            .desc("In Jerry's Workshop, gift presents that are not for you and not from you are hidden, name tags included.")
                            .tags("jerry", "present", "gift", "workshop", "winter");
                });
    }

    public static boolean shouldHide(Entity e) {
        return active && e instanceof ArmorStand && hidden.contains(e) && cfg().hideOtherJerryPresents && cfg().section(Carnival.CATEGORY);
    }

    private static void tick() {
        if (++ticks % 10 != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !SkyblockUtils.isInArea("Jerry's Workshop")) {
            active = false;
            hidden = Set.of();
            return;
        }
        String me = mc.player.getGameProfile().name();
        Set<Entity> out = new HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand from)) continue;
            String fromName = name(from);
            if (fromName == null || !fromName.startsWith("From:")) continue;
            ArmorStand lower = null, present = null;
            AABB box = new AABB(from.getX() - 0.1, from.getY() - 2, from.getZ() - 0.1, from.getX() + 0.1, from.getY() + 0.1, from.getZ() + 0.1);
            for (ArmorStand s : mc.level.getEntitiesOfClass(ArmorStand.class, box)) {
                if (s == from) continue;
                String n = name(s);
                if (n != null && (n.equals("CLICK TO OPEN") || n.startsWith("To:"))) lower = s;
                else if (!s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()) present = s;
            }
            if (lower == null) continue;
            String lowerName = name(lower);
            boolean forMe = "CLICK TO OPEN".equals(lowerName) || me.equals(player(lowerName));
            boolean fromMe = me.equals(player(fromName));
            if (forMe || fromMe) continue;
            out.add(from);
            out.add(lower);
            if (present != null) out.add(present);
        }
        hidden = out;
        active = !out.isEmpty();
    }

    private static String name(ArmorStand s) {
        Component c = s.getCustomName();
        return c == null ? null : StringUtil.stripTrim(c.getString());
    }

    private static String player(String line) {
        if (line == null) return null;
        Matcher m = FROM_TO.matcher(line);
        return m.find() ? m.group(1) : null;
    }
}
