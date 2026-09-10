package com.teslamaps.utils;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kills the matcher boilerplate in chat/lore/scoreboard parsers.
 * Feed hoisted static final Patterns — never Pattern.compile in a hot path.
 * Ported from bax's utils/RegexUtils.
 */
public final class RegexUtil {
    private RegexUtil() {}

    /** Runs action on the first match and returns whether it matched. */
    public static boolean match(Pattern pattern, String input, Consumer<Matcher> action) {
        Matcher m = pattern.matcher(input);
        if (!m.find()) return false;
        action.accept(m);
        return true;
    }

    public static <T> Optional<T> group(Pattern pattern, String input, Function<Matcher, T> extractor) {
        Matcher m = pattern.matcher(input);
        return m.find() ? Optional.ofNullable(extractor.apply(m)) : Optional.empty();
    }

    public static Optional<String> firstGroup(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        return m.find() && m.groupCount() >= 1 ? Optional.ofNullable(m.group(1)) : Optional.empty();
    }

    public static boolean matches(Pattern pattern, String input) {
        return pattern.matcher(input).find();
    }
}
