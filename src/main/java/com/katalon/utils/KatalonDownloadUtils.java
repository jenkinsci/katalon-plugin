package com.katalon.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

class KatalonDownloadUtils {

    private static final String RELEASES_LIST = "https://download.katalon.com/katalon-studio/releases.json";

    static File getKatalonPackage(Logger logger, String versionNumber, String rootDir)
            throws IOException, InterruptedException {

        File katalonDir = getKatalonFolder(versionNumber, rootDir);

        Path fileLog = Paths.get(katalonDir.toString(), ".katalon.done");

        if (fileLog.toFile().exists()) {
            LogUtils.info(logger, "Katalon Studio package has been downloaded already.");
        } else {
            org.apache.commons.io.FileUtils.deleteDirectory(katalonDir);

            boolean katalonDirCreated = katalonDir.mkdirs();
            if (!katalonDirCreated) {
                throw new IllegalStateException("Cannot create directory to store Katalon Studio package.");
            }

            KatalonVersion version = getVersionInfo(logger, versionNumber);
            if (version == null) {
                throw new IllegalStateException("Cannot find Katalon Studio version: " + versionNumber);
            }

            String versionUrl = version.getUrl();

            FileUtils.downloadAndExtract(logger, versionUrl, katalonDir);

            boolean fileLogCreated = fileLog.toFile().createNewFile();
            if (fileLogCreated) {
                LogUtils.info(logger, "Katalon Studio has been installed successfully.");
            }
        }

        String[] childrenNames = katalonDir.list((dir, name) -> {
            File file = new File(dir, name);
            return file.isDirectory() && name.contains("Katalon");
        });

        String katalonContainingDirName = Arrays.stream(childrenNames)
                .findFirst().orElseThrow(() -> new IllegalStateException("Cannot find Katalon Studio directory."));

        File katalonContainingDir = new File(katalonDir, katalonContainingDirName);

        return katalonContainingDir;
    }

    private static File getKatalonFolder(String version, String rootDir) {
        Path p = Paths.get(rootDir, ".katalon", version);
        return p.toFile();
    }

    private static KatalonVersion getVersionInfo(Logger logger, String versionNumber) throws IOException {

        URL url = new URL(RELEASES_LIST);

        String os = OsUtils.getOSVersion(logger);

        LogUtils.info(logger, "Retrieve Katalon Studio version: " + versionNumber + ", OS: " + os);

        ObjectMapper objectMapper = new ObjectMapper();
        List<KatalonVersion> versions = objectMapper.readValue(url, new TypeReference<List<KatalonVersion>>() {
        });

        LogUtils.info(logger, "Number of releases: " + versions.size());

        for (KatalonVersion version : versions) {
            if ((version.getVersion().equals(versionNumber)) && (version.getOs().equalsIgnoreCase(os))) {
                String containingFolder = version.getContainingFolder();
                if (containingFolder == null) {
                    String fileName = version.getFilename();
                    String fileExtension = "";
                    if (fileName.endsWith(".zip")) {
                        fileExtension = ".zip";
                    } else if (fileName.endsWith(".tar.gz")) {
                        fileExtension = ".tar.gz";
                    }
                    containingFolder = fileName.replace(fileExtension, "");
                    version.setContainingFolder(containingFolder);
                }
                LogUtils.info(logger, "Katalon Studio is hosted at " + version.getUrl() + ".");
                return version;
            }
        }
        return null;
    }
}
