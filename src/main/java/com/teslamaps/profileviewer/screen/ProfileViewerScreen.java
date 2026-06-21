/*
 * This file is part of TeslaMaps.
 *
 * TeslaMaps is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. TeslaMaps is distributed WITHOUT ANY WARRANTY; see the GNU General
 * Public License for more details.
 *
 * This file references code from Odin
 * (https://github.com/odtheking/Odin, BSD 3-Clause) and Devonian
 * (https://github.com/Synnerz/devonian, GPL-3.0). See NOTICE.md for attribution.
 *
 * See the LICENSE and NOTICE.md files in the project root for full terms.
 */
package com.teslamaps.profileviewer.screen;

import com.teslamaps.TeslaMaps;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.profileviewer.api.HypixelApi;
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

        ctx.fill(0, 0, width, height, BG_COLOR);

        renderHeader(ctx, mouseX, mouseY, clicked);

        renderTabs(ctx, mouseX, mouseY, clicked);

        int contentY = HEADER_HEIGHT + TAB_HEIGHT;
        int contentHeight = height - contentY;

        if (loading) {
            renderLoading(ctx, contentY, contentHeight);
        } else if (error != null) {
            renderError(ctx, contentY, contentHeight, mouseY, clicked);
        } else if (profiles != null && profiles.getSelectedProfile() != null) {
            if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
                pages.get(selectedPageIndex).render(ctx, 0, contentY, width, contentHeight,
                        mouseX, mouseY, delta);
            }
        }

        if (profileDropdownOpen && profiles != null) {
            renderProfileDropdown(ctx, mouseX, mouseY, clicked);
        }

        wasMouseDown = isMouseDown;

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
        ctx.fill(0, 0, width, HEADER_HEIGHT, HEADER_COLOR);

        String displayName = profiles != null ? profiles.getDisplayName() : targetPlayer;
        ctx.text(font, displayName, 10, 10, TEXT_COLOR);

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
                ctx.text(font, profileName, dropdownX + 4, dropdownY + 4, TEXT_COLOR);
                ctx.text(font, profileDropdownOpen ? "^" : "v",
                        dropdownX + dropdownW - 12, dropdownY + 4, TEXT_GRAY);
            }
        }

        if (profiles != null) {
            String status = profiles.getOnlineStatus();
            int statusColor = status.startsWith("Online") ? 0xFF55FF55 : TEXT_GRAY;
            int statusX = width - font.width(status) - 10;
            ctx.text(font, status, statusX, 10, statusColor);

            String guild = profiles.getGuildName();
            if (guild != null) {
                ctx.text(font, "[" + guild + "]", statusX, 22, 0xFFAAAA00);
            }
        }

        int closeX = width - 20;
        int closeY = 10;
        boolean closeHovered = mouseX >= closeX && mouseX < closeX + 15 && mouseY >= closeY && mouseY < closeY + 15;
        if (clicked && closeHovered) {
            onClose();
        }
        ctx.text(font, "X", closeX, closeY, closeHovered ? 0xFFFF5555 : TEXT_GRAY);
    }

    private void renderTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
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

            if (selected) {
                ctx.fill(tabX, tabY + TAB_HEIGHT - 2, tabX + tabWidth, tabY + TAB_HEIGHT, ACCENT_COLOR);
            }

            String name = page.getTabName();
            int textX = tabX + (tabWidth - font.width(name)) / 2;
            ctx.text(font, name, textX, tabY + 8, selected ? TEXT_COLOR : TEXT_GRAY);

            tabX += tabWidth;
        }

        if (tabX < width) {
            ctx.fill(tabX, tabY, width, tabY + TAB_HEIGHT, TAB_COLOR);
        }
    }

    private void renderProfileDropdown(GuiGraphicsExtractor ctx, int mouseX, int mouseY, boolean clicked) {
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
                for (ProfileViewerPage page : pages) {
                    page.init(this, profiles);
                }
                pages.get(selectedPageIndex).onSelected();
                clickedOutside = false;
            }

            int bgColor = selected ? ACCENT_COLOR : (hovered ? TAB_HOVER_COLOR : TAB_COLOR);
            ctx.fill(dropdownX, y, dropdownX + dropdownW, y + 16, bgColor);
            ctx.text(font, profileName, dropdownX + 4, y + 4,
                    selected ? 0xFF000000 : TEXT_COLOR);

            if (hovered) clickedOutside = false;
            y += 16;
        }

        int headerY = 28;
        if (mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                mouseY >= headerY && mouseY < headerY + 16) {
            clickedOutside = false;
        }

        if (clicked && clickedOutside) {
            profileDropdownOpen = false;
        }
    }

    private void renderLoading(GuiGraphicsExtractor ctx, int contentY, int contentHeight) {
        String dots = ".".repeat(loadingDots);
        String text = "Loading" + dots;
        int textX = (width - font.width(text)) / 2;
        int textY = contentY + contentHeight / 2;
        ctx.text(font, text, textX, textY, TEXT_COLOR);
    }

    private void renderError(GuiGraphicsExtractor ctx, int contentY, int contentHeight, int mouseY, boolean clicked) {
        String title = "Error";
        int titleX = (width - font.width(title)) / 2;
        int titleY = contentY + contentHeight / 2 - 20;
        ctx.text(font, title, titleX, titleY, 0xFFFF5555);

        int msgX = (width - font.width(error)) / 2;
        int msgY = contentY + contentHeight / 2;
        ctx.text(font, error, msgX, msgY, TEXT_GRAY);

        String hint = "Click to retry";
        int hintX = (width - font.width(hint)) / 2;
        int hintY = contentY + contentHeight / 2 + 20;
        ctx.text(font, hint, hintX, hintY, TEXT_GRAY);

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
