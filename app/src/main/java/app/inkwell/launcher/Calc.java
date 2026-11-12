package app.inkwell.launcher;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.regex.Pattern;

/** Tiny calculator for the search box: "12*(3+4)" shows 84. Digits, + - * / ^ % and brackets only. */
final class Calc {
    private static final Pattern ALLOWED = Pattern.compile("[0-9+\\-*/^%().\\s]+");
    private static final Pattern HAS_OP = Pattern.compile(".*[0-9)]\\s*[-+*/^%]\\s*[0-9(-].*");

    private final String s;
    private int i;

    private Calc(String s) { this.s = s; }

    /** Returns the result as text, or null if the input isn't a calculation. */
    static String eval(String input) {
        String t = input.replaceAll("(?<=[0-9)])\\s*[x×]\\s*(?=[0-9(])", "*").trim();
        if (!ALLOWED.matcher(t).matches() || !HAS_OP.matcher(t).matches()) return null;
        try {
            Calc c = new Calc(t);
            double v = c.expr();
            c.skip();
            if (c.i != t.length() || Double.isNaN(v) || Double.isInfinite(v)) return null;
            return new BigDecimal(v).round(new MathContext(12)).stripTrailingZeros().toPlainString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private boolean eat(char c) {
        skip();
        if (i < s.length() && s.charAt(i) == c) { i++; return true; }
        return false;
    }

    private double expr() {
        double v = term();
        while (true) {
            if (eat('+')) v += term();
            else if (eat('-')) v -= term();
            else return v;
        }
    }

    private double term() {
        double v = power();
        while (true) {
            if (eat('*')) v *= power();
            else if (eat('/')) v /= power();
            else if (eat('%')) v %= power();
            else return v;
        }
    }

    private double power() {
        double base = unary();
        return eat('^') ? Math.pow(base, power()) : base;
    }

    private double unary() {
        if (eat('-')) return -unary();
        if (eat('+')) return unary();
        return primary();
    }

    private double primary() {
        if (eat('(')) {
            double v = expr();
            if (!eat(')')) throw new IllegalArgumentException();
            return v;
        }
        skip();
        int start = i;
        while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
        if (start == i) throw new IllegalArgumentException();
        return Double.parseDouble(s.substring(start, i));
    }
}
