// /gfs completion from SkyHanni's GetFromSacksTabComplete.kt (LGPL-2.1), see NOTICE.md
package com.teslamaps.mixin;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel declares {@code /gfs} itself and sends no suggestions for it. Registering a
 * client-side command of the same name breaks the merge into the server dispatcher and
 * takes /warp and /call down with it, so the suggestions are filled in here instead.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    private static final int LIMIT = 100;

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void teslamaps$sackSuggestions(CallbackInfo ci) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.gfsSuggestions || !SkyblockUtils.isInSkyblock()) return;

        String text = input.getValue();
        if (!teslamaps$isSackCommand(text)) return;

        int cursor = Math.min(input.getCursorPosition(), text.length());
        int start = text.lastIndexOf(' ', cursor - 1) + 1;
        if (start <= 0) return;

        String word = text.substring(start, cursor);
        // a number is the amount, not an item
        if (!word.isEmpty() && word.chars().allMatch(Character::isDigit)) return;

        String query = word.toLowerCase(Locale.ROOT);
        List<Suggestion> out = new ArrayList<>();
        StringRange range = StringRange.between(start, cursor);
        for (String option : SackItems.suggestions()) {
            String entry = option.replace(' ', '_');
            if (!entry.toLowerCase(Locale.ROOT).contains(query)) continue;
            out.add(new Suggestion(range, entry));
            if (out.size() >= LIMIT) break;
        }
        if (out.isEmpty()) return;

        pendingSuggestions = CompletableFuture.completedFuture(new Suggestions(range, out));
        showSuggestions(false);
    }

    @Unique
    private static boolean teslamaps$isSackCommand(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.startsWith("/gfs ") || lower.startsWith("/getfromsacks ");
    }
}
