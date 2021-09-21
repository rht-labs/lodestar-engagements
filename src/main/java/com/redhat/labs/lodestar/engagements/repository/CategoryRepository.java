package com.redhat.labs.lodestar.engagements.repository;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Projections.computed;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Sorts.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.enterprise.context.ApplicationScoped;

import com.redhat.labs.lodestar.engagements.utils.*;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Filters;
import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Counter;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import io.quarkus.panache.common.Sort;

@ApplicationScoped
public class CategoryRepository implements PanacheMongoRepository<Category> {
    private static final String COUNT = "count";
    
    public List<Category> getCategories(String engagementUuid) {
        return list("engagementUuid = ?1", engagementUuid);
    }

    public long countCategories(String engagementUuid) {
        return count("engagementUuid = ?1", engagementUuid);
    }
    
    public List<Counter>  getCategoryCounts(List<String> regions, PageFilter paging) {
        List<Bson> bson = new ArrayList<>();

        if(!regions.isEmpty()) {
            bson.add(match(Filters.in("region", regions)));
        }

        bson.add(group("$name", sum(COUNT, 1)));
        bson.add(project(fields(include(COUNT), computed("name", "$_id"))));
        bson.add(sort(orderBy(descending(COUNT), ascending("name"))));

        bson.add(paging.getOffSet());
        bson.add(paging.getLimit());
        
        return mongoCollection().aggregate(bson, Counter.class).into(new ArrayList<>()); 
    }
    
    public List<Category> getCategories(int page, int pageSize) {
        return findAll(Sort.by("name").and("engagementUuid")).page(page, pageSize).list();
    }

    public Set<String> getSuggestions(String partial) {
        Set<String> results = new TreeSet<>();
        find("name like ?1", partial).stream().forEach(c -> results.add(c.getName()));

        return results;
    }

}
