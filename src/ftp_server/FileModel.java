package ftp_server;

import java.io.Serializable;

public class FileModel implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_FILE = "file";
    public static final String TYPE_DIRECTORY = "directory";
    public static final String TYPE_IMAGE = "image";

    private String name;
    private String type;
    private String path;

    public FileModel(String name, String type, String path) {
        this.name = name;
        this.type = type;
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public boolean isFile() {
        return TYPE_FILE.equals(type);
    }

    public boolean isDirectory() {
        return TYPE_DIRECTORY.equals(type);
    }

    public boolean isImage() {
        return TYPE_IMAGE.equals(type);
    }
}
