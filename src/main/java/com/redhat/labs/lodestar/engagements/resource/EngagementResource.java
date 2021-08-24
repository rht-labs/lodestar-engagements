package com.redhat.labs.lodestar.engagements.resource;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.redhat.labs.lodestar.engagements.exception.ErrorMessage;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

@RequestScoped
@Path("/api/engagements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Engagements")
public class EngagementResource {
    private static final String TOTAL_HEADER = "x-total-engagements";

    @Inject
    EngagementService engagementService;
    
    @GET
    public Response getEngagements() {
        List<Engagement> engagements = engagementService.getEngagements();
        return Response.ok(engagements).header(TOTAL_HEADER, engagements.size()).build();
    }
    
    @GET
    @Path("category/{category}")
    public Response getEngagementWithCategory(@PathParam("category") String category) {
        List<Engagement> engagements = engagementService.getEngagementsWithCategory(category);
        return Response.ok(engagements).header(TOTAL_HEADER, engagements.size()).build();
    }
    
    @GET
    @Path("{uuid}")
    public Response getEngagement(@PathParam("uuid") String uuid) {
        Optional<Engagement> engagement = engagementService.getEngagement(uuid);
        
        if(engagement.isPresent()) {
            return Response.ok(engagement).build();
        }
        
        return Response.status(404).entity(new ErrorMessage("No engagement found for uuid %s", uuid)).build();
    }
    
    @DELETE
    @Path("{uuid}")
    public Response deleteEngagement(@PathParam("uuid") String uuid) {
        
        engagementService.delete(uuid);
        return Response.noContent().build();
    }
    
    @POST
    @APIResponses(value = { @APIResponse(responseCode = "400", description = "Failed validity check"),
            @APIResponse(responseCode = "409", description = "Engagement resource already exists"),
            @APIResponse(responseCode = "201", description = "Engagement stored in database") })
    @Operation(summary = "Creates the engagement resource in the database.")
    public Response createEngagement(@Context UriInfo uriInfo, @Valid Engagement engagement) {
        engagementService.create(engagement);
        
        UriBuilder builder = uriInfo.getAbsolutePathBuilder();
        builder.path(engagement.getUuid());
        
        return Response.created(builder.build()).entity(engagement).build();
    }
    
    @PUT
    @APIResponses(value = { @APIResponse(responseCode = "400", description = "Failed validity check"),
            @APIResponse(responseCode = "409", description = "Engagement resource with customer / project combo exists"),
            @APIResponse(responseCode = "404", description = "Engagement does not exist (by uuid)"),
            @APIResponse(responseCode = "204", description = "Engagement contained no changes"),
            @APIResponse(responseCode = "200", description = "Engagement stored in database") })
    @Operation(summary = "Updates the engagement resource in the database.")
    public Response updateEngagement(@Valid Engagement engagement) {
        boolean containedChanges = engagementService.update(engagement);
        
        if(containedChanges) {
            return Response.ok().entity(engagement).build();
        }
        
        return Response.noContent().build();
    }
    
    @PUT
    @Path("refresh")
    public Response refresh() {
        long newCount = engagementService.refresh();
        return Response.ok().header(TOTAL_HEADER, newCount).build();
    }
    
    @GET
    @Path("suggest") 
    public Response suggest(@QueryParam("partial") Optional<String> partial) {
        String tryIt = "";
        if(partial.isPresent()) {
            tryIt = partial.get();
        }
        
        return Response.ok().entity(engagementService.getCustomerSuggestions(tryIt)).build();
    }

}
