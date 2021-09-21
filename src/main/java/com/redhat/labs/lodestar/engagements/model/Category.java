package com.redhat.labs.lodestar.engagements.model;

import java.time.Instant;

import javax.json.bind.annotation.JsonbTransient;

import org.bson.types.ObjectId;
import org.javers.core.metamodel.annotation.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TypeName("Engagement")
@EqualsAndHashCode
@JsonIgnoreProperties(value = { "id" })
public class Category {

    @JsonbTransient
    private ObjectId id;
    private String uuid;
    private Instant created;
    private String engagementUuid;
    
    private String name;
    private String region;
}