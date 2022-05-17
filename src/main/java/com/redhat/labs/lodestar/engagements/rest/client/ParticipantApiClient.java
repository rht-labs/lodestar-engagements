package com.redhat.labs.lodestar.engagements.rest.client;

import org.apache.http.*;
import org.eclipse.microprofile.faulttolerance.*;
import org.eclipse.microprofile.rest.client.inject.*;

import javax.ws.rs.*;
import java.util.*;

@Retry(maxRetries = 5, delay = 1200, retryOn = NoHttpResponseException.class, abortOn = WebApplicationException.class)
@RegisterRestClient(configKey = "participants.api")
@Consumes("application/json")
@Path("api/participants")
public interface ParticipantApiClient {

    @GET
    @Path("/engagements/counts")
    Map<String, Integer> getParticipantCounts();
}
