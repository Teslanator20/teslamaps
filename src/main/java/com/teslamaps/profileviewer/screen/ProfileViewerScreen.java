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

    private final List<ProfileViewerPage> pages = new ArrayList<>();
    private int selectedPageIndex = 0;

    private boolean profileDropdownOpen = false;
    private String hoveredTab = null;
    private net.minecraft.world.item.ItemStack hoveredStack = net.minecraft.world.item.ItemStack.EMPTY;

    public int toScreenX(int panelCoordinate) {
        return panelX + panelCoordinate * scale;
    }

    public int toScreenY(int panelCoordinate) {
        return panelY + panelCoordinate * scale;
    }

    public void setHoveredStack(net.minecraft.world.item.ItemStack stack) {
        this.hoveredStack = stack;
    }

    private String nameInput = null;
    private long nameCursorBlink = 0;
    private int scale = 1;
    private int panelX = 0;
    private int panelY = 0;


    private int scrollOffset = 0;

    public ProfileViewerScreen(String playerName) {
        super(Component.literal("Profile Viewer - " + playerName));
        this.targetPlayer = playerName;
    }

    @Override
    protected void init() {
        pages.clear();
        pages.add(new BasicPage());
        pages.add(new DungeonPage());
        pages.add(new ExtraPage());
        pages.add(new InventoriesPage());
        pages.add(new CollectionsPage());
        pages.add(new PetsPage());
        pages.add(new MiningPage());
        pages.add(new BingoPage());
        pages.add(new TrophyFishPage());
        pages.add(new BestiaryPage());
        pages.add(new CrimsonIslePage());
        pages.add(new MuseumPage());
        pages.add(new RiftPage());
        pages.add(new GardenPage());

        loadProfile();
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

                    for (ProfileViewerPage page : pages) {
                        page.init(this, profiles);
                    }
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

        scale = Math.max(1, Math.min(width / (NeuStyle.PANEL_WIDTH + 60), (height - 20) / (NeuStyle.PANEL_HEIGHT + 60)));
        panelX = (width - NeuStyle.PANEL_WIDTH * scale) / 2;
        panelY = (height - NeuStyle.PANEL_HEIGHT * scale) / 2 + 10 * scale;

        int mx = (mouseX - panelX) / scale;
        int my = (mouseY - panelY) / scale;

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

        // outside the panel transform, so it sits on top and lands where the cursor is
        if (!hoveredStack.isEmpty()) {
            ctx.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            hoveredStack = net.minecraft.world.item.ItemStack.EMPTY;
        }
                    ctx.disableScissor();
                }
            }
        }

        renderFooter(ctx, mx, my);

        if (profileDropdownOpen && profiles != null) {
            renderProfileDropdown(ctx, mx, my);
        }

        ctx.pose().popMatrix();

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        // after the base class, otherwise its widget pass drops the deferred tooltip again
        if (!hoveredStack.isEmpty()) {
            ctx.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
            hoveredStack = net.minecraft.world.item.ItemStack.EMPTY;
        }
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        // NEU: 28 wide, flush with the left window edge, sitting on top of it
        for (int i = 0; i < pages.size(); i++) {
            ProfileViewerPage page = pages.get(i);
            boolean selected = i == selectedPageIndex;
            int x = i * 28;
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
        for (int i = 0; i < Math.min(recent.size(), 7); i++) {
            String name = recent.get(i);
            int y = i * 28;
            boolean selected = name.equalsIgnoreCase(targetPlayer);
            boolean hovered = mouseX >= x && mouseX < x + 28 && mouseY >= y && mouseY < y + 28;

            NeuStyle.sideTab(ctx, selected ? x : x - 1, y, selected);
            PlayerHeads.faceByName(ctx, name, x + 7, y + 6, 16);

            if (hovered) hoveredTab = name;
        }
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int y = NeuStyle.PANEL_HEIGHT + 3;

        if (profiles != null && profiles.isLoaded() && !profiles.getProfiles().isEmpty()) {
            SkyblockProfile selected = profiles.getSelectedProfile();
            if (selected != null) {
                boolean hovered = mouseX >= 0 && mouseX < 100 && mouseY >= y && mouseY < y + 20;

                NeuStyle.window(ctx, 0, y, 100, 20);
                String name = selected.getCuteName();
                ctx.text(font, name, 50 - font.width(name) / 2, y + 7,
                        hovered ? NeuStyle.TEXT : NeuStyle.TEXT_TURQUOISE);
            }
        }

        NeuStyle.window(ctx, NeuStyle.PANEL_WIDTH - 100, y, 100, 20);
        if (nameInput != null) {
            String shown = nameInput;
            while (font.width(shown) > 88) shown = shown.substring(1);
            boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
            String withCaret = shown + (caret ? "_" : "");
            ctx.text(font, withCaret, NeuStyle.PANEL_WIDTH - 96, y + 7, NeuStyle.TEXT_YELLOW);
        } else {
            String displayName = profiles != null ? profiles.getDisplayName() : targetPlayer;
            ctx.text(font, displayName, NeuStyle.PANEL_WIDTH - 50 - font.width(displayName) / 2, y + 7, NeuStyle.TEXT);
        }

        if (hoveredTab != null) {
            int tw = font.width(hoveredTab);
            int tx = Math.min(Math.max(mouseX + 8, 2), NeuStyle.PANEL_WIDTH - tw - 6);
            NeuStyle.window(ctx, tx - 3, mouseY - 12, tw + 6, 14);
            ctx.text(font, hoveredTab, tx, mouseY - 8, NeuStyle.TEXT);
            hoveredTab = null;
        }
    }

    private void renderProfileDropdown(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int w = 100;
        int rows = profiles.getProfiles().size();
        int h = rows * 14 + 6;
        int y = NeuStyle.PANEL_HEIGHT + 3 - h;

        NeuStyle.window(ctx, 0, y, w, h);

        int rowY = y + 3;

        for (String profileName : profiles.getProfiles().keySet()) {
            boolean hovered = mouseX >= 3 && mouseX < w - 3 && mouseY >= rowY && mouseY < rowY + 14;
            boolean selected = profiles.getSelectedProfile() != null &&
                    profileName.equals(profiles.getSelectedProfile().getCuteName());

            if (hovered) NeuStyle.slimBox(ctx, 3, rowY, w - 6, 14);
            ctx.text(font, profileName, 50 - font.width(profileName) / 2, rowY + 3,
                    selected ? NeuStyle.TEXT_TURQUOISE : NeuStyle.TEXT);
            rowY += 14;
        }
    }

    /** Everything clickable lives here, the render pass only draws and hovers. */
    private boolean handleClick(int mx, int my, int button) {
        if (profileDropdownOpen && profiles != null) {
            int w = 100;
            int rows = profiles.getProfiles().size();
            int h = rows * 14 + 6;
            int y = NeuStyle.PANEL_HEIGHT + 3 - h;
            int rowY = y + 3;
            for (String profileName : profiles.getProfiles().keySet()) {
                if (mx >= 3 && mx < w - 3 && my >= rowY && my < rowY + 14) {
                    profiles.setSelectedProfile(profiles.getProfiles().get(profileName));
                    profileDropdownOpen = false;
                    for (ProfileViewerPage page : pages) page.init(this, profiles);
                    pages.get(selectedPageIndex).onSelected();
                    return true;
                }
                rowY += 14;
            }
            profileDropdownOpen = false;
            return true;
        }

        for (int i = 0; i < pages.size(); i++) {
            int x = i * 28;
            if (mx >= x && mx < x + 28 && my >= -28 && my < 0) {
                selectedPageIndex = i;
                pages.get(i).onSelected();
                return true;
            }
        }

        List<String> recent = TeslaMapsConfig.get().recentPlayers;
        if (recent != null) {
            for (int i = 0; i < Math.min(recent.size(), 7); i++) {
                String name = recent.get(i);
                if (mx >= NeuStyle.PANEL_WIDTH && mx < NeuStyle.PANEL_WIDTH + 28
                        && my >= i * 28 && my < i * 28 + 28) {
                    if (!name.equalsIgnoreCase(targetPlayer)) {
                        Minecraft.getInstance().schedule(() -> open(name));
                    }
                    return true;
                }
            }
        }

        int footerY = NeuStyle.PANEL_HEIGHT + 3;
        if (mx >= 0 && mx < 100 && my >= footerY && my < footerY + 20
                && profiles != null && profiles.isLoaded() && !profiles.getProfiles().isEmpty()) {
            profileDropdownOpen = true;
            return true;
        }

        // the name box behaves like NEU's: click to type a player, right click clears it
        if (mx >= NeuStyle.PANEL_WIDTH - 100 && mx < NeuStyle.PANEL_WIDTH && my >= footerY && my < footerY + 20) {
            if (button == 1) {
                nameInput = "";
            } else if (nameInput == null) {
                nameInput = profiles != null ? profiles.getDisplayName() : targetPlayer;
            }
            nameCursorBlink = System.currentTimeMillis();
            return true;
        }
        nameInput = null;

        if (error != null) {
            loadProfile();
            return true;
        }

        return false;
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

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        int mx = (int) ((event.x() - panelX) / scale);
        int my = (int) ((event.y() - panelY) / scale);

        if (!loading && error == null && !profileDropdownOpen
                && selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            if (pages.get(selectedPageIndex).mouseClicked(mx, my, event.button())) return true;
        }
        if (handleClick(mx, my, event.button())) return true;
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
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
