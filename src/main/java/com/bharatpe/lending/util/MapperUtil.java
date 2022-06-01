package com.bharatpe.lending.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
public class MapperUtil {

    public ObjectMapper objectMapper;

    @Autowired
    public MapperUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object getObjectFromJsonString(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, Object.class);
        } catch (IOException e) {
            log.error("Error parsing json String : {} , Error : {}", jsonString, e.getMessage());
        }
        return null;
    }

    public String getJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error converting object : {} to Json String", object.toString());
        }
        return "";
    }
}

