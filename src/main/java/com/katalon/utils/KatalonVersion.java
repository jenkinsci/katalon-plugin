package com.katalon.utils;

public class KatalonVersion {

    private String version;
    private String filename;
    private String os;
    private String url;
    private String containingFolder;

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getContainingFolder() {
        return containingFolder;
    }

    public void setContainingFolder(String containingFolder) {
        this.containingFolder = containingFolder;
    }
}
