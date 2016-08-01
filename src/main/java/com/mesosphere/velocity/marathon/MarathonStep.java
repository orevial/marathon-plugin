package com.mesosphere.velocity.marathon;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarathonStep extends AbstractStepImpl implements AppConfig {
    private final String              url;
    private       List<String>        uris;
    private       Map<String, String> labels;   // this does not work :(
    private       String              appid;
    private       Map<String, Object> docker;
    private       String              filename;
    private       String              credentialsId;
    private       boolean             forceUpdate;

    @DataBoundConstructor
    public MarathonStep(final String url) {
        this.url = MarathonBuilderUtils.rmSlashFromUrl(url);
        this.uris = new ArrayList<String>(5);
        this.labels = new HashMap<String, String>(5);
    }

    @Override
    public String getAppId() {
        return this.appid;
    }

    public String getUrl() {
        return url;
    }

    public Map<String, Object> getDocker() {
        return docker;
    }

    @Override
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(final String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public List<MarathonUri> getUris() {
        final List<MarathonUri> marathonUris = new ArrayList<MarathonUri>(this.uris.size());
        for (final String u : this.uris) {
            marathonUris.add(new MarathonUri(u));
        }
        return marathonUris;
    }

    @DataBoundSetter
    public void setUris(final List<String> uris) {
        this.uris = uris;
    }

    public List<MarathonLabel> getLabels() {
        final List<MarathonLabel> marathonLabels = new ArrayList<MarathonLabel>(this.labels.size());
        for (final Map.Entry<String, String> label : this.labels.entrySet()) {
            marathonLabels.add(new MarathonLabel(label.getKey(), label.getValue()));
        }
        return marathonLabels;
    }

    @DataBoundSetter
    public void setLabels(final Map<String, String> labels) {
        this.labels = labels;
    }

    @DataBoundSetter
    public void setDocker(final Map<String, Object> docker) {
        this.docker = docker;
    }

    public String getAppid() {
        return appid;
    }

    @DataBoundSetter
    public void setAppid(final String appid) {
        this.appid = appid;
    }

    public String getFilename() {
        return filename;
    }

    @DataBoundSetter
    public void setFilename(@Nonnull final String filename) {
        if (filename.trim().length() > 0)
            this.filename = filename;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(MarathonStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "marathon";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Marathon Deployment";
        }
    }

    public static class MarathonStepExecution extends AbstractSynchronousStepExecution<Void> {
        @StepContextParameter
        private transient FilePath ws;

        @StepContextParameter
        private transient EnvVars envVars;

        @Inject
        private transient MarathonStep step;

        @Override
        protected Void run() throws Exception {
            MarathonBuilder
                    .getBuilder(step)
                    .setEnvVars(envVars)
                    .setWorkspace(ws)
                    .read(step.filename)
                    .build()
                    .toFile()
                    .update();
            return null;
        }
    }
}
