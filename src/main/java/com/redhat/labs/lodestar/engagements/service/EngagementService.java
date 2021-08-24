package com.redhat.labs.lodestar.engagements.service;

import java.time.Instant;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpStatus;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.ListCompareAlgorithm;
import org.javers.core.metamodel.clazz.EntityDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.labs.lodestar.engagements.utils.PageFilter;
import com.redhat.labs.lodestar.engagements.exception.ErrorMessage;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.UseCase;
import com.redhat.labs.lodestar.engagements.repository.EngagementRepository;

import io.vertx.mutiny.core.eventbus.EventBus;

@ApplicationScoped
public class EngagementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngagementService.class);
    
    public static final String UPDATE_ENGAGEMENT = "update.engagement.event";
    public static final String CREATE_ENGAGEMENT = "create.engagement.event";
    public static final String DELETE_ENGAGEMENT = "delete.engagement.event";
    
    @Inject
    EventBus bus;
    
    @Inject
    EngagementRepository engagementRepository;

    @Inject
    CategoryService categoryService;
    
    @Inject
    GitlabService gitlabService;
    
    Javers javers;
    
    @PostConstruct
    public void setupJavers() {
        List<String> ignoredProps = Arrays.asList("id", "createdDate", "creationDetails", "lastMessage", "lastUpdateByEmail", "lastUpdateName",
                "lastUpdated", "projectId");

        javers = JaversBuilder.javers().withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE)
                .registerEntity(new EntityDefinition(Engagement.class, "uuid", ignoredProps)).build();
    }

    public void create(Engagement engagement) {
        if(engagement.getUuid() != null) { //This will not be true as long as we persist in lodestar-backend
            throw new WebApplicationException("UUID cannot be set before create", Status.BAD_REQUEST);
        }
        
        if(engagementRepository.exists(engagement.getCustomerName(), engagement.getName())) {
            throw new WebApplicationException("This engagement exists", Status.CONFLICT);
        }
        
        engagement.setUuid(UUID.randomUUID().toString());
        engagement.clean();
        engagement.updateTimestamps();
        engagement.setCreator();
        engagement.persist();
       
        bus.publish(CREATE_ENGAGEMENT, engagement);
    }
    
    public boolean update(Engagement engagement) {
        return update(engagement, true);
    }
    
    public boolean update(Engagement engagement, boolean updateGitlab) {
        boolean updated = false;

        Optional<Engagement> option = engagementRepository.getEngagement(engagement.getUuid());

        Engagement existing = option.orElseThrow(
                () -> new WebApplicationException("no engagement found, use POST to create", HttpStatus.SC_NOT_FOUND));

        if (engagementRepository.isNameTaken(engagement.getUuid(), engagement.getCustomerName(), engagement.getName())) { // Checking customer + engagement name match
            throw new WebApplicationException("This engagement name is in use by another uuid", Status.CONFLICT);
        }

        engagement.updateTimestamps();
        engagement.overrideImmutableFields(existing);

        updateUseCases(engagement, existing);

        Diff diff = javers.compare(existing, engagement);

        LOGGER.debug("diff {}", diff);

        if (diff.hasChanges()) {
            updated = true;
            engagement.update();
            engagement.setLastMessage(diff.prettyPrint());
            if (updateGitlab) {
                bus.publish(UPDATE_ENGAGEMENT, engagement);
            }
        }

        return updated;
    }
    
    public void delete(String uuid) {
        Optional<Engagement> engagementOption = getEngagement(uuid);
        
        if(engagementOption.isEmpty()) {
            throw new WebApplicationException(Status.NOT_FOUND);
        }
        
        Engagement engagement = engagementOption.get();
        
        if(engagement.getLaunch() != null) {
            throw new WebApplicationException(Response.status(Status.BAD_REQUEST).entity(new ErrorMessage("Cannot delete a launched engagement %s", uuid)).build());
        }

        categoryService.updateCategories(engagement, new HashSet<>());
        engagement.delete();
        
        bus.publish(DELETE_ENGAGEMENT, engagement);
    }
    
    public List<Engagement> getEngagements() {
        return engagementRepository.getEngagements();
    }
    
    public long countAll() {
        return engagementRepository.count();
    }
    
    public List<Engagement> getEngagementsWithCategory(String category) {
        return engagementRepository.getEngagementsWithCategory(category);
    }
    
    public Optional<Engagement> getEngagement(String uuid) {
        return engagementRepository.getEngagement(uuid);
    }
    
    public List<UseCase> getUseCases(PageFilter pageFilter) {
        return engagementRepository.getAllUseCases(pageFilter);
    }
    
    public Optional<UseCase> getUseCase(String uuid) {
        return engagementRepository.getUseCase(uuid);
    }
    
    public Set<String> getCustomerSuggestions(String customer) {
        return engagementRepository.suggestCustomer(customer);
    }
    
    public long refresh() {
        LOGGER.debug("Refresh");
        List<Engagement> engagements = gitlabService.getEngagements();
        long purged = engagementRepository.purge();
        LOGGER.info("Purged {} engagements", purged);
        engagementRepository.persistAll(engagements);
        long count = engagementRepository.count();
        LOGGER.info("Refreshed {} engagements", count);
        
        bus.publish(CategoryService.REFRESH_CATEGORIES, CategoryService.REFRESH_CATEGORIES);
        
        return count;
        
    }
    
    /**
     * If no changes (javers) - don't bother
     * if no uuid then it's new
     * if uuid then it should exist (check db). If there are changes (via javers) then update last update
     * @param engagement update this engagement's use cases
     * @param existing compare against existing
     */
    private void updateUseCases(Engagement engagement, Engagement existing) {
        Diff diff = javers.compareCollections(existing.getUseCases(), engagement.getUseCases(), UseCase.class);
        
        if(diff.hasChanges()) {
            LOGGER.debug("Use case differences {}", diff);
            Instant now = Instant.now();
            engagement.getUseCases().forEach(useCase -> {
                if(useCase.getUuid() == null) { //new
                    useCase.setUuid(UUID.randomUUID().toString());
                    useCase.setCreated(now);
                    useCase.setUpdated(now);
                } else {
                    Optional<UseCase> exists = engagementRepository.getUseCase(useCase.getUuid()); //should always exist since uuid is set
                    if(exists.isPresent()) { 
                        if(javers.compare(exists.get(), useCase).hasChanges()) { //updated
                            useCase.setUpdated(now);
                        } else { //not updated - use existing
                            useCase.setUpdated(exists.get().getUpdated());
                        }
                    }
                }
            });   
        }
    }
    
}
