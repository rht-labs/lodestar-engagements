
package com.redhat.labs.lodestar.engagements.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.labs.lodestar.engagements.exception.EngagementException;
import com.redhat.labs.lodestar.engagements.model.Engagement;

/**
 * Used converting String to Objects (non-request, non-response)
 * 
 * @author mcanoy
 *
 */
@ApplicationScoped
public class JsonMarshaller {
    public static final Logger LOGGER = LoggerFactory.getLogger(JsonMarshaller.class);
    
    ObjectMapper om;
    
    public JsonMarshaller() {
        om = new ObjectMapper();
        om = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
        om.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        LOGGER.debug("marshaller started");
    }
    
    public <T> List<T> fromJson(String jsonContent, Class<T> clazz) {
        JavaType type = om.getTypeFactory().constructCollectionType(List.class, clazz);
        
        try {
            return om.readValue(jsonContent, type);
        } catch (IOException e) {
            LOGGER.error(String.format("Found but unable to map file %s", jsonContent), e);
        }
        
        return new ArrayList<>();
    }

    public Engagement fromJson(String json) {
        return fromJson(json, new TypeReference<Engagement>() {});
    }
    
    public <T> T fromJson(String json, TypeReference<T> reference) {
        try {
            return om.readValue(json, reference);
        } catch (JsonProcessingException e) {
            throw new EngagementException("Error translating engagement json data", e);
        }
    }

    public String toJson(Object engagement) {
        try {
            return om.writeValueAsString(engagement);
        } catch (JsonProcessingException e) {
            throw new EngagementException("Error translating engagement data to json", e);
        }
    }

}
