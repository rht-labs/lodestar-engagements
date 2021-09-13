package com.redhat.labs.lodestar.engagements.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.skip;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageFilter {

    @DefaultValue("0")
    @Parameter(description = "page number of results to return")
    @QueryParam("page")
    private int page;
    
    @DefaultValue("5000")
    @Parameter(description = "page size")
    @QueryParam("pageSize")
    private int pageSize;
    
    @Parameter(description = "sort value. Default Dir to DESC. Ex. field1|ASC,field2,field3|ASC")
    @QueryParam("sort")
    private String sort;
    
    public Bson getOffSet() {
        return skip(page * pageSize);
    }
    
    public Bson getLimit() {
        return limit(pageSize);
    }
}
