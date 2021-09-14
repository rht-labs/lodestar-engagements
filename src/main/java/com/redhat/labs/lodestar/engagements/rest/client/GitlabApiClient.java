package com.redhat.labs.lodestar.engagements.rest.client;

import java.security.SecureRandom;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.labs.lodestar.engagements.model.EngagementState;
import com.redhat.labs.lodestar.engagements.service.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.GitLabApiException;
import org.gitlab4j.api.models.Commit;
import org.gitlab4j.api.models.CommitAction;
import org.gitlab4j.api.models.CommitAction.Action;
import org.gitlab4j.api.models.CommitPayload;
import org.gitlab4j.api.models.DeployKey;
import org.gitlab4j.api.models.Group;
import org.gitlab4j.api.models.GroupParams;
import org.gitlab4j.api.models.GroupProjectsFilter;
import org.gitlab4j.api.models.Project;
import org.gitlab4j.api.models.ProjectHook;
import org.gitlab4j.api.models.RepositoryFile;
import org.gitlab4j.api.models.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.labs.lodestar.engagements.utils.JsonMarshaller;
import com.redhat.labs.lodestar.engagements.exception.EngagementException;
import com.redhat.labs.lodestar.engagements.exception.EngagementGitlabException;
import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.model.HookConfig;

@ApplicationScoped
public class GitlabApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GitlabApiClient.class);
    private static final String ENGAGEMENT_DESCRIPTION = "engagement UUID: %s";
    private static final String PROJECT_NAME = "iac";
    private static final String LAUNCHED_MESSAGE = "Launch Ahoy!";
    private static final String SUMMARY_MESSAGE = "Summary Update";
    
    @ConfigProperty(name = "file.engagement")
    String engagementFile;
    
    @ConfigProperty(name = "file.category")
    String categoryFile;
    
    @ConfigProperty(name = "default.branch")
    String branch;
    
    @ConfigProperty(name = "gitlab4j.api.url")
    String gitUrl;
    
    @ConfigProperty(name = "gitlab.personal.access.token")
    String pat;
    
    @ConfigProperty(name = "gitlab.engagements.repository.id")
    Integer engagementRepositoryId;
    
    @ConfigProperty(name = "gitlab.deploy.key")
    Integer deployKey;
    
    @ConfigProperty(name = "environment.label")
    String environment;
    
    @ConfigProperty(name = "gitlab.dir")
    String dataDir;
    
    @ConfigProperty(name = "seed.file.list")
    List<String> seedFileList;
    
    @Inject
    JsonMarshaller json;
    
    @Inject
    ConfigService configService;
    
    @Inject
    CategoryService categoryService;
    
    String engagementPathPrefix;
    
    GitLabApi gitlabApi;
    
    @PostConstruct
    void setupGitlabClient() {
        gitlabApi = new GitLabApi(gitUrl, pat);
        gitlabApi.enableRequestResponseLogging();
        
        Group headGroup;
        try {
            headGroup = gitlabApi.getGroupApi().getGroup(engagementRepositoryId);
        
        
        if(headGroup == null) {
            LOGGER.warn("Could not find the path for repo {}", engagementRepositoryId);
        } else {
            engagementPathPrefix = headGroup.getFullPath();
            LOGGER.info("Engagement repo set to {}", engagementPathPrefix);
        }
        } catch (GitLabApiException e) {
            LOGGER.error("Gitlab api is not working", e);
        }
        
    }
    
    public Optional<Group> getGroup(String groupId) {
        LOGGER.debug("Getting group by id {}", groupId);
        
        try {
            return Optional.ofNullable(gitlabApi.getGroupApi().getGroup(groupId));
        } catch (GitLabApiException e) {
            if(e.getHttpStatus() == 404) {
                LOGGER.debug("No group found for {}", groupId);
                return Optional.empty();
            }
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    
    public Optional<Group> getGroupFromRoot(String groupName) {
        
        String validGroupName = generateValidPath(groupName);

        String sb = engagementPathPrefix + "/" + validGroupName;
        
        return getGroup(sb);
    }
    
    public Optional<Group> getGroup(Engagement engagement) {
        String path = generateValidPath(engagement);
        return getGroup(path);
    }
    
    public List<Group> getSubGroups(String groupId) {
        try {
            return gitlabApi.getGroupApi().getSubGroups(groupId);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    

    public Optional<Project> getProject(int projectId) {
        try {
            return Optional.ofNullable(gitlabApi.getProjectApi().getProject(projectId));
        } catch (GitLabApiException e) {
            if(e.getHttpStatus() == 404) {
                return Optional.empty();
            }
            
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    
    //Optional?
    public Optional<Engagement> getEngagement(Integer projectId) {
        try {
            RepositoryFile file = gitlabApi.getRepositoryFileApi().getFile(projectId, engagementFile, branch);
            return Optional.of(json.fromJson(file.getDecodedContentAsString()));
        } catch (GitLabApiException e) {
            if(e.getHttpStatus() == 404) {
                LOGGER.debug("Could find not file {} for project {}", engagementFile, projectId);
                return Optional.empty();
            }
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason(), "Engagement File Not Retrieved " + projectId);
        }
    }

    public List<Engagement> getEngagements(Set<String> uuids) {
        GroupProjectsFilter filter = new GroupProjectsFilter()
                .withIncludeSubGroups(true);
        List<Engagement> allEngagements = new ArrayList<>();
        try {

            List<Project> allProjects = gitlabApi.getGroupApi().getProjects(engagementRepositoryId, filter);


            allProjects.forEach(p -> {
                if(uuids.isEmpty() || uuids.contains(getUuid(p))) {
                    Optional<Engagement> e = getEngagement(p.getId());
                    e.ifPresent(allEngagements::add);
                }
            });


            LOGGER.debug("projects size {}", allProjects.size());
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }

        return allEngagements;
    }

    private String getUuid(Project project) {
        if(project.getDescription() == null) {
            return "";
        }
        int chum = project.getDescription().lastIndexOf(" ") + 1;
        if(chum < 1) {
            return "";
        }

        return project.getDescription().substring(chum);
    }
    
    public long refreshCategories(List<Engagement> engagements) {
        long categoryCount = 0;
        
        for(Engagement e : engagements) {
            RepositoryFile file;
            try {
                file = gitlabApi.getRepositoryFileApi().getFile(e.getProjectId(), categoryFile, branch);
                List<Category> categories = json.fromJson(file.getDecodedContentAsString(), Category.class);
                if(categories == null) {
                    LOGGER.error("Category null " + e.getUuid());
                }
                categoryService.refresh(categories);
                categoryCount += categories.size();
            } catch (GitLabApiException ex) {
                if(ex.getHttpStatus() != 404) {
                    throw new EngagementGitlabException(ex.getHttpStatus(), ex.getReason(), "Engagement File Not Retrieved " + e.getProjectId());
                }
                LOGGER.debug("Could find not file {} for project {}", categoryFile, e.getProjectId());
                    
            }
        }
        
        return categoryCount;
    }
    
    /**
     * Creates a group at the top-most group
     * @param name the name of the group
     * @return the group
     */
    public Group createGroup(String name) {
        return createGroup(name, engagementRepositoryId);
    }
    
    /**
     * Creates a subgroup below the parentId
     * @param name the name of the new group
     * @param parentId the id of the parent for this group
     * @return the group
     */
    public Group createGroup(String name, int parentId) {

        LOGGER.debug("create {} - {}", name, parentId);
        String groupPath = generateValidPath(name);
        
        LOGGER.debug("Creating group {} ({}), parent {}", name, groupPath, parentId);

        GroupParams params = new GroupParams()
                .withName(name)
                .withPath(groupPath)
                .withParentId(parentId)
                .withVisibility("private");
        
        try {
            return gitlabApi.getGroupApi().createGroup(params);
        } catch (GitLabApiException e) {
            LOGGER.error("",e);
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason()); 
        }
    }
    
    public Group updateGroupName(String name, int groupId) {
        Optional<Group> updated = getGroup(String.valueOf(groupId));

        if(updated.isEmpty()) {
            throw new EngagementException(String.format("Unable to update group name. Could find group %d", groupId));
        }
        String groupPath = generateValidPath(name);
        
        Group toUpdate = updated.get();
        toUpdate.setPath(groupPath);
        toUpdate.setName(name);
        
        try {
            return gitlabApi.getGroupApi().updateGroup(toUpdate);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason()); 
        }
    }
    
    public Group updateGroup(Group group) {
        
        try {
            return gitlabApi.getGroupApi().updateGroup(group);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason()); 
        }
    }
    
    public void deleteGroup(int groupId) {
        String groupIdString = String.valueOf(groupId);
        deleteGroup(groupIdString);
    }
    
    public void deleteGroup(Engagement engagement) {
        String path = generateValidPath(engagement);
        deleteGroup(path);
    }
    
    public void deleteGroup(String groupId) {
        try {
            gitlabApi.getGroupApi().deleteGroup(groupId);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    
    public Project createProject(String uuid, int gitlabGroupId) {
        String description = String.format(ENGAGEMENT_DESCRIPTION, uuid);
        Project newProject = new Project()
                .withName(PROJECT_NAME)
                .withVisibility(Visibility.PRIVATE)
                .withNamespaceId(gitlabGroupId)
                .withDescription(description)
                .withWikiEnabled(false)
                .withWallEnabled(false)
                .withIssuesEnabled(false)
                .withContainerRegistryEnabled(false)
                .withJobsEnabled(false)
                .withLfsEnabled(false)
                .withMergeRequestsEnabled(false)
                .withPackagesEnabled(false);
        
        try {
            return gitlabApi.getProjectApi().createProject(newProject);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }

    public Project transferProject(int projectId, int groupId) {
        try {
            return gitlabApi.getProjectApi().transferProject(projectId, String.valueOf(groupId));
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    
    public void activateDeployKey(Integer projectId) {
        try {
            DeployKey key = gitlabApi.getDeployKeysApi().enableDeployKey(projectId, deployKey);
            gitlabApi.getDeployKeysApi().updateDeployKey(projectId , deployKey, String.format("%s %s",key.getTitle(), environment), true);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason(), "Failed to activate deploy key for project " + projectId);
        }
        
    }
    
    public void createEngagementFiles(Engagement engagement) {
        
        String engagementContent = json.toJson(engagement);
        
        List<CommitAction> commitFiles = new ArrayList<>();
        
        CommitAction action = new CommitAction()
                .withAction(Action.CREATE)
                .withFilePath(engagementFile)
                .withContent(engagementContent);
        
        commitFiles.add(action);

        seedFileList.forEach(f -> commitFiles.add(new CommitAction().withAction(Action.CREATE).withFilePath(dataDir + f).withContent("[]")));
        String commitMessage = String.format("Engagement Created %s %s", getEmoji(), getEmoji());

        CommitPayload payload = new CommitPayload()
                .withBranch(branch)
                .withCommitMessage(commitMessage)
                .withAuthorEmail(engagement.getLastUpdateByEmail())
                .withAuthorName(engagement.getLastUpdateName())
                .withActions(commitFiles);
        
        try {
            Commit commit = gitlabApi.getCommitsApi().createCommit(engagement.getProjectId(), payload);
            LOGGER.debug("Create engagement file successful {}", commit);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }
    
    public void updateCategories(Engagement engagement, List<Category> categories) {
        List<CommitAction> commitActions = new ArrayList<>();
        
        String categoryContent = json.toJson(categories);
        CommitAction action = new CommitAction()
                .withAction(Action.UPDATE)
                .withFilePath(categoryFile)
                .withContent(categoryContent);
        
        commitActions.add(action);
        
        String engagementContent = json.toJson(engagement);
        action = new CommitAction()
                .withAction(Action.UPDATE)
                .withFilePath(engagementFile)
                .withContent(engagementContent);
        
        commitActions.add(action);
        
        String message = String.format("Categories updated %s %s %n %s ",  getEmoji(), getEmoji(), engagement.getLastMessage());
        
        commitUpdate(engagement.getProjectId(), message, commitActions, engagement.getLastUpdateName(), engagement.getLastUpdateByEmail());
    }
    
    public void updateEngagementFile(Engagement engagement) {
        //Transient values set to null
        engagement.setParticipantCount(null);
        engagement.setHostingCount(null);
        engagement.setArtifactCount(null);
        String engagementContent = json.toJson(engagement);
        
        String messagePrefix = engagement.getLastMessage().contains(EngagementService.LAUNCH_MESSAGE) ? LAUNCHED_MESSAGE : SUMMARY_MESSAGE;
        String message = String.format("%s %s %s %n %s", messagePrefix, getEmoji(), getEmoji(), engagement.getLastMessage());

        CommitAction action = new CommitAction()
                .withAction(Action.UPDATE)
                .withFilePath(engagementFile)
                .withContent(engagementContent);
        commitUpdate(engagement.getProjectId(), message, Collections.singletonList(action), engagement.getLastUpdateName(), engagement.getLastUpdateByEmail());
        engagement.setLastMessage(message);
    }
    
    private void commitUpdate(int projectId, String message, List<CommitAction> actions, String authorName, String authorEmail) {
        
        CommitPayload payload = new CommitPayload()
                .withBranch(branch)
                .withCommitMessage(message)
                .withAuthorEmail(authorEmail)
                .withAuthorName(authorName)
                .withActions(actions);
        
        try {
            gitlabApi.getCommitsApi().createCommit(projectId, payload);
            LOGGER.debug("Update engagement file successful {}", projectId);
        } catch (GitLabApiException e) {
            throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
        }
    }

    public void createWebhooks(int projectId, EngagementState state) {
        List<HookConfig> hooks = configService.getHookConfig();

        hooks.stream().filter(h -> h.isEnabledAfterArchive() || EngagementState.PAST != state).forEach(h ->  {
            LOGGER.debug("h --> {}", h);
            ProjectHook hook = new ProjectHook().withPushEvents(true).withPushEventsBranchFilter(h.getPushEventsBranchFilter());
            try {
                gitlabApi.getProjectApi().addHook(projectId, h.getBaseUrl(), hook, true, h.getToken());
            } catch (GitLabApiException e) {
                throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
            }
        });
    }

    public void deleteProjectHooks(int projectId) {
        getProjectHooks(projectId).forEach(hook -> {
            try {
                gitlabApi.getProjectApi().deleteHook(hook);
            } catch (GitLabApiException e) {
                throw new EngagementGitlabException(e.getHttpStatus(), e.getReason());
            }
        });
    }

    public List<ProjectHook> getProjectHooks(int projectId) {
        try {
            return gitlabApi.getProjectApi().getHooks(projectId);
        } catch (GitLabApiException e) {
            LOGGER.error("Exception while getting hooks for project {} --> {}", projectId, e);
        }

        return Collections.emptyList();
    }
    
    public String getEngagementPathPrefix() {
        return this.engagementPathPrefix;
    }

    private String getEmoji() {
        String bear = "\ud83d\udc3b";

        int bearCodePoint = bear.codePointAt(bear.offsetByCodePoints(0, 0));
        int mysteryAnimalCodePoint = bearCodePoint + new SecureRandom().nextInt(144);
        char[] mysteryEmoji = { Character.highSurrogate(mysteryAnimalCodePoint),
                Character.lowSurrogate(mysteryAnimalCodePoint) };

        return String.valueOf(mysteryEmoji);
    }
    
    private String generateValidPath(Engagement engagement) {
        return new StringBuilder(engagementPathPrefix)
                .append("/")
                .append(generateValidPath(engagement.getCustomerName()))
                .append("/")
                .append(generateValidPath(engagement.getName()))
                .toString();
    }
    
    private String generateValidPath(String input) {

        if (null == input || input.trim().length() == 0) {
            throw new IllegalArgumentException("input string cannot be blank.");
        }

        String path = input.trim();
        path = path.toLowerCase();

        // replace whitespace with a '-'
        path = path.replaceAll("\\s", "-");

        // remove any characters other than A-Z, a-z, 0-9, ., -
        // Biggest conflict potential here since bingø and bingΩ would resolve equally
        path = path.replaceAll("[^A-Za-z0-9-\\.]", "");

        // remove leading or trailing hyphens
        path = path.replaceFirst("^-*", "").replaceFirst("-*$", "");

        // remove ending '.', '.git', or '.atom'
        path = path.replaceAll("(\\.|\\.git|\\.atom)$", "");

        LOGGER.debug("input name {}, converted to path {}", input, path);

        return path;

    }

}
