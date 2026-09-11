package com.teslamaps.profileviewer.data;

import java.util.Map;

/**
 * Tiny arithmetic evaluator for the perk reward and cost formulas, e.g. floor((level + 1)^3.1).
 * Supports + - * / ^, parentheses, floor/ceil/round/abs/sqrt and named variables.
 */
public class Eval {

    public static double eval(String expression, Map<String, Double> vars) {
        try {
            return new Parser(expression, vars).expr();
        } catch (Exception e) {
            return 0;
        }
    }

    private static class Parser {
        private final String s;
        private final Map<String, Double> vars;
        private int i = 0;

        Parser(String s, Map<String, Double> vars) {
            this.s = s;
            this.vars = vars;
        }

        private char peek() {
            while (i < s.length() && s.charAt(i) == ' ') i++;
            return i < s.length() ? s.charAt(i) : ' ';
        }

        double expr() {
            double value = term();
            while (true) {
                char c = peek();
                if (c == '+') {
                    i++;
                    value += term();
                } else if (c == '-') {
                    i++;
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = power();
            while (true) {
                char c = peek();
                if (c == '*') {
                    i++;
                    value *= power();
                } else if (c == '/') {
                    i++;
                    value /= power();
                } else {
                    return value;
                }
            }
        }

        private double power() {
            double base = unary();
            if (peek() == '^') {
                i++;
                return Math.pow(base, power());
            }
            return base;
        }

        private double unary() {
            if (peek() == '-') {
                i++;
                return -unary();
            }
            return atom();
        }

        private double atom() {
            char c = peek();
            if (c == '(') {
                i++;
                double value = expr();
                if (peek() == ')') i++;
                return value;
            }
            if (Character.isLetter(c) || c == '_') {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_')) i++;
                String name = s.substring(start, i);
                if (peek() == '(') {
                    i++;
                    double argument = expr();
                    if (peek() == ')') i++;
                    return function(name, argument);
                }
                return vars.getOrDefault(name, 0.0);
            }
            int start = i;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
            try {
                return Double.parseDouble(s.substring(start, i));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        private double function(String name, double argument) {
            return switch (name) {
                case "floor" -> Math.floor(argument);
                case "ceil" -> Math.ceil(argument);
                case "round" -> Math.round(argument);
                case "abs" -> Math.abs(argument);
                case "sqrt" -> Math.sqrt(argument);
                default -> argument;
            };
        }
    }
}
