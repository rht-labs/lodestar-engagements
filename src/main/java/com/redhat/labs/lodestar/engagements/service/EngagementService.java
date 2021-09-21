package com.redhat.labs.lodestar.engagements.service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.redhat.labs.lodestar.engagements.model.*;
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
import com.redhat.labs.lodestar.engagements.repository.EngagementRepository;

import io.vertx.mutiny.core.eventbus.EventBus;

@ApplicationScoped
public class EngagementService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EngagementService.class);
    
    public static final String UPDATE_ENGAGEMENT = "update.engagement.event";
    public static final String CREATE_ENGAGEMENT = "create.engagement.event";
    public static final String DELETE_ENGAGEMENT = "delete.engagement.event";
    public static final String LAUNCH_MESSAGE = "\uD83D\uDEA2 \uD83C\uDFF4\u200D☠️ \uD83D\uDE80";
    
    @Inject
    EventBus bus;
    
    @Inject
    EngagementRepository engagementRepository;

    @Inject
    CategoryService categoryService;

    @Inject
    ParticipantService participantService;

    @Inject
    ArtifactService artifactService;
    
    @Inject
    GitlabService gitlabService;
    
    Javers javers;
    
    @PostConstruct
    public void setupJavers() {
        List<String> ignoredProps = Arrays.asList("id", "createdDate", "creationDetails", "lastMessage", "lastUpdateByEmail", "lastUpdateByName",
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
        engagementRepository.persist(engagement);
       
        bus.publish(CREATE_ENGAGEMENT, engagement);
    }

    public void launch(String uuid, String author, String authorEmail) {
        Optional<Engagement> engagementOption = engagementRepository.getEngagement(uuid);
        if(engagementOption.isEmpty()) {
            throw new WebApplicationException("No engagement for uuid " + uuid, 404);
        }

        Engagement engagement = engagementOption.get();

        if(engagement.getLaunch() != null) {
            throw new WebApplicationException("Engagement already launched " + uuid, 400);
        }

        engagement.setLaunch(Launch.builder().launchedBy(author).launchedByEmail(authorEmail)
                .launchedDateTime(Instant.now()).build());

        engagementRepository.update(engagement);
        engagement.setLastMessage(LAUNCH_MESSAGE);

        bus.publish(UPDATE_ENGAGEMENT, engagement);
    }

    public void updateCount(String uuid, int count, String column) {
        engagementRepository.updateCount(uuid, count, column);
    }
    
    public boolean update(Engagement engagement) {
        return update(engagement, true, false);
    }

    public boolean update(Engagement engagement, boolean updateGitlab) {
        return update(engagement, updateGitlab, false);
    }
    
    public boolean update(Engagement engagement, boolean updateGitlab, boolean categoryUpdate) {
        boolean updated = false;

        Optional<Engagement> option = engagementRepository.getEngagement(engagement.getUuid());

        Engagement existing = option.orElseThrow(
                () -> new WebApplicationException("no engagement found, use POST to create", HttpStatus.SC_NOT_FOUND));

        if (engagementRepository.isNameTaken(engagement.getUuid(), engagement.getCustomerName(), engagement.getName())) { // Checking customer + engagement name match
            throw new WebApplicationException("This engagement name is in use by another uuid", Status.CONFLICT);
        }

        engagement.updateTimestamps();
        boolean initialFieldUpdated = engagement.overrideImmutableFields(existing, categoryUpdate);

        updateUseCases(engagement, existing);

        Diff diff = javers.compare(existing, engagement);

        LOGGER.debug("diff {}", diff);

        if (diff.hasChanges() || initialFieldUpdated) {
            updated = true;
            engagementRepository.update(engagement);

            if (updateGitlab) {
                engagement.setLastMessage(diff.prettyPrint());
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
        engagementRepository.delete(engagement);
        
        bus.publish(DELETE_ENGAGEMENT, engagement);
    }

    public Map<EngagementState, Integer> getEngagementCountByStatus(Instant currentTime) {

        List<Engagement> engagementList = getEngagements();
        Map<EngagementState, Integer> statusCounts = new EnumMap<>(EngagementState.class);

        for (Engagement engagement : engagementList) {
            EngagementState state = engagement.getState(currentTime);

            int count = statusCounts.containsKey(state) ? statusCounts.get(state) + 1 : 1;
            statusCounts.put(state, count);
        }

        statusCounts.put(EngagementState.ANY, engagementList.size());

        return statusCounts;
    }
    
    public List<Engagement> getEngagements() {
        return engagementRepository.getEngagements(PageFilter.builder().page(0).pageSize(1000).build());
    }

    public List<Engagement> getEngagements(PageFilter pageFilter) {
        return engagementRepository.getEngagements(pageFilter);
    }

    public List<Engagement> getEngagements(PageFilter pageFilter, Set<String> regions) {
        return engagementRepository.getEngagements(pageFilter, regions);
    }

    public List<Engagement> getEngagements(Set<EngagementState> states) {
        return getEngagements().stream().filter(e -> states.contains(e.getState())).collect(Collectors.toList());
    }

    public long countAll() {
        return engagementRepository.count();
    }

    public long countRegions(Set<String> regions) {
        return engagementRepository.countEngagements(regions);
    }
    
    public List<Engagement> getEngagementsWithCategory(String category) {
        return engagementRepository.getEngagementsWithCategory(category);
    }


    public Optional<Engagement> getByCustomerAndEngagementName(String customerName, String engagementName) {
        return engagementRepository.getByCustomerAndEngagementName(customerName, engagementName);
    }
    
    public Optional<Engagement> getEngagement(String uuid) {
        return engagementRepository.getEngagement(uuid);
    }

    public Optional<Engagement> getEngagementByProject(int projectId) {
        return engagementRepository.getEngagementByProject(projectId);
    }
    
    public List<UseCase> getUseCases(PageFilter pageFilter, Set<String> regions) {
        return  engagementRepository.getAllUseCases(pageFilter, regions);
    }
    
    public Optional<UseCase> getUseCase(String uuid) {
        return engagementRepository.getUseCase(uuid);
    }
    
    public Set<String> getCustomerSuggestions(String customer) {
        return engagementRepository.suggestCustomer(customer);
    }

    public long refreshSelect(Set<String> uuids) {
        int count = 0;
        LOGGER.debug("Refresh select ({})", uuids.size());
        List<Engagement> engagements = gitlabService.getEngagements(uuids);
        participantService.addEngagementCount(engagements);
        artifactService.addEngagementCount(engagements);

        for(Engagement e : engagements) {
            engagementRepository.delete("uuid = ?1", e.getEngagementLeadEmail());
            engagementRepository.persist(e);
            count++;
        }

        bus.publish(CategoryService.REFRESH_CATEGORIES, CategoryService.REFRESH_CATEGORIES);
        return count;
    }
    
    public long refresh() {
        LOGGER.debug("Refresh");
        List<Engagement> engagements = gitlabService.getEngagements();
        participantService.addEngagementCount(engagements);
        artifactService.addEngagementCount(engagements);
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
