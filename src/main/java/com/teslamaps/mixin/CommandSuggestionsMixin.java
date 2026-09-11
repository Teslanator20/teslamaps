package com.teslamaps.mixin;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.teslamaps.command.HypixelSuggestions;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.SkyblockUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hypixel declares /gfs, /warp and /msg itself and sends no suggestions for them.
 * Registering client-side commands of the same name breaks the merge into the server
 * dispatcher - that is what killed the /warp and /msg suggestions - so they are filled
 * in here instead. Only /call, which Hypixel does not declare, still gets a real
 * client command in HypixelSuggestions.
 */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow private EditBox input;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean keepSuggestions;
    @Shadow private CommandSuggestions.SuggestionsList suggestions;

    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);
    @Shadow public abstract void hide();

    private static final int LIMIT = 100;

    private static final Set<String> SACK_COMMANDS = Set.of("gfs", "getfromsacks");

    /** Everything typed before the word being completed, once shortcuts are resolved. */
    private static final Set<String> NAME_COMMANDS = Set.of(
            "msg", "w", "tell", "whisper", "boop", "visit", "ah", "pv", "viewprofile",
            "duel", "trade", "party", "p",
            "party invite", "p invite", "party kick", "p kick", "party promote", "p promote",
            "party demote", "p demote", "party transfer", "p transfer",
            "f add", "f remove", "friend add", "friend remove",
            "g invite", "g kick", "g promote", "g demote",
            "guild invite", "guild kick", "guild promote", "guild demote",
            "co-op add", "coop add");

    @Unique private boolean teslamaps$ours;
    @Unique private boolean teslamaps$cycled;

    @Inject(method = "updateCommandInfo", at = @At("TAIL"))
    private void teslamaps$suggestions(CallbackInfo ci) {
        // applying an entry rewrites the box and would otherwise rebuild the list and
        // throw the highlight back to the first row
        if (keepSuggestions) return;
        teslamaps$ours = false;
        teslamaps$cycled = false;

        String text = input.getValue();
        if (text.isEmpty() || text.charAt(0) != '/') return;

        int space = text.indexOf(' ');
        if (space < 0) return;
        int cursor = Math.min(input.getCursorPosition(), text.length());
        int start = text.lastIndexOf(' ', cursor - 1) + 1;
        if (start <= 0) return;

        String command = text.substring(1, space).toLowerCase(Locale.ROOT);
        String word = text.substring(start, cursor);

        if (SACK_COMMANDS.contains(command)) {
            teslamaps$sacks(word, start, cursor);
            return;
        }
        if (!HypixelSuggestions.enabled()) return;

        String path = com.teslamaps.command.TMapCommand.expandShortcut(text.substring(1, start).trim())
                .toLowerCase(Locale.ROOT);

        List<String> options;
        if (path.equals("warp")) {
            options = HypixelSuggestions.warps();
        } else if (NAME_COMMANDS.contains(path)) {
            if (!TeslaMapsConfig.get().commandSuggestionsPlayers) return;
            options = HypixelSuggestions.players();
        } else {
            return;
        }

        List<String> matches = HypixelSuggestions.filter(options, word);
        if (matches.isEmpty()) return;

        StringRange range = StringRange.between(start, cursor);
        List<Suggestion> out = new ArrayList<>();
        for (String match : matches) {
            out.add(new Suggestion(range, match));
            if (out.size() >= LIMIT) break;
        }
        teslamaps$show(range, out);
    }

    @Unique
    private void teslamaps$sacks(String word, int start, int cursor) {
        TeslaMapsConfig c = TeslaMapsConfig.get();
        if (!c.gfsSuggestions || !SkyblockUtils.isInSkyblock()) return;
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
        teslamaps$show(range, out);
    }

    @Unique
    private void teslamaps$show(StringRange range, List<Suggestion> out) {
        pendingSuggestions = CompletableFuture.completedFuture(new Suggestions(range, out));
        showSuggestions(false);
        teslamaps$ours = true;
        teslamaps$cycled = false;
    }

    /**
     * Tab walks the list instead of applying the first entry, Enter takes what is
     * highlighted. Enter only does that once Tab was actually used, so a command typed
     * out in full still sends on the first Enter.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void teslamaps$keys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (!teslamaps$ours || suggestions == null) return;

        if (event.isCycleFocus()) {
            suggestions.cycle(event.hasShiftDown() ? -1 : 1);
            teslamaps$cycled = true;
            cir.setReturnValue(true);
        } else if (teslamaps$cycled && event.isConfirmation()) {
            suggestions.useSuggestion();
            hide();
            input.setSuggestion(null);
            teslamaps$ours = false;
            teslamaps$cycled = false;
            cir.setReturnValue(true);
        }
    }
}
