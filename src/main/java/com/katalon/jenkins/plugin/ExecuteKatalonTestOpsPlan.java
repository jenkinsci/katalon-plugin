package com.katalon.jenkins.plugin;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.katalon.jenkins.plugin.entity.Plan;
import com.katalon.jenkins.plugin.entity.Project;
import com.katalon.jenkins.plugin.helper.KatalonTestOpsHelper;
import com.katalon.jenkins.plugin.helper.KatalonTestOpsSearchHelper;
import com.katalon.jenkins.plugin.helper.JenkinsLogger;
import com.katalon.jenkins.plugin.helper.SecurityHelper;
import com.katalon.utils.Logger;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.*;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.*;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.UUID;

public class ExecuteKatalonTestOpsPlan extends Builder implements SimpleBuildStep {

  private String credentialsId;

  private String plan;

  private String serverUrl;

  private String projectId;

  @DataBoundConstructor
  public ExecuteKatalonTestOpsPlan(
      String credentialsId,
      String serverUrl,
      String projectId,
      String planId) {
    if (serverUrl == null) {
      serverUrl = "";
    } else {
      serverUrl = Util.fixEmptyAndTrim(serverUrl);
    }
    assert serverUrl != null;
    if (serverUrl.endsWith("/")) {
      this.serverUrl = serverUrl.substring(0, serverUrl.length() - 1);
    } else {
      this.serverUrl = serverUrl;
    }
    this.credentialsId = credentialsId;
    this.plan = planId;
    this.projectId = projectId;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  public void setCredentialsId(String credentialsId) {
    this.credentialsId = credentialsId;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getPlan() {
    return plan;
  }

  public void setPlan(String plan) {
    this.plan = plan;
  }

  @Override
  public boolean perform(AbstractBuild<?, ?> abstractBuild, Launcher launcher, BuildListener buildListener)
      throws InterruptedException, IOException {
    return doPerform(buildListener);
  }

  @Override
  public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
    doPerform(taskListener);
  }

  private boolean doPerform(TaskListener taskListener) {
    Logger logger = new JenkinsLogger(taskListener);
    KatalonTestOpsHelper katalonAnalyticsHandler = new KatalonTestOpsHelper(logger);
    Secret apiKey = SecurityHelper.getApiKey(credentialsId);
    return katalonAnalyticsHandler.run(serverUrl, apiKey.getPlainText(), plan, projectId);
  }

  @Override
  public BuildStepMonitor getRequiredMonitorService() {
    return BuildStepMonitor.BUILD;
  }

  @Symbol("executeKatalonTestOps")
  @Extension
  public static class DescriptorImpl extends BuildStepDescriptor<Builder> { // Publisher because Notifiers are a type of publisher

    @Override
    public String getDisplayName() {
      return "Execute Katalon TestOps Plan";
    }

    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true; // We are always OK with someone adding this  as a build step for their job
    }

    public DescriptorImpl() {
      super(ExecuteKatalonTestOpsPlan.class);
      load();
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject formData) throws FormException {
      req.bindJSON(this, formData);
      save();
      return super.configure(req, formData);
    }

    @RequirePOST
    public FormValidation doTestConnection(@QueryParameter("serverUrl") final String url,
                                           @QueryParameter("credentialsId") final String credentialsId,
                                           @AncestorInPath Item item) {
      if (item == null) {
        Jenkins.getActiveInstance().checkPermission(Jenkins.ADMINISTER);
      } else {
        item.checkPermission(Item.CONFIGURE);
      }

      if (url.isEmpty()) {
        return FormValidation.error("Please input server url.\n Example: https://testops.katalon.io");
      }

      if (credentialsId.isEmpty()) {
        return FormValidation.error("Please select credentials.");
      }

      Secret apiKey = SecurityHelper.getApiKey(credentialsId);
      if (apiKey == null) {
        return FormValidation.error("Cannot get API key.");
      }

      try {
        KatalonTestOpsHelper katalonTestOpsHelper = new KatalonTestOpsHelper();
        String token = katalonTestOpsHelper.requestToken(url, apiKey.getPlainText());
        if (token != null) {
          return FormValidation.ok("Success!");
        } else {
          return FormValidation.error("Cannot connect to Katalon TestOps.");
        }
      } catch (Exception e) {
        return FormValidation.error("Error " + e.getMessage() + "\nCause: " + e.getCause());
      }
    }

    @RequirePOST
    public ListBoxModel doFillProjectIdItems(@QueryParameter("serverUrl") final String url,
                                             @QueryParameter("credentialsId") final String credentialsId,
                                             @AncestorInPath Item item) {
      if (item == null) {
        Jenkins.getActiveInstance().checkPermission(Jenkins.ADMINISTER);
      } else {
        item.checkPermission(Item.CONFIGURE);
      }

      if (url.isEmpty()) {
        return new ListBoxModel();
      }

      if (credentialsId.isEmpty()) {
        return new ListBoxModel();
      }

      ListBoxModel options = new ListBoxModel();
      Secret apiKey = SecurityHelper.getApiKey(credentialsId);
      if (apiKey != null) {
        KatalonTestOpsHelper katalonTestOpsHelper = new KatalonTestOpsHelper();
        try {
          String token = katalonTestOpsHelper.requestToken(url, apiKey.getPlainText());
          if (token != null) {
            KatalonTestOpsSearchHelper katalonTestOpsSearchHelper = new KatalonTestOpsSearchHelper();
            Project[] projects = katalonTestOpsSearchHelper.getProjects(token, url);
            for (Project project : projects) {
              String name = project.getName();
              name = name.replace('\b',' ');
              options.add(name, String.valueOf(project.getId()));
            }
          }
        } catch (Exception e) {
          //Do nothing here
        }
      }
      options.add("--- Please select project ---", "");
      return options;
    }

    @RequirePOST
    public ListBoxModel doFillPlanItems(@QueryParameter("serverUrl") final String url,
                                        @QueryParameter("credentialsId") final String credentialsId,
                                        @QueryParameter("projectId") final String projectId,
                                        @AncestorInPath Item item) {
      if (item == null) {
        Jenkins.getActiveInstance().checkPermission(Jenkins.ADMINISTER);
      } else {
        item.checkPermission(Item.CONFIGURE);
      }

      if (url.isEmpty()) {
        return new ListBoxModel();
      }

      if (credentialsId.isEmpty()) {
        return new ListBoxModel();
      }

      if (StringUtils.isEmpty(projectId)) {
        return new ListBoxModel();
      }

      Secret apiKey = SecurityHelper.getApiKey(credentialsId);
      if (apiKey == null) {
        return new ListBoxModel();
      }

      ListBoxModel options = new ListBoxModel();
      KatalonTestOpsHelper katalonTestOpsHelper = new KatalonTestOpsHelper();
      try {
        String token = katalonTestOpsHelper.requestToken(url, apiKey.getPlainText());
        if (token != null) {
          KatalonTestOpsSearchHelper katalonTestOpsSearchHelper = new KatalonTestOpsSearchHelper();
          Plan[] plans = katalonTestOpsSearchHelper.getPlan(token, url, projectId);
          for (Plan plan : plans) {
            options.add(plan.getName(), String.valueOf(plan.getId()));
          }
        }
      } catch (Exception e) {
        //Do nothing here
      }
      return options;
    }

    public FormValidation doCheckServerUrl(@QueryParameter String serverUrl) {
      if (StringUtils.isEmpty(serverUrl)) {
        return FormValidation.error("Please enter Server URL");
      }

      return FormValidation.ok();
    }

    public FormValidation doCheckProjectId(@QueryParameter String projectId) {
      if (StringUtils.isEmpty(projectId)) {
        return FormValidation.error("Please select project");
      }

      return FormValidation.ok();
    }

    public FormValidation doCheckPlan(@QueryParameter String plan) {
      if (StringUtils.isEmpty(plan)) {
        return FormValidation.error("Please select test plan");
      }

      return FormValidation.ok();
    }

    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item,
                                                 @QueryParameter String credentialsId) {
      Jenkins instance = Jenkins.getInstance();
      if ((item == null && !instance.hasPermission(Jenkins.ADMINISTER)) ||
        (item != null && !item.hasPermission(Item.EXTENDED_READ))) {
        return new StandardListBoxModel().includeCurrentValue(credentialsId);
      }
      if (item == null) {
        // Construct a fake project
        item = new FreeStyleProject(instance, "fake-" + UUID.randomUUID().toString());
      }

      return new StandardListBoxModel()
              .includeEmptyValue()
              .includeMatchingAs(
                      item instanceof Queue.Task
                              ? Tasks.getAuthenticationOf((Queue.Task) item)
                              : ACL.SYSTEM,
                      item,
                      StringCredentials.class,
                      URIRequirementBuilder.fromUri("").build(),
                      CredentialsMatchers.always())
              .includeCurrentValue(credentialsId);
    }

    @Override
    public Builder newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
      String credentialsId = formData.getString("credentialsId");
      return new ExecuteKatalonTestOpsPlan(
          credentialsId,
          formData.getString("serverUrl"),
          formData.getString("projectId"),
          formData.getString("plan"));
    }
  }
}
