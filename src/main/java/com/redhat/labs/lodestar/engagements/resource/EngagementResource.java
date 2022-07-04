package com.redhat.labs.lodestar.engagements.resource;

import java.time.*;
import java.util.*;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import com.redhat.labs.lodestar.engagements.model.EngagementState;
import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.redhat.labs.lodestar.engagements.exception.ErrorMessage;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

@RequestScoped
@Path("/api/v2/engagements")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Engagements")
public class EngagementResource {
    private static final String TOTAL_HEADER = "x-total-engagements";
    private static final String ACCESS_CONTROL_EXPOSE_HEADER = "Access-Control-Expose-Headers";
    private static final String LAST_UPDATE_HEADER = "last-update";

    @Inject
    EngagementService engagementService;
    
    @GET
    public Response getEngagements(@BeanParam PageFilter pagingFilter, @QueryParam("region") Set<String> region,
               @QueryParam("types") Set<String> types, @QueryParam("inStates") Set<EngagementState> states,
               @QueryParam("q") String search, @QueryParam("category") String category) {

        List<Engagement> engagements;
        long total = 0;
        if(search == null && category == null && region.isEmpty() && types.isEmpty() && states.isEmpty()) {
            engagements = engagementService.getEngagements();
            total = engagements.size();
        } else {
            engagements = engagementService.findEngagements(pagingFilter, search, category, region, types, states);
            total = engagementService.countEngagements(search, category, region, types, states);
        }
        return Response.ok(engagements).header(TOTAL_HEADER, total).build(); //no paging yet
    }

    @GET
    @Path("inStates")
    public Response getEngagements(@QueryParam("inStates") Set<EngagementState> states) {
        if(states.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity(new ErrorMessage("State list is empty")).build();
        }
        List<Engagement> engagements = engagementService.findEngagements(new PageFilter(), null, null, Collections.emptySet(), Collections.emptySet(), states);
        long total = engagementService.countEngagements(null, null, Collections.emptySet(), Collections.emptySet(), states);
        return Response.ok(engagements).header(TOTAL_HEADER, total).build();
    }

    @GET
    @Path("byUser/{email}")
    public Response getEngagementsForUser(@PathParam("email") String email, @QueryParam("engagementUuids") Set<String> engagementUuids, @BeanParam PageFilter pagingFilter) {
        List<Engagement> engagements = engagementService.getEngagementsForUser(pagingFilter, email, engagementUuids);
        return Response.ok(engagements).header(TOTAL_HEADER, engagements.size()).build();
    }

    @GET
    @Path("category/{category}")
    @Operation(summary = "Gets a list of engagements that have use the category input.")
    public Response getEngagementWithCategory(@PathParam("category") String category, @BeanParam PageFilter pagingFilter,
              @QueryParam("region") Set<String> region, @QueryParam("types") Set<String> types, @QueryParam("inStates") Set<EngagementState> states) {
        List<Engagement> engagements = engagementService.getEngagementsWithCategory(category, pagingFilter, region, types, states);
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

    @GET
    @Path("project/{id}")
    public Response getEngagement(@PathParam("id") int projectId) {
        Optional<Engagement> engagement = engagementService.getEngagementByProject(projectId);

        if(engagement.isPresent()) {
            return Response.ok(engagement).build();
        }

        return Response.status(404).entity(new ErrorMessage("No engagement found for project %s", projectId)).build();
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

        Optional<Engagement> persisted = engagementService.getEngagement(engagement.getUuid());
        if(persisted.isPresent()) {
            return Response.created(builder.build()).entity(persisted).build();
        }

        return Response.created(builder.build()).build();
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
    @Path("{uuid}/launch")
    public Response launch(@PathParam("uuid") String uuid, @QueryParam("author") String author, @QueryParam("authorEmail") String authorEmail) {
        engagementService.launch(uuid, author, authorEmail);
        return Response.ok(engagementService.getEngagement(uuid)).build();
    }

    @GET
    @Path("gitlab")
    @Operation(summary = "Gets a list of engagements that are in the db but not gitlab")
    public Set<String> getEngagementsNotInGitlab() {
        return engagementService.getEngagementsNotInGitlab();
    }

    @PUT
    @APIResponses(value = {
            @APIResponse(responseCode = "404", description = "Engagement does not exist (by uuid)"),
            @APIResponse(responseCode = "200", description = "Engagement sent to gitlab") })
    @Operation(summary = "Retries the persist to gitlab. Message only applies to updates. Will ignore")
    @Path("retry")
    public Response retryGitlabPersistence(@QueryParam("uuid") String uuid, @DefaultValue("") @QueryParam("message") String message) {
        Map<String, String> resp = engagementService.resendLastUpdateToGitlab(uuid, message);

        if(resp.isEmpty()) {
            return Response.ok().build();
        }
        return Response.status(Response.Status.NOT_FOUND).entity(resp).build();
    }

    @PUT
    @Path("{uuid}/participants/{count}")
    public Response updateParticipants(@PathParam("uuid") String uuid, @PathParam("count") int count) {
        engagementService.updateCount(uuid, count, "participantCount");
        return Response.ok().build();
    }

    @PUT
    @Path("{uuid}/artifacts/{count}")
    public Response updateArtifacts(@PathParam("uuid") String uuid, @PathParam("count") int count) {
        engagementService.updateCount(uuid, count, "artifactCount");
        return Response.ok().build();
    }

    @PUT
    @Path("{uuid}/lastUpdate")
    public Response updateLastUpdate(@PathParam("uuid") String uuid) {
        String cleanUuid = uuid.replaceAll("[\n\r\t]", "_");
        engagementService.updateLastUpdate(cleanUuid);
        return Response.ok().build();
    }

    
    @PUT
    @Path("refresh")
    public Response refresh(@QueryParam("uuids") Set<String> uuids) {
        long newCount;

        if(uuids.isEmpty()) {
            newCount = engagementService.refresh();
        } else {
            newCount = engagementService.refreshSelect(uuids);
        }

        return Response.ok().header(TOTAL_HEADER, newCount).build();
    }

    @PUT
    @Path("refresh/state")
    public Response updateStatesInGitlab() {
        engagementService.updateAllEngagementStates();
        return Response.ok().build();
    }
    
    @GET
    @Path("suggest") 
    public Response suggestCustomer(@QueryParam("partial") String partial) {
        if(partial == null) {
            return Response.ok(Collections.emptySet()).build();
        }
        
        return Response.ok().entity(engagementService.getCustomerSuggestions(partial)).build();
    }

    @GET
    @Path("customer/{customer}/engagement/{engagement}")
    public Response getByCustomerAndEngagementName(@PathParam("customer") String customerName, @PathParam("engagement") String engagementName) {
        Optional<Engagement> engagement = engagementService.getByCustomerAndEngagementName(customerName, engagementName);

        if(engagement.isPresent()) {
            return Response.ok(engagement.get()).build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("count")
    public Response getCountByStatus(@QueryParam("time") String time, @QueryParam("region") Set<String> regions) {

        Instant compare = Instant.now();

        if(time != null) {
            compare = Instant.parse(time);
        }

        return Response.ok(engagementService.getEngagementCountByStatus(compare, regions, Collections.emptySet())).build();
    }

    @HEAD
    @Path("{uuid}")
    public Response getLastUpdate(@PathParam("uuid") String uuid) {
        Optional<Engagement> engagement = engagementService.getEngagement(uuid);
        if(engagement.isPresent()) {
            return Response.ok().header(LAST_UPDATE_HEADER, engagement.get().getLastUpdate())
                .header(ACCESS_CONTROL_EXPOSE_HEADER, LAST_UPDATE_HEADER).build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

}
