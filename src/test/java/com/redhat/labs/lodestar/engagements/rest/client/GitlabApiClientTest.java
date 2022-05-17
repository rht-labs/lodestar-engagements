package com.redhat.labs.lodestar.engagements.rest.client;

import com.redhat.labs.lodestar.engagements.exception.EngagementGitlabException;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Optional;

@QuarkusTest
class GitlabApiClientTest {

    @Inject
    GitlabApiClient gitlabApiClient;

    @Test
    void getEngagementException() {
        EngagementGitlabException ex = Assertions.assertThrows(EngagementGitlabException.class, () -> {
            gitlabApiClient.getEngagement(8675309);
        });

        Assertions.assertEquals(500, ex.getStatusCode());
    }

    @Test
    void getEngagementNotFound() {
        Optional<Engagement> option = gitlabApiClient.getEngagement(8675308);

        Assertions.assertTrue(option.isEmpty());
    }
}
