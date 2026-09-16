package com.teslamaps.profileviewer.screen;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import java.util.List;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.pages.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ProfileViewerScreen extends Screen {
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int HEADER_COLOR = 0xFF2A2A2A;
    private static final int TAB_COLOR = 0xFF333333;
    private static final int TAB_SELECTED_COLOR = 0xFF444444;
    private static final int TAB_HOVER_COLOR = 0xFF3A3A3A;
    private static final int ACCENT_COLOR = 0xFF55FF55;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;

    private static final int TAB_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 50;

    private final String targetPlayer;
    private String targetUuid;
    private SkyblockProfiles profiles;
    private boolean loading = true;
    private String error = null;
    private int loadingDots = 0;
    private long lastDotUpdate = 0;

    private final List<ProfileViewerPage> allPages = new ArrayList<>();
    private final List<ProfileViewerPage> pages = new ArrayList<>();
    private int selectedPageIndex = 0;

    private String hoveredTab = null;
    private net.minecraft.world.item.ItemStack hoveredStack = net.minecraft.world.item.ItemStack.EMPTY;

    public int toScreenX(int panelCoordinate) {
        return panelX + (int) (panelCoordinate * scale);
    }

    public int toScreenY(int panelCoordinate) {
        return panelY + (int) (panelCoordinate * scale);
    }

    public void setHoveredStack(net.minecraft.world.item.ItemStack stack) {
        this.hoveredStack = stack;
    }

    private java.util.List<Component> hoveredLines = null;

    public void setHoveredLines(java.util.List<Component> lines) {
        this.hoveredLines = lines;
    }

    private String nameInput = null;
    private long nameCursorBlink = 0;
    private float scale = 1;
    private int panelX = 0;
    private int panelY = 0;


    private int scrollOffset = 0;

    public ProfileViewerScreen(String playerName) {
        super(Component.literal("Profile Viewer - " + playerName));
        this.targetPlayer = playerName;
    }

    @Override
    public void removed() {
        // static caches otherwise outlive the screen and keep whole profiles plus a fake
        // player (and through it the level) reachable for good
        ItemGrid.clear();
        PvPlayerRender.reset();
        super.removed();
    }

    @Override
    protected void init() {
        allPages.clear();
        allPages.add(new BasicPage());
        allPages.add(new DungeonPage());
        allPages.add(new InventoriesPage());
        allPages.add(new PetsPage());
        allPages.add(new MiningPage());
        allPages.add(new ForagingPage());
        allPages.add(new CollectionsPage());
        allPages.add(new BestiaryPage());
        allPages.add(new TrophyFishPage());
        allPages.add(new MuseumPage());
        allPages.add(new GardenPage());
        allPages.add(new CrimsonIslePage());
        allPages.add(new RiftPage());
        allPages.add(new ExtraPage());
        allPages.add(new BingoPage());

        pages.clear();
        pages.addAll(allPages);

        loadProfile();
    }

    /** Tabs that do not apply to the selected profile drop out, bingo being the only one so far. */
    private void updateTabs() {
        ProfileViewerPage current = selectedPageIndex >= 0 && selectedPageIndex < pages.size()
                ? pages.get(selectedPageIndex) : null;

        pages.clear();
        for (ProfileViewerPage page : allPages) {
            if (page.isAvailable(profiles)) pages.add(page);
        }

        int index = current == null ? -1 : pages.indexOf(current);
        selectedPageIndex = Math.max(0, index);
    }

    private void loadProfile() {
        loading = true;
        error = null;

        HypixelApi.nameToUuid(targetPlayer).thenAccept(uuid -> {
            if (uuid == null) {
                error = "Player not found: " + targetPlayer;
                loading = false;
                return;
            }

            this.targetUuid = uuid;

            profiles = new SkyblockProfiles(uuid, targetPlayer);
            profiles.load().thenRun(() -> {
                loading = false;
                if (profiles.getError() != null) {
                    error = profiles.getError();
                } else {
                    SkyblockProfiles.addToRecentPlayers(targetPlayer);
                    profiles.loadStatus();
                    profiles.loadPlayer();
                    profiles.loadGuild();

                    for (ProfileViewerPage page : allPages) {
                        page.init(this, profiles);
                    }
                    updateTabs();
                    if (!pages.isEmpty()) {
                        pages.get(selectedPageIndex).onSelected();
                    }
                }
            });
        });
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (loading && System.currentTimeMillis() - lastDotUpdate > 500) {
            loadingDots = (loadingDots + 1) % 4;
            lastDotUpdate = System.currentTimeMillis();
        }

        ctx.fill(0, 0, width, height, NeuStyle.SCREEN_DIM);

        // the window carries a 28px tab strip above and a 24px bar below, both have to fit,
        // and the recent player tabs hang 28 off the right edge
        int totalHeight = NeuStyle.PANEL_HEIGHT + 28 + 26;
        int totalWidth = NeuStyle.PANEL_WIDTH + 28;
        // fractional on purpose: an integer step wastes up to half the screen at the wrong gui scale.
        // capped - filling the whole screen made everything comically fat on big windows
        scale = Math.max(1f, Math.min(1.4f,
                Math.min(width / (float) totalWidth, height / (float) totalHeight)));
        // on a window too narrow for the side tabs the panel wins, they get clipped instead
        panelX = Math.max(0, (int) ((width - totalWidth * scale) / 2));
        panelY = (int) ((height - totalHeight * scale) / 2 + 28 * scale);
        // keep the tab strip and the bottom bar on screen even when the window is short
        int lowest = (int) (height - (NeuStyle.PANEL_HEIGHT + 26) * scale);
        panelY = Math.max(Math.min(panelY, lowest), (int) (28 * scale));

        int mx = (int) ((mouseX - panelX) / scale);
        int my = (int) ((mouseY - panelY) / scale);

        ctx.pose().pushMatrix();
        ctx.pose().translate(panelX, panelY);
        ctx.pose().scale(scale, scale);

        NeuStyle.window(ctx, 0, 0, NeuStyle.PANEL_WIDTH, NeuStyle.PANEL_HEIGHT);

        renderTabs(ctx, mx, my);
        renderRecentPlayers(ctx, mx, my);

        int contentY = 8;
        int contentHeight = NeuStyle.PANEL_HEIGHT - 16;

        if (loading) {
            renderLoading(ctx, contentY, contentHeight);
        } else if (error != null) {
            renderError(ctx, contentY, contentHeight);
        } else if (profiles != null && profiles.getSelectedProfile() != null) {
            if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
                ProfileViewerPage page = pages.get(selectedPageIndex);
                if (page.isFixedLayout()) {
                    page.render(ctx, 8, contentY, NeuStyle.PANEL_WIDTH - 16, contentHeight, mx, my, delta);
                } else {
                    // long pages scroll and stay inside the frame; scissor goes through the pose
                    ctx.enableScissor(4, 4, NeuStyle.PANEL_WIDTH - 4, NeuStyle.PANEL_HEIGHT - 4);
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(0, -page.getScroll());
                    page.render(ctx, 8, contentY, NeuStyle.PANEL_WIDTH - 16, contentHeight,
                            mx, my + page.getScroll(), delta);
                    ctx.pose().popMatrix();
                    ctx.disableScissor();
                }
            }
        }

        renderFooter(ctx, mx, my);

        ctx.pose().popMatrix();

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // after the base class, otherwise its widget pass drops the deferred tooltip again
        if (!hoveredStack.isEmpty()) {
            // key the scroll offset to what the item IS, not to the object - the inventory page
            // rebuilds its stacks every frame, so an identity key reset the offset every frame
            com.teslamaps.features.ScrollableTooltip.setExternalFocus(
                    hoveredStack.getHoverName().getString().hashCode() * 31 + hoveredStack.getCount());
            tooltipOpen = true;
            ctx.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            hoveredStack = net.minecraft.world.item.ItemStack.EMPTY;
        } else if (hoveredLines != null) {
            com.teslamaps.features.ScrollableTooltip.setExternalFocus(hoveredLines.hashCode());
            tooltipOpen = true;
            ctx.setComponentTooltipForNextFrame(font, hoveredLines, mouseX, mouseY);
        } else {
            com.teslamaps.features.ScrollableTooltip.setExternalFocus(-1);
            tooltipOpen = false;
        }
        hoveredLines = null;
    }

    private int tabsX() {
        return Math.max(0, (NeuStyle.PANEL_WIDTH - pages.size() * 28) / 2);
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // NEU: 28 wide, centered on the window, sitting on top of it
        int offset = tabsX();
        for (int i = 0; i < pages.size(); i++) {
            ProfileViewerPage page = pages.get(i);
            boolean selected = i == selectedPageIndex;
            int x = offset + i * 28;
            int y = -28;
            boolean hovered = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;

            NeuStyle.tab(ctx, x, selected ? y : y + 1, selected);
            ctx.item(page.getTabIcon(), x + 6, y + 9);

            if (hovered) hoveredTab = page.getTabName();
        }
    }

    private void renderRecentPlayers(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        List<String> recent = TeslaMapsConfig.get().recentPlayers;
        if (recent == null) return;

        int x = NeuStyle.PANEL_WIDTH;
        // only the first entry matching the shown player lights up, the list can hold it twice
        int selectedIndex = -1;
        for (int i = 0; i < Math.min(recent.size(), 7); i++) {
            String name = recent.get(i);
            if (name.equalsIgnoreCase(targetPlayer)
                    || (profiles != null && name.equalsIgnoreCase(profiles.getDisplayName()))) {
                selectedIndex = i;
                break;
            }
        }

        // bottom aligned - hanging off the top corner they fought with the tab strip for attention
        int count = Math.min(recent.size(), 7);
        int startY = NeuStyle.PANEL_HEIGHT - count * 28;
        for (int i = 0; i < count; i++) {
            String name = recent.get(i);
            int y = startY + i * 28;
            boolean selected = i == selectedIndex;
            boolean hovered = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;

            NeuStyle.sideTab(ctx, selected ? x : x - 1, y, selected);
            PlayerHeads.faceByName(ctx, name, x + 7, y + 6, 16);

            if (hovered) hoveredTab = name;
        }
    }

    /** Bottom bar: one chip per profile on the left, search box and a skycrypt link on the right. */
    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int y = NeuStyle.PANEL_HEIGHT + 3;
        int height = 16;

        // skycrypt button, far right
        String skyLabel = "SkyCrypt";
        int skyWidth = font.width(skyLabel) + 12;
        int skyX = NeuStyle.PANEL_WIDTH - skyWidth;
        boolean skyHovered = mouseX >= skyX && mouseX < skyX + skyWidth && mouseY >= y && mouseY < y + height;
        if (profiles != null && profiles.isLoaded()) {
            NeuStyle.window(ctx, skyX, y, skyWidth, height + 4);
            ctx.text(font, skyLabel, skyX + 6, y + 6, skyHovered ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_TURQUOISE);
            skyRect = new int[]{skyX, y, skyWidth, height};
        } else {
            skyRect = null;
        }

        // name box, left of the button
        int nameWidth = 96;
        int nameX = skyX - nameWidth - 4;
        NeuStyle.window(ctx, nameX, y, nameWidth, height + 4);
        if (nameInput != null) {
            String shown = nameInput;
            while (font.width(shown) > nameWidth - 12) shown = shown.substring(1);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            ctx.text(font, shown + (caret ? "_" : ""), nameX + 5, y + 6, NeuStyle.TEXT_YELLOW);
        } else {
            String displayName = profiles != null ? profiles.getDisplayName() : targetPlayer;
            ctx.text(font, displayName, nameX + 5, y + 6, NeuStyle.TEXT);
        }
        nameRect = new int[]{nameX, y, nameWidth, height};

        // one selector instead of a chip per profile, a whole row of them never fit
        if (profiles != null && profiles.isLoaded()) {
            SkyblockProfile selected = profiles.getSelectedProfile();
            String label = selected == null ? "-" : selected.getCuteName();
            int count = profiles.getProfiles().size();
            String shown = count > 1 ? label + "  " + (profileIndex() + 1) + "/" + count : label;

            int selectorWidth = Math.min(nameX - 6, font.width(shown) + 34);
            NeuStyle.window(ctx, 0, y, selectorWidth, height + 4);

            boolean overLeft = mouseX >= 0 && mouseX < 14 && mouseY >= y && mouseY < y + height;
            boolean overRight = mouseX >= selectorWidth - 14 && mouseX < selectorWidth
                    && mouseY >= y && mouseY < y + height;

            if (count > 1) {
                ctx.text(font, "<", 6, y + 6, overLeft ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
                ctx.text(font, ">", selectorWidth - 10, y + 6, overRight ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT_GRAY);
            }
            ctx.text(font, shown, (selectorWidth - font.width(shown)) / 2, y + 6, NeuStyle.TEXT_TURQUOISE);

            profileSelector = new int[]{0, y, selectorWidth, height};
        } else {
            profileSelector = null;
        }

        if (hoveredTab != null) {
            int tw = font.width(hoveredTab);
            int tx = Math.min(Math.max(mouseX + 8, 2), NeuStyle.PANEL_WIDTH - tw - 6);
            NeuStyle.window(ctx, tx - 3, mouseY - 12, tw + 6, 14);
            ctx.text(font, hoveredTab, tx, mouseY - 8, NeuStyle.TEXT);
            hoveredTab = null;
        }
    }

    private int[] profileSelector;
    private int[] skyRect;
    private int[] nameRect;

    private List<String> profileNames() {
        return profiles == null ? List.of() : new ArrayList<>(profiles.getProfiles().keySet());
    }

    private int profileIndex() {
        SkyblockProfile selected = profiles == null ? null : profiles.getSelectedProfile();
        if (selected == null) return 0;
        return Math.max(0, profileNames().indexOf(selected.getCuteName()));
    }

    /** Steps to the next or previous profile and reloads every page for it. */
    private void cycleProfile(int direction) {
        List<String> names = profileNames();
        if (names.size() < 2) return;

        String next = names.get(((profileIndex() + direction) % names.size() + names.size()) % names.size());
        SkyblockProfile profile = profiles.getProfiles().get(next);
        if (profile == null) return;

        profiles.setSelectedProfile(profile);
        for (ProfileViewerPage page : allPages) page.init(this, profiles);
        updateTabs();
        if (!pages.isEmpty()) pages.get(selectedPageIndex).onSelected();
    }

    private static boolean inside(int[] rect, int x, int y) {
        return rect != null && x >= rect[0] && x < rect[0] + rect[2] && y >= rect[1] && y < rect[1] + rect[3];
    }

    private void renderLoading(GuiGraphicsExtractor ctx, int contentY, int contentHeight) {
        String text = "Loading" + ".".repeat(loadingDots);
        ctx.text(font, text, (NeuStyle.PANEL_WIDTH - font.width(text)) / 2,
                contentY + contentHeight / 2, NeuStyle.TEXT_YELLOW);
    }

    private void renderError(GuiGraphicsExtractor ctx, int contentY, int contentHeight) {
        int centerY = contentY + contentHeight / 2;
        String title = "An error occurred";
        ctx.text(font, title, (NeuStyle.PANEL_WIDTH - font.width(title)) / 2, centerY - 20, NeuStyle.TEXT_RED);
        ctx.text(font, error, (NeuStyle.PANEL_WIDTH - font.width(error)) / 2, centerY, NeuStyle.TEXT_GRAY);

        String hint = "Click to retry";
        ctx.text(font, hint, (NeuStyle.PANEL_WIDTH - font.width(hint)) / 2, centerY + 20, NeuStyle.TEXT_GRAY);
    }

    /** Everything clickable lives here, the render pass only draws and hovers. */
    private boolean handleClick(int mx, int my, int button) {
        int offset = tabsX();
        for (int i = 0; i < pages.size(); i++) {
            int x = offset + i * 28;
            if (mx >= x && mx < x + 28 && my >= -28 && my < 0) {
                selectedPageIndex = i;
                pages.get(i).onSelected();
                return true;
            }
        }

        List<String> recent = TeslaMapsConfig.get().recentPlayers;
        if (recent != null) {
            int count = Math.min(recent.size(), 7);
            int startY = NeuStyle.PANEL_HEIGHT - count * 28;
            for (int i = 0; i < count; i++) {
                String name = recent.get(i);
                if (mx >= NeuStyle.PANEL_WIDTH && mx < NeuStyle.PANEL_WIDTH + 28
                        && my >= startY + i * 28 && my < startY + i * 28 + 28) {
                    if (!name.equalsIgnoreCase(targetPlayer)) {
                        Minecraft.getInstance().schedule(() -> open(name));
                    }
                    return true;
                }
            }
        }

        if (inside(profileSelector, mx, my)) {
            // left edge steps back, anywhere else steps forward
            cycleProfile(mx < profileSelector[0] + 14 ? -1 : 1);
            return true;
        }

        if (inside(skyRect, mx, my) && profiles != null) {
            SkyblockProfile selected = profiles.getSelectedProfile();
            String profileName = selected == null ? "" : "/" + selected.getCuteName();
            net.minecraft.util.Util.getPlatform().openUri(
                    java.net.URI.create("https://sky.shiiyu.moe/stats/" + profiles.getDisplayName() + profileName));
            return true;
        }

        if (inside(nameRect, mx, my)) {
            if (button == 1) {
                nameInput = "";
            } else if (nameInput == null) {
                nameInput = profiles != null ? profiles.getDisplayName() : targetPlayer;
            }
            return true;
        }
        nameInput = null;

        if (error != null) {
            loadProfile();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        int mx = (int) ((event.x() - panelX) / scale);
        int my = (int) ((event.y() - panelY) / scale);

        if (!loading && error == null && selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            if (pages.get(selectedPageIndex).mouseClicked(mx, my, event.button())) return true;
        }
        if (handleClick(mx, my, event.button())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (nameInput == null && !loading && error == null
                && selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && pages.get(selectedPageIndex).charTyped(typed.charAt(0))) return true;
        }
        if (nameInput != null) {
            String typed = event.codepointAsString();
            if (!typed.isEmpty() && nameInput.length() < 16 && typed.matches("[A-Za-z0-9_]+")) {
                nameInput += typed;
                return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (nameInput == null && !loading && error == null
                && selectedPageIndex >= 0 && selectedPageIndex < pages.size()
                && pages.get(selectedPageIndex).keyPressed(event.key(), 0, 0)) {
            return true;
        }
        if (nameInput != null) {
            int key = event.key();
            if (key == 259) { // backspace
                if (!nameInput.isEmpty()) nameInput = nameInput.substring(0, nameInput.length() - 1);
                return true;
            }
            if (key == 257 || key == 335) { // enter
                String wanted = nameInput.trim();
                nameInput = null;
                if (!wanted.isEmpty()) {
                    Minecraft.getInstance().schedule(() -> open(wanted));
                }
                return true;
            }
            if (key == 256) { // escape leaves the box, not the screen
                nameInput = null;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            int mx = (int) ((event.x() - panelX) / scale);
            int my = (int) ((event.y() - panelY) / scale);
            if (pages.get(selectedPageIndex).mouseDragged(mx, my, event.button())) return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            pages.get(selectedPageIndex).mouseReleased();
        }
        return super.mouseReleased(event);
    }

    private boolean tooltipOpen = false;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // a tooltip under the cursor takes the wheel, otherwise it pages the grid below it
        if (tooltipOpen && com.teslamaps.features.ScrollableTooltip.onScroll(verticalAmount)) return true;

        if (!loading && error == null && selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            if (pages.get(selectedPageIndex).mouseScrolled((mouseX - panelX) / scale, (mouseY - panelY) / scale, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static void open(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(new ProfileViewerScreen(playerName)));
    }
}
