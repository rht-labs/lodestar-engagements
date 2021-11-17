package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.*;
import com.redhat.labs.lodestar.engagements.rest.client.*;
import org.eclipse.microprofile.rest.client.inject.*;
import org.slf4j.*;

import javax.enterprise.context.*;
import javax.inject.*;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import java.util.*;

@ApplicationScoped
public class ParticipantService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParticipantService.class);

    @Inject
    @RestClient
    ParticipantApiClient participantApiClient;

    public void addEngagementCount(List<Engagement> engagements) {
        LOGGER.debug("Get counts for {} engagements", engagements.size());
        try {
            Map<String, Integer> engagementCounts = participantApiClient.getParticipantCounts();
            engagements.forEach(e -> getCount(e, engagementCounts.get(e.getUuid())));
        } catch (WebApplicationException | ProcessingException ex) {
            LOGGER.error("Unable to fetch participant counts so they won't be set and are likely inaccurate", ex);
        }
    }

    private void getCount(Engagement engagement, Integer count) {
        int value = (count == null) ? 0 : count;

        engagement.setParticipantCount(value);
    }



}
