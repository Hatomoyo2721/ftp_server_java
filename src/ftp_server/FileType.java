package ftp_server;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class FileType {

    private static final Set<String> TEXT_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "txt", "csv", "log", "json", "xml", "md"));
    private static final Set<String> IMAGE_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp"));
    private static final Set<String> VIDEO_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "avi", "mov", "mkv", "flv", "wmv"));
    private static final Set<String> DATABASE_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "sql", "db", "sqlite", "mdb", "accdb"));
    private static final Set<String> CODE_FILE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "java", "py", "js", "html", "css", "cpp", "c", "cs", "php", "rb", "swift", "kt", "rs", "go"));

    public static String getFileExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        return (lastDotIndex == -1) ? "" : name.substring(lastDotIndex + 1).toLowerCase();
    }

    public static boolean isTextFile(String fileExtension) {
        return TEXT_FILE_EXTENSIONS.contains(fileExtension);
    }

    public static boolean isImageFile(String fileExtension) {
        return IMAGE_FILE_EXTENSIONS.contains(fileExtension);
    }

    public static boolean isVideoFile(String fileExtension) {
        return VIDEO_FILE_EXTENSIONS.contains(fileExtension);
    }

    public static boolean isDatabaseFile(String fileExtension) {
        return DATABASE_FILE_EXTENSIONS.contains(fileExtension);
    }

    public static boolean isCodeFile(String fileExtension) {
        return CODE_FILE_EXTENSIONS.contains(fileExtension);
    }
}
