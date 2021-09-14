package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.model.CreationDetails;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.repository.EngagementRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(ExternalApiWireMock.class)
class GitlabServiceTest {

    @Inject
    EngagementRepository engagementRepository;

    @Inject
    GitlabService gitlabService;

    @Inject
    ConfigService configService;

    @Test
    void testCreateEngagement() {
        configService.getHookConfig();

        Engagement engagement = Engagement.builder().uuid("engagement-ten").customerName("customer-ten").name("engagement-ten")
                .region("na").type("Residency").build();

        engagementRepository.persist(engagement);

        gitlabService.createEngagementInGitlab(engagement);

        Optional<Engagement> option = engagementRepository.getEngagement("engagement-ten");

        assertTrue(option.isPresent());
        assertEquals(11, option.get().getProjectId());
    }

    @Test
    void testUpdateEngagementNameChange() {
        Instant now = Instant.now();

        Engagement engagement = Engagement.builder().uuid("engagement-twelve").customerName("customer-twelve").name("engagement-twelve")
                .region("na").type("Residency").createdDate(now).lastMessage("")
                .creationDetails(CreationDetails.builder().createdOn(now).createdByUser("Billy Berue").createdByEmail("jack@beanstalk.com").build())
                .projectId(12).build();

        engagementRepository.persist(engagement);
        engagement.setStartDate(Instant.now()); //Fake change

        gitlabService.updateEngagementInGitlab(engagement);

        Optional<Engagement> option = engagementRepository.getEngagement("engagement-twelve");

        assertTrue(option.isPresent());
        assertTrue(engagement.getLastMessage().startsWith("Summary Update"));
    }

    @Test
    void testUpdateEngagementNameChangeTransferProject() {

        Instant now = Instant.now();

        Engagement engagement = Engagement.builder().uuid("engagement-fifteen").customerName("customer-fifteen").name("engagement-fifteen")
                .region("na").type("Residency").createdDate(now).lastMessage("")
                .creationDetails(CreationDetails.builder().createdOn(now).createdByUser("Billy Berue").createdByEmail("jack@beanstalk.com").build())
                .projectId(15).build();

        engagementRepository.persist(engagement);
        engagement.setStartDate(Instant.now()); //Fake change

        gitlabService.updateEngagementInGitlab(engagement);

        Optional<Engagement> option = engagementRepository.getEngagement("engagement-fifteen");

        assertTrue(option.isPresent());
        assertTrue(engagement.getLastMessage().startsWith("Summary Update"));
    }
}
