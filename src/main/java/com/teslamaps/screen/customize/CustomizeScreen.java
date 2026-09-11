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
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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

    private final LinearLayout footerLayout = LinearLayout.horizontal().spacing(5);
    private ArmorTab armorTab;

    public CustomizeScreen(@Nullable Screen previousScreen, boolean item) {
        super(Component.translatable("teslamaps.customize.title").withStyle(ChatFormatting.GRAY));
        this.previousScreen = previousScreen;
        this.item = item;
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
        tabNavigation = TabNavigationBar.builder(tabManager, width)
                .addTabs(armorTab, new ItemTab(this))
                .build();
        int top = tabNavigation.getRectangle().bottom();
        tabNavigation.arrangeElements();
        tabManager.setTabArea(new ScreenRectangle(0, top, width, height - top - 30));
        tabNavigation.selectTab(item ? 1 : 0, false);
        addRenderableWidget(tabNavigation);

        addRenderableWidget(footerLayout.addChild(Button.builder(Component.translatable("gui.cancel"), b -> cancel()).build()));
        addRenderableWidget(footerLayout.addChild(Button.builder(Component.translatable("gui.done"), b -> onClose()).build()));
        footerLayout.arrangeElements();
        repositionElements();
    }

    @Override
    public void added() {
        super.added();
        if (armorTab != null) armorTab.recreate();
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
        tabNavigation.arrangeElements();
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
        minecraft.setScreen(previousScreen);
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
