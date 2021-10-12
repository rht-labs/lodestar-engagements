package com.redhat.labs.lodestar.engagements.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import io.quarkus.qute.Template;
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

    // Template to retrofit the data back to v1 for orchestration
    // Burn after upgrade
    @Inject
    Template engagement;

    @ConfigProperty(name = "disable.refresh.bus")
    boolean disableBus;
    
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

        gitlabApiClient.createWebhooks(engagement.getProjectId(), engagement.getState());
        gitlabApiClient.activateDeployKey(engagement.getProjectId());

        String legacy = this.createLegacyJson(engagement);

        gitlabApiClient.createEngagementFiles(engagement, legacy);
        
        engagementService.update(engagement, false);

        LOGGER.debug("creation complete {}", engagement);
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
        LOGGER.debug("Gitlabbing engagement update - {}", engagement);

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

        List<Category> categories = categoryService.getCategories(engagement.getUuid());
        String legacy = updateLegacyJson(engagement, null, null, null, categories);

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
        
        Group engagementCurrentGroup = currentEngagementGroupOption.get();
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
        return updateLegacyJson(e, null, null, null, Collections.emptyList());
    }

    private String updateLegacyJson(Engagement e, String artifacts, String participants, String hosting, List<Category> categoryList) {
        String categories = json.toJson(categoryList);
        String useCases = json.toJson(e.getUseCases());

        return engagement.data("engagement", e).data("artifacts", artifacts).data("participants", participants)
                .data("hosting", hosting).data("useCases", useCases).data("categories", categories).render();
    }

}
