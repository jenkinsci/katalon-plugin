package com.katalon.jenkins.plugin.helper;

import com.google.common.base.Throwables;
import com.katalon.utils.KatalonUtils;
import com.katalon.utils.Logger;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import jenkins.security.MasterToSlaveCallable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecuteKatalonStudioHelper {

    public static boolean executeKatalon(
            FilePath workspace,
            EnvVars buildEnvironment,
            Launcher launcher,
            TaskListener taskListener,
            String version,
            String location,
            String executeArgs,
            String x11Display,
            String xvfbConfiguration) throws InterruptedException {
        Logger logger = new JenkinsLogger(taskListener);
        try {
            VirtualChannel channel = launcher.getChannel();
            if (channel == null) {
                throw new Exception("Channel not found!");
            }
            return channel.call(new InterruptibleKatalonCallable(
                    taskListener, workspace, buildEnvironment, logger, version,
                    location, executeArgs, x11Display, xvfbConfiguration));
        } catch (InterruptedException e) {
            logger.info("Katalon execution was interrupted");
            throw e; // Re-throw InterruptedException to maintain cancellation behavior
        } catch (Exception e) {
            String stackTrace = Throwables.getStackTraceAsString(e);
            logger.info(stackTrace);
            return false;
        }
    }

    private static class InterruptibleKatalonCallable extends MasterToSlaveCallable<Boolean, Exception> {
        private final TaskListener taskListener;
        private final FilePath workspace;
        private final EnvVars buildEnvironment;
        private final Logger logger;
        private final String version;
        private final String location;
        private final String executeArgs;
        private final String x11Display;
        private final String xvfbConfiguration;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public InterruptibleKatalonCallable(TaskListener taskListener, FilePath workspace,
                                            EnvVars buildEnvironment, Logger logger, String version, String location,
                                            String executeArgs, String x11Display, String xvfbConfiguration) {
            this.taskListener = taskListener;
            this.workspace = workspace;
            this.buildEnvironment = buildEnvironment;
            this.logger = logger;
            this.version = version;
            this.location = location;
            this.executeArgs = executeArgs;
            this.x11Display = x11Display;
            this.xvfbConfiguration = xvfbConfiguration;
        }

        @Override
        public Boolean call() throws Exception {
            Logger logger = new JenkinsLogger(taskListener);

            // Check for interruption at the start
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Thread was interrupted before Katalon execution started");
                throw new InterruptedException("Execution was cancelled");
            }

            if (workspace == null) {
                logger.info("No workspace provided");
                return false;
            }

            String workspaceLocation = workspace.getRemote();
            if (workspaceLocation == null) {
                logger.info("Cannot locate workspace location");
                return false;
            }

            Map<String, String> environmentVariables = new HashMap<>();
            environmentVariables.putAll(System.getenv());
            buildEnvironment.entrySet()
                    .forEach(entry -> environmentVariables.put(entry.getKey(), entry.getValue()));

            // Check for interruption before starting Katalon
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Task was interrupted before executing Katalon");
                throw new InterruptedException("Execution was cancelled");
            }

            try {
                // Create a wrapper that can be interrupted
                return executeKatalonWithInterruption(
                        logger, version, location, workspaceLocation,
                        executeArgs, x11Display, xvfbConfiguration, environmentVariables);
            } catch (InterruptedException e) {
                logger.info("Katalon execution was interrupted due to build cancellation");
                cancelled.set(true);
                throw e;
            }
        }

        private Boolean executeKatalonWithInterruption(
                Logger logger, String version, String location, String workspaceLocation,
                String executeArgs, String x11Display, String xvfbConfiguration,
                Map<String, String> environmentVariables) throws Exception {

            // Create a thread to run Katalon execution
            final Exception[] executionException = new Exception[1];
            final Boolean[] result = new Boolean[1];

            Thread katalonThread = new Thread(() -> {
                try {
                    result[0] = KatalonUtils.executeKatalon(
                            logger, version, location, workspaceLocation,
                            executeArgs, x11Display, xvfbConfiguration, environmentVariables);
                } catch (Exception e) {
                    executionException[0] = e;
                }
            });

            katalonThread.start();

            // Monitor for interruption while Katalon is running
            while (katalonThread.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Build cancellation detected, interrupting Katalon execution");

                    // Interrupt the Katalon thread
                    katalonThread.interrupt();

                    // Wait a bit for graceful shutdown
                    try {
                        katalonThread.join(5000); // Wait up to 5 seconds
                    } catch (InterruptedException ie) {
                        // If we're interrupted while waiting, force stop
                        Thread.currentThread().interrupt();
                    }
                    throw new InterruptedException("Katalon execution was cancelled");
                }

                try {
                    Thread.sleep(1000); // Check every second
                } catch (InterruptedException e) {
                    // If interrupted while sleeping, interrupt Katalon and exit
                    katalonThread.interrupt();
                    throw e;
                }
            }

            // Check if there was an exception during execution
            if (executionException[0] != null) {
                throw executionException[0];
            }
            return result[0] != null ? result[0] : false;
        }
    }
}
