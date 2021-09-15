package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.rest.client.ArtifactApiClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ArtifactService {

    @Inject
    @RestClient
    ArtifactApiClient artifactApiClient;

    public void addEngagementCount(List<Engagement> engagements) {
        Map<String, Integer> engagementCounts = artifactApiClient.getArtifactCounts();
        engagements.forEach(e -> getCount(e, engagementCounts.get(e.getUuid())));
    }

    private void getCount(Engagement engagement, Integer count) {
        int value = (count == null) ? 0 : count;

        engagement.setArtifactCount(value);
    }

}
