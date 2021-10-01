package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.rest.client.ActivityApiClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ActivityService {

    @Inject
    @RestClient
    ActivityApiClient activityApiClient;

    public void getLastActivityPerEngagement(List<Engagement> engagements) {
        Map<String, OffsetDateTime> lastUpdates = activityApiClient.getActivityPerEngagement();

        engagements.forEach(e -> getLastActivity(e, lastUpdates.get(e.getUuid())));
    }

    private void getLastActivity(Engagement engagement, OffsetDateTime lastUpdate) {
        if(lastUpdate != null) {
            engagement.setLastUpdate(lastUpdate.toInstant());
        }
    }

}
