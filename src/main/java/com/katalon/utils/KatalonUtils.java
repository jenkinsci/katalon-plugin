package com.katalon.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class KatalonUtils {
    public static final String ARG_CONSOLE_LOG = "-consoleLog";
    public static final String ARG_NO_EXIT = "-noExit";
    private static final List<String> REMOVABLE_ARGS = Arrays.asList(
            ARG_CONSOLE_LOG,
            ARG_NO_EXIT
    );

    public static String prepareExecutable(
            Logger logger,
            String version,
            String location,
            String rootDir
    ) throws IOException, InterruptedException {
        String katalonDirPath;

        if (StringUtils.isBlank(location)) {
            File katalonDir = KatalonDownloadUtils.getKatalonPackage(logger, version, rootDir);
            katalonDirPath = katalonDir.getAbsolutePath();
        } else {
            katalonDirPath = location;
        }

        LogUtils.info(logger, "Using KRE at " + katalonDirPath);

        String executableFile;
        Path katalonPath, kataloncPath;
        String os = OsUtils.getOSVersion(logger);

        if (os.contains("macos")) {
            kataloncPath = Paths.get(katalonDirPath, "Katalon Studio Engine.app", "Contents", "MacOS", "katalonc");
            katalonPath = Paths.get(katalonDirPath, "Contents", "MacOS", "katalon");
        } else if (os.contains("linux")) {
            kataloncPath = Paths.get(katalonDirPath, "katalonc");
            katalonPath = Paths.get(katalonDirPath, "katalon");
        } else {
            kataloncPath = Paths.get(katalonDirPath, "katalonc.exe");
            katalonPath = Paths.get(katalonDirPath, "katalon.exe");
        }

        if (Files.exists(kataloncPath)) {
            executableFile = kataloncPath.toAbsolutePath().toString();
            makeDriversExecutable(logger, katalonDirPath, true);
        } else {
            executableFile = katalonPath.toAbsolutePath().toString();
            makeDriversExecutable(logger, katalonDirPath, false);
        }

        File file = new File(executableFile);
        if (file.exists()) {
            file.setExecutable(true);
        }

        return executableFile;
    }

    private static void makeDriversExecutable(Logger logger, String katalonDir, boolean isKatalonc) throws IOException {

        Path driverDirectoryPath = null;
        String os = OsUtils.getOSVersion(logger);
        if (os.contains("macos")) {
            if (isKatalonc) {
                driverDirectoryPath = Paths.get(katalonDir, "Katalon Studio Engine.app", "Contents", "Eclipse",
                    "configuration", "resources", "drivers")
                    .toAbsolutePath();
            } else {
                driverDirectoryPath = Paths.get(katalonDir,  "Contents", "Eclipse",
                    "configuration", "resources", "drivers")
                    .toAbsolutePath();
            }
        } else {
            driverDirectoryPath = Paths.get(katalonDir, "configuration", "resources", "drivers")
                    .toAbsolutePath();
        }

        LogUtils.info(logger, "Making driver executables...");
        LogUtils.info(logger, "Drivers folder at: " + driverDirectoryPath.toAbsolutePath());
        Files.walk(driverDirectoryPath).filter(Files::isRegularFile).forEach(a -> {
            LogUtils.info(logger, "Set " + a.getFileName().toString() + " as executable !");
            a.toFile().setExecutable(true);
        });
    }

    public static String generateKreCommand(String executableFile, String arguments, String projectPath) {
        if (executableFile.contains(" ")) {
            executableFile = "\"" + executableFile + "\"";
        }

        String command = executableFile;

        if (!arguments.contains("-noSplash")) {
            command += " -noSplash ";
        }

        if (!arguments.contains("-runMode=console")) {
            command += " -runMode=console ";
        }

        if (!arguments.contains("-projectPath")) {
            command += " -projectPath=\"" + projectPath + "\" ";
        }

        command += " " + arguments + " ";

        // Remove removable arguments
        command = REMOVABLE_ARGS.stream()
                .reduce(command, (newCommand, arg) -> newCommand.replace(" " + arg, ""));

        return command;
    }
}
