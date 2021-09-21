package com.redhat.labs.lodestar.engagements.resource;

import com.redhat.labs.lodestar.engagements.model.HookConfig;
import com.redhat.labs.lodestar.engagements.service.ConfigService;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@RequestScoped
@Path("/api/v2/engagements/gitlab-webhooks")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Webhooks")
public class ConfigResource {

    @Inject
    ConfigService configService;

    @PUT
    public Response updateWebhooks(List<HookConfig> webhooks) {
        configService.updateAllWebhooks(webhooks);
        return Response.accepted().build();
    }

    @GET
    public Response getWebhooks() {
        List<HookConfig> webhooks = configService.getHookConfig();
        return Response.ok(webhooks).header("x-total-webhooks", webhooks.size()).build();
    }
}
