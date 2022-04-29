package com.redhat.labs.lodestar.engagements.resource;

import static org.hamcrest.Matchers.equalTo;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.redhat.labs.lodestar.engagements.mock.ExternalApiWireMock;
import com.redhat.labs.lodestar.engagements.mock.ResourceLoader;
import com.redhat.labs.lodestar.engagements.model.HookConfig;
import com.redhat.labs.lodestar.engagements.service.ConfigService;
import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

@QuarkusTest
@TestHTTPEndpoint(ConfigResource.class)
@QuarkusTestResource(ExternalApiWireMock.class)
class ConfigResourceTest {

    @Inject
    ConfigService configService;

    @Test
    void testGetWebhooksService() {
        HookConfig.deleteAll();
        //from service
        given().when().get().then().statusCode(200).header("x-total-webhooks", equalTo("3")).body("size()", equalTo(3));

    }

    @Test
    void testGetWebhooksDB() {
        HookConfig.deleteAll();

        String body = ResourceLoader.load("/webhooks.json");
        List<HookConfig> webhooks = new JsonMarshaller().fromJson(body, HookConfig.class);

        HookConfig.persist(webhooks);

        //from db
        given().when().get().then().statusCode(200).header("x-total-webhooks", equalTo("3")).body("size()", equalTo(3));
    }

    @Test
    void testPutWebhooks() {
        HookConfig.deleteAll();

        String body = ResourceLoader.load("/webhooks.json");

        given().header("Content-Type",  "application/json").body(body).when().put().then().statusCode(202);

        //no change
        given().header("Content-Type",  "application/json").body(body).when().put().then().statusCode(202);
    }
}
