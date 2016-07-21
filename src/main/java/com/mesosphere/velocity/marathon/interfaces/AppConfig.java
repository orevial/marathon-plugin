package com.mesosphere.velocity.marathon.interfaces;

import com.mesosphere.velocity.marathon.fields.MarathonLabel;
import com.mesosphere.velocity.marathon.fields.MarathonUri;

import java.util.List;
import java.util.Map;

public interface AppConfig {
    /**
     * Get the configured Marathon Application Id.
     *
     * @return the Marathon Application Id
     */
    String getAppId();

    /**
     * Get the URL for the target Marathon instance.
     *
     * @return Marathon instance URL
     */
    String getUrl();

    /**
     * Get the configured docker map.
     *
     * @return Docker image name
     */
    Map<String, Object> getDocker();

    /**
     * Get the Jenkins credentials id for this configuration.
     *
     * @return credentials id
     */
    String getCredentialsId();

    /**
     * Return a list of configured URIs. These will be downloaded into the container
     * sandbox prior to the Marathon application starting.
     *
     * @return list of URIs
     */
    List<MarathonUri> getUris();

    /**
     * Return a list of labels. Marathon uses Labels to attach metadata to an application.
     *
     * @return list of labels
     */
    List<MarathonLabel> getLabels();
}
