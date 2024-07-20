package ftp_server;

import com.google.gson.Gson;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class FTPServerBackend {

    private static final int PORT = 4321;
    private static final String DB_URL = "jdbc:mysql://localhost:3306/ftp_database";
    private static final String DB_USER = "ftp";
    private static final String DB_PASSWORD = "admin";
    private final FTP_Server serverGUI;
    private final File tempDirectory;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;

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
                    handleClientRequest(type, dataInputStream, dataOutputStream, clientSocket);
                } catch (IOException e) {
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

    private void handleClientRequest(String type, DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        switch (type) {
            case "SEND_FILE":
                handleSendFile(dataInputStream, dataOutputStream, clientSocket);
                break;
            case "ADD_USER":
                handleConnection(dataInputStream, dataOutputStream, clientSocket);
                break;
            case "RELOAD_SERVER":
                handleReloadServer(clientSocket);
                break;
            case "LOAD_DIRECTORY":
                handleLoadDirectory(dataInputStream, dataOutputStream, clientSocket);
                break;
            case "EXISTED_CONNECTION":
                handleExistedConnection(dataInputStream, dataOutputStream);
                break;
            case "DOWNLOAD_FILE":
                handleDownloadFile(dataInputStream, dataOutputStream, clientSocket);
                break;
            case "RENAME_FILE":
                handleRenameFile(dataInputStream, dataOutputStream, clientSocket);
                break;

            default:
                serverGUI.appendToConsole(getCurrentTime() + "Unknown request from client. Closing connection.\n");
        }
    }

    private void handleSendFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        String fileName = dataInputStream.readUTF();
        serverGUI.appendToConsole(getCurrentTime() + "Receiving file: " + fileName
                + "\nFrom: " + clientSocket.getInetAddress().getHostAddress());

        byte[] fileData = FileHandler.receiveFileToMemory(dataInputStream, dataOutputStream, fileName, serverGUI);
        if (fileData != null) {
            serverGUI.appendToConsole(getCurrentTime() + "File received. Saving to temp directory.");
            File tempFile = new File(tempDirectory, fileName);
            FileHandler.saveFileFromMemory(fileData, tempFile, serverGUI);
            serverGUI.addFileToList(fileName);
        }
    }

    private void handleConnection(DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        String json = dataInputStream.readUTF();
        Connection_Model connection = new Gson().fromJson(json, Connection_Model.class);
        boolean userExists = saveConnectionToMySQL(connection);
        if (userExists) {
            dataOutputStream.writeUTF("USER_EXISTS");
            serverGUI.appendToConsole(
                    getCurrentTime() + "Existed user: " + connection.getUsername() + "\n");
        } else {
            dataOutputStream.writeUTF("CONNECTION_SAVED");
            serverGUI.appendToConsole(getCurrentTime() + "Received connection details: \n"
                    + "IP Address: " + clientSocket.getInetAddress().getHostAddress() + "\n"
                    + "Username: " + connection.getUsername() + "\n"
                    + "Email: " + connection.getEmail() + "\n");
        }
        dataOutputStream.flush();
    }

    private void handleReloadServer(Socket clientSocket) {
        serverGUI.appendToConsole(getCurrentTime() + "Client requested to reload server: "
                + clientSocket.getInetAddress().getHostAddress() + "\n");
    }

    private void handleLoadDirectory(DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        String username = dataInputStream.readUTF();
        serverGUI.appendToConsole(getCurrentTime() + "User: " + username + " open directory");
        sendDirectoryListToClient(username, dataOutputStream);
    }

    private void handleExistedConnection(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
        String json = dataInputStream.readUTF();
        Connection_Model exist_connection = new Gson().fromJson(json, Connection_Model.class);
        boolean userExists = queryExistingUser(exist_connection);
        serverGUI.appendToConsole(getCurrentTime() + "Querying username from Client request");

        if (userExists) {
            dataOutputStream.writeUTF("EXIST_USER");
            serverGUI.appendToConsole(getCurrentTime() + "User exists in the database: "
                    + exist_connection.getUsername() + "\n");
        } else {
            dataOutputStream.writeUTF("INVALID_USER");
            serverGUI.appendToConsole(getCurrentTime() + "Invalid user or password: "
                    + exist_connection.getUsername() + "\n");
        }
        dataOutputStream.flush();
    }

    private void handleRenameFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        try {
            String currentFilePath = dataInputStream.readUTF();
            String newFileName = dataInputStream.readUTF();

            File currentFile = new File(currentFilePath);

            if (currentFile.exists() && currentFile.isFile()) {
                String parentDir = currentFile.getParent();
                File newFile = new File(parentDir, newFileName);

                boolean renameSuccess = currentFile.renameTo(newFile);

                if (renameSuccess) {
                    dataOutputStream.writeUTF("RENAME_SUCCESS");
                    serverGUI.appendToConsole(getCurrentTime() + "User changed file name: " + currentFile.getName() + " -> " + newFileName);
                } else {
                    //
                }
            } else {
                //
            }

            dataOutputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    private void handleDownloadFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream, Socket clientSocket) throws IOException {
        String fileName = dataInputStream.readUTF();
        String json = dataInputStream.readUTF();
        Connection_Model connection = new Gson().fromJson(json, Connection_Model.class);
        String username = connection.getUsername();

        serverGUI.appendToConsole(getCurrentTime() + "Client requested download: " + fileName
                + " - From user: " + username);

        String userDirectoryPath = "users_directories/" + username;
        File userDirectory = new File(userDirectoryPath);
        File fileToSend = new File(userDirectory, fileName);

        if (fileToSend.exists() && fileToSend.isFile()) {
            serverGUI.appendToConsole(getCurrentTime() + "File found. Sending to client.");

            dataOutputStream.writeUTF("FILE_FOUND");
            FileHandler.sendFile(dataOutputStream, fileToSend, serverGUI);
        } else {
            serverGUI.appendToConsole(getCurrentTime() + "File not found: " + fileName);
            dataOutputStream.writeUTF("FILE_NOT_FOUND");
        }
        dataOutputStream.flush();
    }

    private void sendDirectoryListToClient(String username, DataOutputStream dataOutputStream) throws IOException {
        File userDirectory = new File("users_directories/" + username);
        if (userDirectory.exists() && userDirectory.isDirectory()) {
            File[] files = userDirectory.listFiles();
            if (files != null) {
                dataOutputStream.writeInt(0);

                ArrayList<FileModel> fileModels = new ArrayList<>();
                for (File file : files) {
                    String filePath = file.getPath().replace("\\", "/");
                    FileModel fileModel = new FileModel(file.getName(), file.isDirectory() ? FileModel.TYPE_DIRECTORY : FileModel.TYPE_FILE, filePath);
                    fileModels.add(fileModel);
                }
                dataOutputStream.writeUTF(new Gson().toJson(fileModels));
                serverGUI.appendToConsole(getCurrentTime() + "Directory data sent to client: " + username + "\n");
            } else {
                dataOutputStream.writeInt(1);
                serverGUI.appendToConsole(getCurrentTime() + "No files found for user: " + username + "\n");
            }
        } else {
            dataOutputStream.writeInt(1);
            serverGUI.appendToConsole(getCurrentTime() + "User directory not found: " + username + "\n");
        }
        dataOutputStream.flush();
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
            return false; 
        } catch (SQLException e) {
            serverGUI.appendToConsole(getCurrentTime() + "SQL error saving connection: " + e.getMessage());
            return false;
        }
    }

    private boolean queryExistingUser(Connection_Model exist_connection) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD); PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM connections WHERE username = ? AND password = ?")) {
            checkStmt.setString(1, exist_connection.getUsername());
            checkStmt.setString(2, exist_connection.getPassword());

            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            return count > 0;

        } catch (SQLException e) {
            serverGUI.appendToConsole(getCurrentTime() + "Error querying existing user in MySQL: " + e.getMessage() + "\n");
            return false;
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("[dd/MM/yyyy - hh:mm:ss]: ").format(new Date());
    }

    public void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                serverGUI.appendToConsole(getCurrentTime() + "Server stopped.\n");
            }
        } catch (IOException e) {
            serverGUI.appendToConsole(getCurrentTime() + "Error stopping server: " + e.getMessage() + "\n");
        }
    }
}
