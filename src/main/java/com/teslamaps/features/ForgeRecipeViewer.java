package com.teslamaps.features;

// Ported from SkyOcean's ForgeRecipeScreen.kt, recipes from the NEU repo item files
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.utils.RepoCache;
import com.teslamaps.utils.SkyblockItems;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ForgeRecipeViewer {

    private static final String URL =
            "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/%s.json";
    private static final int[][] SIZES = {
            {}, {10}, {10, 11}, {10, 11, 19}, {10, 11, 19, 20}, {10, 11, 19, 20, 28},
            {10, 11, 19, 20, 28, 29}, {10, 11, 12, 19, 20, 28, 29}, {10, 11, 12, 19, 20, 21, 28, 29},
            {10, 11, 12, 19, 20, 21, 28, 29, 30}};

    record Input(String id, int count) {}
    record Recipe(List<Input> inputs, int count, long seconds) {}

    private ForgeRecipeViewer() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("ForgeRecipeViewer").section("Inventory")
                .config("Inventory", b -> {
                    b.label("Recipe Viewers");
                    b.toggle("Forge Recipe Viewer", () -> cfg().forgeRecipeViewer, v -> cfg().forgeRecipeViewer = v)
                            .desc("/viewforgerecipe <item> shows the forge inputs and time from the NEU repo.");
                });
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("viewforgerecipe")
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                        .suggests((c, b) -> {
                            String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                            if (rem.isEmpty()) return b.buildFuture();
                            int n = 0;
                            for (String name : SkyblockItems.allNames()) {
                                String low = name.toLowerCase(Locale.ROOT);
                                if (low.startsWith(rem) && !low.equals(rem)) {
                                    b.suggest(name);
                                    if (++n >= 40) break;
                                }
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> {
                            open(StringArgumentType.getString(c, "item"));
                            return 1;
                        }))));
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[TeslaMaps] §7" + s));
    }

    private static void open(String input) {
        TeslaMapsConfig c = cfg();
        if (!c.forgeRecipeViewer || !c.section("Inventory")) {
            msg("Enable Inventory > Forge Recipe Viewer first.");
            return;
        }
        String trimmed = input.trim();
        String id = SkyblockItems.idForName(trimmed);
        if (id == null) id = trimmed.toUpperCase(Locale.ROOT).replace(' ', '_');
        String finalId = id;
        CompletableFuture.supplyAsync(() -> load(finalId)).thenAccept(recipe -> Minecraft.getInstance().execute(() -> {
            if (recipe == null) {
                msg("No forge recipe found for " + finalId + ".");
                return;
            }
            Minecraft.getInstance().setScreen(new ForgeScreen(finalId, recipe));
        }));
    }

    private static Recipe load(String id) {
        try {
            String file = URLEncoder.encode(id.replace(':', '-'), StandardCharsets.UTF_8).replace("+", "%20");
            JsonElement root = RepoCache.fetch("neu-item-" + id.replace(':', '-'), String.format(URL, file), 15);
            if (!root.isJsonObject()) return null;
            JsonObject item = root.getAsJsonObject();
            if (!item.has("recipes") || !item.get("recipes").isJsonArray()) return null;
            for (JsonElement el : item.getAsJsonArray("recipes")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("type") || !"forge".equals(o.get("type").getAsString())) continue;
                List<Input> inputs = new ArrayList<>();
                if (o.has("inputs")) {
                    for (JsonElement in : o.getAsJsonArray("inputs")) {
                        String v = in.getAsString();
                        int colon = v.lastIndexOf(':');
                        int count = 1;
                        String ingId = v;
                        if (colon > 0) {
                            try {
                                count = (int) Math.round(Double.parseDouble(v.substring(colon + 1)));
                                ingId = v.substring(0, colon);
                            } catch (NumberFormatException ignored) {}
                        }
                        inputs.add(new Input(ingId, count));
                    }
                }
                int count = o.has("count") ? Math.max(1, o.get("count").getAsInt()) : 1;
                long secs = o.has("duration") ? o.get("duration").getAsLong() : 0;
                return new Recipe(inputs, count, secs);
            }
        } catch (Exception e) {
            com.teslamaps.TeslaMaps.LOGGER.warn("[ForgeRecipe] {} failed: {}", id, e.toString());
        }
        return null;
    }

    static String time(long s) {
        if (s <= 0) return "?";
        long d = s / 86400, h = s % 86400 / 3600, m = s % 3600 / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (sec > 0 && d == 0) sb.append(sec).append("s");
        return sb.toString().trim();
    }

    static final class ForgeScreen extends Screen {
        private static final Identifier BG = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
        private static final int W = 176, H = 17 + 6 * 18 + 7;

        private final String id;
        private final Recipe recipe;
        private final ItemStack furnace, clock;
        private final String[] slotIds = new String[54];
        private final int[] slotCounts = new int[54];
        private int left, top;

        ForgeScreen(String id, Recipe recipe) {
            super(Component.literal("Forge"));
            this.id = id;
            this.recipe = recipe;
            furnace = new ItemStack(Items.FURNACE);
            furnace.set(DataComponents.CUSTOM_NAME, Component.literal("§aForge Recipe"));
            furnace.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("§f<-- §eRequired Items"), Component.literal("§e      Result item §f-->"))));
            clock = new ItemStack(Items.CLOCK);
            clock.set(DataComponents.CUSTOM_NAME, Component.literal("§aTime: §e" + time(recipe.seconds())));
            List<Input> in = recipe.inputs();
            int[] slots = SIZES[Math.min(in.size(), 9)];
            for (int i = 0; i < slots.length; i++) {
                slotIds[slots[i]] = in.get(i).id();
                slotCounts[slots[i]] = in.get(i).count();
            }
            slotIds[16] = id;
            slotCounts[16] = recipe.count();
        }

        @Override
        protected void init() {
            left = (width - W) / 2;
            top = (height - H) / 2;
            ScreenFit.clamp(this);
        }

        private ItemStack stackAt(int slot) {
            if (slot == 14) return furnace;
            if (slot == 23) return clock;
            String sid = slotIds[slot];
            if (sid == null) return ItemStack.EMPTY;
            ItemStack base = SkyblockItems.stackNow(sid.replace('-', ':'));
            if (base.isEmpty()) base = SkyblockItems.stackNow(sid);
            if (base.isEmpty()) base = new ItemStack(Items.BARRIER);
            return base;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
            super.extractRenderState(ctx, mouseX, mouseY, delta);
            ctx.blit(RenderPipelines.GUI_TEXTURED, BG, left, top, 0, 0, W, 17 + 6 * 18, 256, 256);
            ctx.blit(RenderPipelines.GUI_TEXTURED, BG, left, top + 17 + 6 * 18, 0, 215, W, 7, 256, 256);
            ctx.text(font, "Forge", left + 8, top + 6, 0xFF404040, false);
            int hovered = -1;
            for (int s = 0; s < 54; s++) {
                int x = left + 8 + (s % 9) * 18, y = top + 18 + (s / 9) * 18;
                ItemStack st = stackAt(s);
                if (st.isEmpty()) {
                    ctx.fill(x, y, x + 16, y + 16, 0xFF2B2B2B);
                } else {
                    ctx.item(st, x, y);
                    int cnt = slotIds[s] != null ? slotCounts[s] : 1;
                    if (cnt > 1) {
                        String t = String.valueOf(cnt);
                        ctx.text(font, t, x + 17 - font.width(t), y + 9, 0xFFFFFFFF, true);
                    }
                }
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    hovered = s;
                    ctx.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
                }
            }
            if (hovered >= 0) {
                ItemStack st = stackAt(hovered);
                if (!st.isEmpty()) {
                    if (slotIds[hovered] != null) {
                        String name = SkyblockItems.coloredName(slotIds[hovered].replace('-', ':'));
                        List<Component> tip = new ArrayList<>();
                        tip.add(Component.literal((name != null ? name : "§f" + slotIds[hovered]) + " §8x" + slotCounts[hovered]));
                        tip.add(Component.literal("§8" + slotIds[hovered]));
                        ctx.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
                    } else {
                        ctx.setTooltipForNextFrame(font, st, mouseX, mouseY);
                    }
                }
            }
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }
}
