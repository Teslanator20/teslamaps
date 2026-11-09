package com.teslamaps.features;

// Ported from SkyHanni's GardenCustomKeybinds.kt (toggle keys not supported)
import com.mojang.blaze3d.platform.InputConstants;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.KeyNames;
import com.teslamaps.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class FarmKeys {

    private static volatile boolean active;
    private static boolean paused;
    private static boolean toggleWasDown;
    private static KeyMapping[] maps = new KeyMapping[0];
    private static int[] targets = new int[0];
    private static boolean[] consumed = new boolean[0];

    private FarmKeys() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        Feature.of("FarmKeys").section("Garden").when(() -> cfg().farmKeys)
                .tick(FarmKeys::tick)
                .config("Garden", b -> {
                    b.label("Farming Keybinds");
                    b.toggle("Farming Keybinds", () -> cfg().farmKeys, v -> { cfg().farmKeys = v; if (!v) deactivate(); })
                            .desc("While a farming tool is held in the Garden and no menu is open, these keys replace the vanilla ones. "
                                    + "Unbound = vanilla key stays. The keys they replace do nothing meanwhile. Your vanilla binds are not changed.")
                            .tags("farm keys", "keybind", "remap", "controls");
                    b.keybind("  Attack", () -> cfg().farmKeysAttack, v -> cfg().farmKeysAttack = v);
                    b.keybind("  Use Item", () -> cfg().farmKeysUse, v -> cfg().farmKeysUse = v);
                    b.keybind("  Forward", () -> cfg().farmKeysForward, v -> cfg().farmKeysForward = v);
                    b.keybind("  Back", () -> cfg().farmKeysBack, v -> cfg().farmKeysBack = v);
                    b.keybind("  Left", () -> cfg().farmKeysLeft, v -> cfg().farmKeysLeft = v);
                    b.keybind("  Right", () -> cfg().farmKeysRight, v -> cfg().farmKeysRight = v);
                    b.keybind("  Jump", () -> cfg().farmKeysJump, v -> cfg().farmKeysJump = v);
                    b.keybind("  Sneak", () -> cfg().farmKeysSneak, v -> cfg().farmKeysSneak = v);
                    b.keybind("  On/Off Key", () -> cfg().farmKeysToggleKey, v -> cfg().farmKeysToggleKey = v);
                    b.toggle("  Not In The Barn", () -> cfg().farmKeysExcludeBarn, v -> cfg().farmKeysExcludeBarn = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = cfg();
        boolean down = mc.gui.screen() == null && c.farmKeysToggleKey != -1 && KeyNames.isDown(c.farmKeysToggleKey);
        if (down && !toggleWasDown && SkyblockUtils.isInGarden()) {
            paused = !paused;
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §fFarming keybinds " + (paused ? "§coff" : "§aon")));
        }
        toggleWasDown = down;

        boolean want = !paused && mc.player != null && SkyblockUtils.isInGarden()
                && FarmingSensitivity.isFarmingTool(mc.player.getMainHandItem())
                && !(c.farmKeysExcludeBarn && inBarn(mc.player));
        if (want) build(mc.options, c);
        if (want != active) {
            if (!want) deactivate();
            else {
                consumed = new boolean[maps.length];
                for (int i = 0; i < maps.length; i++) consumed[i] = KeyNames.isDown(targets[i]);
                active = true;
            }
        }
    }

    private static void build(Options o, TeslaMapsConfig c) {
        KeyMapping[] all = {o.keyAttack, o.keyUse, o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift};
        int[] keys = {c.farmKeysAttack, c.farmKeysUse, c.farmKeysForward, c.farmKeysBack, c.farmKeysLeft, c.farmKeysRight,
                c.farmKeysJump, c.farmKeysSneak};
        int n = 0;
        for (int k : keys) if (k != -1) n++;
        if (n == maps.length && same(all, keys)) return;
        KeyMapping[] m = new KeyMapping[n];
        int[] t = new int[n];
        int j = 0;
        for (int i = 0; i < all.length; i++) {
            if (keys[i] == -1) continue;
            m[j] = all[i];
            t[j++] = keys[i];
        }
        maps = m;
        targets = t;
        consumed = new boolean[n];
    }

    private static boolean same(KeyMapping[] all, int[] keys) {
        int j = 0;
        for (int i = 0; i < all.length; i++) {
            if (keys[i] == -1) continue;
            if (maps[j] != all[i] || targets[j] != keys[i]) return false;
            j++;
        }
        return true;
    }

    private static void deactivate() {
        if (!active) return;
        active = false;
        KeyMapping.releaseAll();
    }

    private static boolean inBarn(Player p) {
        return p.getX() <= 35.5 && p.getX() >= -32.5 && p.getZ() <= -4.5 && p.getZ() >= -46.5;
    }

    private static boolean live() {
        return active && Minecraft.getInstance().gui.screen() == null && cfg().farmKeys
                && cfg().section("Garden") && SkyblockUtils.isInGarden();
    }

    private static int index(KeyMapping mapping) {
        for (int i = 0; i < maps.length; i++) if (maps[i] == mapping) return i;
        return -1;
    }

    /** True when the key this mapping sits on was taken by a remapped action. */
    private static boolean blocked(KeyMapping mapping) {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
        int code = key.getType() == InputConstants.Type.MOUSE ? KeyNames.forMouse(key.getValue()) : key.getValue();
        if (code == -1) return false;
        for (int t : targets) if (t == code) return true;
        return false;
    }

    public static void overrideIsDown(KeyMapping mapping, CallbackInfoReturnable<Boolean> cir) {
        if (!live()) return;
        int i = index(mapping);
        if (i >= 0) cir.setReturnValue(KeyNames.isDown(targets[i]));
        else if (blocked(mapping)) cir.setReturnValue(false);
    }

    /** true = handled, mixin zeroes clickCount. */
    public static boolean overrideConsumeClick(KeyMapping mapping, CallbackInfoReturnable<Boolean> cir) {
        if (!live()) return false;
        int i = index(mapping);
        if (i >= 0) {
            boolean held = KeyNames.isDown(targets[i]);
            boolean click = held && !consumed[i];
            consumed[i] = held;
            cir.setReturnValue(click);
            return true;
        }
        if (blocked(mapping)) {
            cir.setReturnValue(false);
            return true;
        }
        return false;
    }
}
