package com.teslamaps.features.mining;

// After SkyHanni's BlockStrengthGuide
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.registry.Feature;
import com.teslamaps.render.gui.HudStyle;
import com.teslamaps.screen.ScreenFit;
import com.teslamaps.screen.TeslaButton;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Panel with every mining block: ticks at your mining speed and a bar towards instant mine. */
public final class BlockStrengthGuide extends Screen {

    private static final int COLS = 3, CELL_W = 170, CELL_H = 24;

    private final int speed;
    private final ItemStack[] icons;
    private int left, top, panelW, panelH;

    private BlockStrengthGuide(int speed) {
        super(Component.literal("Block Strength Guide"));
        this.speed = speed;
        OreStrength[] all = OreStrength.values();
        icons = new ItemStack[all.length];
        for (int i = 0; i < all.length; i++) icons[i] = new ItemStack(all[i].icon);
    }

    public static void register() {
        Feature.of("BlockStrengthGuide").section("Mining").when(() -> TeslaMapsConfig.get().blockStrengthGuide)
                .config("Mining", b -> {
                    TeslaMapsConfig c = TeslaMapsConfig.get();
                    b.label("Block Strength");
                    b.toggle("Block Strength Guide", () -> c.blockStrengthGuide, v -> c.blockStrengthGuide = v)
                            .desc("/tmap blockstrength [speed]: ticks per mining block at your Mining Speed and how far you are from instant mine")
                            .tags("mining speed", "ticks", "instant mine", "softcap");
                    b.button("  Open Guide", () -> open(-1));
                    b.info("Reads Mining Speed from the Stats tab widget (/tab)");
                });
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("blockstrength")
                .executes(c -> { open(-1); return 1; })
                .then(ClientCommands.argument("speed", IntegerArgumentType.integer(1, 100000))
                        .executes(c -> { open(IntegerArgumentType.getInteger(c, "speed")); return 1; }));
    }

    private static void open(int override) {
        Minecraft mc = Minecraft.getInstance();
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.blockStrengthGuide || !c.section("Mining")) {
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(
                    "§b[TeslaMaps] §cTurn on Mining > Block Strength Guide first"));
            return;
        }
        int speed = override > 0 ? override : OreStrength.miningSpeed();
        mc.schedule(() -> mc.setScreen(new BlockStrengthGuide(speed)));
    }

    @Override
    protected void init() {
        int rows = (OreStrength.values().length + COLS - 1) / COLS;
        panelW = COLS * CELL_W + 16;
        panelH = rows * CELL_H + 70;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        addRenderableWidget(new TeslaButton(left + panelW - 88, top + panelH - 28, 80, 20, "§fDone", this::onClose));
        ScreenFit.clamp(this);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(g, left, top, panelW, panelH, 8);
        g.text(font, "§f§lBlock Strength Guide", left + 10, top + 10, 0xFFFFFFFF);
        g.text(font, speed > 0 ? "§7Mining Speed: §6⸕" + speed
                        : "§cMining Speed unknown: turn on the Stats tab widget or use /tmap blockstrength <speed>",
                left + 10, top + 22, 0xFFFFFFFF);
        OreStrength[] all = OreStrength.values();
        List<Component> tip = null;
        for (int i = 0; i < all.length; i++) {
            OreStrength o = all[i];
            int x = left + 8 + (i % COLS) * CELL_W, y = top + 40 + (i / COLS) * CELL_H;
            g.item(icons[i], x, y + 2);
            g.text(font, "§f" + o.label, x + 20, y + 1, 0xFFFFFFFF);
            int ticks = o.ticks(speed);
            int barW = 100;
            float frac;
            int color;
            if (ticks == 1) { frac = 1f; color = 0xFF1E90FF; }
            else if (ticks == 4) { frac = (float) speed / o.instant(); color = 0xFF00FA9A; }
            else { frac = speed > 0 ? (float) speed / o.softCap() : 0f; color = 0xFFFF6337; }
            g.fill(x + 20, y + 12, x + 20 + barW, y + 16, 0xFF303030);
            g.fill(x + 20, y + 12, x + 20 + (int) (barW * Math.min(1f, frac)), y + 16, color);
            g.text(font, ticks < 0 ? "§7?" : "§e" + ticks, x + 20 + barW + 4, y + 10, 0xFFFFFFFF);
            if (mouseX >= x && mouseX < x + CELL_W - 4 && mouseY >= y && mouseY < y + CELL_H) tip = tip(o, ticks);
        }
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private List<Component> tip(OreStrength o, int ticks) {
        List<Component> t = new ArrayList<>();
        t.add(Component.literal("§f" + o.label));
        if (ticks > 0) {
            t.add(Component.literal("§3Ticks: §f" + ticks + " §7(" + (ticks * 50) + " ms)"));
            int miss = o.missingForNext(speed);
            if (miss > 0) t.add(Component.literal("§6⸕" + miss + " §cmissing §fto §b" + (ticks - 1) + " §fticks"));
            t.add(Component.literal(ticks == 1 ? "§6Instant Mine"
                    : ticks == 4 ? "§a" + Math.round(speed * 1000.0 / o.instant()) / 10.0 + "% §fto Instant Mine"
                    : "§a" + Math.round(speed * 1000.0 / o.softCap()) / 10.0 + "% §fto Soft Cap"));
        }
        t.add(Component.literal("§3Block Strength: §f" + o.strength));
        t.add(Component.literal("§3Breaking Power: §f" + o.breakingPower));
        t.add(Component.literal("§3Soft Cap: §6⸕" + o.softCap()));
        t.add(Component.literal("§3Instant: §6⸕" + o.instant()));
        t.add(Component.literal("§8Only right at 20 TPS"));
        return t;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
