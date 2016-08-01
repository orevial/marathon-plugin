package com.mesosphere.velocity.marathon.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import mesosphere.marathon.client.Marathon;
import mesosphere.marathon.client.MarathonClient;
import mesosphere.marathon.client.model.v2.App;
import mesosphere.marathon.client.utils.MarathonException;
import mesosphere.marathon.client.utils.ModelUtils;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkinsci.plugins.plaincredentials.StringCredentials;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileInvalidException;
import com.mesosphere.velocity.marathon.exceptions.MarathonFileMissingException;
import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;
import com.mesosphere.velocity.marathon.interfaces.AppConfig;
import com.mesosphere.velocity.marathon.interfaces.MarathonBuilder;
import com.mesosphere.velocity.marathon.util.MarathonBuilderUtils;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Util;

public class MarathonBuilderImpl extends MarathonBuilder {
    private AppConfig  config;
    private JSONObject json;
    private App        app;
    private EnvVars    envVars;
    private FilePath   workspace;

    public MarathonBuilderImpl() {
        this(null);
    }

    public MarathonBuilderImpl(final AppConfig config) {
        this.config = config;
        this.envVars = new EnvVars();
    }

    public String getUrl() {
        return config.getUrl();
    }

    public List<MarathonUri> getUris() {
        return config.getUris();
    }

    public List<MarathonLabel> getLabels() {
        return config.getLabels();
    }

    public String getAppid() {
        return config.getAppId();
    }

    public Map<String, Object> getDocker() {
        return config.getDocker();
    }

    @Override
    public MarathonBuilder update() throws MarathonException {
        if (app != null) {
            final UsernamePasswordCredentials usercreds = MarathonBuilderUtils.getUsernamePasswordCredentials(config.getCredentialsId());
            final StringCredentials creds = MarathonBuilderUtils.getTokenCredentials(config.getCredentialsId());
            final Marathon marathon = creds == null ?
                    usercreds == null ? MarathonClient.getInstance(config.getUrl()) :
                            MarathonClient.getInstanceWithBasicAuth(config.getUrl(), usercreds.getUsername(), usercreds.getPassword().getPlainText()) :
                    MarathonClient.getInstanceWithTokenAuth(config.getUrl(), creds.getSecret().getPlainText());
            marathon.updateApp(app.getId(), app, true);   // uses PUT
        }
        return this;
    }

    @Override
    public MarathonBuilder read(final String filename) throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        final String   realFilename = filename != null ? filename : MarathonBuilderUtils.MARATHON_JSON;
        final FilePath marathonFile = workspace.child(realFilename);

        if (!marathonFile.exists()) {
            throw new MarathonFileMissingException(realFilename);
        } else if (marathonFile.isDirectory()) {
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory.");
        }

        final String content = marathonFile.readToString();
        this.json = JSONObject.fromObject(content);
        return this;
    }

    @Override
    public MarathonBuilder read() throws IOException, InterruptedException, MarathonFileMissingException, MarathonFileInvalidException {
        return read(null);
    }

    @Override
    public MarathonBuilder setJson(final JSONObject json) {
        this.json = json;
        return this;
    }

    @Override
    public JSONObject getJson() {
        return this.json;
    }

    @Override
    public MarathonBuilder setEnvVars(final EnvVars vars) {
        this.envVars = vars;
        return this;
    }

    @Override
    public MarathonBuilder setConfig(final AppConfig config) {
        this.config = config;
        return this;
    }

    @Override
    public MarathonBuilder setWorkspace(final FilePath ws) {
        this.workspace = ws;
        return this;
    }

    @Override
    public MarathonBuilder build() {
        setId();
        setDockerImage();
        setUris();
        setLabels();

        this.app = ModelUtils.GSON.fromJson(json.toString(), App.class);
        return this;
    }

    @Override
    public MarathonBuilder toFile(final String filename) throws InterruptedException, IOException, MarathonFileInvalidException {
        final String   realFilename     = filename != null ? filename : MarathonBuilderUtils.MARATHON_RENDERED_JSON;
        final FilePath renderedFilepath = workspace.child(Util.replaceMacro(realFilename, envVars));
        if (renderedFilepath.exists() && renderedFilepath.isDirectory())
            throw new MarathonFileInvalidException("File '" + realFilename + "' is a directory; not overwriting.");

        renderedFilepath.write(json.toString(), null);
        return this;
    }

    @Override
    public MarathonBuilder toFile() throws InterruptedException, IOException, MarathonFileInvalidException {
        return toFile(null);
    }

    private JSONObject setId() {
        if (config.getAppId() != null && config.getAppId().trim().length() > 0)
            json.put(MarathonBuilderUtils.JSON_ID_FIELD, Util.replaceMacro(config.getAppId(), envVars));

        return json;
    }

    private JSONObject setDockerImage() {
        if (config.getDocker() != null && config.getDocker().get("image") != null && config.getDocker().get("image").toString().trim().length() > 0) {
            // get container -> docker -> image
            if (!json.has(MarathonBuilderUtils.JSON_CONTAINER_FIELD)) {
                json.element(MarathonBuilderUtils.JSON_CONTAINER_FIELD,
                        JSONObject.fromObject(MarathonBuilderUtils.JSON_EMPTY_CONTAINER));
            }

            final JSONObject container = json.getJSONObject(MarathonBuilderUtils.JSON_CONTAINER_FIELD);

            if (!container.has(MarathonBuilderUtils.JSON_DOCKER_FIELD)) {
                container.element(MarathonBuilderUtils.JSON_DOCKER_FIELD, new JSONObject());
            }

            container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD)
                    .element(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FIELD,
                            Util.replaceMacro(config.getDocker().get("image").toString(), envVars));

            container.getJSONObject(MarathonBuilderUtils.JSON_DOCKER_FIELD)
                    .element(MarathonBuilderUtils.JSON_DOCKER_IMAGE_FORCE_PULL, config.getDocker().getOrDefault("forcePullImage", false));
        }

        return json;
    }

    /**
     * Set the root "uris" JSON array with the URIs configured within
     * the Jenkins UI. This handles transforming Environment Variables
     * to their actual values.
     */
    private JSONObject setUris() {
        if (config.getUris().size() > 0) {
            final boolean hasUris = json.get(MarathonBuilderUtils.JSON_URI_FIELD) instanceof JSONArray;

            // create the "uris" field if one is not there or it is of the wrong type.
            if (!hasUris)
                json.element(MarathonBuilderUtils.JSON_URI_FIELD, new JSONArray());

            for (MarathonUri uri : config.getUris()) {
                json.accumulate(MarathonBuilderUtils.JSON_URI_FIELD, Util.replaceMacro(uri.getUri(), envVars));
            }
        }

        return json;
    }

    private JSONObject setLabels() {
        if (!json.has("labels"))
            json.element("labels", new JSONObject());

        final JSONObject labelObject = json.getJSONObject("labels");
        for (MarathonLabel label : config.getLabels()) {
            labelObject.element(Util.replaceMacro(label.getName(), envVars),
                    Util.replaceMacro(label.getValue(), envVars));
        }

        return json;
    }
}
