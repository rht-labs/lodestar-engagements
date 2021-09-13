package com.redhat.labs.lodestar.engagements.mock;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ResourceLoader {
    private static JsonMarshaller json = new JsonMarshaller();
    
    public static String load(String resourceName) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            return IOUtils.toString(is, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Unable to load " + resourceName, e);
        }
    }
    
    public static String loadGitlabFile(String resourceName) {

        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            String loaded = IOUtils.toString(is, StandardCharsets.UTF_8);
            
            GitlabFile gitlabFile = json.fromJson(loaded, new TypeReference<>() {});

            String ejson;
            if(gitlabFile.getEngagement() != null) {
                ejson = json.toJson(gitlabFile.getEngagement());
            } else if(gitlabFile.getCategories() != null) {
                ejson = json.toJson(gitlabFile.getCategories());
            } else {
                ejson = "{}";
            }

            gitlabFile.setContent(ejson);
            gitlabFile.encode();
            
            return json.toJson(gitlabFile);
            
        } catch (IOException e) {
            throw new RuntimeException("Unable to load " + resourceName, e);
        }
    }
}
