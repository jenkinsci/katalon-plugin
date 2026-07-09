package com.katalon.jenkins.plugin;

import com.katalon.jenkins.plugin.helper.ExecuteKatalonStudioHelper;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import javax.annotation.Nonnull;
import java.io.IOException;

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

        // Check for interruption before starting
        if (isInterrupted()) {
            buildListener.getLogger().println("Build was cancelled before Katalon execution started");
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

        // Check for interruption before starting
        if (isInterrupted()) {
            taskListener.getLogger().println("Build was cancelled before Katalon execution started");
            throw new InterruptedException("Build was cancelled");
        }

        EnvVars buildEnvironment = run.getEnvironment(taskListener);
        doPerform(filePath, buildEnvironment, launcher, taskListener);
    }

    private boolean doPerform(FilePath workspace, EnvVars buildEnvironment,
                              Launcher launcher, TaskListener taskListener)
        throws IOException, InterruptedException {

        try {
            return ExecuteKatalonStudioHelper.executeKatalon(
                    workspace,
                    buildEnvironment,
                    launcher,
                    taskListener,
                    version,
                    location,
                    executeArgs,
                    x11Display,
                    xvfbConfiguration);
        } catch (InterruptedException e) {
            taskListener.getLogger().println("Katalon execution was interrupted due to build cancellation");
            throw e; // Re-throw to ensure proper build status
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
