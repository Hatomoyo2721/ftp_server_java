package ftp_server;

import javax.swing.*;

public class Console {

    public static void appendToConsole(JTextArea consoleTextArea, String message) {
        SwingUtilities.invokeLater(() -> {
            String currentText = consoleTextArea.getText();
            if (!currentText.endsWith("\n")) {
                consoleTextArea.append("\n");
            }
            consoleTextArea.append(message + "\n");
            consoleTextArea.setCaretPosition(consoleTextArea.getDocument().getLength());
        });
    }
}
