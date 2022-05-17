package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.EngagementState;
import com.redhat.labs.lodestar.engagements.model.Launch;
import com.redhat.labs.lodestar.engagements.repository.EngagementRepository;
import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class EngagementServiceTest {

    @Inject
    EngagementService engagementService;

    @Inject
    EngagementRepository repository;

    @BeforeEach
    void init() {
        engagementService.refresh();
    }

    @Test
    void testDBCheck() {
        engagementService.checkDB();
        assertEquals(2, repository.findAll().stream().count());

        repository.deleteAll();

        engagementService.checkDB();
        assertEquals(2, repository.findAll().stream().count());
    }

    @Test
    void testLastUpdate() {
        engagementService.checkLastUpdate();

        List<Engagement> engagements = engagementService.getEngagements();
        Engagement engagement = engagements.iterator().next();
        String uuid = engagement.getUuid();
        engagement.setLastUpdate(null);
        repository.update(engagement);

        engagementService.checkLastUpdate();
        engagement = engagementService.getEngagement(uuid).orElse(null);
        assertNotNull(engagement);
        assertNotNull(engagement.getLastUpdate());
    }

    @Test
    void testStatusTimer() {
        String uuid = "status-change";
        Launch l = Launch.builder().launchedBy("Eric").launchedByEmail("eric@redhat.com").launchedDateTime(Instant.now()).build();
        Instant start = Instant.now().minus(Duration.ofDays(60));
        Instant end = Instant.now().minus(Duration.ofDays(30));
        Engagement e = Engagement.builder().uuid((uuid)).name("Status Change").customerName("Status Customer").type("Residency").region("na").launch(l).startDate(start).endDate(end).build();

        repository.persist(e);

        Optional<Engagement> oe = repository.getEngagement(uuid);
        assertTrue(oe.isPresent());

        engagementService.updateStatusTimer();

        oe = repository.getEngagement(uuid);
        assertTrue(oe.isPresent());

        e = oe.get();
        assertEquals(EngagementState.PAST, e.getState());
        assertEquals(EngagementState.PAST, e.getCurrentState());

        engagementService.updateStatusTimer();

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
