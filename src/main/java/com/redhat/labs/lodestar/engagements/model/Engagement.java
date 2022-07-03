package com.redhat.labs.lodestar.engagements.model;

import java.time.Instant;
import java.util.*;

import javax.json.bind.annotation.JsonbTransient;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.ws.rs.WebApplicationException;

import org.bson.types.ObjectId;
import org.javers.core.metamodel.annotation.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.redhat.labs.lodestar.engagements.validation.ValidName;

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
@JsonIgnoreProperties(value = { "id", "gitlabRetry" })
@JsonPropertyOrder(alphabetic = true)
public class Engagement {

    @JsonbTransient
    private ObjectId id;
    private String uuid;
    @NotBlank
    private String type;
    @ValidName
    @Size(min = 3, max = 255)
    private String customerName;
    @ValidName
    @Size(min = 3, max = 255)
    private String name;
    @NotBlank
    private String region;

    private CreationDetails creationDetails;
    private Launch launch;
    
    @Builder.Default
    private Set<String> categories = new TreeSet<>();

    @Valid
    @Builder.Default
    private List<UseCase> useCases = new ArrayList<>();

    private String additionalDetails;
    
    private String description;
    private String lastMessage;
    private String lastUpdateByName;
    private String lastUpdateByEmail;
    private String location;
    private String engagementLeadName;
    private String engagementLeadEmail;
    private String technicalLeadName;
    private String technicalLeadEmail;
    private String customerContactName;
    private String customerContactEmail;
    private String timezone;
    private Boolean publicReference;
    private int projectId;

    private Instant archiveDate;
    private Instant startDate;
    private Instant endDate;
    private Instant createdDate;
    private Instant lastUpdate;

    private EngagementState currentState;

    private boolean gitlabRetry;

    @Builder.Default
    private Integer participantCount = 0;
    @Builder.Default
    private Integer hostingCount = 0;
    @Builder.Default
    private Integer artifactCount = 0;

    //scores
    //billingCodes

    public void clean() {
        customerName = customerName.trim();
        name = name.trim();
    }

    public void setCreator() {
        if (creationDetails != null) {
            throw new WebApplicationException("Creator already set", 400);
        }

        creationDetails = new CreationDetails(lastUpdateByName, lastUpdateByEmail, createdDate);
    }

    public void updateTimestamps() {
        Instant now = Instant.now();

        if (createdDate == null) {
            createdDate = now;
        }

        lastUpdate = now;
    }
    
    /**
     * For values set a creation - always override update
     * For values set after creation (ex. Launch). Override after first setting
     * @param stone Values set in stone
     * @return boolean if an initial field was updated
     */
    public boolean overrideImmutableFields(Engagement stone, boolean categoryUpdate) {
        boolean allowOverride = false;
        
        id = stone.id;
        uuid = stone.getUuid();
        createdDate = stone.getCreatedDate();
        creationDetails = stone.getCreationDetails();
        participantCount = stone.getParticipantCount();
        artifactCount = stone.getArtifactCount();
        hostingCount = stone.getHostingCount();

        if(!categoryUpdate) {
            categories = stone.getCategories();
        }
        
        if(stone.getProjectId() == 0) {
            allowOverride = true;
        } else {
            projectId = stone.getProjectId();
        }
        
        if(stone.getLaunch() != null && launch != null) { //Launch has already happened - you can delete but not change the launch info
            launch = stone.getLaunch();
        }

        return allowOverride;
    }

    public EngagementState getState() {
        return getState(Instant.now());
    }

    public EngagementState getState(Instant currentDate) {

        if (launch == null || endDate == null || startDate == null) { // not launched or irregularly launched
            return EngagementState.UPCOMING;
        }

        if(endDate.isBefore(currentDate)) { //has reached end date
            if(archiveDate != null && archiveDate.isAfter(currentDate)) { //hasn't reached archive date
                return EngagementState.TERMINATING;
            }
            return EngagementState.PAST;
        }

        //has not reached end date
        return EngagementState.ACTIVE;
    }

}
