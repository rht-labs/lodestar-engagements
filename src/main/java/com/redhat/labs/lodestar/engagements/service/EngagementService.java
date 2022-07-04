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
import io.quarkus.scheduler.Scheduled;
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
    public static final String UPDATE_STATUS = "update.status.engagement.event";
    public static final String CREATE_ENGAGEMENT_FILES = "create.engagement.file.event";
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
    ActivityService activityService;
    
    @Inject
    GitlabService gitlabService;
    
    Javers javers;
    
    @PostConstruct
    public void setupJavers() {
        List<String> ignoredProps = Arrays.asList("id", "createdDate", "creationDetails", "lastMessage", "lastUpdateByEmail", "lastUpdateByName",
                "lastUpdate", "projectId", "currentState");

        javers = JaversBuilder.javers().withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE)
                .registerEntity(new EntityDefinition(Engagement.class, "uuid", ignoredProps)).build();
    }

    @Scheduled(every="5m")
    void checkDB() {
        long count = engagementRepository.count();

        if(count == 0) {
            LOGGER.info("No engagements found. Initiating refresh");
            refresh();
        }
    }

    @Scheduled(cron = "{cron.status}")
    void updateStatusTimer() {
        LOGGER.debug("Updating states");
        List<Engagement> changedEngagements = new ArrayList<>();
        engagementRepository.findAll().stream().forEach(e -> {
            if(e.getCurrentState() == null || e.getCurrentState() != e.getState()) {
                e.setCurrentState(e.getState());
                changedEngagements.add(e);
                bus.publish(UPDATE_STATUS, e);
            }
        });

        if(!changedEngagements.isEmpty()) {
            LOGGER.debug("Updating {} states ", changedEngagements.size());
            engagementRepository.update(changedEngagements);
        }
    }

    @Scheduled(every = "5m", delayed = "1m")
    void checkLastUpdate() {
        List<Engagement> noUpdated = engagementRepository.findEngagementsWithoutLastUpdate();
        if(!noUpdated.isEmpty()) {
            LOGGER.info("Attempting update of last activity for {} engagements", noUpdated.size());
            activityService.getLastActivityPerEngagement(noUpdated);
            engagementRepository.update(noUpdated);
            LOGGER.info("Last updated check completed with {} changes", noUpdated.size());
        }
    }

    public void updateAllEngagementStates() {
        LOGGER.debug("Updating all states in gitlab");
        engagementRepository.findAll().stream().forEach(e -> {
                e.setCurrentState(e.getState());
                bus.publish(UPDATE_STATUS, e);
        });
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

    /**
     *
     * @return A list of uuids that do not exist in gitlab
     */
    public Set<String> getEngagementsNotInGitlab() {
        String message = "Engagement %s %s %s";
        Set<String> missing = new TreeSet<>();
        getEngagements().parallelStream().forEach(e -> {
            if(!gitlabService.doesProjectExist(e.getProjectId())) {
                missing.add(String.format(message, e.getCustomerName(), e.getName(), e.getUuid()));
            }
        });

        return missing;
    }

    /**
     * Re-fires a commit without commit info :(
     * @param uuid
     * @param message A message to add to the commit since it be recreated
     * @return
     */
    public Map<String, String> resendLastUpdateToGitlab(String uuid, String message) {
        LOGGER.debug("Retry update {}", uuid);
        String messageKey = "message";
        String messageValue = "%s not found for uuid %s";
        Optional<Engagement> option = getEngagement(uuid);
        if(option.isEmpty()) {
            return  Map.of(messageKey, String.format(messageValue, "Engagement", uuid));
        }

        Engagement engagement = option.get();
        engagement.setGitlabRetry(true);
        LOGGER.debug("last message ({}): {}", engagement.getUuid(), engagement.getLastMessage());

        if(gitlabService.doesProjectExist(engagement.getProjectId())) {
            String lastMessage = engagement.getLastMessage() == null ? message : String.format("%s. %n%s", message, engagement.getLastMessage());
            engagement.setLastMessage(lastMessage);

            if(gitlabService.doesEngagementJsonExist(engagement.getProjectId())) {
                bus.publish(UPDATE_ENGAGEMENT, engagement);
            } else {
                bus.publish(CREATE_ENGAGEMENT_FILES, engagement);
            }
        } else {
            bus.publish(CREATE_ENGAGEMENT, engagement);
        }

        return Collections.emptyMap();
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

        LOGGER.debug("Launch ahoy {} -- {}", engagement.getUuid(), engagement.getState());
        engagement.setCurrentState(engagement.getState());
        engagementRepository.update(engagement);
        engagement.setLastMessage(LAUNCH_MESSAGE);

        bus.publish(UPDATE_ENGAGEMENT, engagement);
        bus.publish(UPDATE_STATUS, engagement);
    }

    public void updateCount(String uuid, int count, String column) {
        engagementRepository.updateCount(uuid, count, column);
    }

    public void updateLastUpdate(String uuid) {
        LOGGER.debug("last update for {}", uuid);
        engagementRepository.updateLastUpdate(uuid, Instant.now());
    }
    
    public boolean update(Engagement engagement) {
        return update(engagement, true, false);
    }

    public boolean update(Engagement engagement, boolean updateGitlab) {
        return update(engagement, updateGitlab, false);
    }
    
    public boolean update(Engagement engagement, boolean updateGitlab, boolean categoryUpdate) {
        LOGGER.debug("updating e {} up git {} up cat {}", engagement.getUuid(), updateGitlab, categoryUpdate);

        boolean updated = false;

        Optional<Engagement> option = engagementRepository.getEngagement(engagement.getUuid());

        Engagement existing = option.orElseThrow(
                () -> new WebApplicationException(String.format("no engagement found for uuid %s, use POST to create", engagement.getUuid()), HttpStatus.SC_NOT_FOUND));

        if (engagementRepository.isNameTaken(engagement.getUuid(), engagement.getCustomerName(), engagement.getName())) { // Checking customer + engagement name match
            throw new WebApplicationException("This engagement name is in use by another uuid", Status.CONFLICT);
        }

        if(updateGitlab) {
            engagement.updateTimestamps();
        }

        boolean initialFieldUpdated = engagement.overrideImmutableFields(existing, categoryUpdate);

        updateUseCases(engagement, existing);

        Diff diff = javers.compare(existing, engagement);

        LOGGER.debug("diff {} ==> {}", diff.hasChanges(), diff);

        if (diff.hasChanges() || initialFieldUpdated) {
            updated = true;

            if(engagement.getCurrentState() != engagement.getState()) {
                engagement.setCurrentState(engagement.getState());
                bus.publish(UPDATE_STATUS, engagement);
            }

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

    public Map<EngagementState, Integer> getEngagementCountByStatus(Instant currentTime, Set<String> regions, Set<String> types) {


        List<Engagement> engagementList = regions.isEmpty() ? getEngagements() :
            getEngagements(PageFilter.builder().page(0).pageSize(1000).build(), regions, types, Collections.emptySet());

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

    public List<Engagement> getEngagements(PageFilter pageFilter, Set<String> regions, Set<String> types, Set<EngagementState> inStates) {
        List<Engagement> engagements = engagementRepository.getEngagements(pageFilter, regions, types);
        if(inStates.isEmpty()) {
            return engagements;
        }

        return filterEngagementsByState(engagements, inStates);

    }

    public List<Engagement> getEngagementsForUser(PageFilter pageFilter, String userEmail, Set<String> engagementUuids) {
        return engagementRepository.getEngagementsByUser(pageFilter, userEmail, engagementUuids);
    }

    public long countEngagements(String input, String category, Set<String> regions, Set<String> types, Set<EngagementState> states) {
        return engagementRepository.countEngagements(input, category, regions, types, states);
    }

    public List<Engagement> findEngagements(PageFilter pageFilter, String input, String category, Set<String> regions, Set<String> types, Set<EngagementState> states) {
        return engagementRepository.findEngagements(pageFilter, input, category, regions, types, states);
    }

    public List<Engagement> filterEngagementsByState(List<Engagement> engagements, Set<EngagementState> states) {
        return engagements.stream().filter(e -> states.contains(e.getState())).collect(Collectors.toList());
    }

    public List<Engagement> getEngagements(Set<EngagementState> states) {
        return getEngagements();
    }

    public long countAll() {
        return engagementRepository.count();
    }

    public long count(Set<String> regions, Set<String> types, Set<EngagementState> inStates) {
        return engagementRepository.countEngagements(regions, types, inStates);
    }

    public long countRegions(Set<String> regions, Set<String> types) {
        return engagementRepository.countEngagements(regions, types, Collections.emptySet());
    }
    
    public List<Engagement> getEngagementsWithCategory(String category, PageFilter pageFilter, Set<String> regions, Set<String> types, Set<EngagementState> inStates) {
        List<Engagement> engagements = engagementRepository.getEngagementsWithCategory(category, pageFilter, regions, types);
        if(inStates.isEmpty()) {
            return engagements;
        }

        return filterEngagementsByState(engagements, inStates);
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
        activityService.getLastActivityPerEngagement(engagements);

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
        engagements.forEach(e -> e.setCurrentState(e.getState()));
        participantService.addEngagementCount(engagements);
        artifactService.addEngagementCount(engagements);
        activityService.getLastActivityPerEngagement(engagements);

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
