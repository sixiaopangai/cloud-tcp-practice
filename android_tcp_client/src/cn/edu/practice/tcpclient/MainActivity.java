package cn.edu.practice.tcpclient;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int COLOR_PRIMARY = Color.rgb(0, 137, 123);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(0, 105, 92);
    private static final int COLOR_BORDER = Color.rgb(210, 216, 216);
    private static final int COLOR_BG = Color.rgb(250, 250, 250);
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int REQUEST_CAMERA_PERMISSION = 1001;
    private static final String PROJECT_URL = "https://github.com/sixiaopangai/cloud-tcp-practice";
    private static final String PREF_STROBE_ON_MS = "strobe_on_ms";
    private static final String PREF_STROBE_OFF_MS = "strobe_off_ms";
    private static final String PREF_LED_ON_MODE = "led_on_mode";
    private static final int LED_ON_MODE_TORCH = 0;
    private static final int LED_ON_MODE_STROBE = 1;

    private Handler mainHandler;
    private Spinner modeSpinner;
    private TextView statusText;
    private TextView hostLabel;
    private TextView logText;
    private EditText hostInput;
    private EditText portInput;
    private EditText messageInput;
    private Button connectButton;
    private Button disconnectButton;
    private Button sendButton;

    private volatile boolean running = false;
    private volatile boolean clientMode = true;
    private Socket clientSocket;
    private ServerSocket serverSocket;
    private final List<Socket> serverClients = Collections.synchronizedList(new ArrayList<Socket>());
    private SharedPreferences preferences;
    private String lastClientHost = "";
    private String torchCameraId;
    private Runnable pendingTorchAction;
    private volatile boolean torchEnabled = false;
    private volatile boolean strobeRunning = false;
    private Runnable strobeRunnable;
    private int strobeGeneration = 0;
    private StrobeSettings strobeSettings = StrobeSettings.defaults();
    private int ledOnMode = LED_ON_MODE_TORCH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mainHandler = new Handler(Looper.getMainLooper());
        preferences = getSharedPreferences("tcp_settings", MODE_PRIVATE);
        lastClientHost = preferences.getString("host", "");
        strobeSettings = loadStrobeSettings();
        ledOnMode = preferences.getInt(PREF_LED_ON_MODE, LED_ON_MODE_TORCH);

        setContentView(createContentView());
        updateModeUi();
        appendLog("应用已启动，无广告版本。");
    }

    private View createContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(18), 0, dp(10), 0);
        titleBar.setBackgroundColor(COLOR_PRIMARY_DARK);
        root.addView(titleBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(58)));

        TextView title = new TextView(this);
        title.setText("TCP调试助手");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1));

        TextView helpButton = titleButton("?");
        TextView menuButton = titleButton("⋮");
        titleBar.addView(helpButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        titleBar.addView(menuButton, new LinearLayout.LayoutParams(dp(42), dp(48)));

        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHelpDialog();
            }
        });
        menuButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showOverflowMenu(v);
            }
        });

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(10));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));

        modeSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"TCP Client", "TCP Server"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (running) {
                    disconnect();
                }
                if (clientMode && position == 1) {
                    lastClientHost = hostInput.getText().toString().trim();
                }
                clientMode = position == 0;
                updateModeUi();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        content.addView(labeledRow("模式", modeSpinner));

        hostLabel = label("地址");
        hostInput = editText(lastClientHost);
        hostInput.setSingleLine(true);
        hostInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(rowWithLabel(hostLabel, hostInput));

        portInput = editText(preferences.getString("port", ""));
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(labeledRow("端口", portInput));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER_VERTICAL);
        buttonRow.setPadding(0, dp(8), 0, dp(4));

        connectButton = primaryButton("连接");
        disconnectButton = outlineButton("断开");
        Button clearButton = outlineButton("清空");

        buttonRow.addView(connectButton, weightedButtonParams());
        buttonRow.addView(space(dp(8), 1));
        buttonRow.addView(disconnectButton, weightedButtonParams());
        buttonRow.addView(space(dp(8), 1));
        buttonRow.addView(clearButton, weightedButtonParams());
        content.addView(buttonRow);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (clientMode) {
                    connectClient();
                } else {
                    startServer();
                }
            }
        });
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                logText.setText("");
            }
        });

        statusText = new TextView(this);
        statusText.setTextColor(Color.rgb(68, 75, 75));
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(6), 0, dp(8));
        content.addView(statusText);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackground(panelBg());
        logText = new TextView(this);
        logText.setTextColor(Color.rgb(30, 36, 36));
        logText.setTextSize(14);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setPadding(dp(10), dp(10), dp(10), dp(10));
        logText.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        scrollView.addView(logText, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        content.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setPadding(0, dp(10), 0, dp(6));
        Button openButton = outlineButton("openled");
        Button closeButton = outlineButton("closeled");
        Button helloButton = outlineButton("hello");
        quickRow.addView(openButton, weightedButtonParams());
        quickRow.addView(space(dp(8), 1));
        quickRow.addView(closeButton, weightedButtonParams());
        quickRow.addView(space(dp(8), 1));
        quickRow.addView(helloButton, weightedButtonParams());
        content.addView(quickRow);

        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText("openled");
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText("closeled");
            }
        });
        helloButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText("hello");
            }
        });

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        sendRow.setGravity(Gravity.CENTER_VERTICAL);
        messageInput = editText("");
        messageInput.setHint("消息");
        messageInput.setSingleLine(true);
        sendButton = primaryButton("发送");
        sendRow.addView(messageInput, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1));
        sendRow.addView(space(dp(8), 1));
        sendRow.addView(sendButton, new LinearLayout.LayoutParams(dp(86), dp(48)));
        content.addView(sendRow);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = messageInput.getText().toString();
                if (text.trim().length() == 0) {
                    return;
                }
                sendText(text);
                messageInput.setText("");
                hideKeyboard();
            }
        });

        return root;
    }

    private void connectClient() {
        if (running) {
            toast("已经连接");
            return;
        }

        final String host = hostInput.getText().toString().trim();
        final int port = parsePort();
        if (host.length() == 0 || port <= 0) {
            return;
        }

        saveSettings(host, String.valueOf(port));
        setRunningUi(true, "正在连接 " + host + ":" + port + " ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress(host, port), 5000);
                    clientSocket = socket;
                    appendLog("已连接服务器 " + host + ":" + port);
                    postStatus("已连接 " + host + ":" + port);
                    readLoop(socket, "服务器");
                } catch (IOException e) {
                    appendLog("连接失败: " + e.getMessage());
                } finally {
                    closeQuietly(socket);
                    if (clientSocket == socket) {
                        clientSocket = null;
                    }
                    postDisconnected();
                }
            }
        }).start();
    }

    private void startServer() {
        if (running) {
            toast("已经启动");
            return;
        }

        final int port = parsePort();
        if (port <= 0) {
            return;
        }

        saveSettings(lastClientHost, String.valueOf(port));
        setRunningUi(true, "正在监听端口 " + port + " ...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    appendLog("TCP Server 已启动，本机IP " + getLocalIpAddress() + "，端口 " + port);
                    postStatus("正在监听端口 " + port);

                    while (running) {
                        Socket socket = serverSocket.accept();
                        serverClients.add(socket);
                        appendLog("客户端接入: " + socket.getRemoteSocketAddress());
                        startServerClientReader(socket);
                    }
                } catch (IOException e) {
                    if (running) {
                        appendLog("监听失败: " + e.getMessage());
                    }
                } finally {
                    closeQuietly(serverSocket);
                    serverSocket = null;
                    closeAllServerClients();
                    postDisconnected();
                }
            }
        }).start();
    }

    private void startServerClientReader(final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop(socket, String.valueOf(socket.getRemoteSocketAddress()));
                serverClients.remove(socket);
                closeQuietly(socket);
                appendLog("客户端断开: " + socket.getRemoteSocketAddress());
            }
        }).start();
    }

    private void readLoop(Socket socket, String from) {
        byte[] buffer = new byte[2048];
        try {
            InputStream inputStream = socket.getInputStream();
            while (running && !socket.isClosed()) {
                int len = inputStream.read(buffer);
                if (len == -1) {
                    break;
                }
                String message = new String(buffer, 0, len, UTF8);
                appendLog("<- " + from + ": " + message);
                handleIncomingMessage(message);
            }
        } catch (IOException e) {
            if (running) {
                appendLog("接收中断: " + e.getMessage());
            }
        }
    }

    private void sendText(String text) {
        if (!running) {
            toast("请先连接或启动监听");
            return;
        }

        final byte[] data = text.getBytes(UTF8);

        new Thread(new Runnable() {
            @Override
            public void run() {
                if (clientMode) {
                    sendToClientSocket(data);
                } else {
                    sendToServerClients(data);
                }
            }
        }).start();

        appendLog("-> " + text);
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add("切换为 TCP Client");
        popupMenu.getMenu().add("切换为 TCP Server");
        popupMenu.getMenu().add(clientMode ? "连接服务器" : "开始监听");
        popupMenu.getMenu().add("断开连接");
        popupMenu.getMenu().add("清空日志");
        popupMenu.getMenu().add("发送 openled");
        popupMenu.getMenu().add("发送 closeled");
        popupMenu.getMenu().add("发送 hello");
        popupMenu.getMenu().add("灯光控制");
        popupMenu.getMenu().add("本机自测");

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                String title = item.getTitle().toString();
                if ("切换为 TCP Client".equals(title)) {
                    if (running) {
                        disconnect();
                    }
                    modeSpinner.setSelection(0);
                    return true;
                }
                if ("切换为 TCP Server".equals(title)) {
                    if (running) {
                        disconnect();
                    }
                    modeSpinner.setSelection(1);
                    return true;
                }
                if ("连接服务器".equals(title)) {
                    connectClient();
                    return true;
                }
                if ("开始监听".equals(title)) {
                    startServer();
                    return true;
                }
                if ("断开连接".equals(title)) {
                    disconnect();
                    return true;
                }
                if ("清空日志".equals(title)) {
                    logText.setText("");
                    return true;
                }
                if ("发送 openled".equals(title)) {
                    sendText("openled");
                    return true;
                }
                if ("发送 closeled".equals(title)) {
                    sendText("closeled");
                    return true;
                }
                if ("发送 hello".equals(title)) {
                    sendText("hello");
                    return true;
                }
                if ("灯光控制".equals(title)) {
                    showLightControlDialog();
                    return true;
                }
                if ("本机自测".equals(title)) {
                    runLoopbackSelfTest();
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    private void runLoopbackSelfTest() {
        if (running) {
            disconnect();
        }

        final int port = parseSelfTestPort();
        if (port <= 0) {
            return;
        }

        appendLog("本机自测开始: " + LoopbackSelfTest.HOST + ":" + port);
        postStatus("正在进行本机自测");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LoopbackSelfTest.Result result = LoopbackSelfTest.run(port, "SELFTEST:HELLO", 3000);
                    appendLog("本机自测发送: " + result.getSentMessage());
                    appendLog("本机自测服务端收到: " + result.getServerReceivedMessage());
                    appendLog("本机自测客户端收到: " + result.getClientReceivedReply());
                    appendLog("本机自测成功: " + result.getHost() + ":" + result.getPort()
                            + "，耗时 " + result.getDurationMs() + "ms");
                    postStatus("本机自测成功");
                } catch (Exception e) {
                    appendLog("本机自测失败: " + e.getMessage());
                    postStatus("本机自测失败");
                }
            }
        }).start();
    }

    private void showLightControlDialog() {
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(8), dp(18), 0);

        TextView controlLabel = dialogLabel("收到 LED:ON 后");
        layout.addView(controlLabel);

        final Spinner ledModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"常亮手电筒", "启动频闪"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ledModeSpinner.setAdapter(modeAdapter);
        ledModeSpinner.setSelection(ledOnMode == LED_ON_MODE_STROBE ? 1 : 0);
        layout.addView(ledModeSpinner, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)));

        TextView frequencyLabel = dialogLabel("频闪频率");
        frequencyLabel.setPadding(0, dp(10), 0, 0);
        layout.addView(frequencyLabel);

        LinearLayout frequencyRow = new LinearLayout(this);
        frequencyRow.setOrientation(LinearLayout.HORIZONTAL);
        frequencyRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText onInput = editText(String.valueOf(strobeSettings.getOnMs()));
        onInput.setHint("亮灯ms");
        onInput.setSingleLine(true);
        onInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText offInput = editText(String.valueOf(strobeSettings.getOffMs()));
        offInput.setHint("灭灯ms");
        offInput.setSingleLine(true);
        offInput.setInputType(InputType.TYPE_CLASS_NUMBER);

        frequencyRow.addView(onInput, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1));
        frequencyRow.addView(space(dp(8), 1));
        frequencyRow.addView(offInput, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1));
        layout.addView(frequencyRow);

        TextView hint = new TextView(this);
        hint.setText("默认 500/500ms，范围 100-10000ms。");
        hint.setTextColor(Color.rgb(92, 101, 101));
        hint.setTextSize(13);
        hint.setPadding(0, dp(6), 0, dp(6));
        layout.addView(hint);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(6), 0, dp(6));
        Button torchOnButton = outlineButton("打开灯");
        Button strobeButton = outlineButton("开始频闪");
        Button torchOffButton = outlineButton("关闭灯");
        actionRow.addView(torchOnButton, weightedButtonParams());
        actionRow.addView(space(dp(8), 1));
        actionRow.addView(strobeButton, weightedButtonParams());
        actionRow.addView(space(dp(8), 1));
        actionRow.addView(torchOffButton, weightedButtonParams());
        layout.addView(actionRow);

        torchOnButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopStrobeOnly();
                setTorch(true);
            }
        });
        strobeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    StrobeSettings settings = StrobeSettings.fromText(
                            onInput.getText().toString(),
                            offInput.getText().toString());
                    strobeSettings = settings;
                    saveLightSettings();
                    startStrobe(settings);
                } catch (IllegalArgumentException e) {
                    toast(e.getMessage());
                }
            }
        });
        torchOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopStrobeAndTurnOff();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("灯光控制")
                .setView(layout)
                .setPositiveButton("保存", null)
                .setNegativeButton("关闭", null)
                .setNeutralButton("停止频闪", null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialogInterface) {
                final AlertDialog shownDialog = (AlertDialog) dialogInterface;
                shownDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            StrobeSettings settings = StrobeSettings.fromText(
                                    onInput.getText().toString(),
                                    offInput.getText().toString());
                            strobeSettings = settings;
                            ledOnMode = ledModeSpinner.getSelectedItemPosition() == 1
                                    ? LED_ON_MODE_STROBE
                                    : LED_ON_MODE_TORCH;
                            saveLightSettings();
                            toast("灯光控制设置已保存");
                            shownDialog.dismiss();
                        } catch (IllegalArgumentException e) {
                            toast(e.getMessage());
                        }
                    }
                });

                shownDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        stopStrobeAndTurnOff();
                    }
                });
            }
        });

        dialog.show();
    }

    private void showHelpDialog() {
        String message =
                "1. TCP Client：填写服务器地址和端口，点击连接后发送消息。\n\n" +
                "2. TCP Server：切换到 TCP Server 后点击监听，其他设备可连接本机 IP 和端口。\n\n" +
                "3. openled / closeled：大作业控制指令，设备端收到后回传 LED:ON 或 LED:OFF。\n\n" +
                "4. 手电筒联动：本机收到 LED:ON 会尝试打开手电筒，收到 LED:OFF 会关闭手电筒。\n\n" +
                "5. 日志区域：显示发送和接收的数据。\n\n" +
                "6. 灯光控制：右上角三点菜单进入，可选择 LED:ON 后常亮或频闪，并设置亮灯/灭灯毫秒数，默认 500/500ms。\n\n" +
                "7. 远程频闪：收到 STROBE:ON:500:500 会按指定频率频闪，收到 STROBE:OFF 会停止频闪并关灯。\n\n" +
                "8. 本机自测：右上角三点菜单进入，会在手机内部临时监听 127.0.0.1:端口，再由内部客户端连接并收发测试数据。\n\n" +
                "9. 右上角三点菜单：可快速切换模式、连接、断开、清空日志、发送常用指令、灯光控制和本机自测。\n\n" +
                "本软件由“改名楠”开发，项目地址: " + PROJECT_URL;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("使用教程")
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .create();
        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            Linkify.addLinks(messageView, Linkify.WEB_URLS);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setLinksClickable(true);
        }
    }

    private void handleIncomingMessage(String message) {
        StrobeSettings.Command strobeCommand = StrobeSettings.parseCommand(message);
        if (strobeCommand != null) {
            if (strobeCommand.getType() == StrobeSettings.CommandType.START) {
                startStrobe(strobeCommand.getSettings());
            } else {
                stopStrobeAndTurnOff();
            }
            return;
        }

        String normalized = message.toUpperCase(Locale.US);
        if (normalized.contains("LED:ON")) {
            if (ledOnMode == LED_ON_MODE_STROBE) {
                startStrobe(strobeSettings);
            } else {
                stopStrobeOnly();
                setTorch(true);
            }
        } else if (normalized.contains("LED:OFF")) {
            stopStrobeAndTurnOff();
        }
    }

    private void setTorch(final boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            appendLog("手电筒控制需要 Android 6.0 或更高版本");
            return;
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchAction = new Runnable() {
                @Override
                public void run() {
                    applyTorch(enabled);
                }
            };
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            });
            appendLog("需要相机权限才能控制手电筒");
            return;
        }

        applyTorch(enabled);
    }

    private void startStrobe(final StrobeSettings settings) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            appendLog("频闪控制需要 Android 6.0 或更高版本");
            return;
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingTorchAction = new Runnable() {
                @Override
                public void run() {
                    beginStrobe(settings);
                }
            };
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            });
            appendLog("需要相机权限才能控制频闪");
            return;
        }

        beginStrobe(settings);
    }

    private void beginStrobe(final StrobeSettings settings) {
        stopStrobeOnly();
        strobeSettings = settings;
        strobeRunning = true;
        final int generation = ++strobeGeneration;
        appendLog("频闪已启动: 亮 " + settings.getOnMs() + "ms / 灭 " + settings.getOffMs() + "ms");

        strobeRunnable = new Runnable() {
            private boolean nextEnabled = true;

            @Override
            public void run() {
                if (!strobeRunning || generation != strobeGeneration) {
                    return;
                }

                applyTorch(nextEnabled, false);
                int delay = nextEnabled ? settings.getOnMs() : settings.getOffMs();
                nextEnabled = !nextEnabled;
                mainHandler.postDelayed(this, delay);
            }
        };
        mainHandler.post(strobeRunnable);
    }

    private void stopStrobeAndTurnOff() {
        boolean wasRunning = strobeRunning;
        stopStrobeOnly();
        setTorch(false);
        if (wasRunning) {
            appendLog("频闪已停止");
        }
    }

    private void stopStrobeOnly() {
        strobeRunning = false;
        strobeGeneration++;
        if (strobeRunnable != null) {
            mainHandler.removeCallbacks(strobeRunnable);
            strobeRunnable = null;
        }
    }

    private StrobeSettings loadStrobeSettings() {
        return StrobeSettings.fromText(
                String.valueOf(preferences.getInt(PREF_STROBE_ON_MS, StrobeSettings.DEFAULT_ON_MS)),
                String.valueOf(preferences.getInt(PREF_STROBE_OFF_MS, StrobeSettings.DEFAULT_OFF_MS)));
    }

    private void saveLightSettings() {
        preferences.edit()
                .putInt(PREF_STROBE_ON_MS, strobeSettings.getOnMs())
                .putInt(PREF_STROBE_OFF_MS, strobeSettings.getOffMs())
                .putInt(PREF_LED_ON_MODE, ledOnMode)
                .apply();
    }

    private void applyTorch(final boolean enabled) {
        applyTorch(enabled, true);
    }

    private void applyTorch(final boolean enabled, boolean writeLog) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (cameraManager == null) {
                appendLog("手电筒控制失败: 无法获取相机服务");
                return;
            }

            String cameraId = getTorchCameraId(cameraManager);
            if (cameraId == null) {
                appendLog("当前设备没有可用闪光灯");
                return;
            }

            cameraManager.setTorchMode(cameraId, enabled);
            torchEnabled = enabled;
            if (writeLog) {
                appendLog(enabled ? "手电筒已打开" : "手电筒已关闭");
            }
        } catch (CameraAccessException e) {
            appendLog("手电筒控制失败: " + e.getMessage());
        } catch (SecurityException e) {
            appendLog("手电筒控制失败: 相机权限未授权");
        }
    }

    private String getTorchCameraId(CameraManager cameraManager) throws CameraAccessException {
        if (torchCameraId != null) {
            return torchCameraId;
        }

        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (Boolean.TRUE.equals(hasFlash)
                    && lensFacing != null
                    && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                torchCameraId = cameraId;
                return torchCameraId;
            }
        }

        for (String cameraId : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (Boolean.TRUE.equals(hasFlash)) {
                torchCameraId = cameraId;
                return torchCameraId;
            }
        }

        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_CAMERA_PERMISSION) {
            return;
        }

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Runnable action = pendingTorchAction;
            pendingTorchAction = null;
            if (action != null) {
                action.run();
            }
        } else {
            pendingTorchAction = null;
            appendLog("相机权限被拒绝，无法控制手电筒");
        }
    }

    private void sendToClientSocket(byte[] data) {
        Socket socket = clientSocket;
        if (socket == null || socket.isClosed()) {
            appendLog("发送失败: 未连接服务器");
            return;
        }
        try {
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(data);
            outputStream.flush();
        } catch (IOException e) {
            appendLog("发送失败: " + e.getMessage());
        }
    }

    private void sendToServerClients(byte[] data) {
        synchronized (serverClients) {
            if (serverClients.isEmpty()) {
                appendLog("发送失败: 当前没有客户端连接");
                return;
            }
            for (Socket socket : new ArrayList<Socket>(serverClients)) {
                try {
                    OutputStream outputStream = socket.getOutputStream();
                    outputStream.write(data);
                    outputStream.flush();
                } catch (IOException e) {
                    appendLog("发送到 " + socket.getRemoteSocketAddress() + " 失败: " + e.getMessage());
                    closeQuietly(socket);
                    serverClients.remove(socket);
                }
            }
        }
    }

    private void disconnect() {
        running = false;
        closeQuietly(clientSocket);
        clientSocket = null;
        closeQuietly(serverSocket);
        serverSocket = null;
        closeAllServerClients();
        setRunningUi(false, "未连接");
        appendLog("已断开");
    }

    private void closeAllServerClients() {
        synchronized (serverClients) {
            for (Socket socket : new ArrayList<Socket>(serverClients)) {
                closeQuietly(socket);
            }
            serverClients.clear();
        }
    }

    private void setRunningUi(final boolean isRunning, final String status) {
        running = isRunning;
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                connectButton.setEnabled(!isRunning);
                disconnectButton.setEnabled(isRunning);
                modeSpinner.setEnabled(!isRunning);
                hostInput.setEnabled(!isRunning && clientMode);
                portInput.setEnabled(!isRunning);
                statusText.setText(status);
            }
        });
    }

    private void postStatus(final String status) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                statusText.setText(status);
            }
        });
    }

    private void postDisconnected() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    setRunningUi(false, "未连接");
                    return;
                }
                running = false;
                setRunningUi(false, "连接已断开");
            }
        });
    }

    private void updateModeUi() {
        if (hostInput == null || hostLabel == null || connectButton == null) {
            return;
        }

        if (clientMode) {
            hostLabel.setText("地址");
            hostInput.setText(lastClientHost);
            hostInput.setEnabled(!running);
            connectButton.setText("连接");
            statusTextSafe("未连接");
        } else {
            lastClientHost = hostInput.getText().toString().trim();
            hostLabel.setText("本机IP");
            hostInput.setText(getLocalIpAddress());
            hostInput.setEnabled(false);
            connectButton.setText("监听");
            statusTextSafe("未监听");
        }
    }

    private void statusTextSafe(String value) {
        if (statusText != null) {
            statusText.setText(value);
        }
    }

    private int parsePort() {
        try {
            int port = Integer.parseInt(portInput.getText().toString().trim());
            if (port <= 0 || port > 65535) {
                toast("端口范围应为 1-65535");
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            toast("端口格式不正确");
            return -1;
        }
    }

    private int parseSelfTestPort() {
        String text = portInput.getText().toString().trim();
        if (text.length() == 0) {
            return LoopbackSelfTest.DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(text);
            if (port <= 0 || port > 65535) {
                toast("本机自测端口范围应为 1-65535");
                return -1;
            }
            return port;
        } catch (NumberFormatException e) {
            toast("本机自测端口格式不正确");
            return -1;
        }
    }

    private void saveSettings(String host, String port) {
        preferences.edit()
                .putString("host", host)
                .putString("port", port)
                .apply();
    }

    private String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String ip = address.getHostAddress();
                    if (!address.isLoopbackAddress() && ip.indexOf(':') < 0) {
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }

    private void appendLog(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
                logText.append("[" + time + "] " + message + "\n");
                final View parent = (View) logText.getParent();
                if (parent instanceof ScrollView) {
                    parent.post(new Runnable() {
                        @Override
                        public void run() {
                            ((ScrollView) parent).fullScroll(View.FOCUS_DOWN);
                        }
                    });
                }
            }
        });
    }

    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(54, 63, 63));
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER_VERTICAL);
        return label;
    }

    private LinearLayout labeledRow(String label, View field) {
        return rowWithLabel(label(label), field);
    }

    private LinearLayout rowWithLabel(TextView label, View field) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(label, new LinearLayout.LayoutParams(dp(68), dp(48)));
        row.addView(field, new LinearLayout.LayoutParams(
                0,
                dp(48),
                1));
        return row;
    }

    private TextView dialogLabel(String text) {
        TextView label = label(text);
        label.setPadding(0, 0, 0, dp(4));
        return label;
    }

    private EditText editText(String value) {
        EditText editText = new EditText(this);
        editText.setText(value);
        editText.setTextSize(16);
        editText.setPadding(dp(10), 0, dp(10), 0);
        editText.setBackground(inputBg());
        return editText;
    }

    private Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(buttonBg(COLOR_PRIMARY, COLOR_PRIMARY));
        return button;
    }

    private Button outlineButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(COLOR_PRIMARY_DARK);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setBackground(buttonBg(Color.WHITE, COLOR_BORDER));
        return button;
    }

    private TextView titleButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(24);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(COLOR_PRIMARY_DARK);
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        return new LinearLayout.LayoutParams(0, dp(44), 1);
    }

    private View space(int width, int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    private GradientDrawable inputBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), COLOR_BORDER);
        drawable.setCornerRadius(dp(4));
        return drawable;
    }

    private GradientDrawable panelBg() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), COLOR_BORDER);
        drawable.setCornerRadius(dp(4));
        return drawable;
    }

    private GradientDrawable buttonBg(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setStroke(dp(1), stroke);
        drawable.setCornerRadius(dp(4));
        return drawable;
    }

    private void hideKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (manager != null && view != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void toast(final String text) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        stopStrobeOnly();
        if (torchEnabled) {
            applyTorch(false);
        }
        disconnect();
        super.onDestroy();
    }
}
