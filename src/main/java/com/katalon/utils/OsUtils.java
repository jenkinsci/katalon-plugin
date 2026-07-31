package com.katalon.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

public class OsUtils {

    public static String getOSVersion(Logger logger) {

        if (SystemUtils.IS_OS_WINDOWS) {

            try {
                Process p = Runtime.getRuntime().exec(new String[]{"wmic", "os", "get", "osarchitecture"});
                try (InputStream inputStream = p.getInputStream()) {
                    String output = IOUtils.toString(inputStream, (Charset)null);
                    p.destroy();

                    if (output.contains("64")) {
                        return "windows 64";
                    } else {
                        return "windows 32";
                    }
                }
            } catch (Exception e) {
                LogUtils.info(logger, "Cannot detect the OS architecture. Assume it is x64.");
                LogUtils.info(logger, "Reason: " + e.getMessage() + ".");
                return "windows 64";
            }

        } else if (SystemUtils.IS_OS_MAC) {
            return "macos (app)";
        } else if (SystemUtils.IS_OS_LINUX) {
            return "linux";
        }
        return "";
    }

    public static boolean runCommand(
            Logger logger,
            String command,
            Path workingDirectory,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables)
            throws IOException, InterruptedException {

        ProcessBuilder pb = buildProcess(
                command,
                workingDirectory,
                x11Display,
                xvfbConfiguration,
                environmentVariables);
        LogUtils.info(logger, "Execute " + Arrays.toString(pb.command().toArray()) + " in " + workingDirectory);

        Process cmdProcess = startProcess(pb, logger);
        cmdProcess.waitFor();

        LogUtils.info(logger, MessageFormat.format("Finished executing {0}. Exit code: {1}.", String.join(" ", pb.command()), cmdProcess.exitValue()));
        return cmdProcess.exitValue() == 0;
    }

    public static ProcessBuilder buildProcess(
            String command,
            Path workingDirectory,
            String x11Display,
            String xvfbConfiguration,
            Map<String, String> environmentVariables) {

        String[] commands;

        if (SystemUtils.IS_OS_WINDOWS) {
            commands = Arrays.asList("cmd", "/c", command).toArray(new String[]{});
        } else {
            if (!StringUtils.isBlank(x11Display)) {
                command = "DISPLAY=" + x11Display + " " + command;
            }
            if (!StringUtils.isBlank(xvfbConfiguration)) {
                command = "xvfb-run " + xvfbConfiguration + " " + command;
            }
            List<String> cmdlist = Arrays.asList("sh", "-c", command);
            commands = cmdlist.toArray(new String[]{});
        }

        ProcessBuilder pb = new ProcessBuilder(commands);
        Map<String, String> env = pb.environment();

        if (environmentVariables != null) {
            env.putAll(environmentVariables);
        }

        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true);

        return pb;
    }

    public static Process startProcess(ProcessBuilder pb, Logger logger) throws IOException {
        Process cmdProcess = pb.start();

        try (
                BufferedReader stdoutReader = new BufferedReader(
                        new InputStreamReader(
                                cmdProcess.getInputStream(), StandardCharsets.UTF_8))
        ) {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                LogUtils.info(logger, line);
            }
        }

        return cmdProcess;
    }
}
