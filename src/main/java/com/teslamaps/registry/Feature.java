package com.teslamaps.registry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.LegitMode;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.world.phys.Vec3;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * One feature as the registry sees it. Every callback added here is gated by the same
 * section / legit / when checks and wrapped in Perf, so a switched-off feature is never called.
 *
 * Callbacks run in registration order, per kind.
 */
public final class Feature {

    final String name;
    String section;
    boolean legit;
    BooleanSupplier when;
    boolean always;
    int condStamp = -1;
    boolean condOk = true;

    private Feature(String name) {
        this.name = name;
        FeatureRegistry.ALL.add(this);
        FeatureConditions.NAMES.add(name);
    }

    public static Feature of(String name) {
        return new Feature(name);
    }

    /** Section master gate, one of {@link TeslaMapsConfig#SECTIONS}. */
    public Feature section(String section) {
        this.section = section;
        return this;
    }

    /** Skipped while Legit Mode blocks cheats. */
    public Feature legit() {
        this.legit = true;
        return this;
    }

    /** Extra gate, usually the feature's own toggle. Must be cheap: it runs every call. */
    public Feature when(BooleanSupplier when) {
        this.when = when;
        return this;
    }

    public boolean active() {
        if (section != null && !TeslaMapsConfig.get().section(section)) return false;
        if (legit && LegitMode.blocksCheats()) return false;
        if (section != null && !always && FeatureRegistry.blockedOutsideSb(name)) return false;
        if (when != null && !when.getAsBoolean()) return false;
        return !FeatureConditions.on || FeatureConditions.allows(this);
    }

    /** End of client tick, only while a player and level exist. Perf name {@code <name>.tick}. */
    public Feature tick(Runnable r) {
        FeatureRegistry.TICKS.add(new FeatureRegistry.Tick(this, name + ".tick", r, false));
        return this;
    }

    /** End of client tick, also on the title screen. */
    public Feature tickAlways(Runnable r) {
        always = true;
        FeatureRegistry.TICKS.add(new FeatureRegistry.Tick(this, name + ".tick", r, true));
        return this;
    }

    /** World pass after translucent terrain. Perf name {@code <name>}. */
    public Feature world(BiConsumer<PoseStack, Vec3> r) {
        FeatureRegistry.WORLD.add(new FeatureRegistry.World(this, name, w -> r.accept(w.pose(), w.cam())));
        return this;
    }

    /** World pass with the buffer source as well. */
    public Feature worldCtx(Consumer<FeatureRegistry.WorldCtx> r) {
        FeatureRegistry.WORLD.add(new FeatureRegistry.World(this, name, r));
        return this;
    }

    /** HUD overlay under the tab list. Perf name = id. */
    public Feature hud(String id, HudElement r) {
        FeatureRegistry.addHud(this, id, r, FeatureRegistry.InventoryMode.ALWAYS);
        return this;
    }

    /** HUD overlay that moves over the inventory screen while it is open (see HudForeground). */
    public Feature hudForeground(String id, HudElement r) {
        FeatureRegistry.addHud(this, id, r, FeatureRegistry.InventoryMode.FOREGROUND);
        return this;
    }

    /** HUD overlay hidden while the inventory screen is open. */
    public Feature hudHiddenInInventory(String id, HudElement r) {
        FeatureRegistry.addHud(this, id, r, FeatureRegistry.InventoryMode.HIDDEN);
        return this;
    }

    /** Every chat line, player chat included. Color codes are NOT stripped. */
    public Feature chat(Consumer<String> r) {
        FeatureRegistry.CHAT.add(new FeatureRegistry.Chat(this, name + ".chat", r, false));
        return this;
    }

    /** Only lines a player could not have typed ({@code StringUtil.isServerMessage}). */
    public Feature serverChat(Consumer<String> r) {
        FeatureRegistry.CHAT.add(new FeatureRegistry.Chat(this, name + ".chat", r, true));
        return this;
    }

    /** Rows in a config-screen category, appended after the built-in ones. */
    public Feature config(String category, Consumer<com.teslamaps.screen.config.ConfigBuilder> build) {
        FeatureRegistry.CONFIG.add(new FeatureRegistry.Config(category, build, FeatureRegistry.freshRows));
        return this;
    }
}
