package com.bharatpe.lending.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

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

    public static JsonNode convertBsonDocumentToJsonNode(Document bsonDocument) {
        try {
            // Convert Document to JSON string first
            String jsonString = bsonDocument.toJson();

            // Parse the JSON string to JsonNode
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(jsonString);

            // Clean up Jackson type metadata and convert BSON types
            return cleanJsonNode(root);
        } catch (Exception e) {
            log.error("Error converting BSON Document to JsonNode: {}", e.getMessage());
            throw new RuntimeException("Failed to convert BSON Document", e);
        }
    }

    private static JsonNode cleanJsonNode(JsonNode node) {
        ObjectMapper objectMapper = new ObjectMapper();
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;

            // Handle Jackson type metadata cleanup
            if (objectNode.has("_value") && objectNode.has("_class")) {
                // Extract the actual value from Jackson's type metadata
                return cleanJsonNode(objectNode.get("_value"));
            }

            if (objectNode.has("_children") && objectNode.has("_class")) {
                // Handle ObjectNode with _children structure
                JsonNode childrenNode = objectNode.get("_children");
                if (childrenNode.isObject()) {
                    return cleanJsonNode(childrenNode);
                }
            }

            // Handle known BSON extended JSON types
            if (objectNode.has("$numberLong")) {
                return handleBsonType(objectNode, "$numberLong", JsonNode::asLong);
            }
            if (objectNode.has("$date")) {
                return handleBsonType(objectNode, "$date", JsonNode::asLong);
            }
            if (objectNode.has("$numberInt")) {
                return handleBsonType(objectNode, "$numberInt", JsonNode::asInt);
            }
            if (objectNode.has("$numberDouble")) {
                return handleBsonType(objectNode, "$numberDouble", JsonNode::asDouble);
            }
            if (objectNode.has("$numberDecimal")) {
                return handleBsonType(objectNode, "$numberDecimal", JsonNode::asDouble);
            }
            if (objectNode.has("$oid")) {
                return handleBsonType(objectNode, "$oid", JsonNode::asText);
            }
            if (objectNode.has("$binary")) {
                return handleBsonType(objectNode, "$binary", JsonNode::asText);
            }
            if (objectNode.has("$timestamp")) {
                return handleBsonType(objectNode, "$timestamp", node1 -> node1.asLong());
            }

            // Generic handler for unknown BSON types starting with $
            JsonNode unknownBsonType = handleUnknownBsonTypes(objectNode);
            if (unknownBsonType != null) {
                return unknownBsonType;
            }

            // Recursively clean all fields
            ObjectNode result = objectMapper.createObjectNode();
            objectNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldValue = entry.getValue();

                // Skip Jackson metadata fields and internal node factory fields
                if (!isJacksonMetadataField(fieldName)) {
                    result.set(fieldName, cleanJsonNode(fieldValue));
                }
            });
            return result;
        } else if (node.isArray()) {

            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(element -> arrayNode.add(cleanJsonNode(element)));
            return arrayNode;
        }

        return node;
    }

    private static JsonNode handleBsonType(ObjectNode objectNode, String typeField, Function<JsonNode, Object> converter) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode typeValue = objectNode.get(typeField);
            Object convertedValue = converter.apply(typeValue);
            return objectMapper.valueToTree(convertedValue);
        } catch (Exception e) {
            log.warn("Failed to convert BSON type {}: {}", typeField, e.getMessage());
            // Return the original value if conversion fails
            return objectNode.get(typeField);
        }
    }

    private static JsonNode handleUnknownBsonTypes(ObjectNode objectNode) {
        // Look for any field starting with $ (BSON extended JSON indicator)
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String fieldName = entry.getKey();

            if (fieldName.startsWith("$") && objectNode.size() == 1) {
                // Single field starting with $ - treat as BSON extended type
                JsonNode value = entry.getValue();
                log.info("Handling unknown BSON type: {} with value: {}", fieldName, value);

                // For unknown types, try to extract the most meaningful value
                if (value.isTextual()) {
                    return value;
                } else if (value.isNumber()) {
                    return value;
                } else if (value.isObject()) {
                    // If the value itself is an object, recursively clean it
                    return cleanJsonNode(value);
                } else {
                    return value;
                }
            }
        }
        return null;
    }

    private static boolean isJacksonMetadataField(String fieldName) {
        // Common Jackson metadata fields to skip
        return fieldName.startsWith("_class") ||
                fieldName.startsWith("_value") ||
                fieldName.startsWith("_children") ||
                fieldName.startsWith("_nodeFactory") ||
                fieldName.startsWith("_cfg") ||
                fieldName.startsWith("_empty") ||
                fieldName.startsWith("_missing");
    }

}

