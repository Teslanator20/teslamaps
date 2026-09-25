package com.teslamaps.features;

// Ported from Skytils' features/impl/misc/ScamCheck.kt
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.TeslaMaps;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScamCheck {

    private static final String P = "§b[TeslaMaps] ";
    private static final String API = "https://api.skytils.gg/api/scams/check?uuid=";
    private static final Pattern TRADE_TITLE = Pattern.compile("^You {4,}\\w{1,16}$");
    private static final Pattern TRADING_WITH = Pattern.compile("Trading with.*? (\\w{1,16})\\.");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private static int checkedContainer = -1;

    private ScamCheck() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ScamCheck").section("Inventory")
                .when(() -> cfg().scamCheck)
                .tick(ScamCheck::tick)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = cfg();
                    b.label("Trading");
                    b.toggle("Scam Check on Trade", () -> c.scamCheck, v -> c.scamCheck = v)
                            .desc("Checks your trade partner against the Skytils scammer database when a trade window opens. Also /tmap scamcheck <name>");
                    b.toggle("  Close Trade on Known Scammer", () -> c.scamCheckClose, v -> c.scamCheckClose = v);
                });

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("tmap")
                .then(ClientCommands.literal("scamcheck")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    c.getSource().sendFeedback(Component.literal(P + "§7Checking §f" + name + "§7..."));
                                    check(name, null, r -> { });
                                    return 1;
                                })))));
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        int id = screen.getMenu().containerId;
        if (id == checkedContainer) return;
        if (!TRADE_TITLE.matcher(com.teslamaps.utils.StringUtil.stripTrim(screen.getTitle().getString())).matches()) return;
        if (screen.getMenu().slots.size() <= 41) return;
        ItemStack stack = screen.getMenu().getSlot(41).getItem();
        if (stack.isEmpty()) return;
        String other = null;
        for (String line : ItemUtil.loreLines(stack)) {
            Matcher m = TRADING_WITH.matcher(line);
            if (m.find()) { other = m.group(1); break; }
        }
        if (other == null) return;
        checkedContainer = id;
        String uuid = null;
        for (Player p : mc.level.players()) {
            if (p.getUUID().version() == 4 && p.getGameProfile().name().equals(other)) { uuid = p.getUUID().toString(); break; }
        }
        check(other, uuid, scammer -> {
            if (!scammer || !cfg().scamCheckClose) return;
            if (mc.screen instanceof AbstractContainerScreen<?> s && s.getMenu().containerId == id && mc.player != null) {
                mc.player.closeContainer();
            }
        });
    }

    /** Result lands on the main thread: true = known scammer. */
    private static void check(String name, String knownUuid, Consumer<Boolean> done) {
        CompletableFuture<String> uuid = knownUuid != null ? CompletableFuture.completedFuture(knownUuid) : HypixelApi.nameToUuid(name);
        uuid.thenApplyAsync(u -> {
            if (u == null) return new Result(null, "§cCould not get the UUID of " + name + " - nicked?");
            return query(name, com.teslamaps.api.PvApi.dashed(u.replace("-", "")));
        }).thenAccept(r -> Minecraft.getInstance().execute(() -> {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal(P + r.text));
            if (r.scammer != null) done.accept(r.scammer);
        }));
    }

    private record Result(Boolean scammer, String text) {}

    private static Result query(String name, String uuid) {
        try {
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(API + uuid + "&utm_source=tradewindow"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Skytils/1.10.0 TeslaMaps")
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return new Result(null, "§cScam check failed (HTTP " + resp.statusCode() + ")");
            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            boolean scammer = json.has("isScammer") && json.get("isScammer").getAsBoolean();
            if (!scammer) return new Result(false, "§a" + name + " is not a known scammer");
            StringBuilder sb = new StringBuilder("§c§l" + name + " is a known scammer!§r\n§bDatabases:");
            if (json.has("reasons") && json.get("reasons").isJsonObject()) {
                for (var e : json.getAsJsonObject("reasons").entrySet()) {
                    JsonElement v = e.getValue();
                    sb.append("\n §b").append(e.getKey()).append(": §f").append(v.isJsonPrimitive() ? v.getAsString() : v.toString());
                }
            }
            return new Result(true, sb.toString());
        } catch (Exception e) {
            TeslaMaps.LOGGER.warn("Scam check for {} failed: {}", name, e.toString());
            return new Result(null, "§cScam check failed (no answer from the Skytils API)");
        }
    }
}
