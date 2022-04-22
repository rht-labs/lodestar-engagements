package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    @Test
    void testEngagementSearch() {

        engagementService.create(Engagement.builder().name("Circus").customerName("ANAFARK").type("do500").region("latam").build());
        engagementService.create(Engagement.builder().name("Circus").customerName("ANAFARL").type("do500").region("na").build());
        engagementService.create(Engagement.builder().name("Circus").customerName("ANAFARM").type("Residency").categories(Set.of("one", "two", "three")).region("na").build());

        PageFilter pf = PageFilter.builder().pageSize(100).page(0).build();

        List<Engagement> engagements = engagementService.findEngagements(pf, "aNa", null, Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

        assertEquals(5, engagements.size());

        engagements = engagementService.findEngagements(pf, "aNa", null, Set.of("na"), Collections.emptySet(), Collections.emptySet());
        assertEquals(4, engagements.size());

        engagements = engagementService.findEngagements(pf, "aNa", null, Set.of("na"), Set.of("do500"), Collections.emptySet());
        assertEquals(3, engagements.size());

        engagements = engagementService.findEngagements(pf, null, "two", Set.of("na"), Set.of("Residency"), Collections.emptySet());
        assertEquals(1, engagements.size());


    }

}
