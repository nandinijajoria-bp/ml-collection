package com.bharatpe.lending.lendingplatform.lms.exception;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.web.client.HttpServerErrorException;

import java.io.IOException;

public class HttpServerErrorExceptionSerializer extends JsonSerializer<HttpServerErrorException> {

    @Override
    public void serialize(HttpServerErrorException value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField("statusCode", String.valueOf(value.getStatusCode()));
        gen.writeStringField("responseBody", value.getResponseBodyAsString());
        gen.writeStringField("message", value.getMessage());
        gen.writeEndObject();
    }
}
