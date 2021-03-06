package com.redhat.labs.lodestar.engagements.resource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.model.UseCase;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

@RequestScoped
@Path("/api/v2/usecases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Use Cases")
public class UseCaseResource {
    
    @Inject
    EngagementService engagementService;

    @GET
    public Response getUseCases(@BeanParam PageFilter pageFilter, @QueryParam("regions") Set<String> regions) {
        List<UseCase> cases = engagementService.getUseCases(pageFilter, regions);
        return Response.ok(cases).header("x-total-use-cases", cases.size()).build();
    }
    
    @GET
    @Path("{uuid}")
    public Response getUseCase(@PathParam("uuid") String uuid) {
        Optional<UseCase> useCase = engagementService.getUseCase(uuid);
        
        if(useCase.isPresent()) {
            return Response.ok(useCase).build();
        }
        
        return Response.status(Status.NOT_FOUND).build();
    }
}
