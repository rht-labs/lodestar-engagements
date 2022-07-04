package com.redhat.labs.lodestar.engagements.rest.client;

import com.redhat.labs.lodestar.engagements.exception.EngagementGitlabException;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class GitlabApiClientTest {
    private static final int JENNY = 8675309;
    private static final int LITTLE_JENNY = 8675308;

    @Inject
    GitlabApiClient gitlabApiClient;

    @Test
    void testGetEngagementException() {
        EngagementGitlabException ex = assertThrows(EngagementGitlabException.class, () -> gitlabApiClient.getEngagement(JENNY));

        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void testGetEngagementNotFound() {
        Optional<Engagement> option = gitlabApiClient.getEngagement(LITTLE_JENNY);

        assertTrue(option.isEmpty());
    }

    @Test
    void testGetProjectException() {
        EngagementGitlabException ex = assertThrows(EngagementGitlabException.class, () -> gitlabApiClient.getProject(JENNY));

        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void testGetGroupException() {
        EngagementGitlabException ex = assertThrows(EngagementGitlabException.class, () -> gitlabApiClient.getGroup(String.valueOf(JENNY)));

        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void testGetLegacyJsonException() {
        EngagementGitlabException ex = assertThrows(EngagementGitlabException.class, () -> gitlabApiClient.getLegacyEngagement(JENNY));

        assertEquals(500, ex.getStatusCode());
    }

    @Test
    void testGetLegacyNotFound() {
        String legacy = gitlabApiClient.getLegacyEngagement(LITTLE_JENNY);

        assertNull(legacy);
    }
}
