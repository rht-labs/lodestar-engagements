package com.redhat.labs.lodestar.engagements.model;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreationDetails {

    private String createdByUser;
    private String createdByEmail;
    private Instant createdOn;

}
