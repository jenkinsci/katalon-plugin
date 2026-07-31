package com.katalon.jenkins.plugin;

import com.katalon.jenkins.plugin.helper.JenkinsLogger;
import com.katalon.utils.Logger;

import com.google.common.base.Throwables;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class ExecuteKatalonStudioTask extends Builder implements SimpleBuildStep {

    private String version;

    private String location;

    private String executeArgs;

    private String x11Display;

    private String xvfbConfiguration;

    @DataBoundConstructor
    public ExecuteKatalonStudioTask(
        String version,
        String location,
        String executeArgs,
        String x11Display,
        String xvfbConfiguration) {
        this.version = version;
        this.location = location;
        this.executeArgs = executeArgs;
        this.x11Display = x11Display;
        this.xvfbConfiguration = xvfbConfiguration;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getExecuteArgs() {
        return executeArgs;
    }

    public void setExecuteArgs(String executeArgs) {
        this.executeArgs = executeArgs;
    }

    public String getX11Display() {
        return x11Display;
    }

    public void setX11Display(String x11Display) {
        this.x11Display = x11Display;
    }

    public String getXvfbConfiguration() {
        return xvfbConfiguration;
    }

    public void setXvfbConfiguration(String xvfbConfiguration) {
        this.xvfbConfiguration = xvfbConfiguration;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener buildListener)
        throws InterruptedException, IOException {
        Logger logger = new JenkinsLogger(buildListener);

        // Check for interruption before starting
        if (isInterrupted()) {
            logger.info("Build was cancelled before KRE started");
            throw new InterruptedException("Build was cancelled");
        }

        FilePath workspace = abstractBuild.getWorkspace();
        EnvVars buildEnvironment = abstractBuild.getEnvironment(buildListener);
        return doPerform(workspace, buildEnvironment, launcher, buildListener);
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher,
                        @Nonnull TaskListener taskListener)
        throws InterruptedException, IOException {
        Logger logger = new JenkinsLogger(taskListener);

        // Check for interruption before starting
        if (isInterrupted()) {
            logger.info("Build was cancelled before KRE started");
            throw new InterruptedException("Build was cancelled");
        }

        EnvVars buildEnvironment = run.getEnvironment(taskListener);
        doPerform(filePath, buildEnvironment, launcher, taskListener);
    }

    private boolean doPerform(FilePath workspace, EnvVars buildEnvironment,
                              Launcher launcher, TaskListener taskListener)
        throws InterruptedException {

        Logger logger = new JenkinsLogger(taskListener);
        try {
            VirtualChannel channel = launcher.getChannel();
            if (channel == null) {
                throw new Exception("Channel not found!");
            }

            String processMarker = UUID.randomUUID().toString();
            Future<Boolean> result = channel.callAsync(new InterruptibleCallable(
                    taskListener, workspace, buildEnvironment, version,
                    location, executeArgs, x11Display, xvfbConfiguration, processMarker));
            try {
                return result.get();
            } catch (InterruptedException e) {
                // Don't cancel the remote call: stop KRE gracefully and wait for it, so that the
                // remote logs are not lost when Jenkins finishes the aborted build.
                logger.info("Build was interrupted, stopping KRE");
                channel.call(new InterruptibleCallable.StopCallable(taskListener, processMarker));

                try {
                    result.get(10, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // KRE is already stopped; the outcome no longer matters
                }

                throw e;
            }
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            logger.info(Throwables.getStackTraceAsString(e));
            return false;
        }
    }

    @Symbol("executeKatalon")
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> { // Publisher because Notifiers are a type of publisher
        @Override
        public String getDisplayName() {
            return "Execute Katalon Studio Tests"; // What people will see as the plugin name in the configs
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true; // We are always OK with someone adding this  as a build step for their job
        }

        @Override
        public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }

    private static boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }
}
