package com.redhat.labs.lodestar.engagements.mock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GitlabFileTest {

    
    @Test
    void transformToGitlabFile() {
        String gitlabFile = ResourceLoader.loadGitlabFile("gitlab-engagement-file-1.json");
        
        assertTrue(gitlabFile.contains("region"));
    }
}
