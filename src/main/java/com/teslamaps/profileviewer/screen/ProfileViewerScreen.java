package com.teslamaps.profileviewer.screen;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.api.HypixelApi;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.data.SkyblockProfiles;
import com.teslamaps.profileviewer.screen.pages.*;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Main Profile Viewer screen with tabbed navigation.
 */
public class ProfileViewerScreen extends Screen {
    // Colors
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int HEADER_COLOR = 0xFF2A2A2A;
    private static final int TAB_COLOR = 0xFF333333;
    private static final int TAB_SELECTED_COLOR = 0xFF444444;
    private static final int TAB_HOVER_COLOR = 0xFF3A3A3A;
    private static final int ACCENT_COLOR = 0xFF55FF55;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;

    // Layout
    private static final int TAB_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 50;

    // State
    private final String targetPlayer;
    private String targetUuid;
    private SkyblockProfiles profiles;
    private boolean loading = true;
    private String error = null;
    private int loadingDots = 0;
    private long lastDotUpdate = 0;

    // Pages
    private final List<ProfileViewerPage> pages = new ArrayList<>();
    private int selectedPageIndex = 0;

    // Profile dropdown
    private boolean profileDropdownOpen = false;

    // Mouse state for click detection
    private boolean wasMouseDown = false;

    // Scroll offset for pages
    private int scrollOffset = 0;

    public ProfileViewerScreen(String playerName) {
        super(Text.literal("Profile Viewer - " + playerName));
        this.targetPlayer = playerName;
    }

    @Override
    protected void init() {
        // Initialize all 14 pages
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

        // Start loading
        loadProfile();
    }

    private void loadProfile() {
        loading = true;
        error = null;

        // First get UUID from name
        HypixelApi.nameToUuid(targetPlayer).thenAccept(uuid -> {
            if (uuid == null) {
                error = "Player not found: " + targetPlayer;
                loading = false;
                return;
            }

            this.targetUuid = uuid;

            // Create profiles container and load
            profiles = new SkyblockProfiles(uuid, targetPlayer);
            profiles.load().thenRun(() -> {
                loading = false;
                if (profiles.getError() != null) {
                    error = profiles.getError();
                } else {
                    // Add to recent players
                    SkyblockProfiles.addToRecentPlayers(targetPlayer);

                    // Initialize pages with loaded data
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
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Mouse click detection
        boolean isMouseDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean clicked = isMouseDown && !wasMouseDown;

        // Update loading animation
        if (loading && System.currentTimeMillis() - lastDotUpdate > 500) {
            loadingDots = (loadingDots + 1) % 4;
            lastDotUpdate = System.currentTimeMillis();
        }

        // Background
        ctx.fill(0, 0, width, height, BG_COLOR);

        // Header
        renderHeader(ctx, mouseX, mouseY, clicked);

        // Tab bar
        renderTabs(ctx, mouseX, mouseY, clicked);

        // Content area
        int contentY = HEADER_HEIGHT + TAB_HEIGHT;
        int contentHeight = height - contentY;

        if (loading) {
            renderLoading(ctx, contentY, contentHeight);
        } else if (error != null) {
            renderError(ctx, contentY, contentHeight, mouseY, clicked);
        } else if (profiles != null && profiles.getSelectedProfile() != null) {
            // Render current page
            if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
                pages.get(selectedPageIndex).render(ctx, 0, contentY, width, contentHeight,
                        mouseX, mouseY, delta);
            }
        }

        // Profile dropdown (rendered on top)
        if (profileDropdownOpen && profiles != null) {
            renderProfileDropdown(ctx, mouseX, mouseY, clicked);
        }

        wasMouseDown = isMouseDown;

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderHeader(DrawContext ctx, int mouseX, int mouseY, boolean clicked) {
        ctx.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);

        // Player name
        String displayName = profiles != null ? profiles.getDisplayName() : targetPlayer;
        ctx.drawTextWithShadow(textRenderer, displayName, 10, 10, TEXT_COLOR);

        // Profile selector (if loaded)
        if (profiles != null && profiles.isLoaded() && !profiles.getProfiles().isEmpty()) {
            SkyblockProfile selected = profiles.getSelectedProfile();
            if (selected != null) {
                String profileName = selected.getCuteName();
                int dropdownX = 10;
                int dropdownY = 28;
                int dropdownW = 100;
                int dropdownH = 16;

                boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                        mouseY >= dropdownY && mouseY < dropdownY + dropdownH;

                if (clicked && hovered && !profileDropdownOpen) {
                    profileDropdownOpen = true;
                }

                ctx.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + dropdownH,
                        hovered ? TAB_HOVER_COLOR : TAB_COLOR);
                ctx.drawTextWithShadow(textRenderer, profileName, dropdownX + 4, dropdownY + 4, TEXT_COLOR);
                ctx.drawTextWithShadow(textRenderer, profileDropdownOpen ? "^" : "v",
                        dropdownX + dropdownW - 12, dropdownY + 4, TEXT_GRAY);
            }
        }

        // Status (if available)
        if (profiles != null) {
            String status = profiles.getOnlineStatus();
            int statusColor = status.startsWith("Online") ? 0xFF55FF55 : TEXT_GRAY;
            int statusX = width - textRenderer.getWidth(status) - 10;
            ctx.drawTextWithShadow(textRenderer, status, statusX, 10, statusColor);

            // Guild
            String guild = profiles.getGuildName();
            if (guild != null) {
                ctx.drawTextWithShadow(textRenderer, "[" + guild + "]", statusX, 22, 0xFFAAAA00);
            }
        }

        // Close button
        int closeX = width - 20;
        int closeY = 10;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 15 && mouseY >= closeY && mouseY < closeY + 15;
        if (clicked && closeHovered) {
            close();
        }
        ctx.drawTextWithShadow(textRenderer, "X", closeX, closeY, closeHovered ? 0xFFFF5555 : TEXT_GRAY);
    }

    private void renderTabs(DrawContext ctx, int mouseX, int mouseY, boolean clicked) {
        int tabY = HEADER_HEIGHT;
        int tabX = 0;
        int tabWidth = Math.min(80, width / Math.max(1, pages.size()));

        for (int i = 0; i < pages.size(); i++) {
            ProfileViewerPage page = pages.get(i);
            boolean selected = i == selectedPageIndex;
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth &&
                    mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            if (clicked && hovered && !profileDropdownOpen) {
                selectedPageIndex = i;
                pages.get(selectedPageIndex).onSelected();
            }

            int bgColor = selected ? TAB_SELECTED_COLOR : (hovered ? TAB_HOVER_COLOR : TAB_COLOR);
            ctx.fill(tabX, tabY, tabX + tabWidth, tabY + TAB_HEIGHT, bgColor);

            // Bottom border for selected tab
            if (selected) {
                ctx.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + tabWidth, tabY + TAB_HEIGHT, ACCENT_COLOR);
            }

            // Tab name
            String name = page.getTabName();
            int textX = tabX + (tabWidth - textRenderer.getWidth(name)) / 2;
            ctx.drawTextWithShadow(textRenderer, name, textX, tabY + 8, selected ? TEXT_COLOR : TEXT_GRAY);

            tabX += tabWidth;
        }

        // Fill remaining space
        if (tabX < width) {
            ctx.fill(tabX, tabY, width, tabY + TAB_HEIGHT, TAB_COLOR);
        }
    }

    private void renderProfileDropdown(DrawContext ctx, int mouseX, int mouseY, boolean clicked) {
        int dropdownX = 10;
        int dropdownY = 44;
        int dropdownW = 100;

        int y = dropdownY;
        boolean clickedOutside = clicked;

        for (String profileName : profiles.getProfiles().keySet()) {
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                    mouseY >= y && mouseY < y + 16;
            boolean selected = profiles.getSelectedProfile() != null &&
                    profileName.equals(profiles.getSelectedProfile().getCuteName());

            if (clicked && hovered) {
                SkyblockProfile profile = profiles.getProfiles().get(profileName);
                profiles.setSelectedProfile(profile);
                profileDropdownOpen = false;
                // Reinitialize pages with new profile
                for (ProfileViewerPage page : pages) {
                    page.init(this, profiles);
                }
                pages.get(selectedPageIndex).onSelected();
                clickedOutside = false;
            }

            int bgColor = selected ? ACCENT_COLOR : (hovered ? TAB_HOVER_COLOR : TAB_COLOR);
            ctx.fill(dropdownX, y, dropdownX + dropdownW, y + 16, bgColor);
            ctx.drawTextWithShadow(textRenderer, profileName, dropdownX + 4, y + 4,
                    selected ? 0xFF000000 : TEXT_COLOR);

            if (hovered) clickedOutside = false;
            y += 16;
        }

        // Check for click in dropdown header area
        int headerY = 28;
        if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                mouseY >= headerY && mouseY < headerY + 16) {
            clickedOutside = false;
        }

        // Click outside closes dropdown
        if (clicked && clickedOutside) {
            profileDropdownOpen = false;
        }
    }

    private void renderLoading(DrawContext ctx, int contentY, int contentHeight) {
        String dots = ".".repeat(loadingDots);
        String text = "Loading" + dots;
        int textX = (width - textRenderer.getWidth(text)) / 2;
        int textY = contentY + contentHeight / 2;
        ctx.drawTextWithShadow(textRenderer, text, textX, textY, TEXT_COLOR);
    }

    private void renderError(DrawContext ctx, int contentY, int contentHeight, int mouseY, boolean clicked) {
        // Error title
        String title = "Error";
        int titleX = (width - textRenderer.getWidth(title)) / 2;
        int titleY = contentY + contentHeight / 2 - 20;
        ctx.drawTextWithShadow(textRenderer, title, titleX, titleY, 0xFFFF5555);

        // Error message
        int msgX = (width - textRenderer.getWidth(error)) / 2;
        int msgY = contentY + contentHeight / 2;
        ctx.drawTextWithShadow(textRenderer, error, msgX, msgY, TEXT_GRAY);

        // Retry hint
        String hint = "Click to retry";
        int hintX = (width - textRenderer.getWidth(hint)) / 2;
        int hintY = contentY + contentHeight / 2 + 20;
        ctx.drawTextWithShadow(textRenderer, hint, hintX, hintY, TEXT_GRAY);

        // Retry on click
        if (clicked && mouseY >= contentY && mouseY < contentY + contentHeight) {
            loadProfile();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!loading && error == null && selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            if (pages.get(selectedPageIndex).mouseScrolled(mouseX, mouseY, verticalAmount)) {
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }


    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Don't render default background
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Open the profile viewer for a player.
     */
    public static void open(String playerName) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.send(() -> mc.setScreen(new ProfileViewerScreen(playerName)));
    }
}
