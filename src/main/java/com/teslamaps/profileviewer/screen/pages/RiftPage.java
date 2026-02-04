package com.teslamaps.profileviewer.screen.pages;

import com.google.gson.JsonObject;
import com.teslamaps.profileviewer.data.SkyblockProfile;
import com.teslamaps.profileviewer.screen.ProfileViewerPage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * Rift page showing Rift-specific progression.
 */
public class RiftPage extends ProfileViewerPage {
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFF888888;
    private static final int TEXT_GREEN = 0xFF55FF55;
    private static final int TEXT_PURPLE = 0xFFAA00AA;
    private static final int TEXT_GOLD = 0xFFFFAA00;
    private static final int BAR_BG = 0xFF333333;

    @Override
    public String getTabName() {
        return "Rift";
    }

    @Override
    public ItemStack getTabIcon() {
        return new ItemStack(Items.ENDER_EYE);
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int height,
                       int mouseX, int mouseY, float delta) {
        SkyblockProfile profile = getProfile();
        if (profile == null) return;

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        JsonObject memberData = profile.getMemberData();
        int padding = 15;
        int contentX = x + padding;
        int col2X = x + width / 2;

        int lineY = y + padding;

        ctx.drawTextWithShadow(tr, "The Rift", contentX, lineY, TEXT_PURPLE);
        lineY += 20;

        // Get rift data
        JsonObject rift = getNestedObject(memberData, "rift");
        if (rift == null) {
            ctx.drawTextWithShadow(tr, "No Rift data found", contentX, lineY, TEXT_GRAY);
            return;
        }

        // === Motes ===
        ctx.drawTextWithShadow(tr, "Motes", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject currencies = profile.getMemberData().has("currencies") ?
                profile.getMemberData().getAsJsonObject("currencies") : null;
        if (currencies != null && currencies.has("motes_purse")) {
            double motes = currencies.get("motes_purse").getAsDouble();
            ctx.drawTextWithShadow(tr, "Current: " + formatNumber(motes), contentX, lineY, TEXT_PURPLE);
            lineY += 12;
        }

        if (rift.has("lifetime_motes_earned")) {
            double lifetimeMotes = rift.get("lifetime_motes_earned").getAsDouble();
            ctx.drawTextWithShadow(tr, "Lifetime: " + formatNumber(lifetimeMotes), contentX, lineY, TEXT_GRAY);
            lineY += 20;
        }

        // === Timecharms ===
        ctx.drawTextWithShadow(tr, "Timecharms", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject timecharms = getNestedObject(rift, "gallery.secured_trophies");
        if (timecharms != null) {
            int count = timecharms.size();
            ctx.drawTextWithShadow(tr, "Collected: " + count, contentX, lineY, TEXT_WHITE);
            lineY += 20;
        } else {
            ctx.drawTextWithShadow(tr, "None collected", contentX, lineY, TEXT_GRAY);
            lineY += 20;
        }

        // === Enigma Souls ===
        ctx.drawTextWithShadow(tr, "Enigma Souls", contentX, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject enigma = getNestedObject(rift, "enigma");
        if (enigma != null && enigma.has("found_souls")) {
            int souls = enigma.getAsJsonArray("found_souls").size();
            ctx.drawTextWithShadow(tr, "Found: " + souls + " / 42", contentX, lineY,
                    souls >= 42 ? TEXT_GREEN : TEXT_WHITE);
        } else {
            ctx.drawTextWithShadow(tr, "Found: 0 / 42", contentX, lineY, TEXT_GRAY);
        }
        lineY += 20;

        // === Right column ===
        lineY = y + padding;

        // Montezuma
        ctx.drawTextWithShadow(tr, "Montezuma", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject castle = getNestedObject(rift, "castle");
        if (castle != null) {
            // Grubber stacks
            if (castle.has("grubber_stacks")) {
                int stacks = castle.get("grubber_stacks").getAsInt();
                ctx.drawTextWithShadow(tr, "Grubber Stacks: " + stacks, col2X, lineY, TEXT_WHITE);
                lineY += 12;
            }
        } else {
            ctx.drawTextWithShadow(tr, "No castle data", col2X, lineY, TEXT_GRAY);
            lineY += 12;
        }

        lineY += 8;

        // Village Plaza
        ctx.drawTextWithShadow(tr, "Village Plaza", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject village = getNestedObject(rift, "village_plaza");
        if (village != null) {
            if (village.has("murder")) {
                JsonObject murder = village.getAsJsonObject("murder");
                if (murder.has("step_index")) {
                    int step = murder.get("step_index").getAsInt();
                    ctx.drawTextWithShadow(tr, "Murder Mystery: Step " + step, col2X, lineY, TEXT_WHITE);
                    lineY += 12;
                }
            }
        }

        // Slayer
        lineY += 8;
        ctx.drawTextWithShadow(tr, "Rift Slayer", col2X, lineY, TEXT_GREEN);
        lineY += 16;

        JsonObject slayer = getNestedObject(rift, "slayer");
        if (slayer != null && slayer.has("vampire")) {
            JsonObject vampire = slayer.getAsJsonObject("vampire");
            if (vampire.has("rift_level")) {
                int level = vampire.get("rift_level").getAsInt();
                ctx.drawTextWithShadow(tr, "Vampire Level: " + level, col2X, lineY, TEXT_GOLD);
            }
        }
    }

    private JsonObject getNestedObject(JsonObject root, String path) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (String part : parts) {
            if (current == null || !current.has(part)) return null;
            if (!current.get(part).isJsonObject()) return null;
            current = current.getAsJsonObject(part);
        }
        return current;
    }
}
