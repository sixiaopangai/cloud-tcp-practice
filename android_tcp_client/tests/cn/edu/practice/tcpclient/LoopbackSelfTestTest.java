package cn.edu.practice.tcpclient;

public final class LoopbackSelfTestTest {
    public static void main(String[] args) throws Exception {
        runsClientAndServerOnLoopback();
        preservesPayloadInReply();
    }

    private static void runsClientAndServerOnLoopback() throws Exception {
        LoopbackSelfTest.Result result = LoopbackSelfTest.run(0, "SELFTEST:HELLO", 3000);

        assertEquals("127.0.0.1", result.getHost(), "host");
        assertTrue(result.getPort() > 0, "port should be assigned");
        assertEquals("SELFTEST:HELLO", result.getSentMessage(), "sent message");
        assertEquals("SELFTEST:HELLO", result.getServerReceivedMessage(), "server received");
        assertEquals("SELFTEST:OK:SELFTEST:HELLO", result.getClientReceivedReply(), "client reply");
    }

    private static void preservesPayloadInReply() throws Exception {
        LoopbackSelfTest.Result result = LoopbackSelfTest.run(0, "openled", 3000);

        assertEquals("openled", result.getServerReceivedMessage(), "server received custom payload");
        assertEquals("SELFTEST:OK:openled", result.getClientReceivedReply(), "client custom reply");
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
}
