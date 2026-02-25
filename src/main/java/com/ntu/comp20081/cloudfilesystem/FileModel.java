package com.ntu.comp20081.cloudfilesystem;

import javafx.beans.property.SimpleStringProperty;

public class FileModel {
    private final SimpleStringProperty fileName;
    private final SimpleStringProperty fileSize;
    private final SimpleStringProperty uploadDate;
    private final SimpleStringProperty permission;

    public FileModel(String fileName, String fileSize, String uploadDate, String permission) {
        this.fileName = new SimpleStringProperty(fileName);
        this.fileSize = new SimpleStringProperty(fileSize);
        this.uploadDate = new SimpleStringProperty(uploadDate);
        this.permission = new SimpleStringProperty(permission);
    }

    
    public String getFileName() { return fileName.get(); }
    public String getFileSize() { return fileSize.get(); }
    public String getUploadDate() { return uploadDate.get(); }
    
    public String getPermission() { return permission.get(); }

    public SimpleStringProperty fileNameProperty() { return fileName; }
    public SimpleStringProperty fileSizeProperty() { return fileSize; }
    public SimpleStringProperty uploadDateProperty() { return uploadDate; }
    public SimpleStringProperty permissionProperty() { return permission; }
}