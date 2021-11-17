package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.rest.client.ArtifactApiClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ArtifactService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactService.class);

    @Inject
    @RestClient
    ArtifactApiClient artifactApiClient;

    public void addEngagementCount(List<Engagement> engagements) {

        try {
            Map<String, Integer> engagementCounts = artifactApiClient.getArtifactCounts();
            engagements.forEach(e -> getCount(e, engagementCounts.get(e.getUuid())));
        } catch (WebApplicationException| ProcessingException ex) {
            LOGGER.error("Unable to fetch artifact counts so they won't be set and are likely inaccurate", ex);
        }
    }

    private void getCount(Engagement engagement, Integer count) {
        int value = (count == null) ? 0 : count;

        engagement.setArtifactCount(value);
    }

}
