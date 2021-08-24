package com.redhat.labs.lodestar.engagements.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import javax.ws.rs.WebApplicationException;

import org.javers.core.metamodel.annotation.TypeName;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.redhat.labs.lodestar.engagements.validation.ValidName;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
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
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(value = { "id" })
@JsonPropertyOrder(alphabetic = true)
public class Engagement extends PanacheMongoEntity {

    private String uuid;
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
    private Set<String> categories = new HashSet<>();
    
    @Builder.Default
    private List<UseCase> useCases = new ArrayList<>();

    private String additionalDetails;
    
    private String description;
    private String lastMessage;
    private String lastUpdateName;
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
    private Instant lastUpdated;
    
    //status
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

        creationDetails = new CreationDetails(lastUpdateName, lastUpdateByEmail, createdDate);
    }

    public void updateTimestamps() {
        Instant now = Instant.now();

        if (createdDate == null) {
            createdDate = now;
        }

        lastUpdated = now;
    }
    
    /**
     * For values set a creation - always override update
     * For values set after creation (ex. Launch). Override after first setting
     * @param stone Values set in stone
     */
    public void overrideImmutableFields(Engagement stone) {
        
        super.id = stone.id;
        uuid = stone.getUuid();
        createdDate = stone.getCreatedDate();
        creationDetails = stone.getCreationDetails();
        categories = stone.getCategories();
        
        if(stone.getProjectId() != 0) {
            projectId = stone.getProjectId();
        }
        
        if(stone.getLaunch() != null) { //Launch has already happened
            launch = stone.getLaunch();
        }

        // status
        //engagement.setStatus(existing.getStatus())

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
