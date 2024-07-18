package ftp_server;

import com.google.gson.Gson;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class FTPServerBackend {

    private static final int PORT = 4321;
    private final FTP_Server serverGUI;
    private final File tempDirectory;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;

    private static final String DB_URL = "jdbc:mysql://localhost:3306/ftp_database";
    private static final String DB_USER = "ftp";
    private static final String DB_PASSWORD = "admin";

    public FTPServerBackend(FTP_Server serverGUI, File downloadDirectory, File tempDirectory) {
        this.serverGUI = serverGUI;
        this.tempDirectory = tempDirectory;
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void startServer() {
        new Thread(this::runServer).start();
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            serverGUI.appendToConsole(getCurrentTime() + "Server started on port " + PORT + "\n");

            while (!serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    serverGUI.appendToConsole(getCurrentTime() + "Client connected from IP: "
                            + clientSocket.getInetAddress().getHostAddress() + "\n");
                    threadPool.submit(() -> handleClientConnection(clientSocket));
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        serverGUI.appendToConsole(
                                getCurrentTime() + "Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            serverGUI.appendToConsole(getCurrentTime() + "Error starting server: " + e.getMessage());
        }
    }

    private void handleClientConnection(Socket clientSocket) {
        try (DataInputStream dataInputStream = new DataInputStream(clientSocket.getInputStream()); DataOutputStream dataOutputStream = new DataOutputStream(clientSocket.getOutputStream())) {

            while (!serverSocket.isClosed()) {
                try {
                    String type = dataInputStream.readUTF();

                    if ("SEND_FILE".equals(type)) {
                        String fileName = dataInputStream.readUTF();
                        serverGUI.appendToConsole(getCurrentTime() + "Receiving file: " + fileName
                                + "\nFrom: " + clientSocket.getInetAddress().getHostAddress());

                        byte[] fileData = FileHandler.receiveFileToMemory(dataInputStream, dataOutputStream, fileName,
                                serverGUI);
                        if (fileData != null) {
                            serverGUI.appendToConsole(getCurrentTime() + "File received. Saving to temp directory.");
                            File tempFile = new File(tempDirectory, fileName);
                            FileHandler.saveFileFromMemory(fileData, tempFile, serverGUI);
                            serverGUI.addFileToList(fileName);
                        }
                    } else if ("CONNECTION".equals(type)) {
                        String json = dataInputStream.readUTF();
                        Connection_Model connection = new Gson().fromJson(json, Connection_Model.class);
                        boolean userExists = saveConnectionToMySQL(connection);
                        if (userExists) {
                            dataOutputStream.writeUTF("USER_EXISTS");
                            dataOutputStream.flush();
                            serverGUI.appendToConsole(
                                    getCurrentTime() + "Existed user: " + connection.getUsername() + "\n");
                        } else {
                            dataOutputStream.writeUTF("CONNECTION_SAVED");
                            dataOutputStream.flush();
                            serverGUI.appendToConsole(getCurrentTime() + "Received connection details: \n"
                                    + "IP Address: " + clientSocket.getInetAddress().getHostAddress() + "\n"
                                    + "Username: " + connection.getUsername() + "\n"
                                    + "Email: " + connection.getEmail() + "\n");
                        }
                    } else if ("RELOAD_SERVER".equals(type)) {
                        serverGUI.appendToConsole(getCurrentTime() + "Client requested to reload server: "
                                + clientSocket.getInetAddress().getHostAddress() + "\n");

                    } else if ("LOAD_DIRECTORY".equals(type)) {
                        String username = dataInputStream.readUTF();
                        serverGUI.appendToConsole(getCurrentTime() + "User: " + username + " open directory");
                        sendDirectoryListToClient(username, dataOutputStream);

                    } else if ("EXISTED_CONNECTION".equals(type)) {
                        String json = dataInputStream.readUTF();
                        Connection_Model exist_connection = new Gson().fromJson(json, Connection_Model.class);
                        boolean userExists = queryExistingUser(exist_connection);
                        serverGUI.appendToConsole(getCurrentTime() + "Querying username from Client request");

                        if (userExists) {
                            dataOutputStream.writeUTF("EXIST_USER");
                            dataOutputStream.flush();
                            serverGUI.appendToConsole(getCurrentTime() + "User exists in the database: "
                                    + exist_connection.getUsername() + "\n");
                        } else {
                            dataOutputStream.writeUTF("INVALID_USER");
                            dataOutputStream.flush();
                            serverGUI.appendToConsole(getCurrentTime() + "Invalid user or password: "
                                    + exist_connection.getUsername() + "\n");
                        }
                    } else {
                        serverGUI.appendToConsole(
                                getCurrentTime() + "Unknown request from client. Closing connection.\n");
                        break;
                    }
                } catch (IOException e) {
                    // serverGUI.appendToConsole("Error reading from client: " + e.getMessage());
                    break;
                }
            }
        } catch (IOException e) {
            serverGUI.appendToConsole(getCurrentTime() + "Error handling client connection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                serverGUI.appendToConsole(getCurrentTime() + "Error closing client socket: " + e.getMessage() + "\n");
            }
        }
    }

    private void sendDirectoryListToClient(String username, DataOutputStream dataOutputStream) throws IOException {
        File userDirectory = new File("users_directories/" + username);
        if (userDirectory.exists() && userDirectory.isDirectory()) {
            File[] files = userDirectory.listFiles();
            if (files != null) {
                dataOutputStream.writeInt(0);
                dataOutputStream.flush();

                ArrayList<FileModel> fileModels = new ArrayList<FileModel>();
                for (File file : files) {
                    String filePath = file.getPath().replace("\\", "/");
                    FileModel fileModel = new FileModel(file.getName(), file.isDirectory() ? FileModel.TYPE_DIRECTORY : FileModel.TYPE_FILE, filePath);
                    fileModels.add(fileModel);
                }
                dataOutputStream.writeUTF(new Gson().toJson(fileModels));
                dataOutputStream.flush();
                serverGUI.appendToConsole(getCurrentTime() + "Directory data sent to client: " + username + "\n");
            } else {
                dataOutputStream.writeInt(1);
                dataOutputStream.flush();
                serverGUI.appendToConsole(getCurrentTime() + "No files found for user: " + username + "\n");
            }
        } else {
            dataOutputStream.writeInt(1);
            dataOutputStream.flush();
            serverGUI.appendToConsole(getCurrentTime() + "No directory found for user: " + username + "\n");
        }
    }

    private boolean queryExistingUser(Connection_Model exist_connection) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM connections WHERE username = ? AND password = ?");) {
            stmt.setString(1, exist_connection.getUsername());
            stmt.setString(2, exist_connection.getPassword());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            serverGUI.appendToConsole(getCurrentTime() + "SQL error querying existing user: " + e.getMessage());
            return false;
        }
    }

    private boolean saveConnectionToMySQL(Connection_Model connection) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM connections WHERE username = ?"); PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO connections(id, ip_address, port, username, password, email, creation_date) VALUES(?,?,?,?,?,?,?)")) {
            checkStmt.setString(1, connection.getUsername());
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    serverGUI.appendToConsole(getCurrentTime() + "User already exists: " + connection.getUsername() + "\n");
                    return true;
                }
            }

            insertStmt.setString(1, connection.getId());
            insertStmt.setString(2, connection.getIpAddress());
            insertStmt.setInt(3, connection.getPort());
            insertStmt.setString(4, connection.getUsername());
            insertStmt.setString(5, connection.getPassword());
            insertStmt.setString(6, connection.getEmail());

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String currentDate = dateFormat.format(new Date());

            insertStmt.setString(7, currentDate);

            insertStmt.executeUpdate();

            File userDirectory = new File("users_directories/" + connection.getUsername());
            if (!userDirectory.exists()) {
                userDirectory.mkdirs();
            }

            serverGUI.appendToConsole(getCurrentTime() + "Connection data saved to database: " + connection.getUsername());
            return false; // User did not previously exist
        } catch (SQLException e) {
            serverGUI.appendToConsole(getCurrentTime() + "SQL error saving connection: " + e.getMessage());
            return false;
        }
    }

    public void stopServer() {
        try {
            serverSocket.close();
            threadPool.shutdown();
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
            serverGUI.appendToConsole(getCurrentTime() + "Server stopped\n");
        } catch (IOException | InterruptedException e) {
            serverGUI.appendToConsole(getCurrentTime() + "Error stopping server: " + e.getMessage() + "\n");
        }
    }

    private String getCurrentTime() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return "[" + now.format(formatter) + "] ";
    }
}
