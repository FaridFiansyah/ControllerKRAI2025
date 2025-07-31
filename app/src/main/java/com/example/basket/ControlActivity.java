package com.example.basket;

import com.github.chrisbanes.photoview.PhotoView;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Vibrator;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

import android.graphics.Matrix;
import android.graphics.RectF;


//metode udp


public class ControlActivity extends AppCompatActivity {
    private static final String TAG = "ControlActivity";
    private float scaleX, scaleY;
    private static final float courtWidth = 15000f;
    private static final float courtHeight = 8000f;
    private int lastX = 0, lastY = 0;
    private int angle = 34, rpm = 1000;
    private boolean isControllerActive = false;
    private float dpadX, dpadY, leftStickX, leftStickY;
    private float rightStickX, rightStickY, leftTrigger, rightTrigger;
    private ImageView markerPoint;
    private FrameLayout courtContainer;
    private boolean isSendingPosition = false;
    private TextView coordinateText;
    private TextView rpmstatus;
    private TextView anglestatus;
    private TextView connectionStatusText;
    private ImageView lapangan;
    private Handler handler;
    private Runnable statusUpdateRunnable;
    boolean wasJoystickMoving = true;
    boolean wasDpadPressed = false;
    private Runnable dataUpdateRunnable;
    private static final int STATUS_UPDATE_INTERVAL = 500; // 500ms for status updates
    private static final int DATA_UPDATE_INTERVAL = 50;   // 50ms for data updates
    private String lastSentData = null;
    private long lastDataSentTime = 0;
    private ZContext context;
    private ZMQ.Socket pullSocket;
    private double last_data = -1; // Set to -1 to force first update
    private float flipy;
    private ImageButton virtualControllerButton; // Button for virtual controller


    //udp
    private static final String TAG_UDP = "UDPStream";
    private static final int PORT = 5005;
    private boolean isReceiving = false;
    private DatagramSocket socket;
    private Thread receiverThread;
    private Paint gridPaint;
    private Paint crosshairPaint;
    private Paint tebalGridPaint;

    //batre
//    private TextView batteryText;
//    private Runnable batteryUpdateRunnable;
//    private ProgressBar batteryBar;
//    private volatile double batre = 22.70;

    //batre kedip
    private boolean isBlinking = false;
    private Handler blinkHandler = new Handler();
    private int blinkInterval = 500;

    //dial
    private double persenfloat;
    private DialView dialView;
    private volatile float yaw = 0f;
    private AtomicBoolean isDialAdjusted = new AtomicBoolean(false);


    //ip camera
    private String addreszmq;
    private String ip;

    //stream foto
    private OkHttpClient client = new OkHttpClient();
    private Runnable fetchImageRunnable;
    private boolean isStreaming = false;

    //mode switch
    private boolean isCameraMode = true;
//    private ImageView imageView;

    // Flag to track if returning from virtual controller
    private boolean returningFromVirtualController = false;
    private boolean isButtonPressed = false;
    private String lastButtonCommand = "";

    // Map to manage sound resources
    private Map<String, MediaPlayer> soundPlayers = new HashMap<>();

    // Flags for sound states
    private volatile boolean isPercentageSoundPlaying = false;

    // Thread control
    private AtomicBoolean isReceivingData = new AtomicBoolean(true);
    private Thread dataReceiverThread;
    private Thread bluetoothCheckThread;
    private Thread wifiCheckThread;
    private int persen;

    private PhotoView imageView;
    private float currentZoom = 1.0f;
    private RectF currentViewRect;
    private boolean isZoomInitialized = false;

    //preset
    private TextView tpr1;
    private TextView tpr2;
    private TextView tpr3;
    private TextView tpr4;
    private TextView tpr5;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);
        handler = new Handler(Looper.getMainLooper());

        //penerimaan ip
        addreszmq = ConnectionManager.getInstance().currentZmqAddress;
        if (addreszmq != null && !addreszmq.isEmpty()) {
            //mengambil IP dari format "tcp://ip:port"
            String[] parts = addreszmq.split("//");
            if (parts.length > 1) {
                String ipAndPort = parts[1];
                ip = ipAndPort.split(":")[0]; // Ambil IP nya saja
            }
        }

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
//            setupTouchListener();
            initControllerDetection();
//            initializeSounds();


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
    private void initializePaints() {
        gridPaint = new Paint();
        gridPaint.setColor(Color.GREEN);
        gridPaint.setStrokeWidth(2);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setAlpha(128); // Semi-transparent

        crosshairPaint = new Paint();
        crosshairPaint.setColor(Color.RED);
        crosshairPaint.setStrokeWidth(4);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        // Paint untuk garis grid tebal (biru)
        tebalGridPaint = new Paint();
        tebalGridPaint.setColor(Color.GREEN); // Warna berbeda untuk garis tebal
        tebalGridPaint.setStrokeWidth(7);    // Lebih tebal dari gridPaint
        tebalGridPaint.setStyle(Paint.Style.STROKE);
        tebalGridPaint.setAlpha(128);
    }
    private void resetZoom() {
        try {
            if (imageView != null) {
                runOnUiThread(() -> {
                    try {
                        imageView.setScale(1.0f, true); // smooth animation
                        currentZoom = 1.0f;
                    } catch (Exception e) {
                        Log.e(TAG, "Error resetting zoom: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in resetZoom: " + e.getMessage());
        }
    }

    private void setupImageViewGestures() {
        if (imageView != null) {
            // Enable panning/dragging
            imageView.setOnMatrixChangeListener(rect -> {
                // Save current view rect for maintaining position during updates
                currentViewRect = rect;
            });

            // Set minimum and maximum scale
            imageView.setMinimumScale(1.0f);
            imageView.setMaximumScale(5.0f);
            imageView.setMediumScale(2.5f);

            // Enable panning when zoomed
            imageView.setAllowParentInterceptOnEdge(true);

            // Optional: add custom touch listener if needed
            imageView.setOnViewTapListener((view, x, y) -> {
                // Handle single tap if needed
            });
        }
    }


    //udp
    private void startUdpReceiver(String serverIP, int port) {
        if (isReceiving) return;
        isReceiving = true;
        initializePaints();
        Toast.makeText(getApplicationContext(), "IP : " + serverIP + " port : " + port, Toast.LENGTH_SHORT).show();

        receiverThread = new Thread(() -> {
            try {
                socket = new DatagramSocket();
                InetAddress serverAddr = InetAddress.getByName(serverIP);

                byte[] startMessage = "start".getBytes();
                DatagramPacket startPacket = new DatagramPacket(
                        startMessage,
                        startMessage.length,
                        serverAddr,
                        port
                );
                socket.send(startPacket);
                Log.d(TAG, "Sent start message to server");

                byte[] buffer = new byte[65535];

                while (isReceiving) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    String base64Image = new String(packet.getData(), 0, packet.getLength());
                    byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

                    if (bitmap != null) {
                        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas canvas = new Canvas(mutableBitmap);

                        int width = mutableBitmap.getWidth();
                        int height = mutableBitmap.getHeight();

                        final int NUM_VERTICAL_DIVISIONS = 10;
                        final int NUM_HORIZONTAL_DIVISIONS = 8;

                        float verticalSpacing = (float) width / NUM_VERTICAL_DIVISIONS;
                        float horizontalSpacing = (float) height / NUM_HORIZONTAL_DIVISIONS;

                        // Gambar garis-garis vertikal
                        for (int i = 0; i <= NUM_VERTICAL_DIVISIONS; i++) {
                            float x = i * verticalSpacing;
                            if (x >= width) {
                                x = width - 1;
                            }

                            // Cek apakah indeks garis genap
                            if (i % 2 == 0) {
                                // Jika genap, gambar garis tebal berwarna biru
                                canvas.drawLine(x, 0, x, height, tebalGridPaint);
                            } else {
                                // Jika ganjil, gambar garis normal berwarna hijau
                                canvas.drawLine(x, 0, x, height, gridPaint);
                            }
                        }

                        // Gambar garis-garis horizontal
                        for (int i = 0; i <= NUM_HORIZONTAL_DIVISIONS; i++) {
                            float y = i * horizontalSpacing;
                            if (y >= height) {
                                y = height - 1;
                            }

                            // Cek apakah indeks garis genap
                            if (i % 2 == 0) {
                                // Jika genap, gambar garis tebal berwarna biru
                                canvas.drawLine(0, y, width, y, tebalGridPaint);
                            } else {
                                // Jika ganjil, gambar garis normal berwarna hijau
                                canvas.drawLine(0, y, width, y, gridPaint);
                            }
                        }

                        int centerX = width / 2;
                        int centerY = height / 2;
                        int crosshairSize = 20;

                        canvas.drawLine(centerX - crosshairSize, centerY,
                                centerX + crosshairSize, centerY, crosshairPaint);
                        canvas.drawLine(centerX, centerY - crosshairSize,
                                centerX, centerY + crosshairSize, crosshairPaint);

                        runOnUiThread(() -> {
                            try {
                                if (imageView != null) {
                                    // Save current zoom and view state
                                    currentZoom = Math.max(1.0f, Math.min(imageView.getScale(), 5.0f));
                                    currentViewRect = imageView.getDisplayRect();
                                }
                                if (mutableBitmap != null && imageView != null) {
                                    // Update bitmap while maintaining zoom state
                                    imageView.setImageBitmap(mutableBitmap);
                                    imageView.setScale(currentZoom);

                                    // Apply zoom with bounds checking
                                    float targetZoom = Math.max(1.0f, Math.min(currentZoom, 5.0f));
                                    imageView.setScale(targetZoom);

                                    // Initialize zoom limits once
                                    if (!isZoomInitialized) {
                                        imageView.setMinimumScale(1.0f);
                                        imageView.setMaximumScale(5.0f);
                                        imageView.setMediumScale(2.5f);
                                        isZoomInitialized = true;
                                    }


                                    imageView.post(() -> {
                                        if (isCameraMode && !isDialAdjusted.getAndSet(true)) {
                                            adjustDialToCameraView();
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating image view: " + e.getMessage());
                            }
                        });

                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                isReceiving = false;
            }
        });

        receiverThread.start();
    }

    private void stopUdpReceiver() {
        isReceiving = false;

        if (socket != null) {
            socket.close();
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

//    private void initializeSounds() {
//        try {
//            // Pre-load all sound resources
//            soundPlayers.put("xp", MediaPlayer.create(this, R.raw.xpass));
//
//
//            // Set completion listeners
//            for (Map.Entry<String, MediaPlayer> entry : soundPlayers.entrySet()) {
//                if (entry.getValue() != null) {
//                    entry.getValue().setOnCompletionListener(mp -> {
//                        isPercentageSoundPlaying = false;
//                    });
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error initializing sounds: " + e.getMessage(), e);
//        }
//    }
//
//    private void playSound() {
//        try {
//            MediaPlayer player = soundPlayers.get("xp");
//            if (player != null && !player.isPlaying()) {
//                // Reset and start the player
//                player.seekTo(0);
//                player.start();
//
//                // Set volume for specific sounds if needed
//                player.setVolume(500, 500);
//
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error playing sound " + "xp" + ": " + e.getMessage(), e);
//        }
//    }
//
//    private void stopSound() {
//        try {
//            MediaPlayer player = soundPlayers.get("xp");
//            if (player != null && player.isPlaying()) {
//                player.pause();
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error stopping sound " + "xp" + ": " + e.getMessage(), e);
//        }
//    }

    private void initializeViews() {
        try {
            courtContainer = findViewById(R.id.courtContainer);
            coordinateText = findViewById(R.id.coordinateText);
            View buttonContainer = findViewById(R.id.buttonContainer);
            connectionStatusText = findViewById(R.id.connectionStatusText);
            rpmstatus = findViewById(R.id.textrpm);
            anglestatus = findViewById(R.id.textangle);
            lapangan = findViewById(R.id.lap);
            imageView = findViewById(R.id.imageView);
            imageView.setVisibility(View.GONE);
//            batteryText = findViewById(R.id.battxt);
//            batteryBar = findViewById(R.id.battbarr);
            dialView = findViewById(R.id.dial_view);
            buttonContainer.setVisibility(View.GONE);

            setupImageViewGestures();
//            setupBackButton();
            // Set visibility awal
            imageView.setVisibility(View.VISIBLE);
            lapangan.setVisibility(View.GONE);
            dialView.setVisibility(View.VISIBLE);

            // Sembunyikan coordinate text dan connection status
            if (coordinateText != null) {
                coordinateText.setVisibility(View.GONE);
            }
            if (connectionStatusText != null) {
                connectionStatusText.setVisibility(View.GONE);
            }
            if (markerPoint != null) {
                markerPoint.setVisibility(View.GONE);
            }

            // Mulai UDP receiver
            if (ip != null && !ip.isEmpty()) {
                startUdpReceiver(ip, PORT);
            }

            // Initialize virtual controller button
            virtualControllerButton = findViewById(R.id.virtualControllerButton);
            if (virtualControllerButton != null) {
                virtualControllerButton.setOnClickListener(v -> openVirtualController());
            }

            ImageButton modeSwitchButton = findViewById(R.id.buttonmode);
            if (modeSwitchButton != null) {
                modeSwitchButton.setOnClickListener(v -> toggleMode());
            }

            // Initialize marker point
            markerPoint = new ImageView(this);
            markerPoint.setBackground(getResources().getDrawable(R.drawable.circle_marker));
            int pointSize = 24;
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(pointSize, pointSize);
            markerPoint.setLayoutParams(params);
            markerPoint.setVisibility(View.INVISIBLE);
            courtContainer.addView(markerPoint);

//             Setup back button
            ImageButton backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                backButton.setOnClickListener(view -> finish());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
        }
    }

//    @Override
//    public void onBackPressed() {
//        try {
//            if (!isCameraMode) {
//                // If in map mode, switch to camera mode first
//                toggleMode(); // This will switch to camera mode
//                return; // Don't exit yet
//            }
//
//            // If already in camera mode, cleanup and exit
//            cleanupAndReturnToMain();
//            super.onBackPressed();
//        } catch (Exception e) {
//            Log.e(TAG, "Error in onBackPressed: " + e.getMessage());
//            super.onBackPressed();
//        }
//    }

//    private void cleanupAndReturnToMain() {
//        try {
//
//            stopBlinking();
//            stopAllSounds();

            // Reset camera mode flags

//            ConnectionManager connectionManager = ConnectionManager.getInstance();
//            if (connectionManager != null) {
//                // Wait briefly for reset command to be sent
//                Thread.sleep(100);
//                connectionManager.cleanup();
//            }

            // Return to MainActivity
//            Intent intent = new Intent(this, MainActivity.class);
//            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
//            startActivity(intent);
//            finish();
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error during cleanup: " + e.getMessage());
//            // Force return to MainActivity even if cleanup fails
//            startActivity(new Intent(this, MainActivity.class));
//            finish();
//        }
//    }

//    private void setupBackButton() {
//        ImageButton backButton = findViewById(R.id.backButton);
//        if (backButton != null) {
//            backButton.setOnClickListener(v -> {
//                if (!isCameraMode) {
//                    toggleMode(); // Switch to camera mode
//                } else {
//                    cleanupAndReturnToMain();
//                    finish();
//                }
//            });
//        }
//    }

    private void adjustDialToCameraView() {
        // Guard clause, pastikan semua view ada dan gambar sudah dimuat
        if (imageView == null || imageView.getDrawable() == null || dialView == null) {
            return;
        }

        Matrix imageMatrix = imageView.getImageMatrix();

        RectF drawableRect = new RectF(0, 0,
                imageView.getDrawable().getIntrinsicWidth(),
                imageView.getDrawable().getIntrinsicHeight());


        imageMatrix.mapRect(drawableRect);

        int finalDialWidth = (int) drawableRect.width();
        int finalLeftMargin = (int) drawableRect.left;


        // margin atas  (dalam dp)
        int topMarginDp = 5;
        int topMarginPx = (int) (topMarginDp * getResources().getDisplayMetrics().density);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dialView.getLayoutParams();

        params.width = finalDialWidth;
        params.leftMargin = finalLeftMargin;
        params.topMargin = topMarginPx;
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;

        dialView.setLayoutParams(params);
        dialView.setYaw(yaw);
        dialView.setVisibility(View.VISIBLE);
    }

    //toggleswitch
    private void toggleMode() {
        isCameraMode = !isCameraMode;
        View buttonContainer = findViewById(R.id.buttonContainer);

        if (isCameraMode) {
            // Switch to camera mode
            lapangan.setVisibility(View.GONE);
            imageView.setVisibility(View.VISIBLE); // Tampilkan ImageView untuk RTSP stream
            dialView.setVisibility(View.VISIBLE);
            buttonContainer.setVisibility(View.GONE);

//
            startUdpReceiver(ip,PORT);
            lapangan.setOnTouchListener(null);// Disable touch listener for court

            if (coordinateText != null) {
                coordinateText.setVisibility(View.GONE);
            }
            if (connectionStatusText != null) {
                connectionStatusText.setVisibility(View.GONE);
            }
            if(markerPoint != null){
                markerPoint.setVisibility(View.GONE);
            }



        } else {
            // Switch to court mode
            preset();
            imageView.setVisibility(View.GONE);
            stopImageStreaming();// Stop camera
            lapangan.setVisibility(View.VISIBLE);
            dialView.setVisibility(View.GONE);
            buttonContainer.setVisibility(View.VISIBLE);
//            setupTouchListener(); // Re-enable touch listener for court
            stopUdpReceiver();

            if (lapangan != null) {
                lapangan.post(() -> {
                    scaleX = courtWidth / lapangan.getWidth();
                    scaleY = courtHeight / lapangan.getHeight();
                });
            }
            updateCoordinateText(0, 0);
            if (coordinateText != null) {
                coordinateText.setVisibility(View.GONE);
            }
            if (connectionStatusText != null) {
                connectionStatusText.setVisibility(View.GONE);
            }
        }
    }

    private void preset(){
        tpr1 = findViewById(R.id.tpr1);
        tpr2 = findViewById(R.id.tpr2);
        tpr3 = findViewById(R.id.tpr3);
        tpr4 = findViewById(R.id.tpr4);

        ImageButton pr1 = findViewById(R.id.buton1);
        if (pr1 != null) {
            pr1.setOnClickListener(v -> {
//                ConnectionManager connectionManager = ConnectionManager.getInstance();
                rpm = 2700;
                rpmstatus.setText("RPM : " + rpm);
//                connectionManager.sendData("S "+rpm);
            });
        }
        ImageButton pr2 = findViewById(R.id.buton2);
        if (pr2 != null) {
            pr2.setOnClickListener(v -> {
//                ConnectionManager connectionManager = ConnectionManager.getInstance();
                rpm = 2900;
                rpmstatus.setText("RPM : " + rpm);
//                connectionManager.sendData("S "+rpm);

            });
        }
        ImageButton pr3 = findViewById(R.id.buton3);
        if (pr3 != null) {
            pr3.setOnClickListener(v -> {
//                ConnectionManager connectionManager = ConnectionManager.getInstance();
                rpm = 3200;
                rpmstatus.setText("RPM : " + rpm);
//                connectionManager.sendData("S "+rpm);

            });
        }
        ImageButton pr4 = findViewById(R.id.buton4);
        if (pr4 != null) {
            pr4.setOnClickListener(v -> {
//                ConnectionManager connectionManager = ConnectionManager.getInstance();
                rpm = 3400;
                rpmstatus.setText("RPM : " + rpm);
//                connectionManager.sendData("S "+rpm);
            });
        }
    }

    private void startImageStreaming(String url) {
        Log.d(TAG, "Starting image stream from: " + url);
        isStreaming = true; // Set status streaming
        fetchImageRunnable = new Runnable() {
            @Override
            public void run() {
                Request request = new Request.Builder().url(url).build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "Stream request failed: " + e.getMessage());
                        // Anda bisa menambahkan logika untuk mencoba lagi di sini
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "Stream response unsuccessful: " + response.code());
                            return;
                        }

                        try {
                            String base64Image = response.body().string();
                            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                            if (decodedByte != null) {
                                handler.post(() -> imageView.setImageBitmap(decodedByte));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error processing frame: " + e.getMessage());
                        }
                    }
                });

                // Loop untuk frame berikutnya
                if(isStreaming) {
                    handler.postDelayed(this, 50); // Target ~20 FPS
                }
            }
        };

        handler.post(fetchImageRunnable); // Memulai loop
    }

    private void stopImageStreaming() {
        if (fetchImageRunnable != null) {
            handler.removeCallbacks(fetchImageRunnable);
        }
        isStreaming = false; // Set status berhenti
        Log.d(TAG, "Image stream stopped.");
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
                                    markerPoint.setVisibility(View.INVISIBLE);
                                    wasJoystickMoving = true;
                                    wasDpadPressed = false;
                                    lastDataSentTime = System.currentTimeMillis();
                                } else if (wasDpadPressed) {
                                    connectionManager.sendData("");
//                                wasDpadPressed = false;
                                    lastDataSentTime = System.currentTimeMillis();
                                } else {
                                    if (wasJoystickMoving) {
                                        connectionManager.sendData("T 0 0 0");
                                        markerPoint.setVisibility(View.INVISIBLE);
//                                        wasJoystickMoving = false;
                                        lastDataSentTime = System.currentTimeMillis();
                                    }
                                }
                            } else {
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

            // Battery update runnable
//            batteryUpdateRunnable = new Runnable() {
//                @Override
//                public void run() {
//                    try {
//                        updateBatteryStatus();
//                        handler.postDelayed(this, 700);
//                    } catch (Exception e) {
//                        Log.e(TAG, "Error in battery update: " + e.getMessage(), e);
//                    }
//                }
//            };

            // Start all handlers
            handler.post(statusUpdateRunnable);
            handler.post(dataUpdateRunnable);
//            handler.post(batteryUpdateRunnable);


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
                    byte[] message = pullSocket.recv(ZMQ.DONTWAIT);
                    if (message != null) {
                        String receivedText = new String(message, ZMQ.CHARSET);
                        Log.d(TAG, "RECEIVE: " + receivedText);

                        // Split message jika ada spasi
                        String[] parts = receivedText.split("\\s+");

                        try {
//                            if (parts[0].equalsIgnoreCase("XP")){
////                                playSound();
//                            }
//                            if (parts.length > 0) {
                                // Cek identifikasi pesan
//                                if (parts[0].equals("V")) {
//                                    // Ini adalah data battery voltage
//                                    float newBattery = Float.parseFloat(parts[1]);
//                                    if (newBattery >= 22.70 && newBattery <= 25.20) {
//                                        batre = newBattery;
//                                        Log.d(TAG, "Battery value updated to: " + batre);
//                                    }
//                                }
                                if (parts[0].equals("YAW")) {
                                    // Ini adalah data YAW
                                    if (parts.length >= 2) {
                                        float newyaw = Float.parseFloat(parts[1]);
                                        if(newyaw >= 0.0f && newyaw <= 360.0f) {
                                            yaw = newyaw;
                                            Log.d(TAG, String.format("YAW: %f", yaw));
                                            // Update variables sesuai kebutuhan
                                            // Update dial di UI thread
                                            if (isCameraMode) {
                                                runOnUiThread(() -> {
                                                    if (dialView != null) {
                                                        dialView.setYaw(yaw);
                                                    }
                                                });
                                            }
                                        }
                                    }
                                }
                                else if(parts[0].equals('0')){
                                    Log.d(TAG, String.format("data sampah"));
                                }
                                else{
//                                    stopSound();
                                }
                                // Tambahkan identifikasi lain sesuai kebutuhan
//                            }
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Error parsing data: " + receivedText + " - " + e.getMessage());
                        }
                    }
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Error receiving data: " + e.getMessage());
                    Thread.sleep(500);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Fatal error in receive data thread: " + e.getMessage());
        }
    }

//    private void updateBatteryStatus() {
//        try {
//            // Use synchronized to prevent concurrent access issues
//            synchronized (this) {
//                // Check if battery level changed
//                boolean batteryLevelChanged = (last_data != batre);
//
//                // Update battery UI
//                updateBatteryUI(batre);
//
//                // Log the battery value for debugging
//                if (batteryLevelChanged) {
//                    Log.d(TAG, "Battery level updated to: " + batre + "%");
//                }
//
//                // Handle sound notifications based on battery level
//                // updateBatterySounds(batre);
//
//                // Store current level for next comparison
//                last_data = batre;
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Error updating battery status: " + e.getMessage(), e);
//        }
//    }

//    private void updateBatteryUI(final double level) {
//        try {
//            if (level < 22.70 || level > 25.20) {
//                Log.w(TAG, "Invalid battery level: " + level);
//                return;
//            }
//
//            runOnUiThread(() -> {
//                try {
//                    if (batteryBar != null) {
//                        // Calculate percentage
//                        persenfloat = (((level - 22.70)/2.50)*100);
//                        persen = (int) Math.round(persenfloat);
//                        batteryBar.setProgress(persen);
//
//                        // Set colors and start/stop blinking based on level
//                        if (level < 23.30) {
//                            // Low battery - blink red
//                            startBlinking(getResources().getColor(R.color.low_color),
//                                    Color.TRANSPARENT);
//                        }
//                        else if (level < 24.4) {
//                            // Medium battery - blink yellow
//                            startBlinking(getResources().getColor(R.color.med_color),
//                                    Color.TRANSPARENT);
//                        }
//                        else {
//                            // High battery - solid green, stop blinking
//                            stopBlinking();
//                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                                batteryBar.setProgressTintList(ColorStateList.valueOf(
//                                        getResources().getColor(R.color.high_color)));
//                            }
//                        }
//                    }
//
//                    if (batteryText != null) {
//                        batteryText.setText(String.format("%.2f V", level));
//                    }
//
//                } catch (Exception e) {
//                    Log.e(TAG, "Error in battery UI update: " + e.getMessage());
//                }
//            });
//        } catch (Exception e) {
//            Log.e(TAG, "Error updating battery UI: " + e.getMessage());
//        }
//    }

//    private void startBlinking(final int colorOn, final int colorOff) {
//        if (isBlinking) return;
//        isBlinking = true;
//
//        final Runnable blink = new Runnable() {
//            boolean isColorOn = true;
//
//            @Override
//            public void run() {
//                if (!isBlinking) return;
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//                    batteryBar.setProgressTintList(ColorStateList.valueOf(
//                            isColorOn ? colorOn : colorOff));
//                }
//                isColorOn = !isColorOn;
//                blinkHandler.postDelayed(this, blinkInterval);
//            }
//        };
//
//        blinkHandler.post(blink);
//    }

//    private void stopBlinking() {
//        isBlinking = false;
//        blinkHandler.removeCallbacksAndMessages(null);
//    }


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

                                x = Math.max(0, Math.min(x, courtWidth));
                                y = Math.max(0, Math.min(y, courtHeight));

                                lastX = (int) x;
                                lastY = (int) y;

                                updateMarkerPosition(x, y);
                                updateCoordinateText(x, y);
                                if (lastX > 400 && lastY > 400) {
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
                            action = "S " + rpm;
                            angle = 34;
                            anglestatus.setText("Angle : " + angle);
                            break;
                        case "KEYCODE_BUTTON_START":
                            action = "R45";
                            break;
                        case "KEYCODE_BUTTON_SELECT":
                            action = "L45";
                            break;
                        case "KEYCODE_BUTTON_Y":
                            action = "G";
                            toggleMode();
                            break;
                        case "KEYCODE_BUTTON_A":
                            action = "P";
                            break;
                        case "KEYCODE_BUTTON_B":
                            action = "D";
                            angle = 54;
                            anglestatus.setText("Angle : " + angle);
                            break;
                        case "KEYCODE_BUTTON_R1":
                            if (rpm < 6000) {
                                rpm = rpm + 100;
                            } else {
                                rpm = 6000;
                            }
                            rpmstatus.setText("RPM : " + rpm);
                            //                            Toast.makeText(this, "RPM : " + rpm, Toast.LENGTH_SHORT).show();
                            break;
                        case "KEYCODE_BUTTON_L1":
//                            if (angle < 56) {
//                                angle = angle + 1;
//                            } else {
//                                angle = 56;
//                            }
//                            Toast.makeText(this, "Angle : " + angle, Toast.LENGTH_SHORT).show();
//                            anglestatus.setText("Angle : " + angle);
                            action = "L1";
                            break;
                        case "KEYCODE_BUTTON_R2":
                            if (rpm > 1000) {
                                rpm = rpm - 100;
                            } else {
                                rpm = 1000;
                            }
                            rpmstatus.setText("RPM : " + rpm);
                            // Toast.makeText(this, "RPM : " + angle, Toast.LENGTH_SHORT).show();
                            break;
                        case "KEYCODE_BUTTON_L2":
//                            if (angle > 34) {
//                                angle = angle - 1;
//                            } else {
//                                angle = 34;
//                            }
//                            anglestatus.setText("Angle : " + angle);
                            // Toast.makeText(this, "Angle : " + angle, Toast.LENGTH_SHORT).show();
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
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
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
        stopImageStreaming();
        stopUdpReceiver();
        try {
            // Pause any playing sounds
            stopAllSounds();
        } catch (Exception e) {
            Log.e(TAG, "Error in onPause: " + e.getMessage(), e);
        }
    }

    @Override
    protected void onDestroy() {
        stopImageStreaming();
        stopUdpReceiver();
//        stopBlinking();
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

            if (bluetoothCheckThread != null && bluetoothCheckThread.isAlive()) {
                try {
                    bluetoothCheckThread.interrupt();
                    bluetoothCheckThread.join(500);
                } catch (Exception e) {
                    Log.e(TAG, "Error interrupting bluetooth thread: " + e.getMessage(), e);
                }
                bluetoothCheckThread = null;
            }

            if (wifiCheckThread != null && wifiCheckThread.isAlive()) {
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
            Log.e(TAG, "Error in closeZmqResources: " + e);
        }
    }
}