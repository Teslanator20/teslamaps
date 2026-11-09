package com.teslamaps.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.features.EnchantColors;
import com.teslamaps.render.gui.HudStyle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EnchantRulesScreen extends Screen {
    private static final int PANEL_W = 440, PANEL_H = 300;
    private static final int ROW_H = 24, VISIBLE = 7;
    private static final String[] MOD_LABELS = {"B", "I", "O", "U", "S"};
    private static final String[] MOD_CODES = {"l", "o", "k", "n", "m"};

    private final Screen parent;
    private int panelX, panelY, listX, listY;
    private int scroll = 0;
    private String status = "";
    private final List<int[]> fields = new ArrayList<>();
    private final List<Boolean> valid = new ArrayList<>();

    public EnchantRulesScreen(Screen parent) {
        super(Component.literal("Enchant Rules"));
        this.parent = parent;
    }

    private List<String> list() { return TeslaMapsConfig.get().enchantColorRules; }

    private static String[] split(String rule) {
        String[] p = Arrays.copyOf(rule.split(":", 5), 5);
        for (int i = 0; i < 5; i++) if (p[i] == null) p[i] = "";
        if (p[1].isEmpty()) p[1] = ">";
        if (p[3].isEmpty()) p[3] = "6";
        if (p[4].isEmpty()) p[4] = "0";
        return p;
    }

    private void set(int idx, String[] p) {
        list().set(idx, String.join(":", p));
        validate();
    }

    private void validate() {
        valid.clear();
        for (String r : list()) valid.add(EnchantColors.parseRule(r) != null);
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        listX = panelX + 12;
        listY = panelY + 48;
        rebuild();
        ScreenFit.clamp(this);
    }

    private void rebuild() {
        clearWidgets();
        fields.clear();
        validate();
        List<String> rules = list();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rules.size() - VISIBLE)));

        for (int row = 0; row < VISIBLE; row++) {
            final int idx = scroll + row;
            if (idx >= rules.size()) break;
            String[] p = split(rules.get(idx));
            int ry = listY + row * ROW_H;
            int x = listX;

            EditBox name = new EditBox(font, x + 4, ry + 5, 122, 12, Component.literal("enchant"));
            name.setMaxLength(128);
            name.setBordered(false);
            name.setHint(Component.literal("Sharpness|Giant Killer"));
            name.setValue(p[0]);
            name.setResponder(v -> { p[0] = v.replace(":", ""); set(idx, p); });
            addRenderableWidget(name);
            fields.add(new int[] {x, ry, 130, 20});
            x += 134;

            addRenderableWidget(new TeslaButton(x, ry, 24, 20, p[1], () -> {
                List<String> cmps = Arrays.asList(EnchantColors.RULE_CMPS);
                p[1] = EnchantColors.RULE_CMPS[(cmps.indexOf(p[1]) + 1) % cmps.size()];
                set(idx, p);
                rebuild();
            }));
            x += 28;

            EditBox level = new EditBox(font, x + 4, ry + 5, 22, 12, Component.literal("level"));
            level.setMaxLength(3);
            level.setBordered(false);
            level.setHint(Component.literal("5"));
            level.setValue(p[2]);
            level.setResponder(v -> { p[2] = v; set(idx, p); });
            addRenderableWidget(level);
            fields.add(new int[] {x, ry, 30, 20});
            x += 34;

            char code = p[3].isEmpty() ? '6' : p[3].charAt(0);
            String sample = code == 'z' ? "§fChroma" : "§" + code + "Colour";
            addRenderableWidget(new TeslaButton(x, ry, 50, 20, sample, () -> {
                String codes = EnchantColors.RULE_CODES;
                int i = codes.indexOf(p[3].isEmpty() ? '6' : p[3].charAt(0));
                p[3] = String.valueOf(codes.charAt((i + 1) % codes.length()));
                set(idx, p);
                rebuild();
            }));
            x += 54;

            int mods;
            try { mods = Integer.parseInt(p[4].trim()); } catch (NumberFormatException e) { mods = 0; }
            for (int b = 0; b < MOD_LABELS.length; b++) {
                final int bit = 1 << b;
                boolean on = (mods & bit) != 0;
                final int cur = mods;
                addRenderableWidget(new TeslaButton(x, ry, 16, 20,
                        on ? "§e§" + MOD_CODES[b] + MOD_LABELS[b] : "§8" + MOD_LABELS[b], () -> {
                    p[4] = String.valueOf(cur ^ bit);
                    set(idx, p);
                    rebuild();
                }));
                x += 17;
            }
            x += 4;

            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "↑", () -> move(idx, -1)));
            x += 17;
            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "↓", () -> move(idx, 1)));
            x += 17;
            addRenderableWidget(new TeslaButton(x, ry, 16, 20, "§cx", () -> { list().remove(idx); rebuild(); }));
        }

        int by = panelY + PANEL_H - 30;
        addRenderableWidget(new TeslaButton(panelX + 12, by, 90, 20, "§a+ Add Rule", () -> {
            list().add(".*:>=:1:6:0");
            scroll = Math.max(0, list().size() - VISIBLE);
            rebuild();
        }));
        addRenderableWidget(new TeslaButton(panelX + 108, by, 100, 20, "Import Clipboard", this::importClipboard));
        addRenderableWidget(new TeslaButton(panelX + 214, by, 100, 20, "Export Clipboard", this::exportClipboard));
        addRenderableWidget(new TeslaButton(panelX + PANEL_W - 12 - 80, by, 80, 20, "§fDone", this::onClose));
    }

    private void move(int idx, int dir) {
        List<String> rules = list();
        int to = idx + dir;
        if (to < 0 || to >= rules.size()) return;
        String r = rules.remove(idx);
        rules.add(to, r);
        rebuild();
    }

    private void importClipboard() {
        try {
            String raw = Minecraft.getInstance().keyboardHandler.getClipboard().trim();
            String decoded = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);
            if (!decoded.startsWith(EnchantColors.SHARE_PREFIX)) { status = "§cNot an enchant rule export"; return; }
            JsonArray arr = JsonParser.parseString(decoded.substring(EnchantColors.SHARE_PREFIX.length())).getAsJsonArray();
            List<String> out = new ArrayList<>();
            for (JsonElement e : arr) {
                String s = e.getAsString();
                if (EnchantColors.parseRule(s) != null) out.add(s);
            }
            if (out.isEmpty()) { status = "§cNo valid rules in clipboard"; return; }
            list().clear();
            list().addAll(out);
            scroll = 0;
            status = "§aImported " + out.size() + " rules";
            rebuild();
        } catch (RuntimeException e) {
            status = "§cClipboard is not a valid export";
        }
    }

    private void exportClipboard() {
        JsonArray arr = new JsonArray();
        for (String s : list()) arr.add(s);
        String enc = Base64.getEncoder().encodeToString(
                (EnchantColors.SHARE_PREFIX + arr).getBytes(StandardCharsets.UTF_8));
        Minecraft.getInstance().keyboardHandler.setClipboard(enc);
        status = "§aCopied " + list().size() + " rules";
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (list().size() > VISIBLE) {
            scroll = Math.max(0, Math.min(list().size() - VISIBLE, scroll - (int) Math.signum(sy)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB0000000);
        HudStyle.screenPanel(ctx, panelX, panelY, PANEL_W, PANEL_H, 8);

        ctx.text(font, "§f§lEnchant Rules", panelX + 12, panelY + 12, 0xFFFFFFFF);
        ctx.text(font, "§8enchant regex · level compare · colour · B I O U S · first match wins",
                panelX + 12, panelY + 26, 0xFFFFFFFF);

        for (int[] f : fields) HudStyle.screenButton(ctx, f[0], f[1], f[2], f[3], false, true);

        List<String> rules = list();
        for (int row = 0; row < VISIBLE && scroll + row < rules.size(); row++) {
            if (scroll + row < valid.size() && !valid.get(scroll + row)) {
                ctx.text(font, "§c!", listX - 8, listY + row * ROW_H + 6, 0xFFFFFFFF);
            }
        }
        if (rules.isEmpty()) {
            ctx.text(font, "§7No rules - click \"+ Add Rule\".", panelX + 12, listY + 6, 0xFFFFFFFF);
        }
        if (!status.isEmpty()) ctx.text(font, status, panelX + 12, panelY + PANEL_H - 44, 0xFFFFFFFF);

        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        TeslaMapsConfig.save();
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
