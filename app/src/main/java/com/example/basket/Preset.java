// Perbaikan untuk Preset.java
package com.example.basket;

public class Preset {
    private String name;
    private String ipAddress;
    private String port;
    private String port2;
    private String macAddress;

    public Preset(String name, String ipAddress, String port, String port2, String macAddress) {
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.port2 = port2;
        this.macAddress = macAddress;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getPort2() {
        return port2;
    }

    public void setPort2(String port2) {
        this.port2 = port2;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    @Override
    public String toString() {
        return name + "," + ipAddress + "," + port + "," + port2 + "," + macAddress;
    }

    // Method baru untuk parse string kembali menjadi Preset
    public static Preset fromString(String presetString) {
        String[] parts = presetString.split(",");
        if (parts.length == 5) {
            return new Preset(parts[0], parts[1], parts[2], parts[3], parts[4]);
        }
        return null;
    }
}