package com.katalon.jenkins.plugin;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.katalon.jenkins.plugin.helper.JenkinsLogger;
import com.katalon.utils.KatalonUtils;
import com.katalon.utils.LogUtils;
import com.katalon.utils.Logger;
import com.katalon.utils.OsUtils;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.console.LineTransformationOutputStream;
import hudson.model.TaskListener;
import hudson.util.ProcessTree;
import jenkins.security.MasterToSlaveCallable;

public class InterruptibleCallable extends MasterToSlaveCallable<Boolean, Exception> {
    static final String PROCESS_MARKER_ENV = "KATALON_JENKINS_PROCESS_MARKER";
    private static final long GRACEFUL_STOP_TIMEOUT_SECONDS = 300;

    private final TaskListener taskListener;
    private final FilePath workspace;
    private final EnvVars buildEnvironment;
    private final String version;
    private final String location;
    private final String executeArgs;
    private final String x11Display;
    private final String xvfbConfiguration;
    private final String processMarker;

    public InterruptibleCallable(TaskListener taskListener, FilePath workspace,
                                 EnvVars buildEnvironment, String version,
                                 String location, String executeArgs,
                                 String x11Display, String xvfbConfiguration,
                                 String processMarker) {
        this.taskListener = taskListener;
        this.workspace = workspace;
        this.buildEnvironment = buildEnvironment;
        this.version = version;
        this.location = location;
        this.executeArgs = executeArgs;
        this.x11Display = x11Display;
        this.xvfbConfiguration = xvfbConfiguration;
        this.processMarker = processMarker;
    }

    @Override
    public Boolean call() throws Exception {
        Logger logger = new JenkinsLogger(taskListener);

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
            throw new InterruptedException("Execution was cancelled");
        }

        String executableFile = setup();
        return execute(executableFile, workspaceLocation, executeArgs, x11Display, xvfbConfiguration, environmentVariables);
    }

    private String setup() throws InterruptedException {
        Logger logger = new JenkinsLogger(taskListener);
        logger.info("Setting up KRE before execution");

        final AtomicReference<Exception> exceptionRef = new AtomicReference<>(null);
        final AtomicReference<String> fileRef = new AtomicReference<>(null);

        Thread setupThread = new Thread(() -> {
            try {
                String userHome = System.getProperty("user.home");
                String executableFile = KatalonUtils.prepareExecutable(logger, version, location, userHome);

                fileRef.set(executableFile);
            } catch (Exception e) {
                exceptionRef.set(e);
            }
        });
        setupThread.start();

        try {
            setupThread.join();
        } catch (InterruptedException e) {
            logger.info("Build cancellation detected during KRE setup, cancel the setup");
            setupThread.interrupt();
            throw e;
        }

        if (exceptionRef.get() != null) {
            throw new RuntimeException("Failed to setup KRE", exceptionRef.get());
        }

        return fileRef.get();
    }

    private Boolean execute(
            String executableFile, String workspaceLocation, String executeArgs, String x11Display,
            String xvfbConfiguration, Map<String, String> environmentVariables
    ) throws Exception {
        Logger logger = new JenkinsLogger(taskListener);
        logger.info("Preparing KRE command with provided arguments");

        String command = KatalonUtils.generateKreCommand(executableFile, executeArgs, workspaceLocation);
        Path workingDirectory = Files.createTempDirectory("katalon-");

        // Only used to build the OS-specific command line (cmd /c | sh -c, DISPLAY, xvfb-run)
        List<String> commandLine = OsUtils.buildProcess(
                command,
                workingDirectory,
                x11Display,
                xvfbConfiguration,
                environmentVariables).command();
        LogUtils.info(logger, "Executing " + commandLine + " in " + workingDirectory);

        // Tag KRE and all of its descendants so they can be found again for the graceful stop
        environmentVariables.put(PROCESS_MARKER_ENV, processMarker);

        Launcher launcher = new Launcher.LocalLauncher(taskListener);
        // Prefix every line of KRE output with the timestamp, like the logger does
        OutputStream output = new LineTransformationOutputStream() {
            @Override
            protected void eol(byte[] b, int len) {
                logger.info(new String(b, 0, len, StandardCharsets.UTF_8).stripTrailing());
            }
        };
        Proc proc = launcher.launch()
                .cmds(commandLine)
                .envs(environmentVariables)
                .pwd(new FilePath(workingDirectory.toFile()))
                .stdout(output)
                .quiet(true)
                .start();

        logger.info("Waiting for KRE execution to finish");
        int exitCode = proc.join();
        logger.info("KRE execution finished with exit code: " + exitCode);

        output.close();
        return exitCode == 0;
    }

    /**
     * Asks the top-most process tagged with {@code marker} to terminate (SIGTERM on Unix), then waits up to
     * {@link #GRACEFUL_STOP_TIMEOUT_SECONDS} seconds for all tagged processes to exit. Whatever is still
     * alive after that is killed forcibly. Safe to call more than once.
     */
    static void stopGracefully(String marker, TaskListener taskListener) throws InterruptedException {
        Logger logger = new JenkinsLogger(taskListener);
        logger.info("Build cancellation detected, sending SIGTERM to KRE process");

        Map<String, String> markerEnv = Collections.singletonMap(PROCESS_MARKER_ENV, marker);
        for (ProcessTree.OSProcess osProcess : ProcessTree.get()) {
            // Only the top-most tagged process, i.e. the one we launched; it is responsible for its children
            ProcessTree.OSProcess parent = osProcess.getParent();
            boolean isRoot = parent == null || !parent.hasMatchingEnvVars(markerEnv);
            if (isRoot && osProcess.hasMatchingEnvVars(markerEnv)) {
                logger.info("Sending SIGTERM to KRE process PID: " + osProcess.getPid());
                ProcessHandle.of(osProcess.getPid()).ifPresent(ProcessHandle::destroy);
            }
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(GRACEFUL_STOP_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (!hasRunning(marker)) {
                logger.info("KRE stopped gracefully");
                return;
            }
            Thread.sleep(500);
        }

        logger.info("KRE did not stop within " + GRACEFUL_STOP_TIMEOUT_SECONDS + " seconds, killing process tree");
        for (ProcessTree.OSProcess osProcess : ProcessTree.get()) {
            if (osProcess.hasMatchingEnvVars(markerEnv)) {
                osProcess.killRecursively();
            }
        }
    }

    private static boolean hasRunning(String marker) {
        Map<String, String> markerEnv = Collections.singletonMap(PROCESS_MARKER_ENV, marker);
        for (ProcessTree.OSProcess osProcess : ProcessTree.get()) {
            if (osProcess.hasMatchingEnvVars(markerEnv)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Runs on the same node as {@link InterruptibleCallable}; the master invokes it when the build is
     * aborted so that the stop (and its log output) completes before the build is finished.
     */
    public static class StopCallable extends MasterToSlaveCallable<Void, InterruptedException> {
        private final TaskListener taskListener;
        private final String processMarker;

        public StopCallable(TaskListener taskListener, String processMarker) {
            this.taskListener = taskListener;
            this.processMarker = processMarker;
        }

        @Override
        public Void call() throws InterruptedException {
            stopGracefully(processMarker, taskListener);
            return null;
        }
    }
}
