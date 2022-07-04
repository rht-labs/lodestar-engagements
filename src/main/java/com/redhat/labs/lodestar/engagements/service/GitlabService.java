package com.redhat.labs.lodestar.engagements.service;

import java.util.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.google.gson.*;
import com.redhat.labs.lodestar.engagements.model.EngagementState;
import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.labs.lodestar.engagements.utils.GitLabPathUtils;
import com.redhat.labs.lodestar.engagements.exception.EngagementException;
import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.rest.client.GitlabApiClient;

import io.quarkus.vertx.ConsumeEvent;

@ApplicationScoped
public class GitlabService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabService.class);
    
    @Inject
    EngagementService engagementService;

    @Inject
    CategoryService categoryService;

    @Inject
    GitlabApiClient gitlabApiClient;

    @Inject
    JsonMarshaller json;

    @ConfigProperty(name = "disable.refresh.bus")
    boolean disableBus;

    @ConfigProperty(name = "lodestar.tag")
    String lodestarTag;

    @ConfigProperty(name = "lodestar.tag.format")
    String lodestarTagFormat;

    Gson gson = new GsonBuilder().setPrettyPrinting().create();

    List<String> statusPossibilities;

    @PostConstruct
    void setPossibleStatuses() {
        statusPossibilities = new ArrayList<>();

        for(EngagementState state : EngagementState.values()) {
            statusPossibilities.add(String.format(lodestarTagFormat, state));
        }

        LOGGER.debug("states {}", statusPossibilities);
    }
    
    /**
     * Creates:
     *   - Group (customer) \ Group \ (engagement) \ Project (iac)
     *   - engagement file and seed files for other services
     *   - webhooks
     *   - deploy keys
     * @param engagement create this
     */
    @ConsumeEvent(value = EngagementService.CREATE_ENGAGEMENT, blocking = true)
    public void createEngagementInGitlab(Engagement engagement) {
        LOGGER.debug("E {}", engagement);
        
        Optional<Group> customerGroupOption = gitlabApiClient.getGroupFromRoot(engagement.getCustomerName());
        Group customerGroup;

        customerGroup = customerGroupOption.orElseGet(() -> gitlabApiClient.createGroup(engagement.getCustomerName()));
        
        Group engagementGroup = gitlabApiClient.createGroup(engagement.getName(), customerGroup.getId());
        
        Project project = gitlabApiClient.createProject(engagement.getUuid(), engagementGroup.getId());
        engagement.setProjectId(project.getId());

        //Placing before commit so that the webhook will fire on commit
        gitlabApiClient.createWebhooks(engagement.getProjectId(), engagement.getState());
        gitlabApiClient.activateDeployKey(engagement.getProjectId());

        LOGGER.debug("hooks created");
        createEngagementFilesInGitlab(engagement);
        engagementService.update(engagement, false);

        LOGGER.debug("creation complete {}", engagement);
    }

    /**
     * Only used when a failure occurred and the project was created but the files were not.
     * @param engagement
     */
    @ConsumeEvent(value = EngagementService.CREATE_ENGAGEMENT_FILES, blocking = true)
    public void createEngagementFiles(Engagement engagement) {
        createEngagementFilesInGitlab(engagement);
    }

    private void createEngagementFilesInGitlab(Engagement engagement) {
        String legacy = this.createLegacyJson(engagement);
        gitlabApiClient.createEngagementFiles(engagement, legacy);
    }
    
    /**
     * Updates: 
     *  - Group and Projects - cleans up unused
     *  - Engagement file
     *  
     * @param engagement update this
     */
    @ConsumeEvent(value = EngagementService.UPDATE_ENGAGEMENT, blocking = true)
    public void updateEngagementInGitlab(Engagement engagement) {
        LOGGER.debug("Gitlab engagement update - {}", engagement);

        Optional<Project> existingOption = gitlabApiClient.getProject(engagement.getProjectId());

        if(existingOption.isEmpty()) {
            throw new EngagementException(String.format("Project was not found for engagement %s", engagement));
        }
        
        Project existing = existingOption.get();
        String gitlabPath = existing.getPathWithNamespace();
        String engagementPath = GitLabPathUtils.getValidPath(gitlabApiClient.getEngagementPathPrefix(), engagement.getCustomerName(),
                engagement.getName());
        
        if(!engagementPath.equals(gitlabPath)) {
            LOGGER.debug("old path {} new path {}", gitlabPath, engagementPath);
            changeName(existing, engagement);
        } else {
            LOGGER.debug("path did not change");
        }

        String legacy = updateLegacyJson(engagement);
        gitlabApiClient.updateEngagementFile(engagement, legacy);
        engagementService.update(engagement, false);

    }
    
    @ConsumeEvent(value = EngagementService.DELETE_ENGAGEMENT, blocking = true)
    public void deleteEngagementInGitlab(Engagement engagement) {
        Optional<Group> engagementGroupOption = gitlabApiClient.getGroup(engagement);
        
        if(engagementGroupOption.isEmpty()) {
            throw new EngagementException("Unable to delete engagement " + engagement);
        }
        
        int parentGroup = engagementGroupOption.get().getParentId();
        List<Group> subGroups = gitlabApiClient.getSubGroups(String.valueOf(parentGroup));
        
        if(subGroups.size() == 1) { //This engagement group is the only subgroup delete here
            gitlabApiClient.deleteGroup(parentGroup);
        } else {
            gitlabApiClient.deleteGroup(engagement);
        }
            
    }
    
    @ConsumeEvent(value = CategoryService.MERGE_CATEGORIES, blocking = true)
    public void updateCategoriesInGitlab(Engagement engagement) {
        LOGGER.debug("category update for {}", engagement.getUuid());
        List<Category> categories = categoryService.getCategories(engagement.getUuid());
        gitlabApiClient.updateCategories(engagement, categories);
    }
    
    @ConsumeEvent(value = CategoryService.REFRESH_CATEGORIES, blocking = true)
    public void refreshCategories(String message) {
        LOGGER.debug("{}", message);
        if (!disableBus) {
            refreshCategories();
        }
    }

    @ConsumeEvent(value = ConfigService.UPDATE_ALL_WEBHOOKS, blocking = true)
    public void updateWebhooks(String message) {
        LOGGER.debug("{}", message);
        List<Engagement> engagements = engagementService.getEngagements();
        engagements.forEach(this::updateWebhook);
    }

    @ConsumeEvent(value = EngagementService.UPDATE_STATUS, blocking = true)
    public void updateStatus(Engagement engagement) {
        Optional<Project> p = gitlabApiClient.getProject(engagement.getProjectId());
        if(p.isPresent()) {
            List<String> tags = p.get().getTagList();
            tags.removeAll(statusPossibilities);

            String stateTag = String.format(lodestarTagFormat, engagement.getCurrentState());
            tags.add(stateTag);

            gitlabApiClient.updateProject(p.get());
        }
    }

    /**
     * Fetches project data from gitlab
     * @param projectId the gitlab project id
     * @return true if project data is found otherwise false
     */
    public boolean doesProjectExist(int projectId) {
        if(projectId < 2) {
            return false;
        }
        Optional<Project> p = gitlabApiClient.getProject(projectId);
        return p.isPresent();
    }

    /**
     * Fetches engagement/engagement.json from gitlab
     * @param projectId the gitlab project id
     * @return true if the engagement.json is present otherwise false
     */
    public boolean doesEngagementJsonExist(int projectId) {
        return gitlabApiClient.getEngagement(projectId).isPresent();
    }

    public void refreshCategories() {

        categoryService.purge();
        long categoryCount = gitlabApiClient.refreshCategories(engagementService.getEngagements());
        LOGGER.debug("Added {} categories", categoryCount);
    }
    
    public List<Engagement> getEngagements() {
        return getEngagements(Collections.emptySet());
    }

    public List<Engagement> getEngagements(Set<String> uuids) {
        return gitlabApiClient.getEngagements(uuids);
    }

    private void updateWebhook(Engagement engagement) {
        gitlabApiClient.deleteProjectHooks(engagement.getProjectId());
        gitlabApiClient.createWebhooks(engagement.getProjectId(), engagement.getState());
    }
    
    /**
     * If Engagement group name / path has changed then update the group name / path
     * 
     * If Customer group name / path changed then
     *   check for subgroups - if no others rename name / path of group
     *   
     *   if subgroups
     *     check for existing group
     *       if it doesn't exist - create new customer group
     *     update engagement group parent to customer
     *   
     * 
     * @param existing existing project
     * @param engagement change this
     */
    private void changeName(Project existing, Engagement engagement) {
        String projectPath = GitLabPathUtils.generateValidPath(engagement.getName());
        String currentPath = existing.getNamespace().getPath();
        
        boolean engagementNameChanged = !projectPath.equals(currentPath) || !engagement.getName().equals(existing.getNamespace().getName());
        
        Optional<Group> currentEngagementGroupOption = gitlabApiClient.getGroup(String.valueOf(existing.getNamespace().getId()));
        if(currentEngagementGroupOption.isEmpty()) {
            throw new EngagementException(String.format("Current engagement group was not found %s", currentPath));
        }

        Optional<Group> currentCustomerGroupOption = gitlabApiClient.getGroup(String.valueOf(currentEngagementGroupOption.get().getParentId()));
        if(currentCustomerGroupOption.isEmpty()) {
            throw new EngagementException(String.format("Current customer group was not found %s", currentPath));
        }

        Group customerCurrentGroup = currentCustomerGroupOption.get();
        
        boolean customerChanged = !customerCurrentGroup.getName().equals(engagement.getName()) && 
                !customerCurrentGroup.getPath().equals(GitLabPathUtils.generateValidPath(engagement.getCustomerName()));
        
        if(customerChanged) {
            List<Group> customerSubGroups = gitlabApiClient.getSubGroups(String.valueOf(customerCurrentGroup.getId()));
            Optional<Group> customerNewGroupOption = gitlabApiClient.getGroupFromRoot(engagement.getCustomerName());
            
            if(customerSubGroups.size() == 1 && customerNewGroupOption.isEmpty()) { //This engagement is the only engagement for this customer. Just rename path / name
                LOGGER.debug("Renaming customer group for {}", engagement.getCustomerName());
                customerCurrentGroup.setName(engagement.getCustomerName());
                customerCurrentGroup.setPath(GitLabPathUtils.generateValidPath(engagement.getCustomerName()));
                 
                gitlabApiClient.updateGroup(customerCurrentGroup);
            } else { // A new group is needed here and at the project level since there are multiple engagements for this customer.
                createNewCustomerGroupOnChange(customerNewGroupOption, engagement.getCustomerName(), currentPath,
                        existing.getId(), customerCurrentGroup.getId(), customerSubGroups.size());
            }
        }
        
        if(engagementNameChanged) {
            gitlabApiClient.updateGroupName(engagement.getName(), existing.getNamespace().getId());
        }
    }

    private void createNewCustomerGroupOnChange(Optional<Group> customerNewGroupOption, String customerName, String currentPath, int existingProjectId,
                                           int currentCustomerGroupId, int currentSubGroupSize) {
        Group newCustomerGroup;
        if(customerNewGroupOption.isEmpty()) {
            newCustomerGroup = gitlabApiClient.createGroup(customerName);
        } else { //Group already exists no need to create
            newCustomerGroup = customerNewGroupOption.get();
        }

        Group newEngagementGroup = gitlabApiClient.createGroup(currentPath, newCustomerGroup.getId());
        gitlabApiClient.transferProject(existingProjectId, newEngagementGroup.getId());

        gitlabApiClient.deleteGroup(currentCustomerGroupId);

        if(currentSubGroupSize == 1) { //We needed a new customer group but there was one already, so we couldn't just update the name / path
            gitlabApiClient.deleteGroup(currentCustomerGroupId);
        }
    }
    
    private String createLegacyJson(Engagement e) {
        return getLegacyJson(e, new JsonObject());
    }

    private String updateLegacyJson(Engagement e) {
        String legacy = gitlabApiClient.getLegacyEngagement(e.getProjectId());

        JsonElement legacyElement = gson.fromJson(legacy, JsonElement.class);
        JsonObject legacyJsonObject = legacyElement.getAsJsonObject();
        return getLegacyJson(e, legacyJsonObject);
    }

    String getLegacyJson(Engagement e, JsonObject legacyJsonObject) {

        String v2 = json.toJson(e);
        JsonElement v2element = gson.fromJson(v2, JsonElement.class);
        JsonObject v2FullObject = v2element.getAsJsonObject();

        addElement("hosting_environments", legacyJsonObject, v2FullObject );
        addElement("engagement_users", legacyJsonObject, v2FullObject );
        addElement("artifacts", legacyJsonObject, v2FullObject );
        renameElement("type", "engagement_type", v2FullObject);
        renameElement("region", "engagement_region", v2FullObject);
        renameElement("name", "project_name", v2FullObject);
        v2FullObject.remove("participant_count");
        v2FullObject.remove("hosting_count");
        v2FullObject.remove("artifact_count");
        v2FullObject.remove("last_message");

        //sort for readability on commit
        JsonObject sorted = new JsonObject();
        v2FullObject.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(es -> sorted.add(es.getKey(), es.getValue()));

        LOGGER.debug("render {}", gson.toJson(sorted));

        return gson.toJson(sorted);
    }

    void addElement(String elementName, JsonObject existing, JsonObject newValues) {
        //GET
        JsonElement hostingElement = existing.get(elementName);
        //ADD
        newValues.add(elementName, hostingElement);
    }

    void renameElement(String elementName, String newElementName, JsonObject newValues) {
        //GET
        JsonElement hostingElement = newValues.get(elementName);
        //ADD
        newValues.add(newElementName, hostingElement);
    }

}
