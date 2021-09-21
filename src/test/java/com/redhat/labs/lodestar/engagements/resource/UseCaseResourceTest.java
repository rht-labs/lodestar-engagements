package com.redhat.labs.lodestar.engagements.resource;

import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.service.EngagementService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestHTTPEndpoint(UseCaseResource.class)
@QuarkusTestResource(ExternalApiWireMock.class)
class UseCaseResourceTest {

    @Inject
    EngagementService engagementService;

    @BeforeEach
    void setUp() {
        engagementService.refresh();
    }

    @Test
    void testGetUseCases() {
        given().when().get().then().statusCode(200).header("x-total-use-cases", equalTo("2"))
                .body("[1].uuid", equalTo("use-case-1")).body("[0].title", equalTo("Panama2"));
    }

    @Test
    void testGetUseCaseById() {
        given().pathParam("uuid", "use-case-1").when().get("{uuid}").then().statusCode(200).body("title", equalTo("Panama"));
    }

    @Test
    void testGetUseCaseByIdNotFound() {
        given().pathParam("uuid", "use-case-xxx").when().get("{uuid}").then().statusCode(404);
    }
}
