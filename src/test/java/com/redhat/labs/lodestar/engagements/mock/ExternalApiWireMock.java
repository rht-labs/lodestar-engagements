package com.redhat.labs.lodestar.engagements.mock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Namespace;
import org.gitlab4j.api.models.Project;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class ExternalApiWireMock implements QuarkusTestResourceLifecycleManager {

    JsonMarshaller json = new JsonMarshaller();

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

        body = json.toJson(new Commit().withMessage("ExternalApiWireMock"));
        
        stubFor(post(urlMatching("/api/v4/projects/[0-9]?\\d/repository/commits")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        stubFor(post(urlMatching("/api/v4/projects/[0-9]?\\d/hooks")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        stubFor(post(urlMatching("/api/v4/projects/[0-9][0-9]/deploy_keys/3678/enable")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        stubFor(put(urlMatching("/api/v4/projects/[0-9][0-9]/deploy_keys/3678")).willReturn(aResponse()
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

        stubFor(get(urlEqualTo("/api/v4/projects/12/repository/files/engagement%2Ejson?ref=master")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type",  "application/json")
                .withBody(body)
        ));

        stubFor(get(urlEqualTo("/api/v4/projects/15/repository/files/engagement%2Ejson?ref=master")).willReturn(aResponse()
                .withStatus(200)
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

        body = ResourceLoader.load("webhooks.json");

        stubFor(get(urlEqualTo("/api/v1/configs/webhooks")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json").withStatus(200).withBody(body)));

        body = ResourceLoader.load("runtime-config.json");

        stubFor(get(urlEqualTo("/api/v1/configs/runtime?type=Residency")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withStatus(200).withBody(body)));

        //create engagement
        stubFor(this.createGroup("customer-ten", 3, 10));
        stubFor(this.createGroup("engagement-ten", 10, 11));
        stubFor(this.createProject("engagement-ten", 11));

        //update engagement
        stubFor(this.getProject("engagement-twelve", 12, 13));
        stubFor(this.getGroup("engagement-twelve", 14, 13));
        stubFor(this.getGroup("customer-twelve", 3, 14));
        stubFor(getSubGroups(1,14, 13));

        //update engagement transfer
        stubFor(this.getProject("engagement-fifteen", 15, 16));
        stubFor(this.getGroup("engagement-fifteen", 17, 16));
        stubFor(this.getGroup("customer-fifteen", 3, 17));
        stubFor(getSubGroups(2,17, 16));
        stubFor(this.createGroup("customer-fifteen", 2, 18));

        stubFor(put(urlMatching("/api/v4/groups/[0-9][0-9]")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json").withStatus(200)
                .withBody("{}")
        ));

        body = " { \"d4c228ec-9dcc-435f-bcc1-60ebcec269f3\": 217384 }";

        stubFor(get(urlEqualTo("/api/participants/engagements/counts")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withStatus(200)
                .withBody(body)));

        stubFor(get(urlEqualTo("/api/artifacts/engagements/count")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json")
                .withStatus(200)
                .withBody(body)));

        body = ResourceLoader.load("last-activity.json");

        stubFor(get(urlEqualTo("/api/activity/latestWithTimestamp")).willReturn(aResponse()
                        .withHeader("Content-Type",  "application/json")
                        .withStatus(200)
                        .withBody(body)));

        Map<String, String> config = new HashMap<>();
        config.put("config.api/mp-rest/url", wireMockServer.baseUrl());
        config.put("participants.api/mp-rest/url", wireMockServer.baseUrl());
        config.put("artifacts.api/mp-rest/url", wireMockServer.baseUrl());
        config.put("activity.api/mp-rest/url", wireMockServer.baseUrl());
        config.put("gitlab4j.api.url", wireMockServer.baseUrl());
        
        return config;
    }

    @Override
    public void stop() {
        if(null != wireMockServer) {
           wireMockServer.stop();
        }
    }

    private MappingBuilder createGroup(String groupName, int parentId, int groupId) {

        Group g = instantGroup(groupName, parentId, groupId);
        String group = new JsonMarshaller().toJson(g);

        return post(urlEqualTo("/api/v4/groups")).withRequestBody(containing(groupName)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type",  "application/json").withBody(group));
    }

    private MappingBuilder getGroup(String groupName, int parentId, int groupId) {

        Group g = instantGroup(groupName, parentId, groupId);
        String group = new JsonMarshaller().toJson(g);

        return get(urlEqualTo("/api/v4/groups/" + groupId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type",  "application/json").withBody(group));
    }

    private MappingBuilder getSubGroups(int subGroupCount, int parentId, int groupId) {
        List<Group> groups = new ArrayList<>();

        for(int i=0; i<subGroupCount; i++) {
            groups.add(instantGroup("blah", parentId, groupId));
        }

        String response = json.toJson(groups);

        return get(urlEqualTo("/api/v4/groups/" + parentId + "/subgroups?per_page=96&page=1")).willReturn(aResponse()
                .withHeader("Content-Type",  "application/json").withStatus(200)
                .withBody(response));
    }

    private Group instantGroup(String groupName, int parentId, int groupId) {
        Group g = new Group().withName(groupName)
                .withParentId(parentId)
                .withPath(groupName)
                .withFullName("John / engagements / " + groupName)
                .withFullPath("john/engagements/" + groupName).withId(groupId);
        return g;
    }

    private MappingBuilder createProject(String groupName, int projectId) {

        Project p = new Project().withId(projectId)
                .withName("iac").withDescription("engagement UUID: " + projectId + "-" + groupName);

        String project = json.toJson(p);

        return post(urlEqualTo("/api/v4/projects")).withRequestBody(containing(groupName)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type",  "application/json").withBody(project));
    }

    private MappingBuilder getProject(String groupName, int projectId, int groupId) {
        Namespace ns = new Namespace().withPath(groupName).withId(groupId);

        Project p = new Project().withId(projectId)
                .withName("iac").withDescription("engagement UUID: " + projectId + "-" + groupName)
                .withNamespace(ns);

        String project = json.toJson(p);

        return get(urlEqualTo("/api/v4/projects/" + projectId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type",  "application/json").withBody(project));
    }

}
