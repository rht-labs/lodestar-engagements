package com.redhat.labs.lodestar.engagements.utils;

import io.quarkus.panache.common.Sort;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.conversions.Bson;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.skip;
import static com.mongodb.client.model.Sorts.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageFilter {

    @Builder.Default
    private Sort defaultSort = Sort.by("lastUpdate", Sort.Direction.Descending).and("uuid");

    @Builder.Default
    private Bson defaultBsonSort = descending("lastUpdate", "uuid");

    @DefaultValue("0")
    @Parameter(description = "page number of results to return")
    @QueryParam("page")
    private int page;
    
    @DefaultValue("5000")
    @Parameter(description = "page size")
    @QueryParam("pageSize")
    private int pageSize;
    
    @Parameter(description = "sort value. Default Dir to ASC. Ex. field1|DESC,field2,field3|DESC. Always last sort by uuid")
    @QueryParam("sort")
    private String sort;

    public int getStartAt() {
        return page * pageSize;
    }
    
    public Bson getOffSet() {
        return skip(page * pageSize);
    }
    
    public Bson getLimit() {
        return limit(pageSize);
    }

    public Bson getBsonSort() {
        if(sort == null) {
            return defaultBsonSort;
        }

        String[] sortAll = sort.split(",");
        List<Bson> querySort = new ArrayList<>();
        String direction;

        for (String s : sortAll) {
            String[] sortFields = s.split("\\|");

            if("projectName".equals(sortFields[0])) { //legacy naming on FE
                sortFields[0] = "name";
            }
            direction = sortFields.length == 2 ? sortFields[1] : "";
            querySort.add(isDescending(direction) ? descending(sortFields[0]) : ascending(sortFields[0]));
        }

        querySort.add(ascending("uuid"));
        return orderBy(querySort);

    }

    public Sort getPanacheSort() {
        if(sort == null) {
            return defaultSort;
        }

        String[] sortAll = sort.split(",");
        Sort querySort = null;
        String direction;

        for (String s : sortAll) {
            String[] sortFields = s.split("\\|");

            if("projectName".equals(sortFields[0])) { //legacy naming on FE
                sortFields[0] = "name";
            }
            direction = sortFields.length == 2 ? sortFields[1] : "";
            if (querySort == null) {
                querySort = Sort.by(sortFields[0], getDirection(direction));
            } else {
                querySort.and(sortFields[0], getDirection(direction));
            }
        }

        if(querySort != null) {
            querySort.and("uuid");
        }

        return querySort;
    }

    private Sort.Direction getDirection(String dir) {
        if("DESC".equalsIgnoreCase(dir)) {
            return Sort.Direction.Descending;
        }

        return Sort.Direction.Ascending;
    }

    private boolean isDescending(String dir) {
        return "DESC".equalsIgnoreCase(dir);
    }
}
