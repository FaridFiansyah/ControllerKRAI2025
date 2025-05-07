package com.example.basket;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectionManager {
    private static final String TAG = "ConnectionManager";
    private static ConnectionManager instance;
    private static final long RECONNECT_DELAY = 5000; // 5 seconds
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    // Input source tracking (NEW)
    public static final int INPUT_SOURCE_COURT = 1;
    public static final int INPUT_SOURCE_VIRTUAL = 2;
    private int activeInputSource = INPUT_SOURCE_COURT;

    // UUID for Serial Port Profile (SPP)
    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // ZeroMQ components
    private ZContext zContext;
    private ZMQ.Socket zmqSocket;
    private boolean isZmqConnected = false;
    public String currentZmqAddress;
    private final AtomicBoolean shouldReconnectZmq = new AtomicBoolean(false);
    private Thread zmqReconnectThread;
    private int zmqReconnectAttempts = 0;

    // Bluetooth components
    private BluetoothSocket bluetoothSocket;
    private OutputStream bluetoothOutputStream;
    private boolean isBluetoothConnected = false;
    private String currentBluetoothAddress;
    private final AtomicBoolean shouldReconnectBluetooth = new AtomicBoolean(false);
    private Thread bluetoothReconnectThread;
    private int bluetoothReconnectAttempts = 0;

    // Context and handlers
    private Context context;
    private final Handler mainHandler;
    private WifiManager wifiManager;

    // Connection state listener
    private ConnectionStateListener connectionStateListener;

    // Battery tracking variable
    private volatile int batre;

    public interface ConnectionStateListener {
        void onConnectionStateChanged(boolean isZmqConnected, boolean isBluetoothConnected);

        void onConnectionError(String error);
    }

    private ConnectionManager() {
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static ConnectionManager getInstance() {
        if (instance == null) {
            synchronized (ConnectionManager.class) {
                if (instance == null) {
                    instance = new ConnectionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Gets the current battery level
     * @return current battery level (0-100)
     */
    public int getBatteryLevel() {
        return batre;
    }

    // NEW: Methods to control active input source
    public void setActiveInputSource(int source) {
        if (source == INPUT_SOURCE_COURT || source == INPUT_SOURCE_VIRTUAL) {
            this.activeInputSource = source;
            Log.d(TAG, "Active input source set to: " + (source == INPUT_SOURCE_COURT ? "Court" : "Virtual Controller"));
        }
    }

    public int getActiveInputSource() {
        return activeInputSource;
    }

    public boolean isInputSourceActive(int source) {
        return activeInputSource == source;
    }

    public void checkZmqConnection() {
        if (zmqSocket != null && isZmqConnected) {
            try {
                boolean sent = zmqSocket.send("PING".getBytes(), ZMQ.DONTWAIT);
                if (!sent) {
                    Log.w(TAG, "ZMQ connection may be stale - failed to send heartbeat");
                    setZmqConnected(true);
                    notifyConnectionStateChanged();
                }
            } catch (Exception e) {
                Log.e(TAG, "ZMQ connection error: " + e.getMessage());
                setZmqConnected(false);
                notifyConnectionStateChanged();
            }
        }
    }

    public void initialize(Context context) {
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (zContext == null) {
            zContext = new ZContext();
        }
    }

    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.connectionStateListener = listener;
    }
    Queue<String> failedMessages = new LinkedList<>();
    public void sendData(String message) {
        // Send via ZMQ if connected
        if (isZmqConnected && zmqSocket != null) {
            try {
                String wifiMessage = "1 " + message;
                boolean sendSuccess = zmqSocket.send(wifiMessage.getBytes(ZMQ.CHARSET), ZMQ.DONTWAIT);
                if (sendSuccess) {
                    Log.d(TAG, "Sent via ZMQ: " + wifiMessage);
                }
                else {
                    isZmqConnected = false;
                    Log.w(TAG, "Eror sent via ZMQ");
                    failedMessages.add(wifiMessage);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending ZMQ message: " + e.getMessage());
                isZmqConnected = false;
                failedMessages.add(message);
                notifyConnectionStateChanged();
                notifyError("Failed to send ZMQ message");
            }
        }

        // Send via Bluetooth if connected
        if (isBluetoothConnected && bluetoothOutputStream != null) {
            try {
                // Add the delimiter for Bluetooth messages
                String bluetoothMessage = message + "|";
                bluetoothOutputStream.write(bluetoothMessage.getBytes());
                bluetoothOutputStream.flush();
                Log.d(TAG, "Sent via Bluetooth: " + bluetoothMessage);
            } catch (IOException e) {
                Log.e(TAG, "Error sending Bluetooth message: " + e.getMessage());
                isBluetoothConnected = false;
                notifyConnectionStateChanged();
                notifyError("Failed to send Bluetooth message");
            }
        }
    }

    public void clearFailedMessages() {
        failedMessages.clear(); // Hapus semua pesan yang gagal
        Log.d(TAG, "Cleared all failed messages from buffer");
    }

    public void rekonek(){
        if (zmqReconnectThread != null && zmqReconnectThread.isAlive()) {

        }
        else {
            startZmqReconnectThread(currentZmqAddress);
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Network network = connectivityManager.getActiveNetwork();
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            } else {
                NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected() &&
                        (networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
            }
        }
        return false;
    }

    public void startZmqReconnectThread(String address) {
        if (!isNetworkConnected()) {
            notifyError("WiFi is not enabled");
            showToast("wifi mati");
            return;
        }

        currentZmqAddress = address;
        shouldReconnectZmq.set(true);
        zmqReconnectAttempts = 0;

        if (zmqReconnectThread != null && zmqReconnectThread.isAlive()) {
            return;
        }

        zmqReconnectThread = new Thread(() -> {
            while (shouldReconnectZmq.get() && zmqReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                if (!isNetworkConnected()) {
                    Log.d(TAG, "WiFi connection lost. Waiting before reconnect attempt...");
                    mainHandler.post(() -> {
                        showToast("WiFi disconnected. Reconnect attempt paused.");
                    });

                    try {
                        Thread.sleep(RECONNECT_DELAY);
                        continue;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (!isZmqConnected) {
                    try {
                        zmqReconnectAttempts++;

                        if (zmqSocket != null) {
                            zmqSocket.close();
                        }

                        zmqSocket = zContext.createSocket(SocketType.PUSH);

                        zmqSocket.setReconnectIVL(1000);
                        zmqSocket.setLinger(0);
                        zmqSocket.connect(currentZmqAddress);

                        isZmqConnected = true;
                        zmqReconnectAttempts = 0; // Reset counter setelah berhasil

                        mainHandler.post(() -> {
                            showToast("ZMQ Connected");
                            notifyConnectionStateChanged();
                        });
                        clearFailedMessages();
                        Log.d(TAG, "ZMQ Reconnected successfully");
                    } catch (Exception e) {
                        Log.e(TAG, "ZMQ Reconnection failed: " + e.getMessage());

                        mainHandler.post(() -> {
                            showToast("Reconnect attempt " + zmqReconnectAttempts + " failed");
                        });

                        if (zmqReconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                            mainHandler.post(() -> {
                                notifyError("ZMQ Connection failed after maximum attempts");
                            });
                        }
                    }
                }

                try {
                    Thread.sleep(RECONNECT_DELAY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        zmqReconnectThread.start();
    }

    public void connectBluetoothClassic(String address) {
        if (!checkBluetoothPermissions()) {
            notifyError("Bluetooth permissions not granted");
            return;
        }

        currentBluetoothAddress = address;
        shouldReconnectBluetooth.set(true);
        bluetoothReconnectAttempts = 0;

        try {
            BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

                // Close existing connection if any
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing existing socket: " + e.getMessage());
                    }
                }

                // Create new socket connection
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
                bluetoothSocket.connect();
                bluetoothOutputStream = bluetoothSocket.getOutputStream();

                isBluetoothConnected = true;
                bluetoothReconnectAttempts = 0;

                mainHandler.post(() -> {
                    showToast("Bluetooth Connected");
                    notifyConnectionStateChanged();
                });
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            isBluetoothConnected = false;
            notifyConnectionStateChanged();
            notifyError("Bluetooth security error: " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "Connection failed: " + e.getMessage());
            isBluetoothConnected = false;
            notifyConnectionStateChanged();
            notifyError("Bluetooth connection failed");
            startBluetoothReconnectThread();
        }
    }

    public void startBluetoothReconnectThread() {
        if (bluetoothReconnectThread != null && bluetoothReconnectThread.isAlive()) {
            return;
        }

        bluetoothReconnectThread = new Thread(() -> {
            while (shouldReconnectBluetooth.get() && bluetoothReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                if (!isBluetoothConnected) {
                    try {
                        connectBluetoothClassic(currentBluetoothAddress);
                        Thread.sleep(RECONNECT_DELAY);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
        bluetoothReconnectThread.start();
    }

    public void setBluetoothConnected(boolean connected) {
        this.isBluetoothConnected = connected;
        notifyConnectionStateChanged();
    }

    public boolean isBluetoothConnected() {
        return isBluetoothConnected;
    }

    public void setZmqSocket(ZMQ.Socket socket) {
        this.zmqSocket = socket;
    }

    public void setZmqConnected(boolean connected) {
        this.isZmqConnected = connected;
        notifyConnectionStateChanged();
    }

    public boolean isZmqConnected() {
        return isZmqConnected;
    }

    /**
     * Set the battery level value
     * @param level Battery level (0-100)
     */
    public void setBatteryLevel(int level) {
        if (level >= 0 && level <= 100) {
            this.batre = level;
        }
    }

    private void notifyConnectionStateChanged() {
        if (connectionStateListener != null) {
            mainHandler.post(() ->
                    connectionStateListener.onConnectionStateChanged(isZmqConnected, isBluetoothConnected));
        }
    }

    private void notifyError(String error) {
        Log.e(TAG, error);
        if (connectionStateListener != null) {
            mainHandler.post(() -> connectionStateListener.onConnectionError(error));
        }
    }

    private void showToast(String message) {
        if (context != null) {
            mainHandler.post(() ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }
    }

    private boolean checkBluetoothPermissions() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public void stopReconnectThreads() {
        shouldReconnectZmq.set(false);
        shouldReconnectBluetooth.set(false);

        if (zmqReconnectThread != null) {
            zmqReconnectThread.interrupt();
            zmqReconnectThread = null;
        }

        if (bluetoothReconnectThread != null) {
            bluetoothReconnectThread.interrupt();
            bluetoothReconnectThread = null;
        }

        zmqReconnectAttempts = 0;
        bluetoothReconnectAttempts = 0;
    }

    public void cleanup() {
        stopReconnectThreads();

        if (zmqSocket != null) {
            zmqSocket.close();
            zmqSocket = null;

        }

        if (zContext != null) {
            zContext.close();
            zContext = null;

        }

        if (bluetoothSocket != null && bluetoothOutputStream != null) {
            try {
                bluetoothSocket.close();
                Log.d(TAG, "BERHASIL DISCONNECT");

            } catch (IOException e) {
                Log.e(TAG, "Error closing Bluetooth socket: " + e.getMessage());
            }
            bluetoothSocket = null;
            bluetoothOutputStream = null;
        }


        isZmqConnected = false;
        isBluetoothConnected = false;
        currentZmqAddress = null;
        currentBluetoothAddress = null;

        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

    }

    public boolean isAnyConnectionActive() {
        return isZmqConnected || isBluetoothConnected;
    }

    public String getConnectionStatus() {
        StringBuilder status = new StringBuilder();
        if (isZmqConnected) {
            status.append("WiFi Connected");
        }
        if (isBluetoothConnected) {
            if (status.length() > 0) {
                status.append(", ");
            }
            status.append("Bluetooth Connected");
            if (currentBluetoothAddress != null) {
                status.append(" (").append(currentBluetoothAddress).append(")");
            }
        }
        if (status.length() == 0) {
            status.append("Not Connected");
        }
        return status.toString();
    }
}