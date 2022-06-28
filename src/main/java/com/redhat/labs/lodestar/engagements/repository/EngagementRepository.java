package com.redhat.labs.lodestar.engagements.repository;

import static com.mongodb.client.model.Aggregates.addFields;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Aggregates.replaceRoot;
import static com.mongodb.client.model.Aggregates.sort;
import static com.mongodb.client.model.Aggregates.unwind;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static com.mongodb.client.model.Sorts.descending;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import com.redhat.labs.lodestar.engagements.model.EngagementState;
import org.bson.conversions.Bson;

import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.UseCase;

import io.quarkus.mongodb.panache.PanacheMongoRepository;

@ApplicationScoped
public class EngagementRepository implements PanacheMongoRepository<Engagement> {

    private static final String REGION = "region in :region";
    private static final String TYPE = "'type' in :engagementType";
    private static final String CATEGORY = "categories = :category";

    public List<Engagement> getEngagements(PageFilter pageFilter) {
        return findAll(pageFilter.getPanacheSort())
                .page(pageFilter.getPage(), pageFilter.getPageSize()).list();
    }

    /**
     *
     * @param email the email id to match (tech lead, EL or customer contact)
     * @param engagementIds a list of engagement ids that the user has participated in (from participant service)
     * @return A list of engagements that include the email or engagement ids of participant
     */
    public List<Engagement> getEngagementsByUser(PageFilter pageFilter, String email, Set<String> engagementIds) {
        List<Bson> ors = new ArrayList<>();

        ors.add(eq("engagementLeadEmail", email));
        ors.add(eq("technicalLeadEmail", email));
        ors.add(eq("customerContactEmail", email));
        ors.add(in("uuid", engagementIds));
        return mongoCollection().find(or(ors)).sort(pageFilter.getBsonSort()).skip(pageFilter.getStartAt()).limit(pageFilter.getPageSize()).into(new ArrayList<>());
    }

    public List<Engagement> getEngagements(PageFilter pageFilter, Set<String> regions, Set<String> types) {
        String query = "";
        Map<String, Object> params = new HashMap<>();

        if(!regions.isEmpty()) {
            query = REGION;
            params.put("region", regions);
        }

        if(!types.isEmpty()) {
            query = query.length() > 0 ? query + " and " + TYPE : TYPE;
            params.put("engagementType", types);
        }

        return find(query, pageFilter.getPanacheSort(), params)
                .page(pageFilter.getPage(), pageFilter.getPageSize()).list();
    }

    public long countEngagements(String searchInput, String category, Set<String> regions, Set<String> types, Set<EngagementState> states) {
        Bson finalQuery = createQuery(searchInput, category, regions, types, states);
        return mongoCollection().countDocuments(finalQuery);
    }

    public List<Engagement> findEngagements(PageFilter pageFilter, String searchInput, String category, Set<String> regions, Set<String> types, Set<EngagementState> states) {

        Bson finalQuery = createQuery(searchInput, category, regions, types, states);

        return mongoCollection().find(finalQuery).sort(pageFilter.getBsonSort()).skip(pageFilter.getStartAt()).limit(pageFilter.getPageSize()).into(new ArrayList<>());
    }

    private Bson createQuery(String searchInput, String category, Set<String> regions, Set<String> types, Set<EngagementState> states) {
        List<Bson> ands = new ArrayList<>();

        if(searchInput != null) {
            Pattern p = Pattern.compile(Pattern.quote(searchInput), Pattern.CASE_INSENSITIVE);
            Bson query = or(
                    regex("customerName", p),
                    regex("name", p));
            ands.add(query);
        }

        if(category != null) {
            ands.add(in("categories", category));
        }

        if(!regions.isEmpty()) {
            ands.add(in("region", regions));
        }

        if(!types.isEmpty()) {
            ands.add(in("type", types));
        }

        if(!states.isEmpty()) {
            Set<String> statusValues = states.stream().map(st -> st.toString()).collect(Collectors.toSet());
            ands.add(in("currentState", statusValues));
        }

        return and(ands);
    }

    public List<Engagement> findEngagementsWithoutLastUpdate() {
        return list("lastUpdate is null");
    }

    public long countEngagements(Set<String> regions, Set<String> types, Set<EngagementState> states) {
        Bson query = createQuery(null, null, regions, types, states);
        return mongoCollection().countDocuments(query);
    }

    private Query queryEngagements(Set<String> regions, Set<String> types, String category) {
        String query = "";
        Map<String, Object> params = new HashMap<>();

        if(!regions.isEmpty()) {
            query = REGION;
            params.put("region", regions);
        }

        if(category != null) {
            query = query.length() > 0 ? query + " and " + CATEGORY : CATEGORY;
            params.put("category", category);
        }

        if(!types.isEmpty()) {
            query = query.length() > 0 ? query + " and " + TYPE : TYPE;
            params.put("engagementType", types);
        }

        return new Query(query, params);
    }
    
    /**
     * match if uuid exists (basically is there a use case needed for add fields)
     * addFields - adds some sugar to the use case with engagement info
     * unwind - use cases are embedded docs.
     * replace root - allows mapping to use case object
     * @return all use cases possibly filtered by uuid
     */
    public List<UseCase> getAllUseCases(PageFilter paging, Set<String> regions) {
        List<Bson> bson = new ArrayList<>();

        if(!regions.isEmpty()) {
            bson.add(match(Filters.and(Filters.in("region", regions),
                    Filters.ne("useCases.description", null))));
        } else {
            bson.add(match(Filters.ne("useCases.description", null)));
        }

        bson.add(addFields(new Field<>("useCases.name", "$name"),
                new Field<>("useCases.customerName", "$customerName"),
                new Field<>("useCases.engagementUuid", "$uuid")
                ));
        bson.add(unwind(Columns.USE_CASES.getName()));
        bson.add(replaceRoot(Columns.USE_CASES.getName()));
        bson.add(sort(descending("updated", "uuid")));
        bson.add(paging.getOffSet());
        bson.add(paging.getLimit());

        
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
    
    public List<Engagement> getEngagementsWithCategory(String category, PageFilter pageFilter, Set<String> regions, Set<String> types) {
        Query q = queryEngagements(regions, types, category);
        return find(q.value, pageFilter.getPanacheSort(), q.parameters)
                .page(pageFilter.getPage(), pageFilter.getPageSize()).list();
    }

    public Optional<Engagement> getEngagement(String uuid) {
        return find("uuid", uuid).singleResultOptional();
    }

    public Optional<Engagement> getEngagementByProject(int projectId) {
        return find("projectId", projectId).singleResultOptional();
    }

    public Optional<Engagement> getByCustomerAndEngagementName(String customerName, String engagementName) {
        return find("customerName = ?1 and name = ?2", customerName, engagementName).singleResultOptional();


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

    public void updateCount(String uuid, int count, String column) {
        update(column, count).where("uuid", uuid);
    }

    public void updateLastUpdate(String uuid, Instant time) {
        update("lastUpdate", time).where("uuid", uuid);
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
