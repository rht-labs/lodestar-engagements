package com.redhat.labs.lodestar.engagements.model;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HookConfig extends PanacheMongoEntity {
    
    private String name;
    private String baseUrl;
    private boolean pushEvent;
    private String pushEventsBranchFilter;
    private String token;
    
    /**
     * Should the webhook be enabled after an engagement is archived
     */
    private boolean enabledAfterArchive;

}
