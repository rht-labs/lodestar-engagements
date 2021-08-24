package com.redhat.labs.lodestar.engagements.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import javax.inject.Inject;
import javax.ws.rs.core.Response.Status;

import com.redhat.labs.lodestar.engagements.model.UseCase;
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

import java.util.Optional;

@QuarkusTest
@TestHTTPEndpoint(EngagementResource.class)
@QuarkusTestResource(ExternalApiWireMock.class)
class EngagementResourceTest {

    @Inject
    EngagementService engagementService;

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
    void testGetAllEngagements() {
        given().when().get().then().statusCode(200).body("size()", equalTo(2));
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
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").build();
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON).when().body(e).post().then().statusCode(Status.CREATED.getStatusCode()).header("Location", notNullValue());
    }

    @Test
    void testCreateEngagementBadRequest() {
        Engagement engagement = Engagement.builder().customerName("").region("na").build();
        String e = new JsonMarshaller().toJson(engagement);
        given().contentType(ContentType.JSON).when().body(e).post().then().statusCode(Status.BAD_REQUEST.getStatusCode()).header("Location", nullValue());
    }

    @Test
    void testUpdateEngagement() {
        Engagement engagement = Engagement.builder().name("DO500").customerName("Fish Gym").region("na").build();
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

        given().when().get("suggest").then().statusCode(200).body("size()", equalTo(5)).body("[0]", equalTo("Banana Hut"));
    }

    @Test
    void testGetWithCategory() {
        given().pathParam("category", "philanthropy").when().get("category/{category}").then().statusCode(200).body("size", equalTo(2)).header("x-total-engagements", equalTo("2"));
    }
}
