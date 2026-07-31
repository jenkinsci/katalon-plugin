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

    /**
     * Execute Katalon Studio test projects. Katalon Studio can be downloaded and installed automatically in the user's
     * home directory.
     * @param logger Logger to log activities.
     * @param version Version of Katalon Studio to be installed. Ignored if {@code location} is provided.
     * @param location Local location where Katalon Studio has been pre-installed. If this argument is null or empty,
     *                the package will be downloaded and installed automatically.
     * @param projectPath Path to the Katalon Studio project to be executed. Ignored if provided by {@code executeArgs}.
     * @param executeArgs Arguments for Katalon Studio CLI, without {@code -runMode}. If {@code -projectPath} is
     *                   missing, the argument {@code projectPath} will be used.
     * @param x11Display Linux only. This value will be used as the {@code DISPLAY} environment variable.
     * @param xvfbConfiguration Linux only. This value will be used as the arguments for {@code xvfb-run}.
     * @param environmentVariables Environment variables available when executing Katalon.
     * @return true if the exit code is 0, false otherwise.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    public static boolean executeKatalon(
            Logger logger,
            String version,
            String location,
            String projectPath,
            String executeArgs,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables
    ) throws IOException, InterruptedException {
        return executeKatalon(
                logger,
                version,
                location,
                projectPath,
                executeArgs,
                x11Display,
                xvfbConfiguration,
                environmentVariables,
                System.getProperty("user.home")
        );
    }

    /**
     * Execute Katalon Studio test projects. Katalon Studio can be downloaded and installed automatically.
     * @param logger Logger to log activities.
     * @param version Version of Katalon Studio to be installed. Ignored if {@code location} is provided.
     * @param location Local location where Katalon Studio has been pre-installed. If this argument is null or empty,
     *                the package will be downloaded and installed automatically.
     * @param projectPath Path to the Katalon Studio project to be executed. Ignored if provided by {@code executeArgs}.
     * @param executeArgs Arguments for Katalon Studio CLI, without {@code -runMode}. If {@code -projectPath} is
     *                   missing, the argument {@code projectPath} will be used.
     * @param x11Display Linux only. This value will be used as the {@code DISPLAY} environment variable.
     * @param xvfbConfiguration Linux only. This value will be used as the arguments for {@code xvfb-run}.
     * @param environmentVariables Environment variables available when executing Katalon.
     * @param rootDir Directory to install Katalon Studio. Considered if {@code version} is provided.
     * @return true if the exit code is 0, false otherwise.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    public static boolean executeKatalon(
            Logger logger,
            String version,
            String location,
            String projectPath,
            String executeArgs,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables,
            String rootDir
    ) throws IOException, InterruptedException {

        if (StringUtils.isBlank(executeArgs)) {
            LogUtils.info(logger, "Arguments are blank, no tests will be executed");
            return false;
        }

        String executableFile = prepareExecutable(logger, version, location, rootDir);
        String command = generateCommand(executableFile, executeArgs, projectPath);
        Path workingDirectory = Files.createTempDirectory("katalon-");

        return OsUtils.runCommand(
                logger,
                command,
                workingDirectory,
                x11Display,
                xvfbConfiguration,
                environmentVariables);
    }

    /**
     * Execute Katalon Studio test projects. Katalon Studio can be downloaded and installed automatically in the user's
     * home directory.
     * @param logger Logger to log activities.
     * @param version Version of Katalon Studio to be installed. Ignored if {@code location} is provided.
     * @param location Local location where Katalon Studio has been pre-installed. If this argument is null or empty,
     *                the package will be downloaded and installed automatically.
     * @param projectPath Path to the Katalon Studio project to be executed. Ignored if provided by {@code executeArgs}.
     * @param executeArgs Arguments for Katalon Studio CLI, without {@code -runMode}. If {@code -projectPath} is
     *                   missing, the argument {@code projectPath} will be used.
     * @param x11Display Linux only. This value will be used as the {@code DISPLAY} environment variable.
     * @param xvfbConfiguration Linux only. This value will be used as the arguments for {@code xvfb-run}.
     * @param environmentVariables Environment variables available when executing Katalon.
     * @return The executed Katalon Studio process.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    public static Process executeKatalonProcess(
            Logger logger,
            String version,
            String location,
            String projectPath,
            String executeArgs,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables
    ) throws IOException, InterruptedException {
        return executeKatalonProcess(
                logger,
                version,
                location,
                projectPath,
                executeArgs,
                x11Display,
                xvfbConfiguration,
                environmentVariables,
                System.getProperty("user.home")
        );
    }

    /**
     * Execute Katalon Studio test projects. Katalon Studio can be downloaded and installed automatically.
     * @param logger Logger to log activities.
     * @param version Version of Katalon Studio to be installed. Ignored if {@code location} is provided.
     * @param location Local location where Katalon Studio has been pre-installed. If this argument is null or empty,
     *                the package will be downloaded and installed automatically.
     * @param projectPath Path to the Katalon Studio project to be executed. Ignored if provided by {@code executeArgs}.
     * @param executeArgs Arguments for Katalon Studio CLI, without {@code -runMode}. If {@code -projectPath} is
     *                   missing, the argument {@code projectPath} will be used.
     * @param x11Display Linux only. This value will be used as the {@code DISPLAY} environment variable.
     * @param xvfbConfiguration Linux only. This value will be used as the arguments for {@code xvfb-run}.
     * @param environmentVariables Environment variables available when executing Katalon.
     * @param rootDir Directory to install Katalon Studio. Considered if {@code version} is provided.
     * @return The executed Katalon Studio process.
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    public static Process executeKatalonProcess(
            Logger logger,
            String version,
            String location,
            String projectPath,
            String executeArgs,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables,
            String rootDir)
            throws IOException, InterruptedException {

        if (StringUtils.isBlank(executeArgs)) {
            LogUtils.info(logger, "Arguments are blank, no tests will be executed");
            return null;
        }

        String executableFile = prepareExecutable(logger, version, location, rootDir);
        String command = generateCommand(executableFile, executeArgs, projectPath);
        Path workingDirectory = Files.createTempDirectory("katalon-");

        ProcessBuilder pb = OsUtils.buildProcess(
                command,
                workingDirectory,
                x11Display,
                xvfbConfiguration,
                environmentVariables);
        LogUtils.info(logger, "Execute " + Arrays.toString(pb.command().toArray()) + " in " + workingDirectory);

        return OsUtils.startProcess(pb, logger);
    }

    private static String prepareExecutable(
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

        LogUtils.info(logger, "Using Katalon Studio at " + katalonDirPath);

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

    private static String generateCommand(String executableFile, String arguments, String projectPath) {
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
