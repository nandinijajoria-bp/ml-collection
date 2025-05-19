package com.bharatpe.lending.lendingplatform.lms.util;

import com.bharatpe.lending.lendingplatform.lms.exception.HttpServerErrorExceptionSerializer;
import com.bharatpe.lending.lendingplatform.lms.exception.ObjectConversionException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;

@Slf4j
@Component
public class LmsMapperUtil {

    private final ObjectMapper objectMapper;

    @Autowired
    public LmsMapperUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectMapper.configure(SerializationFeature.FAIL_ON_SELF_REFERENCES, false);
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
//        this.objectMapper.enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS.mappedFeature());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.objectMapper.setSerializationInclusion(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(HttpServerErrorException.class, new HttpServerErrorExceptionSerializer());
        objectMapper.registerModule(module);
    }

    public <T> T readValueAsDefinedClass(String string, Class<T> classs) {
        try {
            return objectMapper.readValue(string, classs);
        } catch (JsonProcessingException e) {
            log.error("[DSA] :: Error converting string object : {} to class:{}", string, classs, e);
            throw new ObjectConversionException(e.getMessage());
        } catch (IOException e) {
	        throw new RuntimeException(e);
        }
    }

    public String getJsonString(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error converting object : {} to Json String", object.toString());
            throw new ObjectConversionException(e.getMessage());
        }
    }

    public <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return objectMapper.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            log.error("[DSA] :: Error converting object : {} to class:{}", fromValue, toValueType, e);
            throw new ObjectConversionException(e.getMessage());
        }
    }

    public <T> T convertValue(String fromValue, TypeReference<T> toValueType) {
        try {
            return objectMapper.readValue(fromValue, toValueType);
        } catch (IllegalArgumentException | IOException e) {
            log.error("[DSA] :: Error converting object : {} to class:{}", fromValue, toValueType, e);
            throw new ObjectConversionException(e.getMessage());
        }
    }

    public <T> T convertValueFromObject(Object fromValue, TypeReference<T> toValueType) {
        try {
            return objectMapper.convertValue(fromValue, toValueType);
        } catch (IllegalArgumentException e) {
            String message =
                    String.format("Error converting value %s from object to class: %s", fromValue, toValueType);
            log.error(message, e);
            throw new ObjectConversionException(message);
        }
    }

    public <T> T convertJsonToObject(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            log.error("Error converting JSON string to class: {}", clazz, e);
            throw new ObjectConversionException(e.getMessage());
        }
    }

    public <T1, T2> T2 parse(T1 object, TypeReference<T2> targetType) {
        T2 result;
        try {
            String jsonString;

            if (object.getClass() != String.class) {
                jsonString = this.objectMapper.writeValueAsString(object);
            } else {
                jsonString = (String) object;
            }

            result = this.objectMapper.readValue(jsonString, targetType);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }
}
