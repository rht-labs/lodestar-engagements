package com.redhat.labs.lodestar.engagements.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import com.redhat.labs.lodestar.engagements.service.GitlabService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.service.CategoryService;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;

@QuarkusTest
@TestHTTPEndpoint(CategoryResource.class)
@QuarkusTestResource(ExternalApiWireMock.class)
class CategoryResourceTest {

    @Inject
    EngagementService engagementService;

    @Inject
    CategoryService categoryService;

    @Inject
    GitlabService gitlabService;

    @BeforeEach
    void setUp() {
        engagementService.refresh();
        gitlabService.refreshCategories();
    }

    @Test
    void testGetAllCategories() {
        
        assertEquals(14L,  categoryService.getCategories(0, 100).size());

        given().when().get().then().statusCode(200).body("size()", equalTo(14)).header("x-total-categories", "14");

        given().queryParam("engagementUuid", "uuid1").when().get().then().statusCode(200).body("size()", equalTo(7)).header("x-total-categories",
                "7");

        given().queryParam("engagementUuid", "uuid0").when().get().then().statusCode(200).body("size()", equalTo(0)).header("x-total-categories",
                "0");
    }

    @Test
    void testUpdateCategories() {
        List<String> categories = Arrays.asList("one", "two", "three", "rat");
        given().contentType(ContentType.JSON).pathParam("engagementUuid", "uuid1").when().body(categories).post("{engagementUuid}").then()
                .statusCode(201).body("size()", equalTo(4));

    }
    
    @Test
    void testUpdateCategoriesNotFound() {
        List<String> categories = Arrays.asList("one", "two", "three");
        given().contentType(ContentType.JSON).pathParam("engagementUuid", "uuid0").when().body(categories).post("{engagementUuid}").then()
                .statusCode(404);

    }

    @Test
    void testRollup() {
        given().when().get("rollup").then().statusCode(200).body("size()", equalTo(11)).body("[0].count", equalTo(2)).body("[10].count", equalTo(1));

    }

}
