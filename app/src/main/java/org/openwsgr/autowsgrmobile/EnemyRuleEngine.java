package org.openwsgr.autowsgrmobile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EnemyRuleEngine {
    private final List<Rule> rules;
    private final boolean formationRules;

    private EnemyRuleEngine(List<Rule> rules, boolean formationRules) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        this.formationRules = formationRules;
    }

    static EnemyRuleEngine emptyEnemyRules() {
        return new EnemyRuleEngine(Collections.<Rule>emptyList(), false);
    }

    static EnemyRuleEngine emptyFormationRules() {
        return new EnemyRuleEngine(Collections.<Rule>emptyList(), true);
    }

    static EnemyRuleEngine fromEnemyRuleItems(List<String> items) throws IOException {
        ArrayList<Rule> parsed = new ArrayList<>();
        for (String item : items) {
            parsed.add(parseEnemyRule(item));
        }
        return new EnemyRuleEngine(parsed, false);
    }

    static EnemyRuleEngine fromFormationRuleItems(List<String> items) throws IOException {
        ArrayList<Rule> parsed = new ArrayList<>();
        for (String item : items) {
            parsed.add(parseFormationRule(item));
        }
        return new EnemyRuleEngine(parsed, true);
    }

    int size() {
        return rules.size();
    }

    RuleAction evaluate(EnemySnapshot snapshot) {
        if (formationRules || snapshot == null) {
            return RuleAction.noAction();
        }
        for (Rule rule : rules) {
            if (rule.matches(snapshot, "")) {
                return rule.action;
            }
        }
        return RuleAction.noAction();
    }

    RuleAction evaluateFormation(String enemyFormation) {
        if (!formationRules || enemyFormation == null) {
            return RuleAction.noAction();
        }
        for (Rule rule : rules) {
            if (rule.matches(null, enemyFormation)) {
                return rule.action;
            }
        }
        return RuleAction.noAction();
    }

    String summary() {
        return formationRules ? "formationRules=" + rules.size() : "enemyRules=" + rules.size();
    }

    private static Rule parseEnemyRule(String raw) throws IOException {
        List<String> parts = parseRuleParts(raw);
        Expression expression = new ExpressionParser(parts.get(0)).parse();
        return Rule.enemy(expression, parseAction(parts.get(1)), raw);
    }

    private static Rule parseFormationRule(String raw) throws IOException {
        List<String> parts = parseRuleParts(raw);
        return Rule.formation(cleanScalar(parts.get(0)), parseAction(parts.get(1)), raw);
    }

    private static List<String> parseRuleParts(String raw) throws IOException {
        String item = raw.trim();
        if (item.startsWith("- ")) {
            item = item.substring(2).trim();
        }
        if (item.startsWith("[") && item.endsWith("]")) {
            item = item.substring(1, item.length() - 1);
            int comma = findTopLevelComma(item);
            if (comma < 0) {
                throw new IOException("rule item missing action: " + raw);
            }
            ArrayList<String> parts = new ArrayList<>(2);
            parts.add(cleanScalar(item.substring(0, comma).trim()));
            parts.add(cleanScalar(item.substring(comma + 1).trim()));
            return parts;
        }
        int arrow = item.indexOf("=>");
        if (arrow >= 0) {
            ArrayList<String> parts = new ArrayList<>(2);
            parts.add(cleanScalar(item.substring(0, arrow).trim()));
            parts.add(cleanScalar(item.substring(arrow + 2).trim()));
            return parts;
        }
        ArrayList<String> parts = new ArrayList<>(2);
        parts.add(cleanScalar(item));
        parts.add("retreat");
        return parts;
    }

    private static int findTopLevelComma(String text) {
        boolean quoted = false;
        char quote = 0;
        int parenDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '"' || c == '\'') && (i == 0 || text.charAt(i - 1) != '\\')) {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
                continue;
            }
            if (quoted) {
                continue;
            }
            if (c == '(') {
                parenDepth++;
            } else if (c == ')' && parenDepth > 0) {
                parenDepth--;
            } else if (c == '[') {
                bracketDepth++;
            } else if (c == ']' && bracketDepth > 0) {
                bracketDepth--;
            } else if (c == ',' && parenDepth == 0 && bracketDepth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static RuleAction parseAction(String value) throws IOException {
        String action = cleanScalar(value).toLowerCase(Locale.US);
        if ("retreat".equals(action)) {
            return RuleAction.retreat();
        }
        if ("detour".equals(action)) {
            return RuleAction.detour();
        }
        if ("fight".equals(action)) {
            return RuleAction.noAction();
        }
        try {
            int formation = Integer.parseInt(action);
            if (formation >= 1 && formation <= 5) {
                return RuleAction.formation(formation);
            }
        } catch (NumberFormatException ignored) {
            // Report a cleaner message below.
        }
        throw new IOException("unknown enemy rule action: " + value);
    }

    private static String cleanScalar(String raw) {
        String value = raw.trim();
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1);
        }
        return value.trim();
    }

    static final class EnemySnapshot {
        private final String label;
        private final LinkedHashMap<String, Integer> counts;

        private EnemySnapshot(String label, LinkedHashMap<String, Integer> counts) {
            this.label = label;
            this.counts = counts;
        }

        static EnemySnapshot of(String label, Object... pairs) {
            LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                Object key = pairs[i];
                Object value = pairs[i + 1];
                if (key instanceof String && value instanceof Number) {
                    counts.put((String) key, ((Number) value).intValue());
                }
            }
            return new EnemySnapshot(label, counts);
        }

        static EnemySnapshot fromCounts(String label, LinkedHashMap<String, Integer> counts) {
            return new EnemySnapshot(label, new LinkedHashMap<>(counts));
        }

        int count(String key) {
            Integer value = counts.get(key);
            return value == null ? 0 : value;
        }

        boolean hasAnyShip() {
            return count("ALL") > 0;
        }

        String summary() {
            return label + counts;
        }
    }

    static final class RuleAction {
        enum Kind {
            NO_ACTION,
            RETREAT,
            DETOUR,
            FORMATION
        }

        final Kind kind;
        final int formation;

        private RuleAction(Kind kind, int formation) {
            this.kind = kind;
            this.formation = formation;
        }

        static RuleAction noAction() {
            return new RuleAction(Kind.NO_ACTION, 0);
        }

        static RuleAction retreat() {
            return new RuleAction(Kind.RETREAT, 0);
        }

        static RuleAction detour() {
            return new RuleAction(Kind.DETOUR, 0);
        }

        static RuleAction formation(int formation) {
            return new RuleAction(Kind.FORMATION, Math.max(1, Math.min(formation, 5)));
        }

        boolean isRetreat() {
            return kind == Kind.RETREAT;
        }

        boolean isDetour() {
            return kind == Kind.DETOUR;
        }

        boolean isFormation(int value) {
            return kind == Kind.FORMATION && formation == value;
        }

        String actionId() {
            if (kind == Kind.RETREAT) {
                return "spot_retreat";
            }
            if (kind == Kind.DETOUR) {
                return "spot_detour";
            }
            if (kind == Kind.FORMATION) {
                return "formation_" + formation;
            }
            return "spot_fight";
        }

        String shortLabel() {
            if (kind == Kind.FORMATION) {
                return "formation_" + formation;
            }
            return kind.name().toLowerCase(Locale.US);
        }
    }

    private static final class Rule {
        final Expression expression;
        final String formationName;
        final RuleAction action;
        final String source;

        private Rule(Expression expression, String formationName, RuleAction action, String source) {
            this.expression = expression;
            this.formationName = formationName;
            this.action = action;
            this.source = source;
        }

        static Rule enemy(Expression expression, RuleAction action, String source) {
            return new Rule(expression, "", action, source);
        }

        static Rule formation(String formationName, RuleAction action, String source) {
            return new Rule(null, formationName, action, source);
        }

        boolean matches(EnemySnapshot snapshot, String enemyFormation) {
            if (expression != null) {
                return snapshot != null && expression.evaluate(snapshot);
            }
            return formationName.length() > 0 && formationName.equals(enemyFormation);
        }
    }

    private interface Expression {
        boolean evaluate(EnemySnapshot snapshot);
    }

    private interface NumericExpression {
        double value(EnemySnapshot snapshot);
    }

    private static final class ExpressionParser {
        private final String text;
        private int index;

        ExpressionParser(String text) {
            this.text = text;
        }

        Expression parse() throws IOException {
            Expression expression = parseOr();
            skipSpaces();
            if (index != text.length()) {
                throw error("unexpected token");
            }
            return expression;
        }

        private Expression parseOr() throws IOException {
            Expression left = parseAnd();
            while (matchWord("or")) {
                final Expression a = left;
                final Expression b = parseAnd();
                left = new Expression() {
                    @Override
                    public boolean evaluate(EnemySnapshot snapshot) {
                        return a.evaluate(snapshot) || b.evaluate(snapshot);
                    }
                };
            }
            return left;
        }

        private Expression parseAnd() throws IOException {
            Expression left = parsePrimary();
            while (matchWord("and")) {
                final Expression a = left;
                final Expression b = parsePrimary();
                left = new Expression() {
                    @Override
                    public boolean evaluate(EnemySnapshot snapshot) {
                        return a.evaluate(snapshot) && b.evaluate(snapshot);
                    }
                };
            }
            return left;
        }

        private Expression parsePrimary() throws IOException {
            skipSpaces();
            if (peek('(')) {
                index++;
                Expression inner = parseOr();
                skipSpaces();
                if (!peek(')')) {
                    throw error("missing ')'");
                }
                index++;
                return inner;
            }
            return parseComparison();
        }

        private Expression parseComparison() throws IOException {
            final NumericExpression left = parseSum();
            skipSpaces();
            final String op = parseOperator();
            final NumericExpression right = parseSum();
            return new Expression() {
                @Override
                public boolean evaluate(EnemySnapshot snapshot) {
                    double a = left.value(snapshot);
                    double b = right.value(snapshot);
                    if (">=".equals(op)) {
                        return a >= b;
                    }
                    if ("<=".equals(op)) {
                        return a <= b;
                    }
                    if (">".equals(op)) {
                        return a > b;
                    }
                    if ("<".equals(op)) {
                        return a < b;
                    }
                    if ("==".equals(op)) {
                        return Math.abs(a - b) < 0.00001;
                    }
                    if ("!=".equals(op)) {
                        return Math.abs(a - b) >= 0.00001;
                    }
                    return false;
                }
            };
        }

        private NumericExpression parseSum() throws IOException {
            NumericExpression left = parseValue();
            while (true) {
                skipSpaces();
                if (!peek('+')) {
                    return left;
                }
                index++;
                final NumericExpression a = left;
                final NumericExpression b = parseValue();
                left = new NumericExpression() {
                    @Override
                    public double value(EnemySnapshot snapshot) {
                        return a.value(snapshot) + b.value(snapshot);
                    }
                };
            }
        }

        private NumericExpression parseValue() throws IOException {
            skipSpaces();
            if (index >= text.length()) {
                throw error("expected value");
            }
            char c = text.charAt(index);
            if (Character.isDigit(c)) {
                final double value = parseNumber();
                return new NumericExpression() {
                    @Override
                    public double value(EnemySnapshot snapshot) {
                        return value;
                    }
                };
            }
            final String identifier = parseIdentifier();
            return new NumericExpression() {
                @Override
                public double value(EnemySnapshot snapshot) {
                    return snapshot.count(identifier);
                }
            };
        }

        private String parseOperator() throws IOException {
            skipSpaces();
            String[] operators = new String[]{">=", "<=", "==", "!=", ">", "<"};
            for (String op : operators) {
                if (text.startsWith(op, index)) {
                    index += op.length();
                    return op;
                }
            }
            throw error("expected comparison operator");
        }

        private double parseNumber() {
            int start = index;
            while (index < text.length()) {
                char c = text.charAt(index);
                if (!Character.isDigit(c) && c != '.') {
                    break;
                }
                index++;
            }
            return Double.parseDouble(text.substring(start, index));
        }

        private String parseIdentifier() throws IOException {
            int start = index;
            while (index < text.length()) {
                char c = text.charAt(index);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    break;
                }
                index++;
            }
            if (start == index) {
                throw error("expected identifier");
            }
            return text.substring(start, index).toUpperCase(Locale.US);
        }

        private boolean matchWord(String word) {
            skipSpaces();
            int end = index + word.length();
            if (end > text.length() || !text.regionMatches(true, index, word, 0, word.length())) {
                return false;
            }
            boolean beforeOk = index == 0 || !isWordChar(text.charAt(index - 1));
            boolean afterOk = end == text.length() || !isWordChar(text.charAt(end));
            if (!beforeOk || !afterOk) {
                return false;
            }
            index = end;
            return true;
        }

        private boolean isWordChar(char c) {
            return Character.isLetterOrDigit(c) || c == '_';
        }

        private boolean peek(char c) {
            return index < text.length() && text.charAt(index) == c;
        }

        private void skipSpaces() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private IOException error(String message) {
            return new IOException(message + " in enemy rule '" + text + "' at " + index);
        }
    }
}
