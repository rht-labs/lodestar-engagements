package com.redhat.labs.lodestar.engagements.rest.client;

import com.redhat.labs.lodestar.engagements.model.HookConfig;
import org.apache.http.NoHttpResponseException;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import java.util.List;

@Retry(maxRetries = 5, delay = 1200, retryOn = NoHttpResponseException.class, abortOn = WebApplicationException.class)
@RegisterRestClient(configKey = "config.api")
@Consumes("application/json")
@Path("api/v1/configs")
public interface ConfigApiClient {

    @GET
    @Path("webhooks")
    List<HookConfig> getWebhooks();

    @GET
    @Path("runtime")
    String getRuntimeConfig(@QueryParam("type") String type);

}
