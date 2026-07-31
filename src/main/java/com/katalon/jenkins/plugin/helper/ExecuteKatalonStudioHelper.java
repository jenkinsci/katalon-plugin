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
            throw e;
        } catch (Exception e) {
            logger.info(Throwables.getStackTraceAsString(e));
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

        public InterruptibleKatalonCallable(TaskListener taskListener, FilePath workspace,
                                            EnvVars buildEnvironment, Logger logger, String version,
                                            String location, String executeArgs,
                                            String x11Display, String xvfbConfiguration) {
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

            Map<String, String> environmentVariables = new HashMap<>(System.getenv());
            buildEnvironment.forEach(environmentVariables::put);

            if (Thread.currentThread().isInterrupted()) {
                logger.info("Task was interrupted before executing Katalon");
                throw new InterruptedException("Execution was cancelled");
            }

            try {
                return executeKatalonWithInterruption(
                        logger, version, location, workspaceLocation,
                        executeArgs, x11Display, xvfbConfiguration, environmentVariables);
            } catch (InterruptedException e) {
                logger.info("Katalon execution was interrupted due to build cancellation");
                throw e;
            }
        }

        private Boolean executeKatalonWithInterruption(
                Logger logger, String version, String location, String workspaceLocation,
                String executeArgs, String x11Display, String xvfbConfiguration,
                Map<String, String> environmentVariables) throws Exception {

            // Phase 1: Setup — KatalonUtils may block here (e.g., downloading Katalon).
            // Run in a thread so the stop signal can still be honored during this phase.
            final Process[] processHolder = {null};
            final Exception[] setupException = {null};

            Thread setupThread = new Thread(() -> {
                try {
                    processHolder[0] = KatalonUtils.executeKatalonProcess(
                            logger, version, location, workspaceLocation,
                            executeArgs, x11Display, xvfbConfiguration, environmentVariables);
                } catch (Exception e) {
                    setupException[0] = e;
                }
            });
            setupThread.start();

            while (processHolder[0] == null && setupThread.isAlive()) {
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Build cancellation detected during Katalon setup");
                    setupThread.interrupt();
                    setupThread.join(3000);
                    throw new InterruptedException("Cancelled during Katalon setup");
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    logger.info("Build cancellation detected during Katalon setup");
                    setupThread.interrupt();
                    throw e;
                }
            }

            if (setupException[0] != null) {
                throw setupException[0];
            }

            Process katalonProcess = processHolder[0];
            if (katalonProcess == null) {
                return false;
            }

            // Phase 2: Process is running — waitFor() throws InterruptedException
            // automatically when Jenkins interrupts the thread on build stop.
            try {
                int exitCode = katalonProcess.waitFor();
                return exitCode == 0;
            } catch (InterruptedException e) {
                logger.info("Build cancellation detected, killing Katalon process tree");
                killProcessTree(katalonProcess, logger);
                throw e;
            }
        }

        /**
         * Kills the entire process tree rooted at {@code process}: descendants first
         * (deepest children before parents), then the root process itself.
         */
        private static void killProcessTree(Process process, Logger logger) {
            process.descendants().forEach(ph -> {
                logger.info("Killing child process: " + ph.pid()
                        + ph.info().command().map(c -> " (" + c + ")").orElse(""));
                ph.destroyForcibly();
            });
            logger.info("Killing Katalon process: " + process.pid());
            process.destroyForcibly();
        }
    }
}
