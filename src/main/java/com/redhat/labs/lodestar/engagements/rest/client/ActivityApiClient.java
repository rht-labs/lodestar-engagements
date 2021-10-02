package com.redhat.labs.lodestar.engagements.rest.client;

import org.apache.http.NoHttpResponseException;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import java.time.OffsetDateTime;
import java.util.Map;

@Retry(maxRetries = 5, delay = 1200, retryOn = NoHttpResponseException.class, abortOn = WebApplicationException.class)
@RegisterRestClient(configKey = "activity.api")
@Consumes("application/json")
@Path("api/activity")
public interface ActivityApiClient {

    @GET
    @Path("latestWithTimestamp")
    Map<String, OffsetDateTime> getActivityPerEngagement();
}
