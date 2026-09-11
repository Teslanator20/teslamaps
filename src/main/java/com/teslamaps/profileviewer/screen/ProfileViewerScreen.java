package com.teslamaps.profileviewer.screen;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.pages.*;
import org.lwjgl.glfw.GLFW;

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
    private int scale = 1;
    private int panelX = 0;
    private int panelY = 0;

    private boolean wasMouseDown = false;

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
        boolean isMouseDown = GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        if (loading && System.currentTimeMillis() - lastDotUpdate > 500) {
            loadingDots = (loadingDots + 1) % 4;
            lastDotUpdate = System.currentTimeMillis();
        }

        ctx.fill(0, 0, width, height, NeuStyle.SCREEN_DIM);

        scale = Math.max(1, Math.min(width / (NeuStyle.PANEL_WIDTH + 20), (height - 40) / (NeuStyle.PANEL_HEIGHT + 60)));
        panelX = (width - NeuStyle.PANEL_WIDTH * scale) / 2;
        panelY = (height - NeuStyle.PANEL_HEIGHT * scale) / 2;

        int mx = (mouseX - panelX) / scale;
        int my = (mouseY - panelY) / scale;

        ctx.pose().pushMatrix();
        ctx.pose().translate(panelX, panelY);
        ctx.pose().scale(scale, scale);

        NeuStyle.window(ctx, 0, 0, NeuStyle.PANEL_WIDTH, NeuStyle.PANEL_HEIGHT);

        renderTabs(ctx, mx, my, clicked);

        int contentY = 8;
        int contentHeight = NeuStyle.PANEL_HEIGHT - 16;

        if (loading) {
            renderLoading(ctx, contentY, contentHeight);
        } else if (error != null) {
            renderError(ctx, contentY, contentHeight, my, clicked);
        } else if (profiles != null && profiles.getSelectedProfile() != null) {
            if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
                pages.get(selectedPageIndex).render(ctx, 8, contentY, NeuStyle.PANEL_WIDTH - 16, contentHeight,
                        mx, my, delta);
            }
        }

        renderFooter(ctx, mx, my, clicked);

        if (profileDropdownOpen && profiles != null) {
            renderProfileDropdown(ctx, mx, my, clicked);
        }

        ctx.pose().popMatrix();

        wasMouseDown = isMouseDown;

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        int tabW = 28;
        int tabH = 22;
        int gap = 2;
        int total = pages.size() * (tabW + gap) - gap;
        int x = (NeuStyle.PANEL_WIDTH - total) / 2;
        int y = -tabH + 3;

        for (int i = 0; i < pages.size(); i++) {
            ProfileViewerPage page = pages.get(i);
            boolean selected = i == selectedPageIndex;
            boolean hovered = mouseX >= x && mouseX < x + tabW && mouseY >= y && mouseY < y + tabH;

            if (clicked && hovered && !profileDropdownOpen) {
                selectedPageIndex = i;
                pages.get(selectedPageIndex).onSelected();
            }

            NeuStyle.window(ctx, x, y, tabW, tabH + (selected ? 6 : 3));

            String label = tabLabel(page.getTabName());
            int textX = x + (tabW - font.width(label)) / 2;
            ctx.text(font, label, textX, y + 7,
                    selected ? NeuStyle.TEXT_YELLOW : hovered ? NeuStyle.TEXT : NeuStyle.TEXT_GRAY);

            if (hovered) hoveredTab = page.getTabName();

            x += tabW + gap;
        }
    }

    private static String tabLabel(String name) {
        if (name.length() <= 4) return name;
        String[] words = name.split(" ");
        if (words.length > 1) {
            return ("" + words[0].charAt(0) + words[1].charAt(0)).toUpperCase();
        }
        return name.substring(0, 4);
    }

    private void renderFooter(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        int y = NeuStyle.PANEL_HEIGHT + 3;

        if (profiles != null && profiles.isLoaded() && !profiles.getProfiles().isEmpty()) {
            SkyblockProfile selected = profiles.getSelectedProfile();
            if (selected != null) {
                boolean hovered = mouseX >= 0 && mouseX < 100 && mouseY >= y && mouseY < y + 20;
                if (clicked && hovered && !profileDropdownOpen) profileDropdownOpen = true;

                NeuStyle.window(ctx, 0, y, 100, 20);
                String name = selected.getCuteName();
                ctx.text(font, name, (100 - font.width(name)) / 2, y + 7,
                        hovered ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT);
            }
        }

        String displayName = profiles != null ? profiles.getDisplayName() : targetPlayer;
        NeuStyle.window(ctx, NeuStyle.PANEL_WIDTH - 100, y, 100, 20);
        ctx.text(font, displayName, NeuStyle.PANEL_WIDTH - 100 + (100 - font.width(displayName)) / 2, y + 7,
                NeuStyle.TEXT);

        if (profiles != null) {
            String status = profiles.getOnlineStatus();
            ctx.text(font, status, 106, y + 7,
                    status.startsWith("Online") ? NeuStyle.TEXT_GREEN : NeuStyle.TEXT_GRAY);

            String guild = profiles.getGuildName();
            if (guild != null) {
                ctx.text(font, "[" + guild + "]", 106 + font.width(status) + 6, y + 7, NeuStyle.TEXT_GOLD);
            }
        }

        if (hoveredTab != null) {
            ctx.text(font, hoveredTab, (NeuStyle.PANEL_WIDTH - font.width(hoveredTab)) / 2, -32, NeuStyle.TEXT);
            hoveredTab = null;
        }
    }

    private void renderProfileDropdown(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        int w = 100;
        int rows = profiles.getProfiles().size();
        int h = rows * 16 + 6;
        int y = NeuStyle.PANEL_HEIGHT + 3 - h;

        NeuStyle.window(ctx, 0, y, w, h);

        boolean clickedOutside = clicked;
        int rowY = y + 3;

        for (String profileName : profiles.getProfiles().keySet()) {
            boolean hovered = mouseX >= 3 && mouseX < w - 3 && mouseY >= rowY && mouseY < rowY + 16;
            boolean selected = profiles.getSelectedProfile() != null &&
                    profileName.equals(profiles.getSelectedProfile().getCuteName());

            if (clicked && hovered) {
                SkyblockProfile profile = profiles.getProfiles().get(profileName);
                profiles.setSelectedProfile(profile);
                profileDropdownOpen = false;
                for (ProfileViewerPage page : pages) {
                    page.init(this, profiles);
                }
                pages.get(selectedPageIndex).onSelected();
                clickedOutside = false;
            }

            if (hovered) {
                NeuStyle.slimBox(ctx, 3, rowY, w - 6, 16);
                clickedOutside = false;
            }
            ctx.text(font, profileName, 6, rowY + 4,
                    selected ? NeuStyle.TEXT_YELLOW : NeuStyle.TEXT);
            rowY += 16;
        }

        int buttonY = NeuStyle.PANEL_HEIGHT + 3;
        if (mouseX >= 0 && mouseX < w && mouseY >= buttonY && mouseY < buttonY + 20) {
            clickedOutside = false;
        }

        if (clicked && clickedOutside) {
            profileDropdownOpen = false;
        }
    }

    private void renderLoading(GuiGraphicsExtractor ctx, int contentY, int contentHeight) {
        String text = "Loading" + ".".repeat(loadingDots);
        ctx.text(font, text, (NeuStyle.PANEL_WIDTH - font.width(text)) / 2,
                contentY + contentHeight / 2, NeuStyle.TEXT_YELLOW);
    }

    private void renderError(GuiGraphicsExtractor ctx, int contentY, int contentHeight, int mouseY, boolean clicked) {
        int centerY = contentY + contentHeight / 2;
        String title = "An error occurred";
        ctx.text(font, title, (NeuStyle.PANEL_WIDTH - font.width(title)) / 2, centerY - 20, NeuStyle.TEXT_RED);
        ctx.text(font, error, (NeuStyle.PANEL_WIDTH - font.width(error)) / 2, centerY, NeuStyle.TEXT_GRAY);

        String hint = "Click to retry";
        ctx.text(font, hint, (NeuStyle.PANEL_WIDTH - font.width(hint)) / 2, centerY + 20, NeuStyle.TEXT_GRAY);

        if (clicked && mouseY >= contentY && mouseY < contentY + contentHeight) {
            loadProfile();
        }
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
