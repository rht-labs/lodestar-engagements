package com.redhat.labs.lodestar.engagements.utils;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.skip;

import java.util.Optional;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.bson.conversions.Bson;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageFilter {

    @DefaultValue("0")
    @Parameter(description = "page number of results to return")
    @QueryParam("page")
    private int page;
    
    @DefaultValue("500")
    @Parameter(description = "page size")
    @QueryParam("pageSize")
    private int pageSize;
    
    @Parameter(description = "sort value. Default Dir to DESC. Ex. field1|ASC,field2,field3|ASC")
    @QueryParam("sort")
    private Optional<String> sort;
    
    public Bson getOffSet() {
        return skip(page * pageSize);
    }
    
    public Bson getLimit() {
        return limit(pageSize);
    }
}
