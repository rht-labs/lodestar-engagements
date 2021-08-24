package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EngagementServiceTest {

    @Inject
    EngagementService engagementService;

    @BeforeEach
    void init() {
        engagementService.refresh();
    }

    @Test
    void testCreateException() {
        final Engagement engagement = Engagement.builder().customerName("Banana Hut").name("banana").build();
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> engagementService.create(engagement));

        assertEquals(409, ex.getResponse().getStatus());

        final Engagement badUuid = Engagement.builder().uuid("not-null").build();
        ex = assertThrows(WebApplicationException.class, () -> engagementService.create(badUuid));

        assertEquals(400, ex.getResponse().getStatus());
    }

    @Test
     void testUpdateException() {
        final Engagement engagement = Engagement.builder().uuid("not-found").build();

        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> engagementService.update(engagement, false));

        assertEquals(404, ex.getResponse().getStatus());

    }
}
