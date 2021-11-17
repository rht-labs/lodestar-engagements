package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.rest.client.ActivityApiClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ActivityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActivityService.class);

    @Inject
    @RestClient
    ActivityApiClient activityApiClient;

    public void getLastActivityPerEngagement(List<Engagement> engagements) {
        try {
            Map<String, OffsetDateTime> lastUpdates = activityApiClient.getActivityPerEngagement();
            engagements.forEach(e -> getLastActivity(e, lastUpdates.get(e.getUuid())));
        } catch (WebApplicationException | ProcessingException ex) {
            LOGGER.error("Unable to fetch last activity so they won't be set and are likely inaccurate", ex);
        }
    }

    private void getLastActivity(Engagement engagement, OffsetDateTime lastUpdate) {
        if(lastUpdate != null) {
            engagement.setLastUpdate(lastUpdate.toInstant());
        }
    }

}
