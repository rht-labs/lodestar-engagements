package com.redhat.labs.lodestar.engagements.model;

import java.time.Instant;

import org.javers.core.metamodel.annotation.DiffIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(callSuper = false)
public class UseCase {

    private String title;
    private String description;
    private Integer order;
    
    private String uuid;
    @DiffIgnore
    private Instant created;
    @DiffIgnore
    private Instant updated;
    
    //These fields are not set when adding / updating use-cases
    //Instead they are set by mongo aggregation when querying for a list of use cases
    @DiffIgnore
    private String engagementUuid;
    @DiffIgnore
    private String customerName;
    @DiffIgnore
    private String name;

}