package cn.edu.practice.tcpclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

public final class LoopbackSelfTest {
    public static final String HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 9999;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private LoopbackSelfTest() {
    }

    public static Result run(int requestedPort, String message, int timeoutMs) throws Exception {
        if (requestedPort < 0 || requestedPort > 65535) {
            throw new IllegalArgumentException("端口范围应为 0-65535");
        }
        if (message == null || message.length() == 0) {
            throw new IllegalArgumentException("测试消息不能为空");
        }

        final long startTime = System.currentTimeMillis();
        final ServerSocket serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(InetAddress.getByName(HOST), requestedPort));
        serverSocket.setSoTimeout(timeoutMs);

        final int boundPort = serverSocket.getLocalPort();
        final AtomicReference<String> serverReceived = new AtomicReference<String>("");
        final AtomicReference<Exception> serverError = new AtomicReference<Exception>();

        Thread serverThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    socket.setSoTimeout(timeoutMs);
                    String received = readOnce(socket.getInputStream());
                    serverReceived.set(received);
                    writeAll(socket.getOutputStream(), "SELFTEST:OK:" + received);
                } catch (Exception e) {
                    serverError.set(e);
                } finally {
                    closeQuietly(socket);
                    closeQuietly(serverSocket);
                }
            }
        }, "loopback-self-test-server");
        serverThread.start();

        Socket clientSocket = new Socket();
        String clientReply;
        try {
            clientSocket.connect(new InetSocketAddress(HOST, boundPort), timeoutMs);
            clientSocket.setSoTimeout(timeoutMs);
            writeAll(clientSocket.getOutputStream(), message);
            clientSocket.shutdownOutput();
            clientReply = readOnce(clientSocket.getInputStream());
        } finally {
            closeQuietly(clientSocket);
        }

        serverThread.join(timeoutMs + 1000L);
        if (serverThread.isAlive()) {
            closeQuietly(serverSocket);
            throw new IOException("本机自测服务端超时");
        }
        if (serverError.get() != null) {
            throw serverError.get();
        }

        return new Result(
                HOST,
                boundPort,
                message,
                serverReceived.get(),
                clientReply,
                System.currentTimeMillis() - startTime);
    }

    private static String readOnce(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[2048];
        int len = inputStream.read(buffer);
        if (len == -1) {
            return "";
        }
        return new String(buffer, 0, len, UTF8);
    }

    private static void writeAll(OutputStream outputStream, String message) throws IOException {
        byte[] data = message.getBytes(UTF8);
        outputStream.write(data);
        outputStream.flush();
    }

    private static void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    public static final class Result {
        private final String host;
        private final int port;
        private final String sentMessage;
        private final String serverReceivedMessage;
        private final String clientReceivedReply;
        private final long durationMs;

        private Result(
                String host,
                int port,
                String sentMessage,
                String serverReceivedMessage,
                String clientReceivedReply,
                long durationMs) {
            this.host = host;
            this.port = port;
            this.sentMessage = sentMessage;
            this.serverReceivedMessage = serverReceivedMessage;
            this.clientReceivedReply = clientReceivedReply;
            this.durationMs = durationMs;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getSentMessage() {
            return sentMessage;
        }

        public String getServerReceivedMessage() {
            return serverReceivedMessage;
        }

        public String getClientReceivedReply() {
            return clientReceivedReply;
        }

        public long getDurationMs() {
            return durationMs;
        }
    }
}
