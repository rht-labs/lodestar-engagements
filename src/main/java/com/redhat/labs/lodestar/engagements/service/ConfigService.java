package com.redhat.labs.lodestar.engagements.service;

import com.redhat.labs.lodestar.engagements.model.HookConfig;
import com.redhat.labs.lodestar.engagements.rest.client.ConfigApiClient;

import io.vertx.mutiny.core.eventbus.EventBus;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.ListCompareAlgorithm;
import org.javers.core.metamodel.clazz.EntityDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class ConfigService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigService.class);

    public static final String UPDATE_ALL_WEBHOOKS = "update.all.webhooks.event";

    @Inject
    @RestClient
    ConfigApiClient configApiClient;

    @Inject
    EventBus bus;

    Javers javers;

    @PostConstruct
    public void setupJavers() {
        List<String> webhookIgnoredProps = Collections.singletonList("id");

        javers = JaversBuilder.javers().withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE)
                .registerEntity(new EntityDefinition(HookConfig.class, "baseUrl", webhookIgnoredProps)).build();
    }

    /**
     * Gets called via notification from the config service.
     * If the config service gets bounced it could send data we already have
     * so don't assume all hooks need updating. Check first
     * @param webhooks a new set of webhooks
     */
    public void updateAllWebhooks(List<HookConfig> webhooks) {
        List<HookConfig> existing = HookConfig.listAll();
        Diff diff = javers.compareCollections(existing, webhooks, HookConfig.class);

        if(diff.hasChanges()) { //There were real changes. Just delete all and persist all.
            LOGGER.debug("webhook changes {}", diff);
            HookConfig.deleteAll();
            HookConfig.persist(webhooks);

            bus.publish(UPDATE_ALL_WEBHOOKS, UPDATE_ALL_WEBHOOKS);
        }
    }

    public List<HookConfig> getHookConfig() {
        List<HookConfig> webhooks = HookConfig.listAll();

        if(webhooks.isEmpty()) { //This scenario should be an outlier
            LOGGER.debug("getting webhooks from config service");
            webhooks = configApiClient.getWebhooks();
            HookConfig.persist(webhooks);
        }

        return webhooks;
    }

    public String getRuntimeConfig(String engagementType) {
        return configApiClient.getRuntimeConfig(engagementType);
    }
}
