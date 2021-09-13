package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.*;
import com.redhat.labs.lodestar.engagements.rest.client.*;
import org.eclipse.microprofile.rest.client.inject.*;
import org.slf4j.*;

import javax.enterprise.context.*;
import javax.inject.*;
import java.util.*;

@ApplicationScoped
public class ParticipantService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngagementService.class);
    private static final String TOTAL_PARTICIPANTS = "x-total-participants";

    @Inject
    @RestClient
    ParticipantApiClient participantApiClient;

    public void addEngagementCount(List<Engagement> engagements) {
        Map<String, Integer> engagementCounts = participantApiClient.getParticipants();
        engagements.forEach(e -> getCount(e, engagementCounts.get(e.getUuid())));
    }

    private void getCount(Engagement engagement, Integer count) {
        int value = (count == null) ? 0 : count;

        engagement.setParticipantCount(value);
    }



}
