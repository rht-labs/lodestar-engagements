package com.redhat.labs.lodestar.engagements.repository;

import java.util.Map;

public class Query {

    String value;
    Map<String, Object> parameters;

    Query(String value, Map<String, Object> parameters) {
        this.value = value;
        this.parameters = parameters;
    }
}
