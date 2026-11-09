package com.teslamaps.features;

// Ported from SkyHanni's AbiphoneFeatures.kt
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

import java.util.regex.Pattern;

public final class AbiphoneAccept {

    private static final Pattern RING = Pattern.compile("^✆ (?:RING\\.{3} ?){1,3}(?:\\s*\\[PICK UP])?$");
    private static final Pattern UUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final long VALID_MS = 20_000;

    private static volatile String uuid;
    private static volatile long since;
    private static boolean keyDown;
    private static Object lastLevel;

    private AbiphoneAccept() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("AbiphoneAccept").section("Chat").when(() -> cfg().abiphoneAccept && cfg().abiphoneAcceptKey != -1)
                .tick(AbiphoneAccept::tick)
                .config("Chat", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Abiphone");
                    b.toggle("Accept Abiphone Call Key", () -> c.abiphoneAccept, v -> c.abiphoneAccept = v)
                            .desc("Picks up a ringing Abiphone call when you press the key (within 20s)");
                    b.keybind("  Accept Key", () -> c.abiphoneAcceptKey, v -> c.abiphoneAcceptKey = v);
                    b.toggle("Abiphone Do Not Disturb", () -> c.abiphoneDnd, v -> c.abiphoneDnd = v)
                            .desc("Hides incoming NPC calls and their ring lines and mutes the ringtone. Elle and Dean still get through.")
                            .tags("abiphone", "call", "dnd", "ring");
                    b.toggle("  Show Blocked Caller", () -> c.abiphoneDndCallerId, v -> c.abiphoneDndCallerId = v);
                });
    }

    public static void onChatComponent(Component message) {
        TeslaMapsConfig c = cfg();
        if (!c.abiphoneAccept || c.abiphoneAcceptKey == -1 || !c.section("Chat")) return;
        String t = com.teslamaps.utils.StringUtil.stripTrim(message.getString());
        if (!t.startsWith("✆ RING") || !RING.matcher(t).matches()) return;
        if (uuid != null && System.currentTimeMillis() - since < VALID_MS) return;
        for (Component part : message.toFlatList()) {
            if (!(part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand run)) continue;
            String cmd = run.command().toLowerCase(java.util.Locale.ROOT);
            if (!cmd.startsWith("/cb ")) continue;
            String id = cmd.substring(4).trim();
            if (!UUID.matcher(id).matches()) continue;
            uuid = id;
            since = System.currentTimeMillis();
            return;
        }
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            uuid = null;
        }
        boolean down = mc.gui.screen() == null && KeyNames.isDown(cfg().abiphoneAcceptKey);
        if (down && !keyDown) {
            String id = uuid;
            if (id != null && System.currentTimeMillis() - since < VALID_MS && mc.getConnection() != null) {
                mc.getConnection().sendCommand("cb " + id);
            }
            uuid = null;
        }
        keyDown = down;
    }
}
