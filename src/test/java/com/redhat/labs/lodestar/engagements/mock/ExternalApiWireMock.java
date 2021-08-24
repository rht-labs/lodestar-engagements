package com.redhat.labs.lodestar.engagements.mock;

import java.util.HashMap;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ExternalApiWireMock implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer; 
    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer();
        wireMockServer.start();

        String body = ResourceLoader.load("gitlab-group-999999.json");

        stubFor(get(urlMatching("\\/api\\/v4\\/groups\\/john%2Fengagements%2Ffish.*")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));
        
        stubFor(post(urlEqualTo("/api/v4/projects/1/repository/commits")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));
        
        body = ResourceLoader.load("seed-project-list-for-group-2.json");
        
        stubFor(get(urlEqualTo("/api/v4/groups/2/projects?include_subgroups=true&per_page=96&page=1"))
                .willReturn(aResponse().withHeader("Content-Type",  "application/json")
                        .withHeader("X-Page", "1").withHeader("X-Total-Pages", "1").withHeader("X-Per-Pages", "96")
                        .withHeader("X-Total", "1").withBody(body)
                        ));

        body = ResourceLoader.loadGitlabFile("gitlab-engagement-file-1.json");

        stubFor(get(urlEqualTo("/api/v4/projects/1/repository/files/engagement%2Fengagement%2Ejson?ref=master")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        body = ResourceLoader.loadGitlabFile("gitlab-engagement-file-2.json");

        stubFor(get(urlEqualTo("/api/v4/projects/2/repository/files/engagement%2Fengagement%2Ejson?ref=master")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));
        
        body = ResourceLoader.load("gitlab-group-2.json");
        
        stubFor(get(urlEqualTo("/api/v4/groups/2")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
                ));
        
        body = ResourceLoader.load("gitlab-group-3.json");
        
        stubFor(get(urlEqualTo("/api/v4/groups/3")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
                ));
        
        body = ResourceLoader.load("gitlab-group-4.json");
        
        stubFor(get(urlEqualTo("/api/v4/groups/john%2Fengagements%2Fbanana%2Dhut%2Fbanana")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
                ));
        
        stubFor(get(urlEqualTo("/api/v4/groups/4")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
                ));
        
        body = ResourceLoader.loadGitlabFile("gitlab-category-file-1.json");
        
        stubFor(get(urlEqualTo("/api/v4/projects/1/repository/files/engagement%2Fcategory%2Ejson?ref=master")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
                ));

        body = ResourceLoader.loadGitlabFile("gitlab-category-file-2.json");

        stubFor(get(urlEqualTo("/api/v4/projects/2/repository/files/engagement%2Fcategory%2Ejson?ref=master")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        stubFor(get(urlEqualTo("/api/v4/projects/99/repository/files/engagement%2Fhosting.json?ref=master")).willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type",  "application/json")
                .withBody("{\"msg\": \" 500 Something bad happened\"}")
                ));

        Map<String, String> config = new HashMap<>();
        config.put("gitlab4j.api.url", wireMockServer.baseUrl());
        
        return config;
    }

    @Override
    public void stop() {
        if(null != wireMockServer) {
           wireMockServer.stop();
        }
        
    }



}
