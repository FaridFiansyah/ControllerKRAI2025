package com.example.basket;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDelegate;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements ConnectionManager.ConnectionStateListener {
    private static final String TAG = "MainActivity";
    private static final String DEFAULT_PORT = "5556";
    private static final int REQUEST_BLUETOOTH_PERMISSION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    private static final int REQUEST_ENABLE_WIFI = 3;

    // UI Components
    private Spinner presetSpinner;
    private Button buttonConnectWifi, buttonConnectBluetooth, buttonConnectBoth, buttonDisconnect;
    private Button buttonPing, buttonReset, buttonControl, buttonPreset;
    private TextView statusText;

    // Connection Components
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private WifiManager wifiManager;

    // ZeroMQ Components
    private ZContext zContext;
    private ZMQ.Socket zmqSocket;
    private String currentZmqAddress;

    // Shared Preferences
    private SharedPreferences kirim;
    private SharedPreferences prefs;
    private ArrayList<Preset> presets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Setup fullscreen immediately
        setupFullScreen();

        // Initialize managers
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        ConnectionManager.getInstance().initialize(this);
        ConnectionManager.getInstance().setConnectionStateListener(this);

        initializeViews();
        buttonUpdate();
        initializeBluetooth();
        requestPermissions();
        loadPresets();
        setupSpinners();
        setupButtonListeners();
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

    private void initializeViews() {
        presetSpinner = findViewById(R.id.presetSpinner);
        buttonConnectWifi = findViewById(R.id.buttonConnectWifi);
        buttonConnectBluetooth = findViewById(R.id.buttonConnectBluetooth);
        buttonConnectBoth = findViewById(R.id.buttonConnectBoth);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);
        buttonPing = findViewById(R.id.buttonPing);
        buttonReset = findViewById(R.id.buttonReset);
        buttonControl = findViewById(R.id.buttonControl);
        buttonPreset = findViewById(R.id.buttonPreset);
        statusText = findViewById(R.id.teksKeterangan);
    }

    private void buttonUpdate(){
        if(!wifiManager.isWifiEnabled()){
            buttonConnectWifi.setBackgroundResource(R.drawable.red_gradient);
        }
        if(bluetoothAdapter == null){
            buttonConnectBluetooth.setBackgroundResource(R.drawable.red_gradient);
        }
    }
    private void initializeBluetooth() {
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE
        };

        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSION);
        }
    }

    private void loadPresets() {
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        Set<String> savedPresets = prefs.getStringSet("presets", new HashSet<>());
        for (String presetString : savedPresets) {
            String[] parts = presetString.split(",");
            if (parts.length == 5) {
                presets.add(new Preset(parts[0], parts[1], parts[2], parts[3], parts[4]));
            }
        }
        if (presets.isEmpty()) {
            presets.add(new Preset("Default", "10.105.52.203", DEFAULT_PORT, "5555", "B0:A7:32:2B:1C:C6"));
        }
    }

    private void setupSpinners() {
        ArrayAdapter<Preset> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        presetAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(presetAdapter);
    }

    private void setupButtonListeners() {
        buttonConnectWifi.setOnClickListener(v -> handleConnectWifi());
        buttonConnectBluetooth.setOnClickListener(v -> handleConnectBluetooth());
        buttonConnectBoth.setOnClickListener(v -> handleConnectBoth());
        buttonDisconnect.setOnClickListener(v -> handleDisconnect());
        buttonPing.setOnClickListener(v -> handlePing());
        buttonReset.setOnClickListener(v -> handleReset());
        buttonControl.setOnClickListener(v -> handleControl());
        buttonPreset.setOnClickListener(v -> showPresetDialog());
    }

    private void handleConnectWifi() {
        if (!wifiManager.isWifiEnabled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("WiFi Required")
                    .setMessage("WiFi needs to be enabled to continue. Would you like to enable it?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        Intent enableWifiIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                        startActivityForResult(enableWifiIntent, REQUEST_ENABLE_WIFI);
                    })
                    .setNegativeButton("No", (dialog, which) -> {
                        Toast.makeText(this, "WiFi connection requires WiFi to be enabled",
                                Toast.LENGTH_SHORT).show();
                    })
                    .show();
            return;
        }

        Preset selectedPreset = (Preset) presetSpinner.getSelectedItem();
        if (selectedPreset != null) {
            Log.d(TAG,"Mencoba konek zero mq");
            String fullAddress = "tcp://" + selectedPreset.getIpAddress() + ":" + selectedPreset.getPort();
            String addres2 = "tcp://" + selectedPreset.getIpAddress() + ":" + selectedPreset.getPort2();
            kirim = getSharedPreferences("app_kirim", MODE_PRIVATE);
            SharedPreferences.Editor alamat = kirim.edit();
            alamat.putString("addres", addres2);
            alamat.apply();
            initializeZmq(fullAddress);
        }
    }

    private void initializeZmq(String address) {
        if (zContext != null) {
            zContext.close();
        }
        currentZmqAddress = address;
        zContext = new ZContext();
        zmqSocket = zContext.createSocket(SocketType.PUSH);
        zmqSocket.setLinger(0);
        zmqSocket.setReconnectIVL(1000);

        try {
            zmqSocket.connect(currentZmqAddress);
            ConnectionManager.getInstance().setZmqSocket(zmqSocket);
            ConnectionManager.getInstance().setZmqConnected(true);
            ConnectionManager.getInstance().startZmqReconnectThread(currentZmqAddress);
            wifiManager.isWifiEnabled();

            buttonConnectWifi.setBackgroundResource(R.drawable.green_gradient);
            statusText.setText("WiFi Connected");

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("current_address", currentZmqAddress);
            editor.apply();

        } catch (Exception e) {
            Log.e(TAG, "Error connecting to ZeroMQ server: " + e.getMessage());
            ConnectionManager.getInstance().setZmqConnected(false);
            statusText.setText("Failed to connect WiFi");
        }
    }

    private void handleConnectBluetooth() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            try {
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception: " + e.getMessage());
                requestPermissions();
            }
            return;
        }

        Preset selectedPreset = (Preset) presetSpinner.getSelectedItem();
        if (selectedPreset != null) {
            try {
                ConnectionManager.getInstance().connectBluetoothClassic(selectedPreset.getMacAddress());
                statusText.setText("Connecting to Bluetooth");

                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("current_mac", selectedPreset.getMacAddress());
                editor.apply();
            } catch (Exception e) {
                Log.e(TAG, "Error initiating Bluetooth connection: " + e.getMessage());
                Toast.makeText(this, "Failed to connect to Bluetooth device", Toast.LENGTH_SHORT).show();
                statusText.setText("Bluetooth Connection Failed");
            }
        }
    }

    private void handleConnectBoth() {
        handleConnectWifi();
        handleConnectBluetooth();
    }

    private void handleDisconnect() {
        ConnectionManager.getInstance().cleanup();
        buttonConnectWifi.setBackgroundResource(R.drawable.red_gradient);
        buttonConnectBluetooth.setBackgroundResource(R.drawable.red_gradient);
        statusText.setText("Disconnected");
    }

    private void handlePing() {
        if (ConnectionManager.getInstance().isAnyConnectionActive()) {
            ConnectionManager.getInstance().sendData("PING from Android");
            statusText.setText("Ping sent");
        } else {
            statusText.setText("Not connected");
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show();
        }
    }

    private void handleReset() {
        handleDisconnect();
        presetSpinner.setSelection(0);
        statusText.setText("Reset complete");
    }

    private void handleControl() {
        if(bluetoothAdapter != null && wifiManager.isWifiEnabled()){
            if (ConnectionManager.getInstance().isAnyConnectionActive()) {
                Intent intent = new Intent(MainActivity.this, ControlActivity.class);
                startActivity(intent);
            }
        }
        else {
            Toast.makeText(this, "Please connect first", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPresetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_preset, null);
        builder.setView(dialogView);

        EditText nameInput = dialogView.findViewById(R.id.nameInput);
        EditText ipInput = dialogView.findViewById(R.id.ipInput);
        EditText portInput = dialogView.findViewById(R.id.portInput);
        EditText macInput = dialogView.findViewById(R.id.macInput);
        Button addButton = dialogView.findViewById(R.id.addButton);
        Button editButton = dialogView.findViewById(R.id.editButton);
        Button deleteButton = dialogView.findViewById(R.id.deleteButton);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.background_blur);
        }

        addButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String ip = ipInput.getText().toString().trim();
            String port = portInput.getText().toString().trim();
            String mac = macInput.getText().toString().trim();

            if (!name.isEmpty() && !ip.isEmpty() && !port.isEmpty() && !mac.isEmpty()) {
                String[] ports = port.split(",");
                if (ports.length == 2) {
                    String port1 = ports[0].trim();
                    String port2 = ports[1].trim();

                    Preset newPreset = new Preset(name, ip, port1, port2, mac);
                    presets.add(newPreset);
                    savePresets();
                    setupSpinners();
                    dialog.dismiss();
                    Toast.makeText(this, "Preset added successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Please enter two ports separated by a comma", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            }
        });

        editButton.setOnClickListener(v -> {
            showEditPresetDialog();
            dialog.dismiss();
        });

        deleteButton.setOnClickListener(v -> {
            showDeletePresetDialog();
            dialog.dismiss();
        });

        // Add fullscreen handling for dialog
        dialog.setOnShowListener(dialogInterface -> {
            // Make sure dialog is also fullscreen and respects cutouts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dialog.getWindow() != null) {
                WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                dialog.getWindow().setAttributes(layoutParams);
            }
        });

        dialog.show();
    }

    private void showEditPresetDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_edit_preset, null);
        builder.setView(view);

        Spinner presetSpinner = view.findViewById(R.id.presetSpinner);
        EditText nameInput = view.findViewById(R.id.nameInput);
        EditText ipInput = view.findViewById(R.id.ipInput);
        EditText portInput = view.findViewById(R.id.portInput);
        EditText macInput = view.findViewById(R.id.macInput);

        ArrayAdapter<Preset> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Preset selectedPreset = (Preset) parent.getItemAtPosition(position);
                nameInput.setText(selectedPreset.getName());
                ipInput.setText(selectedPreset.getIpAddress());
                portInput.setText(selectedPreset.getPort()+","+selectedPreset.getPort2());
                macInput.setText(selectedPreset.getMacAddress());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            int selectedPosition = presetSpinner.getSelectedItemPosition();
            if (selectedPosition != AdapterView.INVALID_POSITION) {
                String name = nameInput.getText().toString().trim();
                String ip = ipInput.getText().toString().trim();
                String port = portInput.getText().toString().trim();
                String mac = macInput.getText().toString().trim();

                if (!name.isEmpty() && !ip.isEmpty() && !port.isEmpty() && !mac.isEmpty()) {
                    String[] ports = port.split(",");
                    if (ports.length == 2) {
                        String port1 = ports[0].trim();
                        String port2 = ports[1].trim();

                        Preset selectedPreset = presets.get(selectedPosition);
                        selectedPreset.setName(name);
                        selectedPreset.setIpAddress(ip);
                        selectedPreset.setPort(port1);
                        selectedPreset.setPort2(port2);
                        selectedPreset.setMacAddress(mac);
                        savePresets();
                        setupSpinners();
                        Toast.makeText(this, "Preset updated successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Please enter two ports separated by a comma", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", null);

        // Buat dialog dari builder
        AlertDialog dialog = builder.create();

        // Set background transparan dan style kustom untuk dialog
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.background_blur);
        }

        // Terapkan style pada komponen setelah dialog ditampilkan
        dialog.setOnShowListener(dialogInterface -> {
            // Set warna teks untuk tombol positif dan negatif
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);

            // Make sure dialog is also fullscreen and respects cutouts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dialog.getWindow() != null) {
                WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                dialog.getWindow().setAttributes(layoutParams);
            }
        });

        dialog.show();
    }

    private void showDeletePresetDialog() {
        if (presets.size() <= 1) {
            Toast.makeText(this, "Cannot delete the last preset", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_delete_preset, null);
        Spinner spinner = view.findViewById(R.id.ipDeleteSpinner);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(view);

        ArrayAdapter<Preset> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        builder.setPositiveButton("Delete", (dialog, which) -> {
            Preset selectedPreset = (Preset) spinner.getSelectedItem();
            if (selectedPreset != null) {
                presets.remove(selectedPreset);
                savePresets();
                setupSpinners();
                Toast.makeText(this, "Preset deleted successfully", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);

        // Buat dialog dari builder
        AlertDialog dialog = builder.create();

        // Set background kustom untuk dialog window
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.background_blur);
        }

        // Ubah warna teks pada tombol dan judul
        dialog.setOnShowListener(dialogInterface -> {
            // Set warna teks untuk tombol positif dan negatif
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);

            // Make sure dialog is also fullscreen and respects cutouts
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && dialog.getWindow() != null) {
                WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                layoutParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                dialog.getWindow().setAttributes(layoutParams);
            }
        });

        dialog.show();
    }

    private void savePresets() {
        Set<String> presetStrings = new HashSet<>();
        for (Preset preset : presets) {
            presetStrings.add(preset.toString());
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet("presets", presetStrings);
        editor.apply();
    }

    @Override
    public void onConnectionStateChanged(boolean isZmqConnected, boolean isBluetoothConnected) {
        runOnUiThread(() -> {
            if (isZmqConnected) {
                buttonConnectWifi.setBackgroundResource(R.drawable.green_gradient);
            } else {
                buttonConnectWifi.setBackgroundResource(R.drawable.red_gradient);
            }

            if (isBluetoothConnected) {
                buttonConnectBluetooth.setBackgroundResource(R.drawable.green_gradient);
            } else {
                buttonConnectBluetooth.setBackgroundResource(R.drawable.red_gradient);
            }

            statusText.setText(ConnectionManager.getInstance().getConnectionStatus());
        });
    }

    @Override
    public void onConnectionError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            statusText.setText("Error: " + error);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    handleConnectBluetooth();
                } else {
                    Toast.makeText(this, "Bluetooth must be enabled to connect",
                            Toast.LENGTH_SHORT).show();
                }
                break;

            case REQUEST_ENABLE_WIFI:
                if (wifiManager.isWifiEnabled()) {
                    handleConnectWifi();
                } else {
                    Toast.makeText(this, "WiFi must be enabled to connect",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }
            if (!allPermissionsGranted) {
                Toast.makeText(this, "Some permissions were denied. App may not function properly.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reapply fullscreen when coming back to this activity
        setupFullScreen();

        String savedAddress = prefs.getString("current_address", null);
        String savedMac = prefs.getString("current_mac", null);

        if (savedAddress != null && ConnectionManager.getInstance().isZmqConnected()) {
            buttonConnectWifi.setBackgroundResource(R.drawable.green_gradient);
        }

        if (savedMac != null && ConnectionManager.getInstance().isBluetoothConnected()) {
            buttonConnectBluetooth.setBackgroundResource(R.drawable.green_gradient);
        }

        statusText.setText(ConnectionManager.getInstance().getConnectionStatus());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            ConnectionManager.getInstance().cleanup();
        }
    }
}