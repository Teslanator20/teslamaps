package com.teslamaps.features;

import com.google.gson.JsonObject;
import com.teslamaps.features.capes.CapeSync;
import com.teslamaps.utils.SkyblockUtils;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** /tmap suggest + /tmap bugreport, stored in the supabase "feedback" table. */
public class Feedback {
    public static final int MAX_CHARS = 4000;

    public static void send(boolean bug, String message, Consumer<String> result) {
        if (!CapeSync.enabled()) {
            result.accept("§cServer is not configured");
            return;
        }
        String area = SkyblockUtils.getArea();
        new Thread(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("action", "feedback");
            body.addProperty("kind", bug ? "bug" : "suggest");
            body.addProperty("message", message);
            body.addProperty("modVersion", version("teslamaps"));
            body.addProperty("mcVersion", version("minecraft"));
            if (!area.isEmpty()) body.addProperty("area", area);
            String line = CapeSync.callFunction(body, bug ? "§aBug report sent, thanks!" : "§aSuggestion sent, thanks!");
            Minecraft.getInstance().execute(() -> result.accept(line));
        }, "TeslaMaps feedback").start();
    }

    private static String version(String mod) {
        return FabricLoader.getInstance().getModContainer(mod)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
