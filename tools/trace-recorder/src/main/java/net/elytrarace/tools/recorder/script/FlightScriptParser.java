package net.elytrarace.tools.recorder.script;

import net.elytrarace.tools.recorder.format.exception.InvalidTraceException;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the flight script text format into a {@link FlightScript}.
 *
 * <p>One directive per line; blank lines and {@code #} comments are ignored. {@code hold <ticks>
 * yaw=<f> pitch=<f>} holds a rotation for the given number of ticks. {@code ramp <ticks> yaw=<f>
 * pitch=<from>..<to>} interpolates linearly across the span, inclusive of the start value and
 * reaching the end value on the final tick; the same {@code a..b} form is accepted for yaw. {@code
 * boost} occupies one tick, requests a firework ignition, and inherits the previous directive's
 * rotation.
 *
 * <p>The ramp interpolation is computed in {@code float}, matching how rotation is held everywhere
 * else in this project — {@code yRot}/{@code xRot} are {@code float} in Vanilla, and a ramp computed
 * in {@code double} and narrowed at the end would produce different values.
 */
@ApiStatus.Internal
public abstract class FlightScriptParser {

    private FlightScriptParser() {
    }

    public static FlightScript parse(String script) {
        List<ScriptedInput> inputs = new ArrayList<>();
        for (String rawLine : script.lines().toList()) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            parseLine(line, inputs);
        }
        return new FlightScript(inputs);
    }

    private static void parseLine(String line, List<ScriptedInput> inputs) {
        String[] tokens = line.split("\\s+");
        String directive = tokens[0];
        switch (directive) {
            case "hold" -> parseHold(tokens, inputs);
            case "ramp" -> parseRamp(tokens, inputs);
            case "boost" -> parseBoost(inputs);
            default -> throw new InvalidTraceException("unknown flight script directive: %s".formatted(directive));
        }
    }

    private static void parseHold(String[] tokens, List<ScriptedInput> inputs) {
        int ticks = parseTicks(tokens);
        Map<String, String> fields = parseFields(tokens);
        float yaw = parseFloatField(fields, "yaw");
        float pitch = parseFloatField(fields, "pitch");
        for (int i = 0; i < ticks; i++) {
            inputs.add(new ScriptedInput(yaw, pitch, false));
        }
    }

    private static void parseRamp(String[] tokens, List<ScriptedInput> inputs) {
        int ticks = parseTicks(tokens);
        Map<String, String> fields = parseFields(tokens);
        float[] yawRange = parseRangeField(fields, "yaw");
        float[] pitchRange = parseRangeField(fields, "pitch");
        for (int i = 0; i < ticks; i++) {
            float yaw = ramp(yawRange[0], yawRange[1], i, ticks);
            float pitch = ramp(pitchRange[0], pitchRange[1], i, ticks);
            inputs.add(new ScriptedInput(yaw, pitch, false));
        }
    }

    private static void parseBoost(List<ScriptedInput> inputs) {
        if (inputs.isEmpty()) {
            throw new InvalidTraceException("boost cannot appear before any rotation has been established");
        }
        ScriptedInput last = inputs.get(inputs.size() - 1);
        inputs.add(new ScriptedInput(last.yaw(), last.pitch(), true));
    }

    /**
     * Interpolates linearly in {@code float}, index {@code 0} landing exactly on {@code a} and index
     * {@code n - 1} exactly on {@code b}. A single-tick span yields the end value.
     */
    private static float ramp(float a, float b, int index, int n) {
        if (n == 1) {
            return b;
        }
        float t = (float) index / (float) (n - 1);
        return a + (b - a) * t;
    }

    private static int parseTicks(String[] tokens) {
        if (tokens.length < 2) {
            throw new InvalidTraceException("directive '%s' is missing its tick span".formatted(tokens[0]));
        }
        int ticks;
        try {
            ticks = Integer.parseInt(tokens[1]);
        } catch (NumberFormatException e) {
            throw new InvalidTraceException("tick span must be an integer, was '%s'".formatted(tokens[1]));
        }
        if (ticks < 1) {
            throw new InvalidTraceException("tick span must be >= 1, was %s".formatted(ticks));
        }
        return ticks;
    }

    private static Map<String, String> parseFields(String[] tokens) {
        Map<String, String> fields = new HashMap<>();
        for (int i = 2; i < tokens.length; i++) {
            String token = tokens[i];
            int eq = token.indexOf('=');
            if (eq < 0) {
                throw new InvalidTraceException("expected a key=value field, found '%s'".formatted(token));
            }
            fields.put(token.substring(0, eq), token.substring(eq + 1));
        }
        return fields;
    }

    private static float parseFloatField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new InvalidTraceException("missing required field '%s'".formatted(key));
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            throw new InvalidTraceException("field '%s' must be a number, was '%s'".formatted(key, value));
        }
    }

    private static float[] parseRangeField(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null) {
            throw new InvalidTraceException("missing required field '%s'".formatted(key));
        }
        int separator = value.indexOf("..");
        if (separator < 0) {
            float constant = parseFloatToken(key, value);
            return new float[] {constant, constant};
        }
        String fromToken = value.substring(0, separator);
        String toToken = value.substring(separator + 2);
        return new float[] {parseFloatToken(key, fromToken), parseFloatToken(key, toToken)};
    }

    private static float parseFloatToken(String key, String token) {
        try {
            return Float.parseFloat(token);
        } catch (NumberFormatException e) {
            throw new InvalidTraceException("field '%s' must be a number, was '%s'".formatted(key, token));
        }
    }
}
