package ftp_server;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class FileHandler {

    private static final int BUFFER_SIZE = 4096;

    public static void openSelectedFile(JList<String> fileList, File tempDirectory, FTP_Server serverGUI) {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile != null) {
            File file = new File(tempDirectory, selectedFile);
            if (file.exists()) {
                openFileExternally(file, serverGUI);
            } else {
                serverGUI.appendToConsole("Selected file does not exist: " + selectedFile);
            }
        } else {
            serverGUI.appendToConsole("No file selected");
        }
    }

    public static void downloadSelectedFile(JList<String> fileList, File tempDirectory, File downloadDirectory,
            FTP_Server serverGUI) {
        String selectedFile = fileList.getSelectedValue();
        if (selectedFile != null) {
            int response = JOptionPane.showConfirmDialog(serverGUI,
                    "Do you want to download the selected file?",
                    "Download File",
                    JOptionPane.YES_NO_OPTION);

            if (response == JOptionPane.YES_OPTION) {
                File tempFile = new File(tempDirectory, selectedFile);
                File downloadFile = new File(downloadDirectory, selectedFile);
                if (downloadFile.exists()) {
                    serverGUI.appendToConsole("File already exists in the download directory: " + selectedFile + "\n");
                } else {
                    try (InputStream in = new FileInputStream(tempFile); OutputStream out = new FileOutputStream(downloadFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                        serverGUI.appendToConsole("File downloaded successfully: " + selectedFile);
                        // Do not add the file to the list
                    } catch (IOException e) {
                        serverGUI.appendToConsole("Error downloading file: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } else {
                serverGUI.appendToConsole("Download cancelled: " + selectedFile);
            }
        } else {
            serverGUI.appendToConsole("No file selected");
        }
    }

    public static byte[] receiveFileToMemory(DataInputStream dataInputStream, DataOutputStream dataOutputStream,
            String fileName, FTP_Server serverGUI) {
        byte[] buffer = new byte[BUFFER_SIZE];
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        int bytesRead;
        long fileSize = 0;

        try {
            fileSize = dataInputStream.readLong();
            serverGUI.appendToConsole("File size: " + convertFileSize(fileSize));

            while (fileSize > 0
                    && (bytesRead = dataInputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
                fileSize -= bytesRead;
            }

            byte[] fileData = byteArrayOutputStream.toByteArray();
            dataOutputStream.writeUTF("SUCCESS");
            dataOutputStream.flush();
            return fileData;

        } catch (IOException e) {
            serverGUI.appendToConsole("Error receiving file: " + e.getMessage());
            e.printStackTrace();
            try {
                dataOutputStream.writeUTF("ERROR");
                dataOutputStream.flush();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            return null;
        }
    }

    public static void saveFileFromMemory(byte[] fileData, File file, FTP_Server serverGUI) {
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            fileOutputStream.write(fileData);
            fileOutputStream.flush();
            serverGUI.appendToConsole("File saved successfully: " + file.getName() + "\n");
        } catch (IOException e) {
            serverGUI.appendToConsole("Error saving file: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    public static void deleteTempDirectory(File tempDirectory) {
        if (tempDirectory.isDirectory()) {
            for (File file : tempDirectory.listFiles()) {
                file.delete();
            }
        }
        tempDirectory.delete();
    }

    private static String convertFileSize(long size) {
        double fileSize = size;
        String[] units = {"B", "KB", "MB", "GB"};
        int unitIndex = 0;

        while (fileSize >= 1024 && unitIndex < units.length - 1) {
            fileSize /= 1024;
            unitIndex++;
        }

        return String.format("%.3f %s", fileSize, units[unitIndex]);
    }

    private static void openFileExternally(File file, FTP_Server serverGUI) {
        try {
            Desktop.getDesktop().open(file);
            serverGUI.appendToConsole("Opening file: " + file.getName() + "\n");
        } catch (IOException e) {
            serverGUI.appendToConsole("Error opening file externally: " + e.getMessage() + "\n");
        }
    }

    public static void sendFile(DataOutputStream dataOutputStream, File file, FTP_Server serverGUI) throws IOException {
        dataOutputStream.writeLong(file.length());
        dataOutputStream.flush();

        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                dataOutputStream.write(buffer, 0, bytesRead);
            }
        }
        serverGUI.appendToConsole("File sent: " + file.getName() + "\nSize: " + convertFileSize(file.length()) + "\n");
    }
}
