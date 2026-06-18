package com.teslamaps.features;

import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Registers a keybind that sends a configurable chat message / command.
 * The key is bound in Minecraft's Controls menu; the message is set via /tmap msg &lt;text&gt;.
 */
public class KeybindMessage {
    private static KeyMapping key;

    public static void register() {
        key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.teslamaps.send_message",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,          // unbound by default
                KeyMapping.Category.MISC
        ));
    }

    public static KeyMapping getKey() {
        return key;
    }

    public static void tick() {
        if (key == null) return;
        Minecraft mc = Minecraft.getInstance();
        while (key.consumeClick()) {
            if (mc.player == null || mc.getConnection() == null) continue;
            String msg = TeslaMapsConfig.get().keybindChatMessage;
            if (msg == null || msg.isBlank()) continue;

            if (msg.startsWith("/")) {
                mc.getConnection().sendCommand(msg.substring(1));
            } else {
                mc.getConnection().sendChat(msg);
            }
        }
    }
}
