package cn.edu.practice.tcpclient;

public final class StrobeSettingsTest {
    public static void main(String[] args) {
        keepsDefaultFrequencyAt500ms();
        parsesUserFrequency();
        rejectsUnsafeIntervals();
        parsesStartCommand();
        parsesStopCommand();
        ignoresUnrelatedMessages();
    }

    private static void keepsDefaultFrequencyAt500ms() {
        StrobeSettings settings = StrobeSettings.defaults();
        assertEquals(500, settings.getOnMs(), "default on interval");
        assertEquals(500, settings.getOffMs(), "default off interval");
    }

    private static void parsesUserFrequency() {
        StrobeSettings settings = StrobeSettings.fromText("200", "800");
        assertEquals(200, settings.getOnMs(), "parsed on interval");
        assertEquals(800, settings.getOffMs(), "parsed off interval");
    }

    private static void rejectsUnsafeIntervals() {
        expectIllegalArgument(new Runnable() {
            @Override
            public void run() {
                StrobeSettings.fromText("50", "500");
            }
        }, "too short interval");

        expectIllegalArgument(new Runnable() {
            @Override
            public void run() {
                StrobeSettings.fromText("500", "20000");
            }
        }, "too long interval");
    }

    private static void parsesStartCommand() {
        StrobeSettings.Command command = StrobeSettings.parseCommand("STROBE:ON:200:800");
        assertTrue(command != null, "start command should parse");
        assertEquals(StrobeSettings.CommandType.START, command.getType(), "start command type");
        assertEquals(200, command.getSettings().getOnMs(), "start on interval");
        assertEquals(800, command.getSettings().getOffMs(), "start off interval");
    }

    private static void parsesStopCommand() {
        StrobeSettings.Command command = StrobeSettings.parseCommand("received STROBE : OFF");
        assertTrue(command != null, "stop command should parse");
        assertEquals(StrobeSettings.CommandType.STOP, command.getType(), "stop command type");
    }

    private static void ignoresUnrelatedMessages() {
        StrobeSettings.Command command = StrobeSettings.parseCommand("LED:ON");
        assertTrue(command == null, "unrelated message should not parse");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + ": expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static void expectIllegalArgument(Runnable runnable, String label) {
        try {
            runnable.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }
}
