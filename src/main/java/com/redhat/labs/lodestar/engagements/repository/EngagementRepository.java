package com.redhat.labs.lodestar.engagements.repository;

import static com.mongodb.client.model.Aggregates.addFields;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.replaceRoot;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Sorts.descending;

import java.util.ArrayList;
import java.util.TreeSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;

import org.bson.conversions.Bson;

import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.UseCase;

import io.quarkus.mongodb.panache.PanacheMongoRepository;

@ApplicationScoped
public class EngagementRepository implements PanacheMongoRepository<Engagement> {

    public List<Engagement> getEngagements() {
        return listAll();
    }
    
    /**
     * match if uuid exists (basically is there a use case needed for add fields)
     * addFields - adds some sugar to the use case with engagement info
     * unwind - use cases are embedded docs.
     * replace root - allows mapping to use case object
     * @return all use cases possibly filtered by uuid
     */
    public List<UseCase> getAllUseCases(PageFilter paging) {
        List<Bson> bson = new ArrayList<>();
                
        bson.add(match(Filters.exists("useCases.uuid")));
        bson.add(addFields(new Field<>("useCases.name", "$name"),
                new Field<>("useCases.customerName", "$customerName"),
                new Field<>("useCases.engagementUuid", "$uuid")
                ));
        bson.add(unwind(Columns.USE_CASES.getName()));
        bson.add(replaceRoot(Columns.USE_CASES.getName()));
        bson.add(sort(descending("updated", "uuid")));
        bson.add(paging.getLimit());
        bson.add(paging.getOffSet());
        
        return mongoCollection().aggregate(bson, UseCase.class).into(new ArrayList<>());
    }
    
    public Optional<UseCase> getUseCase(String uuid) {
        List<Bson> bson = new ArrayList<>();
        
        bson.add(unwind(Columns.USE_CASES.getName()));
        bson.add(match(eq("useCases.uuid", uuid)));
        bson.add(replaceRoot(Columns.USE_CASES.getName()));
                
        List<UseCase> results = mongoCollection().aggregate(bson, UseCase.class).into(new ArrayList<>());
        
        if(results.size() == 1) {
            return Optional.of(results.get(0));
        }
        
        return Optional.empty();
    }
    
    public List<Engagement> getEngagementsWithCategory(String category) {
        return find("categories = ?1", category).list();
    }

    public Optional<Engagement> getEngagement(String uuid) {
        return find("uuid", uuid).singleResultOptional();
    }
    
    /**
     * Applies - distinct, like, sort
     * @param input match the input
     * @return an alphabetically sorted list of customers that match the input
     */
    public Set<String> suggestCustomer(String input) {
        String customer = "customerName";
        
        return mongoCollection().distinct(customer, regex(customer, input), String.class).into(new TreeSet<>());
    }
    
    public List<Engagement> getEngagementsForRegex(String input) {
        return list("customerName like ?1", input);
    }

    /**
     * If true, then this engagement has been persisted to the database
     * @param customerName customer name
     * @param engagementName engagement name
     * @return true if this customer exists by engagement and customer names
     */
    public boolean exists(String customerName, String engagementName) {
        return getByCustomerNameAndEngagementName(customerName, engagementName).isPresent();
    }
    
    public long purge() {
        return deleteAll();
    }
    
    public void persistAll(List<Engagement> engagements) {
        persist(engagements);
    }

    /**
     * Checks to see if the customer + engagement name pair is taken
     * 
     * @param uuid           - Omit this uuid since the engagement may be changing
     *                       names
     * @param customerName the name of the customer
     * @param engagementName the name of the engagement
     * @return true if the name is already in service
     */
    public boolean isNameTaken(String uuid, String customerName, String engagementName) {
        Optional<Engagement> e = find("name = ?1 and customerName = ?2 and uuid != ?3", engagementName, customerName, uuid).firstResultOptional();
        return e.isPresent();
    }

    public Optional<Engagement> getByCustomerNameAndEngagementName(String customerName, String engagementName) {
        return find("name = ?1 and customerName = ?2", engagementName, customerName).firstResultOptional();
    }
    
    public enum Columns {
        USE_CASES("$useCases");
        
        private final String name;
        
        Columns(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
    }
}
