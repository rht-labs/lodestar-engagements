package com.redhat.labs.lodestar.engagements.rest.client;

import java.io.ByteArrayInputStream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestClientResponseMapper implements ResponseExceptionMapper<RuntimeException> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientResponseMapper.class);
    
    @Override
    public RuntimeException toThrowable(Response response) {
        int status = response.getStatus();
        String msg = getBody(response);
        
        LOGGER.error("Rest client response error {} {}", status, msg);

        return new WebApplicationException(msg, status);

    }

    private String getBody(Response response) {

        if (response.hasEntity()) {

            ByteArrayInputStream is = (ByteArrayInputStream) response.getEntity();
            var bytes = new byte[is.available()];
            is.read(bytes, 0, is.available());
            return new String(bytes);

        }

        return null;

    }

}
