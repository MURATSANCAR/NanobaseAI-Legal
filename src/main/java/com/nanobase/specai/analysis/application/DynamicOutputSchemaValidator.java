package com.nanobase.specai.analysis.application;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface DynamicOutputSchemaValidator {
    List<String> validate(JsonNode schema, JsonNode value);
}
