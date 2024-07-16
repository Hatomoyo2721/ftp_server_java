package ftp_server;

import com.google.gson.Gson;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Connection_Model {
    private String id;
    private String ipAddress;
    private int port;
    private String username;
    private String password;
    private String email;
    private String creationDate;

    public Connection_Model(String ipAddress, int port, String username, String password) {
        this.id = generateId();
        this.ipAddress = ipAddress;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getCreationDate() {
        return creationDate;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public static Connection_Model fromJson(String json) {
        return new Gson().fromJson(json, Connection_Model.class);
    }

    private String generateId() {
        String uuid = java.util.UUID.randomUUID().toString();
        return uuid.substring(0, Math.min(uuid.length(), 10));
    }

    private String getCurrentDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}
