package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.exception.EngagementException;
import com.redhat.labs.lodestar.engagements.exception.EngagementGitlabException;
import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.model.CreationDetails;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.repository.EngagementRepository;
import com.redhat.labs.lodestar.engagements.rest.client.GitlabApiClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(ExternalApiWireMock.class)
class GitlabServiceTest {

    @Inject
    EngagementRepository engagementRepository;

    @Inject
    EngagementService engagementService;

    @Inject
    GitlabService gitlabService;

    @Inject
    ConfigService configService;

    @InjectSpy
    GitlabApiClient gitlabApiClient;

    @BeforeEach
    void init() {
        engagementService.refresh();
    }

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

        assertTrue(engagementRepository.getEngagement(engagement.getUuid()).isPresent());

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

    @Test
    void testUpdateCustomerNameChange() {
        Instant now = Instant.now();

        Engagement engagement = Engagement.builder().uuid("name-change-uuid").customerName("customer-org-name").name("eng-org-name")
                .region("na").type("Residency").createdDate(now).lastMessage("")
                .creationDetails(CreationDetails.builder().createdOn(now).createdByUser("Billy Berue").createdByEmail("jack@beanstalk.com").build())
                .projectId(20).build();

        engagementRepository.persist(engagement);
        engagement.setStartDate(Instant.now()); //Fake change
        engagement.setCustomerName("customer-name-change");

        gitlabService.updateEngagementInGitlab(engagement);

        Mockito.verify(gitlabApiClient).transferProject(20,24);
    }

    @Test
    void testDeleteEngagementError() {
        Engagement e = Engagement.builder().customerName("del").name("ete").projectId(10001).build();

        EngagementException ex = assertThrows(EngagementException.class, () -> {
            gitlabService.deleteEngagementInGitlab(e);
        });

        assertEquals("Unable to delete engagement " + e, ex.getMessage());
    }

    @Test
    void testDeleteEngagementWithOneSubGroup() {
        Engagement e = Engagement.builder().customerName("delete").name("one").projectId(-1).build();
        gitlabService.deleteEngagementInGitlab(e);
        Mockito.verify(gitlabApiClient).deleteGroup(27);
    }

    @Test
    void testDeleteEngagementWithMultipleSubGroups() {
        Engagement e = Engagement.builder().customerName("delete").name("two").projectId(-1).build();
        gitlabService.deleteEngagementInGitlab(e);
        Mockito.verify(gitlabApiClient).deleteGroup("john/engagements/delete/two");
    }

}
