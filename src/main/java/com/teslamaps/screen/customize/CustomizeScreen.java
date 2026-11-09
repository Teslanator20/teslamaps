package com.teslamaps.screen.customize;

import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.customize.AnimatedDye;
import com.teslamaps.features.customize.ArmorTrimId;
import com.teslamaps.features.customize.CustomArmorAnimatedDyes;
import com.teslamaps.features.customize.Customize;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** {@code /tmap customize}. Ported from Skyblocker's CustomizeScreen. */
public class CustomizeScreen extends Screen {
    static final Minecraft CLIENT = Minecraft.getInstance();

    private final @Nullable Screen previousScreen;
    private final Map<String, PreviousConfig> previousConfigs = new HashMap<>();

    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final boolean item;
    private TabNavigationBar tabNavigation;

    private final LinearLayout footerLayout = LinearLayout.vertical().spacing(4);
    private ArmorTab armorTab;
    private ItemTab itemTab;

    private final ItemStack initialItem;

    public CustomizeScreen(@Nullable Screen previousScreen, boolean item) {
        this(previousScreen, item, ItemStack.EMPTY);
    }

    /** Opens the item tab on one specific stack (hover key). */
    public CustomizeScreen(@Nullable Screen previousScreen, ItemStack stack) {
        this(previousScreen, true, stack);
    }

    private CustomizeScreen(@Nullable Screen previousScreen, boolean item, ItemStack initialItem) {
        super(Component.translatable("teslamaps.customize.title").withStyle(ChatFormatting.GRAY));
        this.previousScreen = previousScreen;
        this.item = item;
        this.initialItem = initialItem;
    }

    ItemStack initialItem() {
        return initialItem;
    }

    /** Remembers what an item looked like before this screen touched it, so cancel can put it back. */
    public void backupConfigs(ItemStack stack) {
        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty() || previousConfigs.containsKey(uuid)) return;
        TeslaMapsConfig config = TeslaMapsConfig.get();
        previousConfigs.put(uuid, new PreviousConfig(
                Optional.ofNullable(config.customArmorTrims.get(uuid)),
                config.customDyeColors.containsKey(uuid) ? OptionalInt.of(config.customDyeColors.get(uuid)) : OptionalInt.empty(),
                Optional.ofNullable(config.customAnimatedDyes.get(uuid)),
                Optional.ofNullable(config.customHelmetTextures.get(uuid)),
                Optional.ofNullable(config.customAnimatedHelmetTextures.get(uuid)),
                Optional.ofNullable(config.customItemNames.get(uuid)),
                Optional.ofNullable(config.customGlint.get(uuid)),
                Optional.ofNullable(config.customItemModel.get(uuid)),
                Optional.ofNullable(config.customArmorModel.get(uuid))));
    }

    @Override
    public void tick() {
        if (armorTab != null) armorTab.tick();
    }

    @Override
    protected void init() {
        super.init();

        armorTab = new ArmorTab(this);
        itemTab = new ItemTab(this);
        tabNavigation = net.minecraft.client.gui.components.tabs.MenuTabBar.builder(tabManager, width)
                .addTabs(armorTab, itemTab)
                .build();
        int top = tabNavigation.getRectangle().bottom();
        tabNavigation.arrangeElements(width);
        tabManager.setTabArea(new ScreenRectangle(0, top, width, height - top - 30));
        tabNavigation.selectTab(item ? 1 : 0, false);
        addRenderableWidget(tabNavigation);

        LinearLayout resets = footerLayout.addChild(LinearLayout.horizontal().spacing(5), LayoutSettings::alignHorizontallyCenter);
        addRenderableWidget(resets.addChild(Button.builder(
                Component.translatable("teslamaps.customize.resetPiece"), b -> resetPiece()).width(90).build()));
        addRenderableWidget(resets.addChild(Button.builder(
                Component.translatable("teslamaps.customize.resetArmor"), b -> resetArmor()).width(90).build()));
        addRenderableWidget(resets.addChild(Button.builder(
                Component.translatable("teslamaps.customize.resetAll"), b -> resetAll()).width(90).build()));

        LinearLayout actions = footerLayout.addChild(LinearLayout.horizontal().spacing(5), LayoutSettings::alignHorizontallyCenter);
        addRenderableWidget(actions.addChild(Button.builder(Component.translatable("gui.cancel"), b -> cancel()).width(100).build()));
        addRenderableWidget(actions.addChild(Button.builder(Component.translatable("gui.done"), b -> onClose()).width(100).build()));
        footerLayout.arrangeElements();
        repositionElements();
    }

    @Override
    public void added() {
        super.added();
        if (armorTab != null) armorTab.recreate();
    }

    private ItemStack currentTabItem() {
        if (tabManager.getCurrentTab() == itemTab && itemTab != null) return itemTab.selected();
        return armorTab == null ? ItemStack.EMPTY : armorTab.selected();
    }

    private void resetPiece() {
        ItemStack stack = currentTabItem();
        String uuid = Customize.uuid(stack);
        if (uuid.isEmpty()) return;
        backupConfigs(stack);
        Customize.reset(uuid);
        reloadTabs();
    }

    private void resetArmor() {
        if (armorTab == null) return;
        for (ItemStack piece : armorTab.pieces()) {
            if (piece == null || piece.isEmpty()) continue;
            backupConfigs(piece);
            Customize.reset(Customize.uuid(piece));
        }
        reloadTabs();
    }

    private void resetAll() {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                Customize.resetAll();
                // nothing left to restore, the backups would put the old values back
                previousConfigs.clear();
                TeslaMapsConfig.save();
            }
            minecraft.gui.setScreen(this);
        }, Component.translatable("teslamaps.customize.resetAll"),
                Component.translatable("teslamaps.customize.resetAll.confirm")));
    }

    private void reloadTabs() {
        if (armorTab != null) armorTab.reloadFromConfig();
        if (itemTab != null) itemTab.reloadFromConfig();
    }

    private void cancel() {
        TeslaMapsConfig config = TeslaMapsConfig.get();
        previousConfigs.forEach((uuid, previous) -> {
            put(config.customArmorTrims, uuid, previous.armorTrimId());
            previous.color().ifPresentOrElse(
                    color -> config.customDyeColors.put(uuid, color),
                    () -> config.customDyeColors.remove(uuid));
            put(config.customAnimatedDyes, uuid, previous.animatedDye());
            put(config.customHelmetTextures, uuid, previous.helmetTexture());
            put(config.customAnimatedHelmetTextures, uuid, previous.animatedHelmetTexture());
            put(config.customItemNames, uuid, previous.itemName());
            put(config.customGlint, uuid, previous.glint());
            put(config.customItemModel, uuid, previous.itemModel());
            put(config.customArmorModel, uuid, previous.armorModel());
        });
        Customize.refresh();
        TeslaMapsConfig.save();
        onClose();
    }

    private static <T> void put(Map<String, T> map, String uuid, Optional<T> value) {
        value.ifPresentOrElse(v -> map.put(uuid, v), () -> map.remove(uuid));
    }

    /** Every write from a child widget goes through here so the fast-path flags stay in sync. */
    public static void updated() {
        Customize.refresh();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        boolean handled = super.mouseClicked(click, doubled);
        if (!handled) setFocused(null);
        return handled;
    }

    @Override
    protected void repositionElements() {
        int top = tabNavigation.getRectangle().bottom();
        tabNavigation.arrangeElements(width);
        footerLayout.setPosition((width - footerLayout.getWidth()) / 2, height - footerLayout.getHeight() - 5);
        tabManager.setTabArea(new ScreenRectangle(0, top, width, footerLayout.getY() - top - 2));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        if (armorTab != null) armorTab.close();
        CustomArmorAnimatedDyes.cleanTrackers();
        Customize.refresh();
        TeslaMapsConfig.save();
    }

    @Override
    public void onClose() {
        Customize.refresh();
        TeslaMapsConfig.save();
        minecraft.gui.setScreen(previousScreen);
    }

    private record PreviousConfig(Optional<ArmorTrimId> armorTrimId,
                                  OptionalInt color,
                                  Optional<AnimatedDye> animatedDye,
                                  Optional<String> helmetTexture,
                                  Optional<String> animatedHelmetTexture,
                                  Optional<Component> itemName,
                                  Optional<Boolean> glint,
                                  Optional<Identifier> itemModel,
                                  Optional<Identifier> armorModel) {}
}
