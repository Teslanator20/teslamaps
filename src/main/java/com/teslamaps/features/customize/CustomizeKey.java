package com.teslamaps.features.customize;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.screen.customize.CustomizeScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/** Hover key for the customize screen and the {@code /tmap customize <part>} shortcuts on the held item. */
public final class CustomizeKey {

    private CustomizeKey() {}

    public static boolean onKey(int key, AbstractContainerScreen<?> screen, Slot hovered) {
        int bind = TeslaMapsConfig.get().customizeKey;
        if (bind == -1 || key != bind || hovered == null) return false;
        Minecraft mc = Minecraft.getInstance();
        ItemStack stack = hovered.getItem();
        if (stack.isEmpty() || mc.player == null) return false;
        if (Customize.uuid(stack).isEmpty()) {
            mc.player.sendSystemMessage(Component.literal("§c[TeslaMaps] §f").append(stack.getHoverName())
                    .append(Component.literal(" §fcan't be customized"
                            + (TeslaMapsConfig.get().customizeNoUuid ? "" : " (no uuid, see Items Without UUID)"))));
            return true;
        }
        if (((com.teslamaps.injected.CustomizableStack) (Object) stack).teslamaps$uuid().isEmpty())
            mc.player.sendSystemMessage(Component.literal("§e[TeslaMaps] §fNo uuid: this shows on every copy of this item"));
        ItemStack copy = stack.copy();
        if (screen instanceof InventoryScreen) {
            mc.gui.setScreen(new CustomizeScreen(screen, copy));
        } else {
            mc.player.closeContainer();
            mc.gui.setScreen(new CustomizeScreen(null, copy));
        }
        return true;
    }

    // ---------------------------------------------------------------- commands

    private static final String[] PARTS = {"all", "name", "dye", "trim", "head", "glint", "model", "armormodel"};

    public static LiteralArgumentBuilder<FabricClientCommandSource> attach(LiteralArgumentBuilder<FabricClientCommandSource> root) {
        return root
                .then(ClientCommands.literal("rename")
                        .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> rename(c.getSource(), StringArgumentType.getString(c, "name")))))
                .then(ClientCommands.literal("dye")
                        .then(ClientCommands.argument("hex", StringArgumentType.word())
                                .executes(c -> dye(c.getSource(), StringArgumentType.getString(c, "hex")))))
                .then(ClientCommands.literal("trim")
                        .then(ClientCommands.argument("material", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    CustomArmorTrims.init();
                                    CustomArmorTrims.TRIMS_CACHE.keySet().stream().map(t -> t.material().getPath()).distinct().forEach(b::suggest);
                                    return b.buildFuture();
                                })
                                .then(ClientCommands.argument("pattern", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            CustomArmorTrims.init();
                                            CustomArmorTrims.TRIMS_CACHE.keySet().stream().map(t -> t.pattern().getPath()).distinct().forEach(b::suggest);
                                            return b.buildFuture();
                                        })
                                        .executes(c -> trim(c.getSource(), StringArgumentType.getString(c, "material"),
                                                StringArgumentType.getString(c, "pattern"))))))
                .then(ClientCommands.literal("glint")
                        .then(ClientCommands.argument("state", StringArgumentType.word())
                                .suggests((c, b) -> { b.suggest("on"); b.suggest("off"); b.suggest("default"); return b.buildFuture(); })
                                .executes(c -> glint(c.getSource(), StringArgumentType.getString(c, "state")))))
                .then(ClientCommands.literal("model")
                        .then(ClientCommands.argument("skyblockId", StringArgumentType.word())
                                .executes(c -> model(c.getSource(), StringArgumentType.getString(c, "skyblockId")))))
                .then(ClientCommands.literal("reset")
                        .executes(c -> reset(c.getSource(), "all"))
                        .then(ClientCommands.argument("part", StringArgumentType.word())
                                .suggests((c, b) -> { for (String p : PARTS) b.suggest(p); return b.buildFuture(); })
                                .executes(c -> reset(c.getSource(), StringArgumentType.getString(c, "part")))));
    }

    private static String heldKey(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        ItemStack held = mc.player.getMainHandItem();
        String key = held.isEmpty() ? "" : Customize.uuid(held);
        if (key.isEmpty()) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] §fHold an item that can be customized"));
            return null;
        }
        return key;
    }

    private static int done(FabricClientCommandSource src, String what) {
        Customize.refresh();
        TeslaMapsConfig.save();
        src.sendFeedback(Component.literal("§a[TeslaMaps] §f" + what));
        return 1;
    }

    private static int rename(FabricClientCommandSource src, String name) {
        String key = heldKey(src);
        if (key == null) return 0;
        TeslaMapsConfig.get().customItemNames.put(key, Component.literal(name.replace('&', '§')));
        return done(src, "Renamed");
    }

    private static int dye(FabricClientCommandSource src, String hex) {
        String key = heldKey(src);
        if (key == null) return 0;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.equalsIgnoreCase("clear") || h.equalsIgnoreCase("none")) {
            TeslaMapsConfig.get().customDyeColors.remove(key);
            TeslaMapsConfig.get().customAnimatedDyes.remove(key);
            return done(src, "Dye removed");
        }
        int rgb;
        try {
            if (h.length() != 6) throw new NumberFormatException();
            rgb = Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] §fUse a 6 digit hex colour, e.g. #FF8800"));
            return 0;
        }
        TeslaMapsConfig.get().customAnimatedDyes.remove(key);
        TeslaMapsConfig.get().customDyeColors.put(key, 0xFF000000 | rgb);
        return done(src, "Dyed #" + h.toUpperCase(Locale.ROOT));
    }

    private static int trim(FabricClientCommandSource src, String material, String pattern) {
        String key = heldKey(src);
        if (key == null) return 0;
        Identifier m = Identifier.tryParse(material.contains(":") ? material : "minecraft:" + material);
        Identifier p = Identifier.tryParse(pattern.contains(":") ? pattern : "minecraft:" + pattern);
        CustomArmorTrims.init();
        ArmorTrimId id = m == null || p == null ? null : new ArmorTrimId(m, p);
        if (id == null || !CustomArmorTrims.TRIMS_CACHE.containsKey(id)) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] §fUnknown trim material or pattern"));
            return 0;
        }
        TeslaMapsConfig.get().customArmorTrims.put(key, id);
        return done(src, "Trim set");
    }

    private static int glint(FabricClientCommandSource src, String state) {
        String key = heldKey(src);
        if (key == null) return 0;
        switch (state.toLowerCase(Locale.ROOT)) {
            case "on", "true" -> TeslaMapsConfig.get().customGlint.put(key, true);
            case "off", "false" -> TeslaMapsConfig.get().customGlint.put(key, false);
            default -> TeslaMapsConfig.get().customGlint.remove(key);
        }
        return done(src, "Glint " + state);
    }

    /** Uses a skyblock item's look, head skin included. */
    private static int model(FabricClientCommandSource src, String skyblockId) {
        String key = heldKey(src);
        if (key == null) return 0;
        String id = skyblockId.toUpperCase(Locale.ROOT);
        String skin = com.teslamaps.utils.SkyblockItems.skin(id);
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (skin != null && !skin.isEmpty()) {
            c.customItemModel.put(key, Identifier.withDefaultNamespace("player_head"));
            c.customHelmetTextures.put(key, skin);
            c.customAnimatedHelmetTextures.remove(key);
            return done(src, "Model set to " + id);
        }
        ItemStack look = com.teslamaps.utils.SkyblockItems.stackNow(id);
        Identifier model = look.isEmpty() ? null : look.getComponents().get(DataComponents.ITEM_MODEL);
        if (model == null) {
            src.sendFeedback(Component.literal("§c[TeslaMaps] §fUnknown item id (or still loading, try again)"));
            return 0;
        }
        c.customItemModel.put(key, model);
        return done(src, "Model set to " + id);
    }

    private static int reset(FabricClientCommandSource src, String part) {
        String key = heldKey(src);
        if (key == null) return 0;
        TeslaMapsConfig c = TeslaMapsConfig.get();
        switch (part.toLowerCase(Locale.ROOT)) {
            case "all" -> Customize.reset(key);
            case "name" -> c.customItemNames.remove(key);
            case "dye" -> { c.customDyeColors.remove(key); c.customAnimatedDyes.remove(key); }
            case "trim" -> c.customArmorTrims.remove(key);
            case "head" -> { c.customHelmetTextures.remove(key); c.customAnimatedHelmetTextures.remove(key); }
            case "glint" -> c.customGlint.remove(key);
            case "model" -> c.customItemModel.remove(key);
            case "armormodel" -> c.customArmorModel.remove(key);
            default -> {
                src.sendFeedback(Component.literal("§c[TeslaMaps] §fParts: " + String.join(", ", PARTS)));
                return 0;
            }
        }
        return done(src, "Reset " + part);
    }
}
