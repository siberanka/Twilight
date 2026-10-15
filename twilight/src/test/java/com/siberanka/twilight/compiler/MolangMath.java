package com.siberanka.twilight.compiler;

import java.util.*;

/** Evaluates the numeric Molang subset emitted by the display compiler (angles in degrees): assignments,
 * {@code condition?{statements};} blocks, arithmetic, comparisons and logic. Unknown variables/functions fail
 * instead of silently becoming zero. Not a client emulator. */
final class MolangMath {
    final Map<String, Double> variables = new HashMap<>();

    void run(String script, Map<String, Double> properties) {
        for (var entry : properties.entrySet()) {
            script = script.replace("q.property('" + entry.getKey() + "')", "(" + entry.getValue() + ")");
        }
        execute(script.replace(" ", ""));
    }

    private void execute(String script) {
        for (String statement : statements(script)) {
            if (statement.isBlank()) continue;
            int block = statement.indexOf("?{");
            if (block >= 0 && statement.endsWith("}")) {
                if (eval(statement.substring(0, block)) != 0) execute(statement.substring(block + 2, statement.length() - 1));
                continue;
            }
            int equals = assignment(statement);
            variables.put(statement.substring(0, equals).trim(), eval(statement.substring(equals + 1)));
        }
    }

    /** Top-level statements: split at semicolons outside braces. */
    private static List<String> statements(String script) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == ';' && depth == 0) {
                result.add(script.substring(start, i));
                start = i + 1;
            }
        }
        if (start < script.length()) result.add(script.substring(start));
        return result;
    }

    /** The '=' of an assignment (not part of ==, !=, <= or >=). */
    private static int assignment(String statement) {
        for (int i = 0; i < statement.length(); i++) {
            if (statement.charAt(i) != '=') continue;
            char before = i > 0 ? statement.charAt(i - 1) : ' ';
            char after = i + 1 < statement.length() ? statement.charAt(i + 1) : ' ';
            if (after == '=' || before == '=' || before == '!' || before == '<' || before == '>') {
                if (after == '=') i++;
                continue;
            }
            return i;
        }
        throw new IllegalArgumentException("Not an assignment: " + statement);
    }

    double eval(String source) {
        Parser parser = new Parser(source);
        double value = parser.conditional();
        if (parser.position != parser.source.length()) throw new IllegalArgumentException(source);
        return value;
    }

    private final class Parser {
        final String source;
        int position;
        Parser(String source) { this.source = source.replace(" ", ""); }
        boolean take(String text) {
            if (!source.startsWith(text, position)) return false;
            position += text.length();
            return true;
        }
        void expect(String text) { if (!take(text)) throw new IllegalArgumentException(source.substring(position)); }
        double conditional() {
            double value = or();
            if (take("?")) {
                double yes = conditional();
                expect(":");
                double no = conditional();
                return value != 0 ? yes : no;
            }
            return value;
        }
        double or() {
            double value = and();
            while (take("||")) { double right = and(); value = value != 0 || right != 0 ? 1 : 0; }
            return value;
        }
        double and() {
            double value = compare();
            while (take("&&")) { double right = compare(); value = value != 0 && right != 0 ? 1 : 0; }
            return value;
        }
        double compare() {
            double value = add();
            if (take("==")) return value == add() ? 1 : 0;
            if (take("!=")) return value != add() ? 1 : 0;
            if (take("<=")) return value <= add() ? 1 : 0;
            if (take(">=")) return value >= add() ? 1 : 0;
            if (take(">")) return value > add() ? 1 : 0;
            if (take("<")) return value < add() ? 1 : 0;
            return value;
        }
        double add() {
            double value = multiply();
            while (true) {
                if (take("+")) value += multiply();
                else if (take("-")) value -= multiply();
                else return value;
            }
        }
        double multiply() {
            double value = atom();
            while (true) {
                if (take("*")) value *= atom();
                else if (take("/")) value /= atom();
                else return value;
            }
        }
        double atom() {
            if (take("!")) return atom() == 0 ? 1 : 0;
            if (take("-")) return -atom();
            if (take("+")) return atom();
            if (take("(")) { double value = conditional(); expect(")"); return value; }
            int start = position;
            if (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) {
                while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
                if (take("E") || take("e")) {
                    if (!take("-")) take("+");
                    while (position < source.length() && Character.isDigit(source.charAt(position))) position++;
                }
                return Double.parseDouble(source.substring(start, position));
            }
            while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position)) || "._".indexOf(source.charAt(position)) >= 0)) position++;
            String name = source.substring(start, position);
            if (!take("(")) return Objects.requireNonNull(variables.get(name), "Unknown variable " + name);
            List<Double> args = new ArrayList<>();
            do { args.add(conditional()); } while (take(","));
            expect(")");
            double a = args.getFirst();
            return switch (name) {
                case "math.abs" -> Math.abs(a);
                case "math.sqrt" -> Math.sqrt(a);
                case "math.sin" -> Math.sin(Math.toRadians(a));
                case "math.asin" -> Math.toDegrees(Math.asin(a));
                case "math.acos" -> Math.toDegrees(Math.acos(a));
                case "math.atan2" -> Math.toDegrees(Math.atan2(a, args.get(1)));
                case "math.max" -> Math.max(a, args.get(1));
                case "math.clamp" -> Math.clamp(a, args.get(1), args.get(2));
                case "math.mod" -> a % args.get(1);
                case "math.floor" -> Math.floor(a);
                case "math.lerp" -> a + (args.get(1) - a) * args.get(2);
                case "math.pow" -> Math.pow(a, args.get(1));
                case "math.cos" -> Math.cos(Math.toRadians(a));
                case "math.random" -> a + (args.get(1) - a) * 0.5;
                case "q.camera_rotation" -> variables.getOrDefault("q.camera_rotation" + (int) a, 0.0);
                default -> throw new IllegalArgumentException("Unknown function " + name);
            };
        }
    }
}
