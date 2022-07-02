package com.redhat.labs.lodestar.engagements.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

import com.redhat.labs.lodestar.engagements.model.EngagementState;
import com.redhat.labs.lodestar.engagements.model.UseCase;
import com.redhat.labs.lodestar.engagements.service.GitlabService;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.Launch;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@QuarkusTest
@TestHTTPEndpoint(EngagementResource.class)
@QuarkusTestResource(ExternalApiWireMock.class)
class EngagementResourceTest {

    private static final String AUTHOR = "Mitch";
    private static final String AUTHOR_EMAIL = "mitch@bucannon.com";

    @Inject
    EngagementService engagementService;

    @InjectSpy
    GitlabService gitlabService;

    @BeforeEach
    void setUp() {
        engagementService.refresh();
    }

    @Test
    void testRefresh() {
        given().when().put("refresh").then().statusCode(Status.OK.getStatusCode());
        assertEquals(2, engagementService.countAll());
    }

    @Test
    void testRefreshByUuid() {
        String uuid = "uuid1";
        Instant now = Instant.now();
        Engagement engagement = engagementService.getEngagement(uuid).orElseThrow();

        engagement.setLaunch(Launch.builder().launchedBy(AUTHOR).launchedByEmail(AUTHOR_EMAIL).launchedDateTime(now).build());
        engagementService.update(engagement, false, false);

        engagement = engagementService.getEngagement(uuid).orElseThrow();

        assertNotNull(engagement.getLaunch());

        given().queryParam("uuids", "uuid1").when().put("refresh")
                .then().statusCode(Status.OK.getStatusCode());
        assertEquals(2, engagementService.countAll());

        assertNotNull(engagement.getLaunch());
    }

    @Test
    void testGetAllEngagements() {
        given().when().get().then().statusCode(200).body("size()", equalTo(2));
    }

    @Test
    void testGetAllEngagementsForStateNoQueryParam() {
        given().when().get("inStates").then().statusCode(400);
    }

    @Test
    void testGetAllEngagementsForState() {

        given().queryParam("inStates", "ACTIVE")
                .when().get("inStates")
                .then().statusCode(200).body("size()", equalTo(0));

        given().queryParam("inStates", "UPCOMING")
                .when().get("inStates")
                .then().statusCode(200).body("size()", equalTo(2));
    }

    @Test
    void testGetEngagementsForTypeRegionAndState() {
        engagementService.create(Engagement.builder().name("unit test").customerName("blue hat").region("latam").type("Residency").currentState(EngagementState.UPCOMING).build());

        given().queryParam("inStates", EngagementState.UPCOMING)
                .queryParam("region", "na")
                .queryParam("type", "Residency")
                .when().get()
                .then().statusCode(200).body("size()", equalTo(2))
                .body("[0].region", equalTo("na"))
                .body("[0].type", equalTo("do500"));

        given().queryParam("inStates", EngagementState.UPCOMING)
                .queryParam("region", "latam")
                .queryParam("type", "DO500")
                .when().get()
                .then().statusCode(200).body("size()", equalTo(1))
                .body("[0].region", equalTo("latam"))
                .body("[0].type", equalTo("Residency"));

        given().queryParam("inStates", EngagementState.UPCOMING)
                .when().get()
                .then().statusCode(200).body("size()", equalTo(3))
                .body("[0].state", equalTo("UPCOMING"))
                .body("[1].state", equalTo("UPCOMING"))
                .body("[2].state", equalTo("UPCOMING"));
    }

    @Test
    void testGetPagedEngagements() {
        int page = 0;
        int pageSize = 2;
        given().queryParam("page", page).queryParam("pageSize", pageSize)
                .when().get().then().statusCode(200).header("x-total-engagements", equalTo("2")).body("size()", equalTo(2));
    }

    @Test
    void testGetPagedEngagementsForRegion() {
        int page = 0;
        int pageSize = 1;
        given().queryParam("page", page).queryParam("pageSize", pageSize).queryParam("region", "na")
                .queryParam("sort", "name|DESC")
                .when().get().then().statusCode(200).header("x-total-engagements", equalTo("2")).body("size()", equalTo(1));

        given().queryParam("page", page).queryParam("pageSize", pageSize).queryParam("region", "latam")
                .queryParam("sort", "name")
                .when().get().then().statusCode(200).header("x-total-engagements", equalTo("0")).body("size()", equalTo(0));
    }

    @Test
    void testGetPagedEngagementsForType() {
        int page = 0;
        int pageSize = 1;
        given().queryParam("page", page).queryParam("pageSize", pageSize).queryParam("types", "do500")
                .queryParam("sort", "name|DESC")
                .when().get().then().statusCode(200).header("x-total-engagements", equalTo("2")).body("size()", equalTo(1));

        given().queryParam("page", page).queryParam("pageSize", pageSize).queryParam("types", "OpenLeadership")
                .queryParam("sort", "name")
                .when().get().then().statusCode(200).header("x-total-engagements", equalTo("0")).body("size()", equalTo(0));
    }

    @Test
    void testGetEngagementByUuid() {
        String uuid = "uuid1";
        given().pathParam("uuid", uuid).when().get("{uuid}").then().statusCode(200).body("uuid", equalTo("uuid1")).body("name", equalTo("banana"))
                .body("customer_name", equalTo("Banana Hut")).body("categories", hasItems("mat", "pat", "rat", "bat", "cat", "fat"))
                .body("use_cases[0].uuid", equalTo("use-case-1"));
    }

    @Test
    void testGetEngagementByUuidNotFound() {
        String uuid = "uuid1111";
        given().pathParam("uuid", uuid).when().get("{uuid}").then().statusCode(404).body("message", equalTo("No engagement found for uuid uuid1111"));
    }

    @Test
    void testGetEngagementByProjectId() {
        int projectId = 1;
        given().pathParam("id", projectId).when().get("project/{id}").then().statusCode(200).body("uuid", equalTo("uuid1")).body("name", equalTo("banana"))
                .body("customer_name", equalTo("Banana Hut")).body("categories", hasItems("mat", "pat", "rat", "bat", "cat", "fat"))
                .body("use_cases[0].uuid", equalTo("use-case-1"));
    }

    @Test
    void testGetEngagementByProjectIdNotFound() {
        int projectId = 11;
        given().pathParam("id", projectId).when().get("project/{id}").then().statusCode(404).body("message", equalTo("No engagement found for project 11"));
    }

    @Test
    void testGetEngagementByCustomerAndNameNotFound() {
        String customer = "nope";
        String name = "nada";

        given().pathParam("customer", customer).pathParam("engagement", name)
                .when().get("customer/{customer}/engagement/{engagement}")
                .then().statusCode(404);
    }

    @Test
    void testGetEngagementByCustomerAndNameSuccess() {
        String customer = "Banana Hut";
        String name = "banana";

        given().pathParam("customer", customer).pathParam("engagement", name)
                .when().get("customer/{customer}/engagement/{engagement}")
                .then().statusCode(200).body("name", equalTo(name)).body("customer_name", equalTo(customer));
    }

    @Test
    void testGetEngagementCountByStatus() {
        Instant startDate = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endDate = Instant.now().plus(30, ChronoUnit.DAYS);
        Instant archiveDate = Instant.now().plus(60, ChronoUnit.DAYS);
        Launch launch = Launch.builder().launchedDateTime(startDate).launchedByEmail(AUTHOR_EMAIL).launchedBy(AUTHOR).build();

        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").type("Residency")
                .startDate(startDate).endDate(endDate).launch(launch).archiveDate(archiveDate).build();
        engagementService.create(engagement);

        endDate = Instant.now().minus(2, ChronoUnit.DAYS);
        engagement = Engagement.builder().name("DO501").customerName("Fish Gym").region("na").type("Residency")
                .startDate(startDate).endDate(endDate).archiveDate(archiveDate).launch(launch).build();
        engagementService.create(engagement);

        archiveDate = Instant.now().minus(1, ChronoUnit.DAYS);
        engagement = Engagement.builder().name("DO502").customerName("Fish Gym").region("na").type("Residency")
                .startDate(startDate).endDate(endDate).archiveDate(archiveDate).launch(launch).build();
        engagementService.create(engagement);

        engagement = Engagement.builder().name("DO503").customerName("Fish Gym").region("emea").type("Residency")
                .startDate(startDate).endDate(endDate).launch(launch).build();
        engagementService.create(engagement);

        given().when().get("count").then().statusCode(200)
                .body("UPCOMING", equalTo(2))
                .body("TERMINATING", equalTo(1))
                .body("ACTIVE", equalTo(1))
                .body("ANY", equalTo(6))
                .body("PAST", equalTo(2));

        given().queryParam("time", Instant.EPOCH.toString()).when().get("count").then().statusCode(200)
                .body("UPCOMING", equalTo(2))
                .body("ACTIVE", equalTo(4))
                .body("ANY", equalTo(6));

        given().queryParam("time", Instant.EPOCH.toString()).queryParam("region", "na").when().get("count").then().statusCode(200)
                .body("UPCOMING", equalTo(2))
                .body("ACTIVE", equalTo(3))
                .body("ANY", equalTo(5));

    }

    @Test
    void testGetEngagementByUser() {
        String email = "Calem@calm.com";
        given().pathParam("email", email).when().get("byUser/{email}")
                        .then().body("size()", equalTo(0));

        List<Engagement> engagements = engagementService.getEngagements();
        engagements.forEach(e-> {
            e.setEngagementLeadEmail(email);
            engagementService.update(e, false);
        });

        given().pathParam("email", email).when().get("byUser/{email}")
                .then().body("size()", equalTo(2));

    }

    @Test
    void testHeadLastUpdate() {
        String uuid = "uuid1";
        given().pathParam("uuid", uuid).when().head("{uuid}").then().statusCode(200)
                .header("last-update", notNullValue());
    }

    @Test
    void testHeadLastUpdateNotFound() {
        String uuid = "nope";
        given().pathParam("uuid", uuid).when().head("{uuid}").then().statusCode(404);
    }

    @Test
    void testDeleteEngagementByUuid() {
        String uuid = "uuid1";
        given().pathParam("uuid", uuid).when().delete("{uuid}").then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    void testDeleteEngagementByUuidNotFound() {
        String uuid = "uuid1111";
        given().pathParam("uuid", uuid).when().delete("{uuid}").then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    void testDeleteEngagementByUuidLaunchError() {
        Launch launch = Launch.builder().launchedBy("Jim").build();
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").launch(launch).build();
        engagementService.create(engagement);

        assertNotNull(engagement.getUuid());

        given().pathParam("uuid", engagement.getUuid()).when().delete("{uuid}").then().statusCode(Status.BAD_REQUEST.getStatusCode());
    }

    @Test
    void testCreateEngagement() {
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").type("Residency").build();
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON).when().body(e).post().then().statusCode(Status.CREATED.getStatusCode()).header("Location", notNullValue());
    }

    @Test
    void testCreateEngagementNoType() {
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").build();
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON).when().body(e).post().then().statusCode(Status.BAD_REQUEST.getStatusCode()).header("Location", nullValue());
    }

    @Test
    void testCreateEngagementBadRequest() {
        Engagement engagement = Engagement.builder().customerName("ab!!!").name("abcd").region("na").type("Residency").build();
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON)
                .when().body(e).post()
                .then().statusCode(Status.BAD_REQUEST.getStatusCode())
                    .header("Location", nullValue())
                    .body("parameter_violations.size()", equalTo(1))
                    .body("parameter_violations[0].value", equalTo("ab!!!"));
    }

    @Test
    void testUpdateLastUpdate() {
        given().contentType(ContentType.JSON).pathParam("uuid", "uuid1").when().put("{uuid}/lastUpdate").then().statusCode(200);
    }

    @Test
    void testUpdateEngagement() {
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").type("Residency").build();
        engagementService.create(engagement);

        engagement.setDescription("description");
        engagement.getUseCases().add(UseCase.builder().title("added").description("desc").order(1).build());
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON).when().body(e).put().then().statusCode(Status.OK.getStatusCode()).body("description", equalTo("description")).
                body("use_cases[0].title", equalTo("added")).body("use_cases[0].uuid", notNullValue());

        engagement = engagementService.getEngagement(engagement.getUuid()).get();
        UseCase useCase = engagement.getUseCases().get(0);
        useCase.setDescription("updated");
        e = new JsonMarshaller().toJson(engagement);

        given().contentType(ContentType.JSON).when().body(e).put().then().statusCode(Status.OK.getStatusCode()).body("description", equalTo("description")).
                body("use_cases[0].title", equalTo("added")).body("use_cases[0].description", equalTo("updated"));

        engagement = engagementService.getEngagement(engagement.getUuid()).get();
        engagement.setCustomerContactEmail("cust@email.com");
        e = new JsonMarshaller().toJson(engagement);

        given().contentType(ContentType.JSON).when().body(e).put().then().statusCode(Status.OK.getStatusCode()).body("customer_contact_email", equalTo("cust@email.com"));

    }

    @Test
    void testUpdateEngagementNoUpdate() {
        Engagement engagement = engagementService.getEngagements().get(0);
        String e = new JsonMarshaller().toJson(engagement);

        given().contentType(ContentType.JSON).when().body(e).put().then().statusCode(Status.NO_CONTENT.getStatusCode());
    }

    @Test
    void testUpdateConflict() {
        String customerName = "Fish Gym";
        String name = "DO500";
        Engagement engagement = Engagement.builder().name(name).customerName(customerName).region("na").build();
        engagementService.create(engagement);

        Optional<Engagement> engagementOption = engagementService.getEngagement("uuid1");
        assertTrue(engagementOption.isPresent());
        engagement = engagementOption.get();
        engagement.setCustomerName(customerName);
        engagement.setName(name);
        String e = new JsonMarshaller().toJson(engagement);

        given().contentType(ContentType.JSON).when().body(e).put().then().statusCode(Status.CONFLICT.getStatusCode());

    }

    @Test
    void testUpdateParticipants() {
        String uuid = "uuid1";

        Engagement engagement = engagementService.getEngagement(uuid).orElseThrow();
        assertEquals(0, engagement.getParticipantCount());

        given().pathParam("uuid", uuid).pathParam("count", 99)
                .when().put("{uuid}/participants/{count}").then().statusCode(200);

        engagement = engagementService.getEngagement(uuid).orElseThrow();
        assertEquals(99, engagement.getParticipantCount());
    }

    @Test
    void testUpdateArtifacts() {
        String uuid = "uuid1";

        Engagement engagement = engagementService.getEngagement(uuid).orElseThrow();
        assertEquals(0, engagement.getParticipantCount());

        given().pathParam("uuid", uuid).pathParam("count", 99)
                .when().put("{uuid}/artifacts/{count}").then().statusCode(200);

        engagement = engagementService.getEngagement(uuid).orElseThrow();
        assertEquals(99, engagement.getArtifactCount());
    }

    @Test
    void testLaunchNoEngagement() {
        String uuid = "nope";

        given().pathParam("uuid", uuid).queryParam("author", AUTHOR)
                .queryParam("authorEmail", AUTHOR_EMAIL)
                .contentType(ContentType.JSON)
        .when()
                .put("{uuid}/launch")
        .then().statusCode(404);
    }

    @Test
    void testLaunchAlreadyLaunched() {
        String uuid = "uuid1";
        Instant now = Instant.now();
        Engagement engagement = engagementService.getEngagement(uuid).orElseThrow();

        engagement.setLaunch(Launch.builder().launchedBy(AUTHOR).launchedByEmail(AUTHOR_EMAIL).launchedDateTime(now).build());
        engagementService.update(engagement, false, false);

        given().pathParam("uuid", uuid).queryParam("author", AUTHOR)
                .queryParam("authorEmail", AUTHOR_EMAIL)
                .contentType(ContentType.JSON)
        .when()
                .put("{uuid}/launch")
        .then().statusCode(400);

    }

    @Test
    void testLaunchAlreadySuccess() {
        String uuid = "uuid1";

        Optional<Engagement> launchedOption = engagementService.getEngagement("uuid1");
        assertTrue(launchedOption.isPresent());
        Engagement notLaunched = launchedOption.get();
        assertEquals(EngagementState.UPCOMING, notLaunched.getState());
        assertEquals(EngagementState.UPCOMING, notLaunched.getCurrentState());

        given().pathParam("uuid", uuid).queryParam("author", AUTHOR)
                .queryParam("authorEmail", AUTHOR_EMAIL)
                .contentType(ContentType.JSON)
        .when()
                .put("{uuid}/launch")
        .then().statusCode(200);

        verify(gitlabService, timeout(1000).times(1)) .updateEngagementInGitlab(Mockito.any(Engagement.class));

        launchedOption = engagementService.getEngagement("uuid1");
        assertTrue(launchedOption.isPresent());
        Engagement launched = launchedOption.get();
        assertEquals(EngagementState.PAST, launched.getState());
        assertEquals(EngagementState.PAST, launched.getCurrentState());
    }

    @Test
    void testLaunchUpcomingToActive() {
        Engagement e = Engagement.builder().customerName("fragment").name("rock").region("dev").type("DO500").build();
        engagementService.create(e);

        e.setStartDate(Instant.now().minus(7, ChronoUnit.DAYS));
        e.setEndDate(Instant.now().plus(7, ChronoUnit.DAYS));
        e.setEngagementLeadEmail("a@b.com");
        e.setEngagementLeadName("A B");
        e.setTechnicalLeadEmail("c@d.com");
        e.setTechnicalLeadName("C D");
        e.setCustomerContactName("E F");
        e.setCustomerContactEmail("e@f.com");
        e.setArchiveDate(Instant.now().plus(14, ChronoUnit.DAYS));

        engagementService.update(e);

        assertEquals(EngagementState.UPCOMING, e.getState());
        assertEquals(EngagementState.UPCOMING, e.getCurrentState());

        given().pathParam("uuid", e.getUuid()).queryParam("author", AUTHOR)
                .queryParam("authorEmail", AUTHOR_EMAIL)
                .contentType(ContentType.JSON)
                .when()
                .put("{uuid}/launch")
                .then().statusCode(200);

        verify(gitlabService, timeout(1000).times(2)) .updateEngagementInGitlab(Mockito.any(Engagement.class));

        Optional<Engagement> launchedOption = engagementService.getEngagement(e.getUuid());
        assertTrue(launchedOption.isPresent());
        Engagement launched = launchedOption.get();
        assertEquals(EngagementState.ACTIVE, launched.getState());
        assertEquals(EngagementState.ACTIVE, launched.getCurrentState());
    }

    @Test
    void testCustomerSuggest() {

        Engagement engagement = Engagement.builder().name("DO500").customerName("Catfish Gym").region("na").build();
        engagementService.create(engagement);

        engagement = Engagement.builder().name("DO500").customerName("Catfish Arena").region("na").build();
        engagementService.create(engagement);

        engagement = Engagement.builder().name("DO500").customerName("Catfish Amphitheater").region("na").build();
        engagementService.create(engagement);

        given().queryParam("partial", "Catfish").when().get("suggest").then().statusCode(200)
                .body("size()", equalTo(3)).body("[0]", equalTo("Catfish Amphitheater"));

        given().queryParam("partial", "Catfish G").when().get("suggest").then().statusCode(200)
                .body("size()", equalTo(1)).body("[0]", equalTo("Catfish Gym"));

        given().when().get("suggest").then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    void testGetWithCategory() {
        List<Engagement> e = engagementService.getEngagements();
        given().pathParam("category", "philanthropy").queryParam("sort", "name|desc,customerName").when().get("category/{category}")
                .then().statusCode(200).body("size()", equalTo(2)).header("x-total-engagements", equalTo("2"));
    }

    @Test
    void testUpdateStates() {
        given().when().put("refresh/state").then().statusCode(200);
    }

    @Test
    void testMissingGitlabProjects() {
        Engagement engagement = Engagement.builder().name("DO500").customerName("Catfish Gym").region("na").projectId(20).build();
        engagementService.create(engagement);
        given().when().get("gitlab").then().statusCode(200).body("size()", equalTo(2))
                .body("[0]", equalTo("Engagement Banana Hut banana uuid1"))
                .body("[1]", equalTo("Engagement Banana Hut2 banana2 uuid2"));
    }

    @Test
    void testRetryToGitlabInGitlabUpdate() {
        //Update engagement files
        Engagement engagement = Engagement.builder().name("DO500").customerName("Catfish Gym").region("na").projectId(20).build();
        engagementService.create(engagement);

        given().queryParam("uuid", engagement.getUuid()).when().put("retry").then().statusCode(200);
    }

    @Test
    void testRetryToGitlabInGitlabCreate() {
        //Create engagement riles
        Engagement engagement = Engagement.builder().name("DO500").customerName("Catfish Gym").region("na").projectId(15).build();
        engagementService.create(engagement);

        given().queryParam("message", "mess").queryParam("uuid", engagement.getUuid()).when().put("retry").then().statusCode(200);
    }

    @Test
    void testRetryToGitlabNotInGitlab() {
        given().queryParam("uuid", "uuid1").when().put("retry").then().statusCode(200);
    }

    @Test
    void testRetryToGitlabNotFound() {
        given().queryParam("uuid", "uuidxxx").when().put("retry").then().statusCode(404).body("message", equalTo("Engagement not found for uuid uuidxxx"));
    }
}
