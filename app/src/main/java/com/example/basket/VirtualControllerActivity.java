package com.example.basket;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.concurrent.atomic.AtomicBoolean;

public class VirtualControllerActivity extends AppCompatActivity {
    private static final String TAG = "VirtualController";

    // UI Components
    private JoystickView leftJoystick;
    private JoystickView rightJoystick;
    private TextView buttonA, buttonB, buttonX, buttonY;
    private TextView buttonL1, buttonR1, buttonL2, buttonR2;
    private ImageButton dpadUp, dpadDown, dpadLeft, dpadRight;
    private TextView statusText;

    // Controller values
    private volatile float leftStickX = 0f, leftStickY = 0f, rightStickX = 0f;
    private boolean isButtonPressed = false;
    private String lastPressedButton = "";

    // Flag for active input
    private boolean isAnyInputActive = false;
// Same as ControlActivity (700ms)

    // Update handler
    private Handler handler;
    private Runnable continuousDataRunnable;
    private static final int DATA_UPDATE_INTERVAL = 50; // milliseconds

    private ZContext context;
    private ZMQ.Socket pullSocket;
    private Thread dataReceiverThread;
    private AtomicBoolean isReceivingData = new AtomicBoolean(true);
    private int last_data = -1; // Set to -1 to force first update

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_virtual_controller);

        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionManager != null) {
            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
        }
        connectionManager.sendData("RS");
        initializeViews();
        setupButtonListeners();
        setupJoysticks();
        startContinuousDataTransmission();

    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullScreen();
        }
    }

    private void setupFullScreen() {
        // For API 30+ (Android 11+): enable fullscreen experience with cutout support
        View decorView = getWindow().getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(flags);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Make sure this activity is set as the active input source when resumed
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionManager != null) {
            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
        }

        if (handler != null) {
            handler.post(continuousDataRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(continuousDataRunnable);
        }

        // Reset state to not send anything when paused
        isButtonPressed = false;
        lastPressedButton = "";
        leftStickX = 0f;
        leftStickY = 0f;
        rightStickX = 0f;
        isAnyInputActive = false;
    }

    @Override
    protected void onDestroy() {

        // Clean up ZMQ resources
        isReceivingData.set(false);
        if (dataReceiverThread != null) {
            dataReceiverThread.interrupt();
        }
        if (pullSocket != null) {
            try {
                pullSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing ZMQ socket: " + e.getMessage(), e);
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                Log.e(TAG, "Error closing ZMQ context: " + e.getMessage(), e);
            }
        }

        // Set the input source back to court when this activity is destroyed
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionManager != null) {
            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Clean up before leaving
        ConnectionManager connectionManager = ConnectionManager.getInstance();
        if (connectionManager != null) {
            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
        }

        // Reset state
        isButtonPressed = false;
        lastPressedButton = "";
        leftStickX = 0f;
        leftStickY = 0f;
        rightStickX = 0f;
        isAnyInputActive = false;

        super.onBackPressed();
    }

    private void initializeViews() {
        try {
            // Joysticks
            leftJoystick = findViewById(R.id.leftJoystick);
            rightJoystick = findViewById(R.id.rightJoystick);

            // Action buttons - using TextView instead of Button
            buttonA = findViewById(R.id.buttonA);
            buttonB = findViewById(R.id.buttonB);
            buttonX = findViewById(R.id.buttonX);
            buttonY = findViewById(R.id.buttonY);

            // Shoulder buttons - using TextView instead of Button
            buttonL1 = findViewById(R.id.buttonL1);
            buttonR1 = findViewById(R.id.buttonR1);
            buttonL2 = findViewById(R.id.buttonL2);
            buttonR2 = findViewById(R.id.buttonR2);

            // D-pad
            dpadUp = findViewById(R.id.dpadUp);
            dpadDown = findViewById(R.id.dpadDown);
            dpadLeft = findViewById(R.id.dpadLeft);
            dpadRight = findViewById(R.id.dpadRight);

            // Status text
            statusText = findViewById(R.id.statusText);
            // Update initial status text
            statusText.setText("Ready - T 0 0 0");

            // Back button
            ImageButton backButton = findViewById(R.id.backButton);
            if (backButton != null) {
                backButton.setOnClickListener(v -> {
                    // Clean up and set input source back
                    ConnectionManager connectionManager = ConnectionManager.getInstance();
                    if (connectionManager != null) {
                        connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_COURT);
                    }

                    // Reset button and joystick states
                    isButtonPressed = false;
                    lastPressedButton = "";
                    leftStickX = 0f;
                    leftStickY = 0f;
                    rightStickX = 0f;
                    isAnyInputActive = false;

                    finish();
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing views: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing controller UI", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupButtonListeners() {
        try {
            // Action Buttons - Same command format as physical controller
            setupActionButton(buttonA, "P");  // A button
            setupActionButton(buttonB, "D");  // B button
            setupActionButton(buttonX, "S");  // X button
            setupActionButton(buttonY, "G");  // Y button

            // Shoulder Buttons - Same command format as physical controller
            setupActionButton(buttonL1, "C1"); // L1 button
            setupActionButton(buttonR1, "C2"); // R1 button
            setupActionButton(buttonL2, "L2"); // L2 button
            setupActionButton(buttonR2, "R2"); // R2 button

            // D-pad - Same command format as physical controller
            setupActionButton(dpadUp, "UP");  // D-pad Up
            setupActionButton(dpadDown, "DW"); // D-pad Down
            setupActionButton(dpadLeft, "LT"); // D-pad Left
            setupActionButton(dpadRight, "RT"); // D-pad Right
        } catch (Exception e) {
            Log.e(TAG, "Error setting up button listeners: " + e.getMessage(), e);
        }
    }

    private void setupActionButton(View button, final String command) {
        if (button == null) {
            Log.e(TAG, "Button is null for command: " + command);
            return;
        }

        button.setOnTouchListener((v, event) -> {
            try {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Make sure we're the active input source
                        ConnectionManager connectionManager = ConnectionManager.getInstance();
                        if (connectionManager != null) {
                            connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
                        }

                        // Set the button as active
                        isButtonPressed = true;
                        lastPressedButton = command;
                        isAnyInputActive = true;

                        // Provide haptic feedback
                        vibrateShort();
                        v.setPressed(true);

                        // Update status
                        updateStatus("Active: " + command);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        isButtonPressed = false;
                        lastPressedButton = "";
                        v.setPressed(false);

                        // Check if joysticks are active
                        isAnyInputActive = (leftStickX != 0 || leftStickY != 0 || rightStickX != 0);

                        updateStatus(isAnyInputActive ? "Joystick active" : "Idle - T 0 0 0");
                        return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in button touch listener: " + e.getMessage(), e);
            }
            return false;
        });
    }

    private void setupJoysticks() {
        try {
            if (leftJoystick != null) {
                leftJoystick.setOnJoystickMoveListener((angle, power) -> {
                    // Make sure we're the active input source
                    ConnectionManager connectionManager = ConnectionManager.getInstance();
                    if (connectionManager != null) {
                        connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
                    }

                    // Convert angle and power to X,Y coordinates (-1 to 1)
                    leftStickX = calculateXValue(angle, power / 100f);
                    leftStickY = calculateYValue(angle, power / 100f);

                    // Update active state based on joystick position
                    isAnyInputActive = (leftStickX != 0 || leftStickY != 0 || rightStickX != 0 || isButtonPressed);

                    // Update status
                    if (isAnyInputActive && !isButtonPressed) {
                        updateStatus(String.format("Joystick: %.2f, %.2f, %.2f", leftStickX, -leftStickY, rightStickX));
                    }
                });
            } else {
                Log.e(TAG, "Left joystick is null");
            }

            if (rightJoystick != null) {
                rightJoystick.setOnJoystickMoveListener((angle, power) -> {
                    // Make sure we're the active input source
                    ConnectionManager connectionManager = ConnectionManager.getInstance();
                    if (connectionManager != null) {
                        connectionManager.setActiveInputSource(ConnectionManager.INPUT_SOURCE_VIRTUAL);
                    }

                    // For right stick, we only care about X axis for the format "T X Y Z"
                    rightStickX = calculateXValue(angle, power / 100f);

                    // Update active state based on joystick position
                    isAnyInputActive = (leftStickX != 0 || leftStickY != 0 || rightStickX != 0 || isButtonPressed);

                    // Update status
                    if (isAnyInputActive && !isButtonPressed) {
                        updateStatus(String.format("Joystick: %.2f, %.2f, %.2f", leftStickX, -leftStickY, rightStickX));
                    }
                });
            } else {
                Log.e(TAG, "Right joystick is null");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up joysticks: " + e.getMessage(), e);
        }
    }

    private float calculateXValue(float angle, float power) {
        return (float) (Math.cos(Math.toRadians(angle)) * power);
    }

    private float calculateYValue(float angle, float power) {
        return (float) (Math.sin(Math.toRadians(angle)) * power);
    }

    private void startContinuousDataTransmission() {
        handler = new Handler(Looper.getMainLooper());

        continuousDataRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    // Check if this activity is the active input source
                    ConnectionManager connectionManager = ConnectionManager.getInstance();
                    if (connectionManager != null &&
                            connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_VIRTUAL) &&
                            !isFinishing()) {

                        // Always send data in every cycle
                        if (isButtonPressed && lastPressedButton != null && !lastPressedButton.isEmpty()) {
                            sendCommand(lastPressedButton);
                        } else if (leftStickX != 0 || leftStickY != 0 || rightStickX != 0) {
                            float flipY = -leftStickY;
                            String joystickCommand = String.format("T %.2f %.2f %.2f", leftStickX, flipY, rightStickX);
                            sendCommand(joystickCommand);
                        } else {
                            sendCommand("T 0 0 0");
                        }
                    }

                    // Always schedule the next update, ensuring continuous data flow
                    handler.postDelayed(this, DATA_UPDATE_INTERVAL);
                } catch (Exception e) {
                    Log.e(TAG, "Error in continuous data transmission: " + e.getMessage(), e);
                    // Even if there's an error, try to continue
                    handler.postDelayed(this, DATA_UPDATE_INTERVAL);
                }
            }
        };

        // Start the continuous data transmission
        handler.post(continuousDataRunnable);
    }

    private void sendCommand(String command) {
        try {
            ConnectionManager connectionManager = ConnectionManager.getInstance();
            if (connectionManager != null &&
                    connectionManager.isAnyConnectionActive() &&
                    connectionManager.isInputSourceActive(ConnectionManager.INPUT_SOURCE_VIRTUAL) &&
                    !isFinishing()) {

                connectionManager.sendData(command);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending command: " + e.getMessage(), e);
        }
    }

    private void updateStatus(String message) {
        try {
            if (statusText != null && !isFinishing()) {
                runOnUiThread(() -> {
                    statusText.setText(message);
                    if (message.contains("Active")) {
                        statusText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                    } else if (message.contains("Joystick")) {
                        statusText.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                    } else {
                        statusText.setTextColor(getResources().getColor(android.R.color.white));
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating status: " + e.getMessage(), e);
        }
    }

    private void vibrateShort() {
        try {
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(20); // Short vibration
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during vibration: " + e.getMessage(), e);
        }
    }

}
