package com.redhat.labs.lodestar.engagements.service;

import java.time.Instant;
import java.util.*;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.redhat.labs.lodestar.engagements.utils.*;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.javers.core.diff.ListCompareAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.redhat.labs.lodestar.engagements.model.Category;
import com.redhat.labs.lodestar.engagements.model.Counter;
import com.redhat.labs.lodestar.engagements.model.Engagement;
import com.redhat.labs.lodestar.engagements.repository.CategoryRepository;

import io.vertx.mutiny.core.eventbus.EventBus;

@ApplicationScoped
public class CategoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CategoryService.class);
    
    public static final String REFRESH_CATEGORIES = "refresh.category_event";
    public static final String MERGE_CATEGORIES = "merge.category.event";
    
    @Inject
    CategoryRepository categoryRepository;

    @Inject
    EngagementService engagementService;
    
    static final Javers javers = JaversBuilder.javers().withListCompareAlgorithm(ListCompareAlgorithm.LEVENSHTEIN_DISTANCE).build();
    
    @Inject
    EventBus bus;
    
    public List<Category> getCategories(String engagementUuid) {
        List<Category> categories = categoryRepository.getCategories(engagementUuid);
        LOGGER.debug("Categories for uuid {} - {}", engagementUuid, categories);
        return categoryRepository.getCategories(engagementUuid);
    }
    
    public List<Category> getCategories(int page, int pageSize) {
        return categoryRepository.getCategories(page, pageSize);
    }

    public Set<String> suggestCategory(String partial) {
        return categoryRepository.getSuggestions(partial);
    }
    
    public long count() {
        return categoryRepository.count();
    }

    public long countForEngagementUuid(String engagementUuid) {
        return categoryRepository.countCategories(engagementUuid);
    }
    
    public List<Counter> getCategoryCounts(List<String> regions, PageFilter paging) {
        return categoryRepository.getCategoryCounts(regions, paging);
    }
    
    public void updateCategories(Engagement engagement, Set<String> categories) {
        LOGGER.debug("updating categories {}", categories);
        
        Diff diff = javers.compareCollections(engagement.getCategories(), categories, String.class);
        engagement.setLastMessage(diff.prettyPrint());

        if(!diff.hasChanges()) {
            LOGGER.debug("Category update called without changes. Abort");
            return;
        }

        engagement.setCategories(categories);
        engagementService.update(engagement, false, true);

        Set<String> categoryCopy = new TreeSet<>(categories);

        List<Category> current = categoryRepository.getCategories(engagement.getUuid());

        current.forEach(persisted -> {
            if (categoryCopy.contains(persisted.getName())) {
                LOGGER.debug("already in db do nothing {}", persisted);
                categoryCopy.remove(persisted.getName()); // Already in db. Nothing to do
            } else {
                LOGGER.debug("delete category {}", persisted);
                categoryRepository.delete(persisted); // Not in the new list so delete
            }
        });

        Instant now = Instant.now();
        categoryCopy.forEach(newCat -> {
            LOGGER.debug("new category {}", newCat);
            Category cat = Category.builder().engagementUuid(engagement.getUuid()).name(newCat).region(engagement.getRegion())
                    .uuid(UUID.randomUUID().toString()).created(now).build();
            categoryRepository.persist(cat); // Not in db. Add new
        });

        bus.publish(MERGE_CATEGORIES, engagement);
    }
    
    public long purge() {
        return categoryRepository.deleteAll();
    }
    
    public void refresh(List<Category> category) {
        categoryRepository.persist(category);
    }
    
}
