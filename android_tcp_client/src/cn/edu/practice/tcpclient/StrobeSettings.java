package cn.edu.practice.tcpclient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StrobeSettings {
    public static final int DEFAULT_ON_MS = 500;
    public static final int DEFAULT_OFF_MS = 500;
    public static final int MIN_INTERVAL_MS = 100;
    public static final int MAX_INTERVAL_MS = 10000;

    private static final Pattern START_COMMAND = Pattern.compile(
            "STROBE\\s*:\\s*ON\\s*:\\s*(\\d+)\\s*:\\s*(\\d+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_COMMAND = Pattern.compile(
            "STROBE\\s*:\\s*OFF",
            Pattern.CASE_INSENSITIVE);

    private final int onMs;
    private final int offMs;

    private StrobeSettings(int onMs, int offMs) {
        validate(onMs, "亮灯时间");
        validate(offMs, "灭灯时间");
        this.onMs = onMs;
        this.offMs = offMs;
    }

    public static StrobeSettings defaults() {
        return new StrobeSettings(DEFAULT_ON_MS, DEFAULT_OFF_MS);
    }

    public static StrobeSettings fromText(String onText, String offText) {
        return new StrobeSettings(parseInt(onText, "亮灯时间"), parseInt(offText, "灭灯时间"));
    }

    public static Command parseCommand(String message) {
        if (message == null) {
            return null;
        }

        Matcher stopMatcher = STOP_COMMAND.matcher(message);
        if (stopMatcher.find()) {
            return new Command(CommandType.STOP, null);
        }

        Matcher matcher = START_COMMAND.matcher(message);
        if (matcher.find()) {
            return new Command(
                    CommandType.START,
                    new StrobeSettings(
                            parseInt(matcher.group(1), "亮灯时间"),
                            parseInt(matcher.group(2), "灭灯时间")));
        }

        return null;
    }

    public int getOnMs() {
        return onMs;
    }

    public int getOffMs() {
        return offMs;
    }

    public String toCommand() {
        return "STROBE:ON:" + onMs + ":" + offMs;
    }

    private static int parseInt(String text, String label) {
        if (text == null || text.trim().length() == 0) {
            throw new IllegalArgumentException(label + "不能为空");
        }

        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
    }

    private static void validate(int value, String label) {
        if (value < MIN_INTERVAL_MS || value > MAX_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    label + "范围应为 " + MIN_INTERVAL_MS + "-" + MAX_INTERVAL_MS + " 毫秒");
        }
    }

    public enum CommandType {
        START,
        STOP
    }

    public static final class Command {
        private final CommandType type;
        private final StrobeSettings settings;

        private Command(CommandType type, StrobeSettings settings) {
            this.type = type;
            this.settings = settings;
        }

        public CommandType getType() {
            return type;
        }

        public StrobeSettings getSettings() {
            return settings;
        }
    }
}
