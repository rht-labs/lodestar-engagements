package com.redhat.labs.lodestar.engagements.resource;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.model.UseCase;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

@RequestScoped
@Path("/api/usecases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Use Cases")
public class UseCaseResource {
    
    @Inject
    EngagementService engagementService;

    @GET
    public Response getUseCases(@BeanParam PageFilter pageFilter) {
        List<UseCase> cases = engagementService.getUseCases(pageFilter);
        return Response.ok(cases).header("x-total-use-cases", cases.size()).build();
    }
    
    @GET
    @Path("usecases/{uuid}")
    public Response getUseCase(@PathParam("uuid") String uuid) {
        Optional<UseCase> useCase = engagementService.getUseCase(uuid);
        
        if(useCase.isPresent()) {
            return Response.ok(useCase).build();
        }
        
        return Response.status(Status.NOT_FOUND).build();
    }
}
