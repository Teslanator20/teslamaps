package com.teslamaps.utils;

/**
 * Small expression evaluator for the container search bar. Recursive descent, so precedence and
 * brackets come out of the grammar instead of a table: + - are the loosest, then * / %, then ^,
 * then unary minus. k/m/b/t suffixes for coins, s=64 and e=160 for stacks, x as times, purse as variable.
 */
public class Calculator {

    private final String text;
    private int at;

    private Calculator(String text) {
        this.text = text;
    }

    /** @return the value, or null if the input is not a complete expression. */
    public static Double eval(String expression) {
        if (expression == null || expression.isBlank()) return null;
        try {
            Calculator c = new Calculator(expression);
            double value = c.expression();
            c.skipSpace();
            if (c.at < c.text.length()) return null; // trailing junk, do not guess
            return Double.isFinite(value) ? value : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Trims the pointless ".0" and keeps long decimals readable. */
    public static String format(double value) {
        // fixed locale, otherwise a German JVM groups with "." and a reader cannot tell
        // 1.500 from one and a half
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format(java.util.Locale.ROOT, "%,d", (long) value);
        }
        String s = String.format(java.util.Locale.ROOT, "%.6f", value);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    private void skipSpace() {
        while (at < text.length() && text.charAt(at) == ' ') at++;
    }

    private boolean take(char c) {
        skipSpace();
        if (at < text.length() && text.charAt(at) == c) {
            at++;
            return true;
        }
        return false;
    }

    private double expression() {
        double value = term();
        while (true) {
            if (take('+')) value += term();
            else if (take('-')) value -= term();
            else return value;
        }
    }

    private double term() {
        double value = unary();
        while (true) {
            if (take('*') || take('x') || take('X')) value *= unary();
            else if (implicit()) value *= unary();
            else if (take('/')) {
                double d = unary();
                if (d == 0) throw new ArithmeticException("divide by zero");
                value /= d;
            } else if (take('%')) {
                double d = unary();
                if (d == 0) throw new ArithmeticException("divide by zero");
                value %= d;
            } else return value;
        }
    }

    // 2(3) and 2purse
    private boolean implicit() {
        skipSpace();
        if (at >= text.length()) return false;
        char c = text.charAt(at);
        return c == '(' || c == 'p' || c == 'P';
    }

    private double unary() {
        if (take('-')) return -unary();
        if (take('+')) return unary();
        return power();
    }

    private double power() {
        double base = atom();
        if (take('^')) return Math.pow(base, unary()); // right associative: 2^3^2 = 512
        return base;
    }

    private double atom() {
        skipSpace();
        if (at >= text.length()) throw new IllegalStateException("unexpected end");
        if (take('(')) {
            double value = expression();
            if (!take(')')) throw new IllegalStateException("missing )");
            return value;
        }
        if (text.regionMatches(true, at, "purse", 0, 5)) {
            at += 5;
            return purse();
        }
        int start = at;
        while (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) at++;
        if (at == start) throw new IllegalStateException("expected a number");
        double value = Double.parseDouble(text.substring(start, at));
        if (at < text.length()) {
            char suffix = Character.toLowerCase(text.charAt(at));
            if (suffix == 'k') { value *= 1_000d; at++; }
            else if (suffix == 'm') { value *= 1_000_000d; at++; }
            else if (suffix == 'b') { value *= 1_000_000_000d; at++; }
            else if (suffix == 't') { value *= 1_000_000_000_000d; at++; }
            else if (suffix == 's') { value *= 64d; at++; }
            else if (suffix == 'e') { value *= 160d; at++; }
        }
        return value;
    }

    private static double purse() {
        for (String raw : ScoreboardUtils.getScoreboardLines()) {
            String line = ScoreboardUtils.cleanLine(raw);
            if (!line.startsWith("Purse:") && !line.startsWith("Piggy:")) continue;
            String num = line.substring(6).trim();
            int end = 0;
            while (end < num.length() && (Character.isDigit(num.charAt(end)) || num.charAt(end) == ',' || num.charAt(end) == '.')) end++;
            if (end == 0) break;
            return Double.parseDouble(num.substring(0, end).replace(",", ""));
        }
        throw new IllegalStateException("no purse");
    }
}
