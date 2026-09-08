package com.teslamaps.debug;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.dungeon.terminals.TerminalHandler;
import com.teslamaps.dungeon.terminals.TerminalUtils;
import com.teslamaps.mixin.HandledScreenAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.PointerBuffer;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * File-driven remote control for the dev client. Dev environment only.
 * Reads commands from run/agent/cmd.txt, writes results to run/agent/out.txt,
 * screenshots land in run/screenshots/agent_*.png.
 */
public final class AgentBridge {

    private static final boolean ENABLED = FabricLoader.getInstance().isDevelopmentEnvironment();

    private static Path cmdFile;
    private static Path outFile;

    private static final ArrayDeque<String> queue = new ArrayDeque<>();
    private static int wait;
    private static int poll;

    private static String burstName;
    private static int burstLeft;
    private static int burstEvery;
    private static int burstTimer;
    private static int burstIdx;

    private static Input override = Input.EMPTY;
    private static int inputTicks;

    private static float turnYaw;
    private static float turnPitch;
    private static int turnTicks;

    private static final Map<String, Integer> KEYS = Map.ofEntries(
            Map.entry("ESC", GLFW.GLFW_KEY_ESCAPE),
            Map.entry("ENTER", GLFW.GLFW_KEY_ENTER),
            Map.entry("TAB", GLFW.GLFW_KEY_TAB),
            Map.entry("SPACE", GLFW.GLFW_KEY_SPACE),
            Map.entry("BACKSPACE", GLFW.GLFW_KEY_BACKSPACE),
            Map.entry("UP", GLFW.GLFW_KEY_UP),
            Map.entry("DOWN", GLFW.GLFW_KEY_DOWN),
            Map.entry("LEFT", GLFW.GLFW_KEY_LEFT),
            Map.entry("RIGHT", GLFW.GLFW_KEY_RIGHT),
            Map.entry("E", GLFW.GLFW_KEY_E),
            Map.entry("W", GLFW.GLFW_KEY_W),
            Map.entry("A", GLFW.GLFW_KEY_A),
            Map.entry("S", GLFW.GLFW_KEY_S),
            Map.entry("D", GLFW.GLFW_KEY_D),
            Map.entry("F3", GLFW.GLFW_KEY_F3),
            Map.entry("F5", GLFW.GLFW_KEY_F5));

    private AgentBridge() {}

    public static boolean hasInputOverride() {
        return ENABLED && inputTicks > 0;
    }

    public static Input inputOverride() {
        return override;
    }

    public static void tick() {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (cmdFile == null && !init(mc)) return;

        if (inputTicks > 0) inputTicks--;

        // A grabbed cursor traps the mouse on this window; the bridge never needs it.
        if (mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.releaseMouse();

        if (turnTicks > 0 && mc.player != null) {
            mc.player.setYRot(mc.player.getYRot() + turnYaw);
            mc.player.setXRot(Mth.clamp(mc.player.getXRot() + turnPitch, -90f, 90f));
            turnTicks--;
        }

        if (burstLeft > 0 && --burstTimer <= 0) {
            shot(burstName + "_" + (burstIdx < 10 ? "0" : "") + burstIdx);
            burstIdx++;
            burstLeft--;
            burstTimer = burstEvery;
        }

        if (wait > 0) {
            wait--;
            return;
        }
        if (queue.isEmpty()) {
            if (--poll <= 0) {
                poll = 5;
                read();
            }
            return;
        }
        String line = queue.poll();
        try {
            exec(mc, line);
        } catch (Exception e) {
            out("ERR  " + line + "  ->  " + e);
        }
    }

    private static boolean init(Minecraft mc) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("agent");
            Files.createDirectories(dir);
            cmdFile = dir.resolve("cmd.txt");
            outFile = dir.resolve("out.txt");
            if (!Files.exists(cmdFile)) Files.writeString(cmdFile, "");
            mc.options.pauseOnLostFocus = false;
            placeWindow(mc);
            out("bridge ready");
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void read() {
        try {
            if (!Files.exists(cmdFile) || Files.size(cmdFile) == 0) return;
            List<String> lines = Files.readAllLines(cmdFile, StandardCharsets.UTF_8);
            Files.writeString(cmdFile, "");
            for (String l : lines) {
                String t = l.trim();
                if (!t.isEmpty() && !t.startsWith("#")) queue.add(t);
            }
        } catch (IOException ignored) {
        }
    }

    private static void exec(Minecraft mc, String line) throws Exception {
        String[] p = line.split("\\s+");
        String c = p[0].toLowerCase(Locale.ROOT);
        String rest = line.length() > p[0].length() ? line.substring(p[0].length() + 1) : "";

        switch (c) {
            case "shot" -> {
                shot(p[1]);
                out("shot " + p[1]);
            }
            case "burst" -> {
                burstName = p[1];
                burstLeft = Integer.parseInt(p[2]);
                burstEvery = Math.max(1, Integer.parseInt(p[3]));
                burstTimer = 1;
                burstIdx = 0;
                out("burst " + burstName + " x" + burstLeft + " every " + burstEvery);
            }
            case "wait" -> wait = Integer.parseInt(p[1]);
            case "screen" -> {
                switch (p[1].toLowerCase(Locale.ROOT)) {
                    case "config" -> mc.setScreen(new com.teslamaps.screen.MapConfigScreen());
                    case "hudedit" -> mc.setScreen(new com.teslamaps.screen.HudEditScreen(null));
                    case "none" -> mc.setScreen(null);
                    case "title" -> mc.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
                    case "look" -> mc.setScreen(new com.teslamaps.screen.LookScreen(null));
                    case "inventory" -> {
                        if (mc.player != null) {
                            mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                        }
                    }
                    default -> out("ERR unknown screen " + p[1]);
                }
                out("screen " + p[1]);
            }
            case "chatgui" -> {
                mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen("", false));
                out("chatgui");
            }
            case "cmd" -> {
                if (mc.getConnection() != null) mc.getConnection().sendCommand(rest);
                out("cmd " + rest);
            }
            case "chat" -> {
                if (mc.getConnection() != null) mc.getConnection().sendChat(rest);
                out("chat " + rest);
            }
            case "key" -> {
                Integer k = KEYS.get(p[1].toUpperCase(Locale.ROOT));
                Screen s = mc.screen;
                if (k == null) out("ERR unknown key " + p[1]);
                else if (s == null) out("ERR no screen open");
                else {
                    s.keyPressed(new KeyEvent(k, 0, 0));
                    out("key " + p[1]);
                }
            }
            case "hover" -> {
                cursor(mc, Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                if (mc.screen != null) mc.screen.mouseMoved(Double.parseDouble(p[1]), Double.parseDouble(p[2]));
                out("hover " + p[1] + " " + p[2]);
            }
            case "click" -> {
                double x = Double.parseDouble(p[1]);
                double y = Double.parseDouble(p[2]);
                int btn = p.length > 3 && p[3].startsWith("r") ? 1 : 0;
                cursor(mc, x, y);
                Screen s = mc.screen;
                if (s == null) out("ERR no screen open");
                else {
                    MouseButtonEvent ev = new MouseButtonEvent(x, y, new MouseButtonInfo(btn, 0));
                    s.mouseMoved(x, y);
                    s.mouseClicked(ev, false);
                    s.mouseReleased(ev);
                    out("click " + x + " " + y + " btn" + btn);
                }
            }
            case "button" -> {
                Screen s = mc.screen;
                if (s == null) {
                    out("ERR no screen open");
                    break;
                }
                String want = rest.toLowerCase(Locale.ROOT);
                net.minecraft.client.gui.components.AbstractWidget hit = null;
                for (var g : s.children()) {
                    if (g instanceof net.minecraft.client.gui.components.AbstractWidget w
                            && w.getMessage().getString().toLowerCase(Locale.ROOT).contains(want)) {
                        hit = w;
                        break;
                    }
                }
                if (hit == null) {
                    out("ERR no widget '" + rest + "' | " + widgets(s));
                    break;
                }
                double x = hit.getX() + hit.getWidth() / 2.0;
                double y = hit.getY() + hit.getHeight() / 2.0;
                MouseButtonEvent ev = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
                cursor(mc, x, y);
                s.mouseMoved(x, y);
                s.mouseClicked(ev, false);
                s.mouseReleased(ev);
                out("button '" + rest + "' at " + x + "," + y);
            }
            case "widgets" -> out(mc.screen == null ? "ERR no screen open" : widgets(mc.screen));
            case "drag" -> {
                double x1 = Double.parseDouble(p[1]);
                double y1 = Double.parseDouble(p[2]);
                double x2 = Double.parseDouble(p[3]);
                double y2 = Double.parseDouble(p[4]);
                Screen s = mc.screen;
                if (s == null) out("ERR no screen open");
                else {
                    MouseButtonInfo info = new MouseButtonInfo(0, 0);
                    cursor(mc, x1, y1);
                    s.mouseClicked(new MouseButtonEvent(x1, y1, info), false);
                    cursor(mc, x2, y2);
                    s.mouseDragged(new MouseButtonEvent(x2, y2, info), x2 - x1, y2 - y1);
                    s.mouseReleased(new MouseButtonEvent(x2, y2, info));
                    out("drag " + x1 + "," + y1 + " -> " + x2 + "," + y2);
                }
            }
            case "scroll" -> {
                Screen s = mc.screen;
                if (s != null) s.mouseScrolled(Double.parseDouble(p[1]), Double.parseDouble(p[2]), 0,
                        Double.parseDouble(p[3]));
                out("scroll " + rest);
            }
            case "move" -> {
                String f = p[1].toLowerCase(Locale.ROOT);
                override = new Input(f.contains("f"), f.contains("b"), f.contains("l"), f.contains("r"),
                        f.contains("j"), f.contains("s"), f.contains("p"));
                inputTicks = Integer.parseInt(p[2]);
                out("move " + f + " for " + inputTicks + "t");
            }
            case "stop" -> {
                inputTicks = 0;
                turnTicks = 0;
                burstLeft = 0;
                override = Input.EMPTY;
                out("stop");
            }
            case "turn" -> {
                turnYaw = Float.parseFloat(p[1]);
                turnPitch = Float.parseFloat(p[2]);
                turnTicks = Integer.parseInt(p[3]);
                out("turn " + turnYaw + "/" + turnPitch + " x" + turnTicks);
            }
            case "look" -> {
                if (mc.player != null) {
                    mc.player.setYRot(Float.parseFloat(p[1]));
                    mc.player.setXRot(Float.parseFloat(p[2]));
                }
                out("look " + rest);
            }
            case "tp" -> {
                if (mc.player != null) {
                    mc.player.snapTo(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
                }
                out("tp " + rest);
            }
            case "type" -> {
                Screen s = mc.screen;
                if (s == null) out("ERR no screen open");
                else {
                    rest.codePoints().forEach(cp -> s.charTyped(new net.minecraft.client.input.CharacterEvent(cp)));
                    out("type " + rest);
                }
            }
            case "fly" -> {
                if (mc.player != null) {
                    boolean on = !p[1].equalsIgnoreCase("off");
                    mc.player.getAbilities().mayfly = true;
                    mc.player.getAbilities().flying = on;
                    mc.player.onUpdateAbilities();
                }
                out("fly " + rest);
            }
            case "hidegui" -> {
                mc.options.hideGui = !p[1].equalsIgnoreCase("off");
                out("hidegui " + rest);
            }
            case "term" -> out(term(mc));
            case "value" -> {
                if (mc.player == null) {
                    out("ERR no player");
                    break;
                }
                var held = mc.player.getMainHandItem();
                var lines = com.teslamaps.features.EstimatedValue.display(held);
                if (lines.isEmpty()) out("value: (nothing to show)");
                else for (String row : lines) out("value| " + row);
            }
            case "slot" -> {
                if (!(mc.screen instanceof AbstractContainerScreen<?> cs)) {
                    out("ERR not a container screen");
                    break;
                }
                int idx = Integer.parseInt(p[1]);
                double[] xy = slotPos(cs, idx);
                if (xy == null) {
                    out("ERR slot " + idx + " out of range");
                    break;
                }
                int btn = p.length > 2 && p[2].startsWith("r") ? 1 : 0;
                MouseButtonEvent ev = new MouseButtonEvent(xy[0], xy[1], new MouseButtonInfo(btn, 0));
                cursor(mc, xy[0], xy[1]);
                cs.mouseMoved(xy[0], xy[1]);
                cs.mouseClicked(ev, false);
                cs.mouseReleased(ev);
                out("slot " + idx + " btn" + btn + " @" + (int) xy[0] + "," + (int) xy[1]);
            }
            case "cfg" -> {
                TeslaMapsConfig cfg = TeslaMapsConfig.get();
                var f = cfg.getClass().getField(p[1]);
                if (p.length > 2) {
                    // strings take the rest of the line, so values with spaces work
                    String raw = line.substring(line.indexOf(p[1]) + p[1].length()).trim();
                    Object v = switch (f.getType().getSimpleName()) {
                        case "boolean" -> Boolean.parseBoolean(p[2]);
                        case "int" -> Integer.parseInt(p[2]);
                        case "float" -> Float.parseFloat(p[2]);
                        case "double" -> Double.parseDouble(p[2]);
                        default -> raw;
                    };
                    f.set(cfg, v);
                }
                out("cfg " + p[1] + " = " + f.get(cfg));
            }
            case "state" -> out(state(mc));
            case "echo" -> out(rest);
            default -> out("ERR unknown command: " + line);
        }
    }

    /** Borderless fullscreen on the rightmost monitor, so the dev client never covers the user's game. */
    private static void placeWindow(Minecraft mc) {
        PointerBuffer monitors = GLFW.glfwGetMonitors();
        if (monitors == null) return;
        int[] mx = new int[1];
        int[] my = new int[1];
        int bx = 0, by = 0, bw = 0, bh = 0;
        boolean found = false;
        while (monitors.hasRemaining()) {
            long m = monitors.get();
            GLFW.glfwGetMonitorPos(m, mx, my);
            var vm = GLFW.glfwGetVideoMode(m);
            if (vm == null) continue;
            if (!found || mx[0] > bx) {
                bx = mx[0];
                by = my[0];
                bw = vm.width();
                bh = vm.height();
                found = true;
            }
        }
        if (!found) return;
        // Decorated and smaller than the monitor on purpose: a borderless window the
        // size of the screen behaves like exclusive fullscreen on Windows, which made
        // alt-tabbing away from the dev client a fight.
        int width = (int) (bw * 0.72);
        int height = (int) (bh * 0.72);
        int x = bx + (bw - width) / 2;
        int y = by + (bh - height) / 2;

        long handle = mc.getWindow().handle();
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
        GLFW.glfwSetWindowAttrib(handle, GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
        GLFW.glfwSetWindowPos(handle, x, y);
        GLFW.glfwSetWindowSize(handle, width, height);
        sendToBack(handle);
        out("window -> " + x + "," + y + " " + width + "x" + height);
    }

    /**
     * Drops the window to the bottom of the z-order without activating it, so starting the dev
     * client does not tab the user out of whatever they were doing. Rendering continues while
     * unfocused because init() clears pauseOnLostFocus.
     */
    private static void sendToBack(long handle) {
        try {
            long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(handle);
            if (hwnd == 0L) return;
            final long HWND_BOTTOM = 1L;
            final int SWP_NOSIZE = 0x1, SWP_NOMOVE = 0x2, SWP_NOACTIVATE = 0x10;
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                org.lwjgl.system.windows.User32.SetWindowPos(stack.callocInt(1),
                        hwnd, HWND_BOTTOM, 0, 0, 0, 0, SWP_NOSIZE | SWP_NOMOVE | SWP_NOACTIVATE);
            }
        } catch (Throwable t) {
            out("window: could not send to back (" + t + ")");
        }
    }

    private static String term(Minecraft mc) {
        TerminalHandler h = TerminalUtils.getCurrentTerm();
        if (h == null) return "term: none";
        StringBuilder sb = new StringBuilder("term: ").append(h.type)
                .append(" renderType=").append(TeslaMapsConfig.get().terminalRenderType)
                .append(" solverEnabled=").append(TeslaMapsConfig.get().terminalSolver)
                .append(" ticksOpened=").append(h.ticksOpened)
                .append(" clicked=").append(h.clickedSlots.size())
                .append(" solution=").append(h.solution);
        if (mc.screen instanceof AbstractContainerScreen<?> cs) {
            HandledScreenAccessor acc = (HandledScreenAccessor) cs;
            sb.append(" | screen=").append(cs.getClass().getSimpleName())
                    .append(" left=").append(acc.getX()).append(" top=").append(acc.getY())
                    .append(" slots=").append(cs.getMenu().slots.size());
            for (int idx : h.solution) {
                double[] xy = slotPos(cs, idx);
                if (xy != null) sb.append(" #").append(idx).append("@").append((int) xy[0]).append(",")
                        .append((int) xy[1]);
            }
        }
        return sb.toString();
    }

    private static double[] slotPos(AbstractContainerScreen<?> cs, int index) {
        if (index < 0 || index >= cs.getMenu().slots.size()) return null;
        HandledScreenAccessor acc = (HandledScreenAccessor) cs;
        Slot sl = cs.getMenu().slots.get(index);
        return new double[] {acc.getX() + sl.x + 8, acc.getY() + sl.y + 8};
    }

    private static String widgets(Screen s) {
        StringBuilder sb = new StringBuilder("widgets:");
        for (var g : s.children()) {
            if (g instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                sb.append(" [").append(w.getMessage().getString()).append(" @")
                        .append(w.getX() + w.getWidth() / 2).append(",").append(w.getY() + w.getHeight() / 2)
                        .append("]");
            } else {
                sb.append(" <").append(g.getClass().getSimpleName()).append(">");
            }
        }
        return sb.toString();
    }

    private static void cursor(Minecraft mc, double guiX, double guiY) {
        int scale = mc.getWindow().getGuiScale();
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), guiX * scale, guiY * scale);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "agent_" + name + ".png", mc.getMainRenderTarget(), 1, c -> {});
    }

    private static String state(Minecraft mc) {
        StringBuilder sb = new StringBuilder("state:");
        sb.append(" screen=").append(mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        sb.append(" gui=").append(mc.getWindow().getGuiScaledWidth()).append("x")
                .append(mc.getWindow().getGuiScaledHeight()).append("@").append(mc.getWindow().getGuiScale());
        sb.append(" fps=").append(mc.getFps());
        sb.append(" focused=").append(mc.getWindow().isFocused());
        if (mc.player != null) {
            sb.append(String.format(Locale.ROOT, " pos=%.1f/%.1f/%.1f yaw=%.1f pitch=%.1f",
                    mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.player.getYRot(), mc.player.getXRot()));
        } else {
            sb.append(" pos=none");
        }
        return sb.toString();
    }

    private static void out(String s) {
        try {
            Files.writeString(outFile, s + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
