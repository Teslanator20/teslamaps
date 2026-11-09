package com.teslamaps.features;

// Ported from GanKura's DnaAnalyzerHandler (SkyHanni's DnaAnalyzerSolver does the same)
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.utils.SkyblockUtils;
import com.teslamaps.utils.StringUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Greenhouse DNA Analyzer: marks the next two slots to swap on the way to a connected board. */
public final class DnaAnalyzerSolver {

    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    private static final int FIRST_SLOT = COLUMNS;
    private static final int LAST_SLOT = FIRST_SLOT + COLUMNS * ROWS - 1;
    private static final int CLOSE_SLOT = 49;
    private static final int UNREACHABLE = 1000;
    private static final int GREEN = 0xAA55FF55;
    private static final int[][] PERMUTATIONS = permutations();

    private enum Colour { RED, YELLOW, BLUE, GREEN }

    private static Feature feature;
    private static net.minecraft.client.gui.screens.Screen dnaScreen;
    private static AbstractContainerMenu menu;
    private static int state = -1;
    private static boolean valid;
    private static int firstSlot = -1, secondSlot = -1;

    private DnaAnalyzerSolver() {}

    private static TeslaMapsConfig cfg() { return TeslaMapsConfig.get(); }

    public static void register() {
        feature = Feature.of("DnaAnalyzerSolver").section("Garden").when(() -> cfg().dnaAnalyzerSolver)
                .tick(DnaAnalyzerSolver::tick)
                .config("Garden", b -> {
                    TeslaMapsConfig c = b.config();
                    b.label("Greenhouse");
                    b.toggle("DNA Analyzer Solver", () -> c.dnaAnalyzerSolver, v -> c.dnaAnalyzerSolver = v)
                            .desc("Marks the next two slots to swap in green, on the shortest way to a board where "
                                    + "neighbouring columns connect.")
                            .tags("greenhouse", "dna", "puzzle");
                    b.toggle("  Middle Click Instead Of Pick Up", () -> c.dnaMiddleClick, v -> c.dnaMiddleClick = v)
                            .desc("Your click is sent as a middle click, so the item is never picked up");
                    b.toggle("  Block The Close Button", () -> c.dnaBlockClose, v -> c.dnaBlockClose = v);
                    b.toggle("  Hide Board Tooltips", () -> c.dnaHideTooltips, v -> c.dnaHideTooltips = v);
                });
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs)) {
            dnaScreen = null;
            return;
        }
        if (cs == dnaScreen) return;
        dnaScreen = SkyblockUtils.isInSkyblock() && StringUtil.stripTrim(cs.getTitle().getString()).endsWith(" DNA") ? cs : null;
    }

    private static boolean inMenu(AbstractContainerScreen<?> cs) {
        return cs == dnaScreen && feature != null && feature.active();
    }

    /** From SlotBackgrounds. */
    public static void drawHighlight(GuiGraphicsExtractor ctx, Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !inMenu(cs) || !refresh() || firstSlot < 0) return;
        AbstractContainerMenu open = cs.getMenu();
        if (slot == open.getSlot(firstSlot) || slot == open.getSlot(secondSlot)) {
            ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, GREEN);
        }
    }

    /** From HandledScreenClickMixin; true = the click was handled here. */
    public static boolean onSlotClick(AbstractContainerScreen<?> cs, int slotId, ContainerInput input) {
        if (!inMenu(cs) || !refresh()) return false;
        if (slotId == CLOSE_SLOT && cfg().dnaBlockClose) return true;
        if (!cfg().dnaMiddleClick || input == ContainerInput.CLONE || slotId < FIRST_SLOT || slotId > LAST_SLOT) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) return false;
        mc.gameMode.handleContainerInput(cs.getMenu().containerId, slotId, 2, ContainerInput.CLONE, mc.player);
        return true;
    }

    /** From HandledScreenClickMixin's tooltip hook. */
    public static boolean hideTooltip(AbstractContainerScreen<?> cs, Slot hovered) {
        if (hovered == null || !cfg().dnaHideTooltips || !inMenu(cs) || !refresh()) return false;
        int id = cs.getMenu().slots.indexOf(hovered);
        return id >= FIRST_SLOT && id <= LAST_SLOT;
    }

    private static boolean refresh() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        AbstractContainerMenu open = mc.player.containerMenu;
        int openState = open.getStateId();
        if (open == menu && openState == state) return valid;
        menu = open;
        state = openState;
        valid = false;
        firstSlot = secondSlot = -1;
        if (open.slots.size() <= LAST_SLOT) return false;

        Colour[][] board = new Colour[COLUMNS][ROWS];
        for (int slotId = FIRST_SLOT; slotId <= LAST_SLOT; slotId++) {
            Colour colour = colourOf(open.getSlot(slotId).getItem());
            if (colour == null) return false;
            board[slotId % COLUMNS][slotId / COLUMNS - 1] = colour;
        }
        for (Colour[] column : board) {
            for (int a = 0; a < ROWS; a++) {
                for (int b = a + 1; b < ROWS; b++) if (column[a] == column[b]) return false;
            }
        }
        valid = true;
        int[] swap = solve(board);
        if (swap == null) return true;
        firstSlot = swap[0] + (swap[1] + 1) * COLUMNS;
        secondSlot = swap[0] + (swap[2] + 1) * COLUMNS;
        return true;
    }

    /** Next swap as {column, row, row}; null when already solved. Ends stay fixed. */
    private static int[] solve(Colour[][] board) {
        int first = 1, last = COLUMNS - 2;
        int count = last - first + 1;
        int patterns = PERMUTATIONS.length;

        int[][] cost = new int[count][patterns];
        int[][][] swaps = new int[count][patterns][];
        for (int i = 0; i < count; i++) {
            Colour[] column = board[first + i];
            for (int p = 0; p < patterns; p++) {
                int[] result = minimumSwaps(column, permute(column, PERMUTATIONS[p]));
                cost[i][p] = result[0];
                swaps[i][p] = result;
            }
        }

        int[][] best = new int[count][patterns];
        int[][] parent = new int[count][patterns];
        for (int i = 0; i < count; i++) {
            for (int p = 0; p < patterns; p++) {
                best[i][p] = UNREACHABLE;
                parent[i][p] = -1;
            }
        }
        for (int p = 0; p < patterns; p++) {
            if (!connects(board[0], permute(board[first], PERMUTATIONS[p]))) continue;
            best[0][p] = cost[0][p];
        }
        for (int i = 1; i < count; i++) {
            for (int p = 0; p < patterns; p++) {
                Colour[] current = permute(board[first + i], PERMUTATIONS[p]);
                for (int q = 0; q < patterns; q++) {
                    if (best[i - 1][q] == UNREACHABLE) continue;
                    if (!connects(permute(board[first + i - 1], PERMUTATIONS[q]), current)) continue;
                    int total = best[i - 1][q] + cost[i][p];
                    if (total < best[i][p]) {
                        best[i][p] = total;
                        parent[i][p] = q;
                    }
                }
            }
        }

        int cheapest = UNREACHABLE, tail = -1;
        for (int p = 0; p < patterns; p++) {
            if (!connects(permute(board[last], PERMUTATIONS[p]), board[COLUMNS - 1])) continue;
            if (best[count - 1][p] < cheapest) {
                cheapest = best[count - 1][p];
                tail = p;
            }
        }
        if (tail < 0) return null;

        int[] next = null;
        int current = tail;
        for (int i = count - 1; i >= 0 && current >= 0; i--) {
            int[] made = swaps[i][current];
            for (int k = 1; k + 1 < made.length; k += 2) next = new int[]{first + i, made[k], made[k + 1]};
            current = parent[i][current];
        }
        return next;
    }

    private static boolean connects(Colour[] left, Colour[] right) {
        for (int r = 0; r < ROWS; r++) {
            Colour c = left[r];
            if (right[r] == c) continue;
            if (r > 0 && right[r - 1] == c) continue;
            if (r < ROWS - 1 && right[r + 1] == c) continue;
            return false;
        }
        return true;
    }

    private static Colour[] permute(Colour[] column, int[] order) {
        Colour[] result = new Colour[ROWS];
        for (int r = 0; r < ROWS; r++) result[r] = column[order[r]];
        return result;
    }

    /** {swaps, row, row, row, row, ...}; a cycle of length n takes n-1 swaps. */
    private static int[] minimumSwaps(Colour[] from, Colour[] to) {
        int[] destination = new int[ROWS];
        for (int i = 0; i < ROWS; i++) destination[indexOf(from, to[i])] = i;
        boolean[] visited = new boolean[ROWS];
        List<Integer> pairs = new ArrayList<>();
        int cost = 0;
        for (int i = 0; i < ROWS; i++) {
            if (visited[i]) continue;
            List<Integer> cycle = new ArrayList<>();
            int cursor = i;
            while (!visited[cursor]) {
                visited[cursor] = true;
                cycle.add(cursor);
                cursor = destination[cursor];
            }
            if (cycle.size() <= 1) continue;
            cost += cycle.size() - 1;
            for (int k = 1; k < cycle.size(); k++) {
                pairs.add(cycle.get(0));
                pairs.add(cycle.get(k));
            }
        }
        int[] result = new int[pairs.size() + 1];
        result[0] = cost;
        for (int i = 0; i < pairs.size(); i++) result[i + 1] = pairs.get(i);
        return result;
    }

    private static int indexOf(Colour[] column, Colour colour) {
        for (int i = 0; i < ROWS; i++) if (column[i] == colour) return i;
        return 0;
    }

    private static int[][] permutations() {
        List<int[]> result = new ArrayList<>();
        collect(result, new int[]{0, 1, 2, 3}, 0);
        return result.toArray(new int[0][]);
    }

    private static void collect(List<int[]> result, int[] order, int at) {
        if (at == ROWS) {
            result.add(order.clone());
            return;
        }
        for (int i = at; i < ROWS; i++) {
            int keep = order[at]; order[at] = order[i]; order[i] = keep;
            collect(result, order, at + 1);
            keep = order[at]; order[at] = order[i]; order[i] = keep;
        }
    }

    private static Colour colourOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Component name = stack.getHoverName();
        Colour byCode = fromLegacyCode(name.getString());
        if (byCode != null) return byCode;
        Colour byStyle = fromStyle(name);
        if (byStyle != null) return byStyle;
        String id = stack.getItem().getDescriptionId();
        String kind = id.substring(id.lastIndexOf('.') + 1);
        if (kind.startsWith("red_")) return Colour.RED;
        if (kind.startsWith("yellow_")) return Colour.YELLOW;
        if (kind.startsWith("blue_")) return Colour.BLUE;
        if (kind.startsWith("green_")) return Colour.GREEN;
        return null;
    }

    private static Colour fromLegacyCode(String name) {
        for (int at = name.indexOf('§'); at >= 0 && at + 1 < name.length(); at = name.indexOf('§', at + 2)) {
            Colour c = switch (name.charAt(at + 1)) {
                case 'c' -> Colour.RED;
                case 'e' -> Colour.YELLOW;
                case '9' -> Colour.BLUE;
                case 'a' -> Colour.GREEN;
                default -> null;
            };
            if (c != null) return c;
        }
        return null;
    }

    private static Colour fromStyle(Component name) {
        Colour[] found = new Colour[1];
        name.visit((FormattedText.StyledContentConsumer<Object>) (style, text) -> {
            if (found[0] != null || text.isBlank()) return Optional.empty();
            TextColor colour = style.getColor();
            if (colour != null) {
                found[0] = switch (colour.getValue()) {
                    case 0xFF5555 -> Colour.RED;
                    case 0xFFFF55 -> Colour.YELLOW;
                    case 0x5555FF -> Colour.BLUE;
                    case 0x55FF55 -> Colour.GREEN;
                    default -> null;
                };
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }
}
