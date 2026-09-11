package com.teslamaps.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.teslamaps.config.TeslaMapsConfig;
import com.teslamaps.utils.SackItems;
import com.teslamaps.utils.SkyblockUtils;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * Hypixel sends its command tree without suggestions, so the nodes we care about are
 * rebuilt with our own lists as the tree arrives (see CommandsNodeMixin). Doing it on the
 * tree instead of on the popup is what makes it stick - the popup is rebuilt from the
 * server answer a few frames later, which wiped anything written into it directly.
 */
public class HypixelNodes {

    private static final Command<SharedSuggestionProvider> NOOP = context -> 0;

    /** /msg Player some text */
    private static final Set<String> PLAYER_AND_TEXT = Set.of("msg", "w", "tell", "whisper", "message");
    /** /visit Player */
    private static final Set<String> PLAYER_ONLY = Set.of(
            "boop", "visit", "ah", "pv", "viewprofile", "duel", "trade", "invite");

    private static final Set<String> PARTY_SUBS = Set.of(
            "invite", "kick", "promote", "demote", "transfer", "kickoffline", "accept", "join");
    private static final Set<String> FRIEND_SUBS = Set.of("add", "remove");
    private static final Set<String> GUILD_SUBS = Set.of("invite", "kick", "promote", "demote");

    /**
     * @return the node to use for {@code literal}, or {@code original} when we have nothing
     *         better - whatever we do not touch keeps behaving exactly as Hypixel sent it.
     */
    public static CommandNode<SharedSuggestionProvider> replace(
            String literal, CommandNode<SharedSuggestionProvider> original) {
        // the tree arrives on join, possibly before the server address is known, so the
        // config and the "are we on Hypixel" check happen in the providers instead
        String name = literal.toLowerCase(Locale.ROOT);

        if (name.equals("gfs") || name.equals("getfromsacks")) return sacks(name);
        if (name.equals("warp")) return greedy(name, HypixelSuggestions::warps);
        if (name.equals("call")) return greedy(name, HypixelSuggestions::contacts);

        if (PLAYER_AND_TEXT.contains(name)) {
            return literal(name)
                    .then(player().then(text("message")))
                    .build();
        }
        if (PLAYER_ONLY.contains(name)) {
            return literal(name).then(player()).build();
        }
        if (name.equals("party") || name.equals("p")) {
            // /p Player invites, so the bare argument takes names too
            return subCommands(original, name, PARTY_SUBS, true);
        }
        if (name.equals("f") || name.equals("friend")) {
            return subCommands(original, name, FRIEND_SUBS, false);
        }
        if (name.equals("g") || name.equals("guild")) {
            return subCommands(original, name, GUILD_SUBS, false);
        }
        return original;
    }

    /** Our sub-commands plus everything of Hypixel's that we did not rebuild. */
    private static CommandNode<SharedSuggestionProvider> subCommands(
            CommandNode<SharedSuggestionProvider> original, String name, Set<String> subs, boolean bareName) {
        LiteralArgumentBuilder<SharedSuggestionProvider> builder = literal(name);
        for (String sub : subs) builder.then(literal(sub).then(player()));
        if (bareName) builder.then(player());

        CommandNode<SharedSuggestionProvider> node = builder.build();
        for (CommandNode<SharedSuggestionProvider> child : original.getChildren()) {
            if (node.getChild(child.getName()) == null) node.addChild(child);
        }
        return node;
    }

    private static CommandNode<SharedSuggestionProvider> sacks(String name) {
        return literal(name)
                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                                "item", StringArgumentType.word())
                        .suggests(HypixelNodes::sackItems)
                        .executes(NOOP)
                        .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                                        "amount", StringArgumentType.word())
                                .executes(NOOP)))
                .build();
    }

    private static CommandNode<SharedSuggestionProvider> greedy(
            String name, java.util.function.Supplier<List<String>> options) {
        return literal(name)
                .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                                "target", StringArgumentType.greedyString())
                        .suggests((context, builder) -> HypixelSuggestions.enabled()
                                ? suggest(options.get(), builder) : builder.buildFuture())
                        .executes(NOOP))
                .build();
    }

    private static LiteralArgumentBuilder<SharedSuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.<SharedSuggestionProvider>literal(name).executes(NOOP);
    }

    private static RequiredArgumentBuilder<SharedSuggestionProvider, String> player() {
        return RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                        "player", StringArgumentType.word())
                .suggests(players())
                .executes(NOOP);
    }

    private static RequiredArgumentBuilder<SharedSuggestionProvider, String> text(String name) {
        return RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument(
                name, StringArgumentType.greedyString()).executes(NOOP);
    }

    private static SuggestionProvider<SharedSuggestionProvider> players() {
        return (context, builder) -> {
            if (!HypixelSuggestions.enabled() || !TeslaMapsConfig.get().commandSuggestionsPlayers) {
                return builder.buildFuture();
            }
            return suggest(HypixelSuggestions.players(), builder);
        };
    }

    private static CompletableFuture<Suggestions> sackItems(
            com.mojang.brigadier.context.CommandContext<SharedSuggestionProvider> context,
            SuggestionsBuilder builder) {
        if (!TeslaMapsConfig.get().gfsSuggestions || !SkyblockUtils.isInSkyblock()) {
            return builder.buildFuture();
        }
        // a number is the amount, not an item
        if (builder.getRemaining().chars().allMatch(Character::isDigit)
                && !builder.getRemaining().isEmpty()) {
            return builder.buildFuture();
        }
        String query = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : SackItems.suggestions()) {
            String entry = option.replace(' ', '_');
            if (entry.toLowerCase(Locale.ROOT).contains(query)) builder.suggest(entry);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggest(List<String> options, SuggestionsBuilder builder) {
        for (String option : HypixelSuggestions.filter(options, builder.getRemaining())) {
            builder.suggest(option);
        }
        return builder.buildFuture();
    }
}
