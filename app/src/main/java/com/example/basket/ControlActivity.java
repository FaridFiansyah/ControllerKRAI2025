package com.example.basket;

import android.content.Context;
import android.content.Intent;
import android.os.Vibrator;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.FrameLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;
import android.hardware.input.InputManager;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ControlActivity extends AppCompatActivity {
    private static final String TAG = "ControlActivity";
    private float scaleX, scaleY;
    private static final float courtWidth = 15000f;
    private static final float courtHeight = 8000f;
    private int lastX = 0, lastY = 0;
    private boolean isControllerActive = false;
    private boolean preset = false;
    private float dpadX, dpadY, leftStickX, leftStickY;
    private float rightStickX, rightStickY, leftTrigger, rightTrigger;
    private ImageView markerPoint;
    private FrameLayout courtContainer;
    private boolean isSendingPosition = false;
    private TextView coordinateText;
    private TextView connectionStatusText;
    private TextView batteryText;
    private ImageView lapangan;
    private Handler handler;
    private Runnable statusUpdateRunnable;
    boolean wasJoystickMoving = true;
    boolean wasDpadPressed = false;
    private Runnable dataUpdateRunnable;
    private Runnable batteryUpdateRunnable;
    private ProgressBar batteryBar;
    private static final int STATUS_UPDATE_INTERVAL = 500; // 500ms for status updates
    private static final int DATA_UPDATE_INTERVAL = 50;   // 50ms for data updates
    private String lastSentData = null;
    private long lastDataSentTime = 0;
    private static final long MIN_DATA_SEND_INTERVAL = 50;
    private ZContext context;
    private ZMQ.Socket pullSocket;
    private volatile int batre = 0; // Initialize to 0 instead of uninitialized
    private int last_data = -1; // Set to -1 to force first update
    private float flipy;
    private ImageButton virtualControllerButton; // Button for virtual controller

    // Flag to track if returning from virtual controller
    private boolean returningFromVirtualController = false;
    private boolean isButtonPressed = false;
    private String lastButtonCommand = "";

    // Map to manage sound resources
    private Map<String, MediaPlayer> soundPlayers = new HashMap<>();

    // Flags for sound states
    private volatile boolean isPercentageSoundPlaying = false;
    private Map<Integer, Boolean> percentageSoundFlags = new HashMap<>();

    // Thread control
    private AtomicBoolean isReceivingData = new AtomicBoolean(true);
    private Thread dataReceiverThread;
    private Thread bluetoothCheckThread;
    private Thread wifiCheckThread;
    private Thread batteryUpdateThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        // Apply fullscreen mode immediately
        setupFullScreen();

        try {
            // Initialize preferences and connection
            SharedPreferences kirim = getSharedPreferences("app_kirim", MODE_PRIVATE);
            String address = kirim.getString("addres", "");
            Log.d(TAG, "PULL address: " + address);

            // Initialize connection manager first
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager != null) {
                connectionManager.initialize(this);
                // Set this activity as the active input source
                connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
            } else {
                Log.e(TAG, "ConnectionManager instance is null");
                Toast.makeText(this, "Error initializing connection", Toast.LENGTH_SHORT).show();
            }

            // Initialize UI and controller detection
            initializeViews();
            setupTouchListener();
            initControllerDetection();
            initializeSounds();

            // Start background threads and handlers
            startUpdateHandlers();

            // Setup battery monitoring
            if (!address.isEmpty()) {
                updateBattery(address);
            } else {
                Log.w(TAG, "ZMQ address is empty");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Setup fullscreen mode with proper notch/cutout support
     */
    private void setupFullScreen() {
        // For Android R (API 30) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }

            // Enable content to be displayed in the cutout area
            WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
            layoutParams.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(layoutParams);
        }
        // For older versions (Android 9+)
        else {
            View decorView = getWindow().getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(flags);

            // Support cutout mode for Android P (API 28)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(layoutParams);
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Reapply fullscreen mode
        setupFullScreen();

        // When returning to this activity, set it as the active input source
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionManager != null) {
            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
        }

        // IMPORTANT: Reset lastSentData to prevent automatic sending of previous coordinates
        lastSentData = null;

        // Also clear the sending position flag
        isSendingPosition = false;
        isButtonPressed = false;

        // If marker is showing, hide it until user touches court again
        if (markerPoint != null) {
            markerPoint.setVisibility(View.INVISIBLE);
        }

        // Resume other onResume tasks
        String savedAddress = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("current_address", null);
        String savedMac = getSharedPreferences("app_prefs", MODE_PRIVATE).getString("current_mac", null);

        if (savedAddress != null && ConnectionManager.getInstance().isZmqConnected()) {
            // Update UI to show connected state
        }

        if (savedMac != null && ConnectionManager.getInstance().isBluetoothConnected()) {
            // Update UI to show connected state
        }

        if (connectionStatusText != null) {
            connectionStatusText.setText(ConnectionManager.getInstance().getConnectionStatus());
        }
    }

    private void initializeSounds() {
        try {
            // Pre-load all sound resources
            soundPlayers.put("low", MediaPlayer.create(this, R.raw.low));
            soundPlayers.put("med", MediaPlayer.create(this, R.raw.med));
            soundPlayers.put("10", MediaPlayer.create(this, R.raw.snd10));
            soundPlayers.put("20", MediaPlayer.create(this, R.raw.snd20));
            soundPlayers.put("30", MediaPlayer.create(this, R.raw.snd30));
            soundPlayers.put("40", MediaPlayer.create(this, R.raw.snd40));
            soundPlayers.put("50", MediaPlayer.create(this, R.raw.snd50));

            // Initialize percentage flags
            percentageSoundFlags.put(10, false);
            percentageSoundFlags.put(20, false);
            percentageSoundFlags.put(30, false);
            percentageSoundFlags.put(40, false);
            percentageSoundFlags.put(50, false);

            // Set completion listeners
            for (Map.Entry<String, MediaPlayer> entry : soundPlayers.entrySet()) {
                if (entry.getValue() != null) {
                    entry.getValue().setOnCompletionListener(mp -> {
                        isPercentageSoundPlaying = false;

                        // For percentage sounds, play the appropriate alarm after completion
                        if (entry.getKey().equals("10") || entry.getKey().equals("20")) {
                            playSound("low");
                        } else if (entry.getKey().equals("30") || entry.getKey().equals("40")) {
                            playSound("med");
                        }
                    });
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing sounds: " + e.getMessage(), e);
        }
    }

    private void playSound(String soundKey) {
        try {
            MediaPlayer player = soundPlayers.get(soundKey);
            if (player != null && !player.isPlaying()) {
                // Reset and start the player
                player.seekTo(0);
                player.start();

                // Set volume for specific sounds if needed
                if (soundKey.equals("20")) {
                    player.setVolume(500, 500);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound " + soundKey + ": " + e.getMessage(), e);
        }
    }

    private void stopSound(String soundKey) {
        try {
            MediaPlayer player = soundPlayers.get(soundKey);
            if (player != null && player.isPlaying()) {
                player.pause();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping sound " + soundKey + ": " + e.getMessage(), e);
        }
    }

    private void initializeViews() {
        try {
            courtContainer = findViewById(R.id.courtContainer);
            coordinateText = findViewById(R.id.coordinateText);
            connectionStatusText = findViewById(R.id.connectionStatusText);
            batteryText = findViewById(R.id.battxt);
            batteryBar = findViewById(R.id.battbarr);
            lapangan = findViewById(R.id.lap);

            // Initialize virtual controller button
            virtualControllerButton = findViewById(R.id.virtualControllerButton);
            if (virtualControllerButton != null) {
                virtualControllerButton.setOnClickListener(v -> openVirtualController());
            }

            // Initialize marker point
            markerPoint = new ImageView(this);
            markerPoint.setBackground(getResources().getDrawable(R.drawable.circle_marker));
            int pointSize = 24;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(pointSize, pointSize);
            markerPoint.setLayoutParams(params);
            markerPoint.setVisibility(View.INVISIBLE);
            courtContainer.addView(markerPoint);

            //setup reset button
            ImageButton rstButton = findViewById(R.id.rstbutton);
            if(rstButton != null) {
                rstButton.setOnClickListener(v -> {
                    ConnectionManager connectionManager = ConnectionManager.getInstance();
//                    lastSentData = "RS";
                    connectionManager.sendData("RS");
                    markerPoint.setVisibility(View.INVISIBLE);
                    updateCoordinateText(0,0);
                });
            }

            //setup manual button
            EditText manualinputx = findViewById(R.id.manualx);
            EditText manualinputy = findViewById(R.id.manualy);
            ImageButton manualb = findViewById(R.id.buttonmanual);
            manualb.setOnClickListener(v -> {
                try {
                    String manualxStr = manualinputx.getText().toString().trim();
                    String manualyStr = manualinputy.getText().toString().trim();

                    lastX = Integer.parseInt(manualxStr);
                    lastY = Integer.parseInt(manualyStr);

                    if (lastX > 15000 || lastX < 0) {
                        Toast.makeText(getApplicationContext(), "Nilai X harus antara 0 dan 15000", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (lastY > 8000 || lastY < 0) {
                        Toast.makeText(getApplicationContext(), "Nilai Y harus antara 0 dan 8000", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    lastSentData = null;
                    isButtonPressed = false;
                    isSendingPosition = true;
//                    ConnectionManager connectionManager = ConnectionManager.getInstance();
                    lastSentData = String.format("A %d %d", lastX, lastY);
                    updateMarkerPosition(lastX,lastY);
                    updateCoordinateText(lastX,lastY);
//                    connectionManager.sendData(lastSentData);
                } catch (NumberFormatException e) {

                    Toast.makeText(getApplicationContext(), "Nilai X sama Y Harus Diisi angka valid", Toast.LENGTH_SHORT).show();
                }
            });

            // Setup back button
            ImageButton backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                ConnectionManager connectionManager = ConnectionManager.getInstance();
//                lastSentData = "RS";
                connectionManager.sendData("RS");
                backButton.setOnClickListener(view -> finish());
            }

            // Calculate scaling factors after layout
            if (lapangan != null) {
                lapangan.post(() -> {
                    scaleX = courtWidth / lapangan.getWidth();
                    scaleY = courtHeight / lapangan.getHeight();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
        }
    }

    // Method to open the virtual controller
    private void openVirtualController() {
        try {
            // Set the input source to virtual before starting activity
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager != null) {
                connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
            }

            // Set flag to indicate we're starting the virtual controller
            returningFromVirtualController = true;

            // Clear last sent data to prevent sending when we return
            lastSentData = null;
            isSendingPosition = false;
            isButtonPressed = false;

            Intent intent = new Intent(this, VirtualControllerActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening virtual controller: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening virtual controller", Toast.LENGTH_SHORT).show();
        }
    }

    private void vibrator() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                vibrator.vibrate(1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating: " + e.getMessage(), e);
        }
    }

    private void startUpdateHandlers() {
        try {
            handler = new Handler(Looper.getMainLooper());

            // Status update runnable
            statusUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        updateConnectionStatus();
                        handler.postDelayed(this, STATUS_UPDATE_INTERVAL);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in status update: " + e.getMessage(), e);
                    }
                }
            };

//             Data update runnable - Modified to check active input source and implement continuous sending
//            dataUpdateRunnable = new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        ConnectionManager connectionManager = ConnectionManager.getInstance();
//                        // Only send data if we are the active input source
//                        if (connectionManager != null &&
//                                connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_COURT)) {
//
//                            // Determine what to send based on active inputs
//                            if (isSendingPosition && lastSentData != null) {
//                                // If court coordinate is active, continuously send it
//                                connectionManager.sendData(lastSentData);
//                                lastDataSentTime = System.currentTimeMillis();
//                            }
//
//                            else if (isButtonPressed && lastButtonCommand != null && !lastButtonCommand.isEmpty()) {
//                                // If a button is pressed, send the button command
//                                connectionManager.sendData(lastButtonCommand);
////                                preset = true;
////                                if (preset) {
//////                                    // Idle state
////                                    connectionManager.sendData("");
////                                }
//                                lastDataSentTime = System.currentTimeMillis();
//
//
//                            } else if (isControllerActive) {
//                                // If controller is active but no specific input, send "T 0 0 0"
//                                if (leftStickX != 0 || leftStickY != 0 || rightStickX != 0) {
//                                    // Joystick is moved
//                                    String joystickCommand = String.format("T %.2f %.2f %.2f", leftStickX, flipy, rightStickX);
//                                    connectionManager.sendData(joystickCommand);
//                                }
////                                }if (preset) {
////                                    // Idle state
////                                    connectionManager.sendData("");
////                                }
//                                else{
//                                    connectionManager.sendData("T 0 0 0");
//                                }
//                                lastDataSentTime = System.currentTimeMillis();
//                            }
//                            else{
//                                connectionManager.sendData("");
//                            }
//                        }
//                        handler.postDelayed(this, DATA_UPDATE_INTERVAL);
//                    } catch (Exception e) {
//                        Log.e(TAG, "Error in data update: " + e.getMessage(), e);
//                    }
//                }
//            };

            dataUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        ConnectionManager connectionManager = ConnectionManager.getInstance();
                        if (connectionManager != null &&
                                connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_COURT)) {
                            if (isSendingPosition && lastSentData != null) {
                                connectionManager.sendData(lastSentData);
                                lastDataSentTime = System.currentTimeMillis();

                            } else if (isButtonPressed && lastButtonCommand != null && !lastButtonCommand.isEmpty()) {
                                // D-Pad ditekan
                                connectionManager.sendData(lastButtonCommand);
                                lastDataSentTime = System.currentTimeMillis();
                                wasDpadPressed = true;
                                wasJoystickMoving = false;
//                                isButtonPressed = false; // reset setelah dikirim sekali

                            } else if (isControllerActive) {
                                if (leftStickX != 0 || leftStickY != 0 || rightStickX != 0) {
                                    String joystickCommand = String.format("T %.2f %.2f %.2f", leftStickX, flipy, rightStickX);
                                    connectionManager.sendData(joystickCommand);
                                    wasJoystickMoving = true;
                                    wasDpadPressed = false;
                                    lastDataSentTime = System.currentTimeMillis();
                                }
                                else if (wasDpadPressed) {
                                    connectionManager.sendData("");
//                                wasDpadPressed = false;
                                    lastDataSentTime = System.currentTimeMillis();
                                }
                                else {
                                    if (wasJoystickMoving) {
                                        connectionManager.sendData("T 0 0 0");
//                                        wasJoystickMoving = false;
                                        lastDataSentTime = System.currentTimeMillis();
                                    }
                                }
                            }

                            else {
                                connectionManager.sendData("T 0 0 0");
                                lastDataSentTime = System.currentTimeMillis();
                            }
                        }

                        handler.postDelayed(this, DATA_UPDATE_INTERVAL);

                    } catch (Exception e) {
                        Log.e(TAG, "Error in data update: " + e.getMessage(), e);
                    }
                }
            };


//            dataUpdateRunnable = new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        ConnectionManager connectionManager = ConnectionManager.getInstance();
//                        if (connectionManager != null &&
//                                connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_COURT)) {
//
//                            if (preset) {
//                                if (!hasSentPresetCommand && lastButtonCommand != null && !lastButtonCommand.isEmpty()) {
//                                    connectionManager.sendData(lastButtonCommand); // Kirim command preset sekali
//                                    hasSentPresetCommand = true;
//                                } else {
//                                    connectionManager.sendData(""); // Kirim kosong setelah preset command terkirim
//                                }
//                            }
//                            else if (isSendingPosition && lastSentData != null) {
//                                connectionManager.sendData(lastSentData);
//                                lastDataSentTime = System.currentTimeMillis();
//                                hasSentPresetCommand = false; // Reset flag saat keluar dari preset
//                            }
//                            else if (isButtonPressed && lastButtonCommand != null && !lastButtonCommand.isEmpty()) {
//                                connectionManager.sendData(lastButtonCommand);
//                                lastDataSentTime = System.currentTimeMillis();
//                                hasSentPresetCommand = false;
//                            }
//                            else if (isControllerActive) {
//                                if (leftStickX != 0 || leftStickY != 0 || rightStickX != 0) {
//                                    String joystickCommand = String.format("T %.2f %.2f %.2f", leftStickX, flipy, rightStickX);
//                                    connectionManager.sendData(joystickCommand);
//                                } else {
//                                    connectionManager.sendData("T 0 0 0");
//                                }
//                                lastDataSentTime = System.currentTimeMillis();
//                                hasSentPresetCommand = false;
//                            }
//                            else {
//                                connectionManager.sendData("T 0 0 0");
//                                hasSentPresetCommand = false;
//                            }
//                        }
//                        handler.postDelayed(this, DATA_UPDATE_INTERVAL);
//                    } catch (Exception e) {
//                        Log.e(TAG, "Error in data update: " + e.getMessage(), e);
//                    }
//                }
//            };

            // Battery update runnable
            batteryUpdateRunnable = new Runnable() {
                @Override
                public void run() {
                    try {
                        updateBatteryStatus();
                        handler.postDelayed(this, 700);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in battery update: " + e.getMessage(), e);
                    }
                }
            };

            // Start all handlers
            handler.post(statusUpdateRunnable);
            handler.post(dataUpdateRunnable);
            handler.post(batteryUpdateRunnable);

            // Start background threads for monitoring connections
            startMonitoringThreads();
        } catch (Exception e) {
            Log.e(TAG, "Error starting handlers: " + e.getMessage(), e);
        }
    }

    private void startMonitoringThreads() {
        // Monitor Bluetooth connection
        bluetoothCheckThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        ConnectionManager manager = ConnectionManager.getInstance();
                        if (manager != null) {
                            manager.startBluetoothReconnectThread();
                        }
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Bluetooth monitoring thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in Bluetooth monitoring: " + e.getMessage(), e);
                        // Don't break on single error, add delay to prevent tight loop
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Fatal error in Bluetooth monitoring thread: " + e.getMessage(), e);
            }
        });
        bluetoothCheckThread.start();

        // Monitor WiFi connection with similar robust error handling
        wifiCheckThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        ConnectionManager manager = ConnectionManager.getInstance();
                        if (manager != null && !manager.isZmqConnected()) {
                            manager.rekonek();
                        }
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.d(TAG, "WiFi monitoring thread interrupted");
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Error in WiFi monitoring: " + e.getMessage(), e);
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Fatal error in WiFi monitoring thread: " + e.getMessage(), e);
            }
        });
        wifiCheckThread.start();
    }

    public void updateBattery(String address) {
        try {
            // Check if thread already exists and clean up if necessary
            if (dataReceiverThread != null && dataReceiverThread.isAlive()) {
                isReceivingData.set(false);
                dataReceiverThread.interrupt();
                try {
                    dataReceiverThread.join(500);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error joining previous receiver thread", e);
                }
            }

            // Reset flag for new thread
            isReceivingData.set(true);

            // Close any existing ZMQ resources before creating new ones
            closeZmqResources();

            dataReceiverThread = new Thread(() -> {
                try {
                    Log.d(TAG, "Starting ZMQ battery receiver thread");
                    context = new ZContext();
                    pullSocket = context.createSocket(ZMQ.PULL);

                    // Set socket options for better error handling
                    pullSocket.setReceiveTimeOut(1000);  // 1 second timeout
                    pullSocket.setLinger(0);  // Don't block on close

                    pullSocket.connect(address);
                    Log.d(TAG, "ZMQ connected to: " + address);

                    receiveData();
                } catch (Exception e) {
                    final String errorMsg = e.getMessage();
                    Log.e(TAG, "Error setting up ZMQ: " + errorMsg, e);
                    runOnUiThread(() ->
                        Toast.makeText(ControlActivity.this, "Gagal koneksi: " + errorMsg, Toast.LENGTH_SHORT).show()
                    );
                } finally {
                    closeZmqResources();
                }
            });
            dataReceiverThread.start();
        } catch (Exception e) {
            Log.e(TAG, "Error starting battery update thread: " + e.getMessage(), e);
        }
    }

    private void receiveData() {
        try {
            while (isReceivingData.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Use a timeout to make the receive operation interruptible
                    byte[] message = pullSocket.recv(ZMQ.DONTWAIT);
                    if (message != null) {
                        String receivedText = new String(message, ZMQ.CHARSET);
                        Log.d(TAG, "RECEIVE: " + receivedText);
                        try {
                            int newBattery = Integer.parseInt(receivedText);
                            // Only update if the battery value is valid (0-100%)
                            if (newBattery >= 0 && newBattery <= 100) {
                                batre = newBattery;
                                Log.d(TAG, "Battery value updated to: " + batre);
                            } else {
                                Log.w(TAG, "Invalid battery percentage received: " + newBattery);
                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Invalid battery value received: " + receivedText, e);
                        }
                    }
                    // Small sleep to prevent busy waiting
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Battery receiver thread interrupted");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error receiving data: " + e.getMessage(), e);
                    // Add a small delay to prevent tight loop on error
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in receive data thread: " + e.getMessage(), e);
        } finally {
            // Make sure to clean up ZMQ resources even if thread exits abnormally
            Log.d(TAG, "Battery receiver thread exiting");
        }
    }

    private void updateBatteryStatus() {
        try {
            // Use synchronized to prevent concurrent access issues
            synchronized (this) {
                // Check if battery level changed
                boolean batteryLevelChanged = (last_data != batre);

                // Update battery UI
                updateBatteryUI(batre);

                // Log the battery value for debugging
                if (batteryLevelChanged) {
                    Log.d(TAG, "Battery level updated to: " + batre + "%");
                }

                // Handle sound notifications based on battery level
                updateBatterySounds(batre, batteryLevelChanged);

                // Store current level for next comparison
                last_data = batre;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery status: " + e.getMessage(), e);
        }
    }

    private void updateBatteryUI(final int level) {
        try {
            // Ensure we're working with a valid battery level
            if (level < 0 || level > 100) {
                Log.w(TAG, "Invalid battery level: " + level);
                return;
            }

            // Set progress bar color based on battery level
            final int color;
            if (level < 20) {
                color = getResources().getColor(R.color.low_color);
            } else if (level < 50) {
                color = getResources().getColor(R.color.med_color);
            } else {
                color = getResources().getColor(R.color.high_color);
            }

            // Update progress bar and text on UI thread
            runOnUiThread(() -> {
                try {
                    if (batteryBar != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            batteryBar.setProgressTintList(ColorStateList.valueOf(color));
                        }
                        batteryBar.setProgress(level);
                    }

                    if (batteryText != null) {
                        batteryText.setText(level + "%");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in battery UI update runnable: " + e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery UI: " + e.getMessage(), e);
        }
    }

    private void updateBatterySounds(int level, boolean batteryLevelChanged) {
        try {
            // Handle exact percentage level sounds
            if (level == 10 || level == 20 || level == 30 || level == 40 || level == 50) {
                boolean hasPlayedBefore = percentageSoundFlags.getOrDefault(level, false);

                if (!hasPlayedBefore || batteryLevelChanged) {
                    // Stop all current sounds
                    stopAllSounds();

                    // Play the percentage announcement
                    playSound(String.valueOf(level));
                    isPercentageSoundPlaying = true;

                    // Mark this percentage as played
                    percentageSoundFlags.put(level, true);

                    // Reset other percentage flags
                    for (int key : percentageSoundFlags.keySet()) {
                        if (key != level) {
                            percentageSoundFlags.put(key, false);
                        }
                    }
                } else if (!isPercentageSoundPlaying) {
                    // Play appropriate background sound if no percentage sound is playing
                    if (level <= 20) {
                        playSound("low");
                    } else if (level < 50) {
                        playSound("med");
                    }
                }
            }
            // Handle other battery levels
            else if (level < 20) {
                if (!isPercentageSoundPlaying) {
                    playSound("low");
                    stopSound("med");
                }
                // Reset level 10 flag when not exactly at 10%
                percentageSoundFlags.put(10, false);
            }
            else if (level < 50) {
                if (!isPercentageSoundPlaying) {
                    playSound("med");
                    stopSound("low");
                }
                // Reset percentage flags
                percentageSoundFlags.put(20, false);
                percentageSoundFlags.put(30, false);
                percentageSoundFlags.put(40, false);
            }
            else {
                // Above 50%, stop all sounds
                stopAllSounds();
                isPercentageSoundPlaying = false;

                // Reset all percentage flags
                for (int key : percentageSoundFlags.keySet()) {
                    percentageSoundFlags.put(key, false);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating battery sounds: " + e.getMessage(), e);
        }
    }

    private void stopAllSounds() {
        try {
            for (MediaPlayer player : soundPlayers.values()) {
                if (player != null && player.isPlaying()) {
                    player.pause();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping all sounds: " + e.getMessage(), e);
        }
    }

    private void updateConnectionStatus() {
        try {
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            boolean isConnected = connectionManager != null &&
                    (connectionManager.isZmqConnected() ||
                            connectionManager.isBluetoothConnected());

            runOnUiThread(() -> {
                connectionStatusText.setText("Status: " + (isConnected ? "Terhubung" : "Terputus"));
                connectionStatusText.setTextColor(getResources().getColor(
                        isConnected ? android.R.color.holo_green_dark : android.R.color.holo_red_dark
                ));
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating connection status: " + e.getMessage(), e);
        }
    }

    private void setupTouchListener() {
        try {
            if (lapangan != null) {
                lapangan.setOnTouchListener((v, event) -> {
                    try {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                            case MotionEvent.ACTION_MOVE:
                                // Make sure we're the active input source
                                ConnectionManager connectionManager = ConnectionManager.getInstance();
                                if (connectionManager != null) {
                                    connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
                                }

                                // Set court touchpoint as active
                                isSendingPosition = true;
                                isButtonPressed = false;
                                isControllerActive = false;

//                                kuadran 2
                                float transformedX = lapangan.getWidth() - event.getX();
                                float transformedY = lapangan.getHeight() - event.getY();

                                float x = transformedX / lapangan.getWidth() * courtWidth;
                                float y = transformedY / lapangan.getHeight() * courtHeight;
//
//                                kuadran 4
//                                float x = event.getX() / lapangan.getWidth() * courtWidth;
//                                float y = event.getY() / lapangan.getHeight() * courtHeight;

                                x = Math.max(0, Math.min(x, courtWidth));
                                y = Math.max(0, Math.min(y, courtHeight));

                                lastX = (int) x;
                                lastY = (int) y;

                                updateMarkerPosition(x, y);
                                updateCoordinateText(x, y);
                                if (lastX > 400 && lastY > 400){
                                    lastSentData = String.format("A %d %d", lastX, lastY);
                                }
                                break;

                            case MotionEvent.ACTION_UP:
                                // Keep sending position data even after touch is released
                                // isSendingPosition = false;
                                break;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in touch listener: " + e.getMessage(), e);
                    }
                    return true;
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up touch listener: " + e.getMessage(), e);
        }
    }

    private void updateMarkerPosition(float x, float y) {
        try {
            if (markerPoint != null) {
                int screenX = (int) ((courtWidth - x) / scaleX) - (markerPoint.getWidth() / 2);
                int screenY = (int) ((courtHeight - y) / scaleY) - (markerPoint.getHeight() / 2);
                markerPoint.setX(screenX);
                markerPoint.setY(screenY);
                markerPoint.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating marker position: " + e.getMessage(), e);
        }
    }

    private void updateCoordinateText(float x, float y) {
        try {
            String formattedX = String.format("%.2f", x);
            String formattedY = String.format("%.2f", y);
            runOnUiThread(() -> {
                if (coordinateText != null) {
                    coordinateText.setText("Koordinat: (" + formattedX + "mm, " + formattedY + "mm)");
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error updating coordinate text: " + e.getMessage(), e);
        }
    }

    private void initControllerDetection() {
        try {
            InputManager inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) {
                // Use handler from main thread to avoid potential issues
                inputManager.registerInputDeviceListener(new InputDeviceListenerImpl(), new Handler(Looper.getMainLooper()));
                // Check controller state after registration
                checkInitialControllerState();
            } else {
                Log.e(TAG, "InputManager is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing controller detection: " + e.getMessage(), e);
        }
    }

    private void checkInitialControllerState() {
        try {
            boolean hasControllers = !getGameControllerIds().isEmpty();
            updateControllerState(hasControllers);
        } catch (Exception e) {
            Log.e(TAG, "Error checking initial controller state: " + e.getMessage(), e);
        }
    }

    private void updateControllerState(boolean newState) {
        try {
            // Only update if state has changed
            if (isControllerActive == newState) return;

            isControllerActive = newState;

            // Update UI if controller disconnected
            if (!newState) {
                updateCoordinateText(lastX, lastY);
                updateMarkerPosition(lastX, lastY);
            }

            // Don't send controller connection status messages as they cause errors
            // Just log the state change locally
            Log.d(TAG, "Controller state updated: " + (newState ? "connected" : "disconnected"));

            // ConnectionManager doesn't need to know about controller connection state
            // We'll only send actual control data when using the controller
        } catch (Exception e) {
            Log.e(TAG, "Error updating controller state: " + e.getMessage(), e);
        }
    }

    private class InputDeviceListenerImpl implements InputManager.InputDeviceListener {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            try {
                InputDevice device = InputDevice.getDevice(deviceId);
                if (isGameController(device)) {
                    checkInitialControllerState();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onInputDeviceAdded: " + e.getMessage(), e);
            }
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            try {
                // For removed devices, we can't get the device info
                // Just check overall controller state
                checkInitialControllerState();
            } catch (Exception e) {
                Log.e(TAG, "Error in onInputDeviceRemoved: " + e.getMessage(), e);
            }
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            try {
                // Implement to handle device changes that might affect controller status
                InputDevice device = InputDevice.getDevice(deviceId);
                if (device != null) {
                    checkInitialControllerState();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in onInputDeviceChanged: " + e.getMessage(), e);
            }
        }
    }

    private boolean isGameController(InputDevice device) {
        if (device == null) return false;

        try {
            // Bitwise AND to check for gamepad or joystick sources
            int sources = device.getSources();
            return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                    ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
        } catch (Exception e) {
            Log.e(TAG, "Error checking if device is game controller: " + e.getMessage(), e);
            return false;
        }
    }

    public ArrayList<Integer> getGameControllerIds() {
        ArrayList<Integer> ids = new ArrayList<>();
        try {
            int[] deviceIds = InputDevice.getDeviceIds();
            if (deviceIds != null) {
                for (int deviceId : deviceIds) {
                    InputDevice device = InputDevice.getDevice(deviceId);
                    if (isGameController(device)) {
                        ids.add(deviceId);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting game controller IDs: " + e.getMessage(), e);
        }
        return ids;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        try {
            // Check if we're the active input source
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager == null ||
                    !connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_COURT)) {
                return super.dispatchKeyEvent(event);
            }

            InputDevice device = event.getDevice();
            // Hanya proses event ACTION_DOWN (tombol ditekan)
            if (device != null && isGamepadSource(device.getSources())) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    String buttonName = KeyEvent.keyCodeToString(event.getKeyCode());
                    String action = "";

                    // Tentukan action berdasarkan tombol yang ditekan
                    switch (buttonName) {
                        case "KEYCODE_BUTTON_X":
                            action = "S";
                            break;
                        case "KEYCODE_BUTTON_Y":
                            action = "G";
                            break;
                        case "KEYCODE_BUTTON_A":
                            action = "P";
                            break;
                        case "KEYCODE_BUTTON_B":
                            action = "D";
                            break;
                        case "KEYCODE_BUTTON_R1":
                            action = "C2";
                            break;
                        case "KEYCODE_BUTTON_L1":
                            action = "C1";
                            break;
                        case "KEYCODE_BUTTON_R2":
                            action = "R2";
                            break;
                        case "KEYCODE_BUTTON_L2":
                            action = "L2";
                            break;
                        default:
                            // Tombol lain tidak perlu diproses
                            return super.dispatchKeyEvent(event);
                    }

                    // Kirim action jika valid (tidak kosong)
                    if (!action.isEmpty()) {
                        isButtonPressed = true;
                        isSendingPosition = false;
                        isControllerActive = true;
                        lastButtonCommand = action;

                        if (connectionManager != null) {
                            connectionManager.sendData(action);
                        }
                        return true;
                    }
                }
                else if (event.getAction() == KeyEvent.ACTION_UP) {
                    // When button is released, still set as active controller but not pressing button
                    isButtonPressed = false;
                    lastButtonCommand = "";
                    isControllerActive = true;
                    isSendingPosition = false;
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in dispatchKeyEvent: " + e.getMessage(), e);
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        try {
            // Check if we're the active input source
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager == null ||
                    !connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_COURT)) {
                return super.onGenericMotionEvent(event);
            }

            int source = event.getSource();
            if ((source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                    (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
                isControllerActive = true;

                if (isControllerActive) {
                    isSendingPosition = false;
                    isButtonPressed = false;

                    // Read controller inputs with dead zone applied
                    dpadX = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_HAT_X), 0.1f);
                    dpadY = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_HAT_Y), 0.1f);
                    leftStickX = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_X), 0.05f);
                    leftStickY = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Y), 0.05f);
                    rightStickX = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_Z), 0.05f);
                    rightStickY = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_RZ), 0.05f);
                    leftTrigger = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), 0.05f);
                    rightTrigger = applyDeadZone(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), 0.05f);

                    // Handle D-pad input
                    handleDpadInput();


                    // Update stick values
                    flipy = -leftStickY;

                    // No need to set lastSentData here, we'll send in the runnable with live joystick values
                }
                return true;

            }

        } catch (Exception e) {
            Log.e(TAG, "Error in onGenericMotionEvent: " + e.getMessage(), e);
        }
        return super.onGenericMotionEvent(event);
    }

    private void handleDpadInput() {
        try {
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager != null && (dpadX != 0 || dpadY != 0)) {
                String direction = "";
                if (dpadX == 1) {
                    direction = "RT";
                    preset = true;
                } else if (dpadX == -1) {
                    direction = "LT";
                } else if (dpadY == 1) {
                    direction = "DW";
                } else if (dpadY == -1) {
                    direction = "UP";
                }

                if (!direction.isEmpty()) {
                    isButtonPressed = true;
                    lastButtonCommand = direction;
                    connectionManager.sendData(direction);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling D-pad input: " + e.getMessage(), e);
        }
    }

    private boolean isGamepadSource(int sources) {
        return ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) ||
                ((sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK);
    }

    private float applyDeadZone(float value, float threshold) {
        return (Math.abs(value) < threshold) ? 0.0f : value;
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            // Pause any playing sounds
            stopAllSounds();
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            connectionManager.sendData("RS");
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            Log.d(TAG, "ControlActivity onDestroy started");

            // Stop receiving data first
            isReceivingData.set(false);

            // Clean up threads with timeout handling to prevent hanging
            if (dataReceiverThread != null) {
                try {
                    dataReceiverThread.interrupt();
                    // Join with timeout to avoid blocking UI if thread doesn't respond
                    dataReceiverThread.join(500);
                } catch (Exception e) {
                    Log.e(TAG, "Error interrupting data receiver thread: " + e.getMessage(), e);
                }
                dataReceiverThread = null;
            }

            if (bluetoothCheckThread != null) {
                try {
                    bluetoothCheckThread.interrupt();
                    bluetoothCheckThread.join(500);
                } catch (Exception e) {
                    Log.e(TAG, "Error interrupting bluetooth thread: " + e.getMessage(), e);
                }
                bluetoothCheckThread = null;
            }

            if (wifiCheckThread != null) {
                try {
                    wifiCheckThread.interrupt();
                    wifiCheckThread.join(500);
                } catch (Exception e) {
                    Log.e(TAG, "Error interrupting wifi thread: " + e.getMessage(), e);
                }
                wifiCheckThread = null;
            }

            // Remove handler callbacks
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);  // Remove all callbacks
                handler = null;
            }

            // Release MediaPlayer resources
            for (Map.Entry<String, MediaPlayer> entry : soundPlayers.entrySet()) {
                MediaPlayer player = entry.getValue();
                if (player != null) {
                    try {
                        if (player.isPlaying()) {
                            player.stop();
                        }
                        player.release();
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MediaPlayer " + entry.getKey() + ": " + e.getMessage(), e);
                    }
                }
            }
            soundPlayers.clear();

            // Close ZMQ resources safely
            closeZmqResources();

            Log.d(TAG, "ControlActivity cleanup completed");
        } catch (Exception e) {
            Log.e(TAG, "Error in onDestroy: " + e.getMessage(), e);
        } finally {
            super.onDestroy();
        }
    }

    // Add a method to safely close ZMQ resources
    private void closeZmqResources() {
        try {
            if (pullSocket != null) {
                try {
                    // Setting linger to 0 ensures socket close doesn't block
                    pullSocket.setLinger(0);
                    pullSocket.close();
                    Log.d(TAG, "ZMQ pull socket closed");
                } catch (Exception e) {
                    Log.e(TAG, "Error closing ZMQ socket: " + e.getMessage(), e);
                } finally {
                    pullSocket = null;
                }
            }

            if (context != null) {
                try {
                    context.close();
                    Log.d(TAG, "ZMQ context closed");
                } catch (Exception e) {
                    Log.e(TAG, "Error closing ZMQ context: " + e.getMessage(), e);
                } finally {
                    context = null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in closeZmqResources: " + e.getMessage(), e);
        }
    }
}

