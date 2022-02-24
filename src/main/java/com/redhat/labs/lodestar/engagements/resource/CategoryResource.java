package com.redhat.labs.lodestar.engagements.resource;

import java.util.*;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Counter;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.service.CategoryService;
import com.redhat.labs.lodestar.engagements.service.EngagementService;

@RequestScoped
@Path("/api/v2/categories")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Category")
public class CategoryResource {
    
    @Inject
    EngagementService engagementService;
    
    @Inject
    CategoryService categoryService;
    
    @GET
    public Response getCategories(@QueryParam("engagementUuid") Optional<String> engagementUuidOption, @BeanParam PageFilter pageFilter) {
        List<Category> categories;
        long count ;

        if(engagementUuidOption.isPresent()) {
            categories = categoryService.getCategories(engagementUuidOption.get());
            count = categoryService.countForEngagementUuid(engagementUuidOption.get());
        } else {
            categories = categoryService.getCategories(pageFilter.getPage(), pageFilter.getPageSize());
            count = categoryService.count();
        }
        return Response.ok(categories).header("x-total-categories", count).build();
    }

    @GET
    @Path("suggest")
    @Operation(summary = "Suggest categories based on input.")
    public Set<String> getCategorySuggestions(@DefaultValue("") @QueryParam("partial") String partial) {
        if(partial == null) {
            Set<String> cats = new TreeSet<>();
            categoryService.getCategories(1, 7).forEach(c -> cats.add(c.getName()));
            return cats;
        }
        return categoryService.suggestCategory(partial);
    }
    
    @GET
    @Path("rollup")
    public Response getCategoryRollup(@QueryParam("region") List<String> regions, @BeanParam PageFilter pageFilter) {
        List<Counter> categoryCounts = categoryService.getCategoryCounts(regions, pageFilter);
        return Response.ok(categoryCounts).build();
    }
    
    @POST
    @Path("{engagementUuid}")
    @APIResponses(value = { @APIResponse(responseCode = "201", description = "Categories stored in database") })
    @Operation(summary = "Creates the category resources in the database.")
    public Response updateCategories(@Context UriInfo uriInfo, @PathParam("engagementUuid") String engagementUuid, @DefaultValue("Gaton Boucher") @QueryParam("authorName") String authorName, @DefaultValue("bot@bot.com") @QueryParam("authorEmail") String authorEmail, Set<String> categories) {
        Optional<Engagement> engagement = engagementService.getEngagement(engagementUuid);
        
        if(engagement.isPresent()) {
            Engagement e = engagement.get();
            e.setLastUpdateByName(authorName);
            e.setLastUpdateByEmail(authorEmail);
            
            categoryService.updateCategories(engagement.get(), categories);
            
            UriBuilder builder = uriInfo.getAbsolutePathBuilder();
            
            return Response.created(builder.build()).entity(categories).build();
        }
        
        return Response.status(Status.NOT_FOUND).build();
    }
}
