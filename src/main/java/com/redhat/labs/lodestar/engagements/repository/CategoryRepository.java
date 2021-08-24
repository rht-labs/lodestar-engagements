package com.redhat.labs.lodestar.engagements.repository;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Projections.computed;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Sorts.descending;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

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
    
    public List<Counter>  getCategoryCounts(Optional<String> region) {
        List<Bson> bson = new ArrayList<>();

        region.ifPresent(s -> bson.add(match(Filters.eq("region", s))));
        
        bson.add(group("$name", sum(COUNT, 1)));
        bson.add(project(fields(include(COUNT), computed("name", "$_id"))));
        bson.add(sort(descending(COUNT)));
        
        return mongoCollection().aggregate(bson, Counter.class).into(new ArrayList<>()); 
    }
    
    public List<Category> getCategories(int page, int pageSize) {
        return findAll(Sort.by("name").and("engagementUuid")).page(page, pageSize).list();
    }

}
