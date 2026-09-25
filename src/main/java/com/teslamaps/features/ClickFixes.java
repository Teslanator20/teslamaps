package com.teslamaps.features;

// Ported from SkyHanni's HuntrapMisclickPrevention, SnakeGame and MiddleClickFix
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.ItemUtil;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

public final class ClickFixes {

    private static Feature inv, shards;
    private static long lastTrapMsg, lastSnake;

    private ClickFixes() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        inv = Feature.of("ClickFixes").section("Inventory")
                .when(() -> cfg().snakeGameKeys || cfg().middleClickFix)
                .config("Inventory", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Click Fixes");
                    b.toggle("Abiphone Snake Keys", () -> c.snakeGameKeys, v -> c.snakeGameKeys = v)
                            .desc("Steer the Abiphone Snake game with WASD (your movement keys) or the arrow keys");
                    b.toggle("Middle Click Fix", () -> c.middleClickFix, v -> c.middleClickFix = v)
                            .desc("SkyBlock: a middle click in menus is sent as a real middle click in survival too, so Hypixel's middle-click actions work");
                });
        shards = Feature.of("HuntrapMisclick").section("Shards")
                .when(() -> cfg().huntrapMisclick)
                .config("Shards", b -> b.toggle("Block Empty Trap Clicks", () -> cfg().huntrapMisclick,
                                v -> cfg().huntrapMisclick = v)
                        .desc("Hunting Toolkit: blocks clicks on traps that show Status: EMPTY"));
    }

    public static boolean blockHuntrap(AbstractContainerScreen<?> cs, Slot slot) {
        if (!cfg().huntrapMisclick || shards == null || !shards.active() || slot == null) return false;
        if (!StringUtil.stripTrim(cs.getTitle().getString()).startsWith("Hunting Toolkit")) return false;
        boolean empty = false;
        for (String l : ItemUtil.loreLines(slot.getItem())) {
            if (StringUtil.strip(l).contains("Status: EMPTY")) { empty = true; break; }
        }
        if (!empty) return false;
        long now = System.currentTimeMillis();
        Minecraft mc = Minecraft.getInstance();
        if (now - lastTrapMsg > 10_000 && mc.player != null) {
            lastTrapMsg = now;
            mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §7Prevented clicking an empty trap in the Hunting Toolkit §8(Shards > Block Empty Trap Clicks)"));
        }
        return true;
    }

    public static boolean onSnakeKey(AbstractContainerScreen<?> cs, KeyEvent event) {
        if (!cfg().snakeGameKeys || inv == null || !inv.active()) return false;
        String title = StringUtil.stripTrim(cs.getTitle().getString());
        if (!title.equals("Snake") && !title.equals("Eat 8 apples to win!")) return false;
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        int k = event.key();
        int slot;
        if (o.keyLeft.matches(event) || k == GLFW.GLFW_KEY_LEFT) slot = 24;
        else if (o.keyUp.matches(event) || k == GLFW.GLFW_KEY_UP) slot = 16;
        else if (o.keyRight.matches(event) || k == GLFW.GLFW_KEY_RIGHT) slot = 26;
        else if (o.keyDown.matches(event) || k == GLFW.GLFW_KEY_DOWN) slot = 34;
        else return false;
        long now = System.currentTimeMillis();
        if (now - lastSnake < 100) return true;
        lastSnake = now;
        if (mc.gameMode == null || mc.player == null || cs.getMenu().slots.size() <= slot) return true;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 2, ContainerInput.CLONE, mc.player);
        return true;
    }

    public static boolean middleClickFix() {
        return cfg().middleClickFix && inv != null && inv.active() && SkyblockUtils.isInSkyblock();
    }
}
