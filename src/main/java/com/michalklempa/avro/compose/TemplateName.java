package com.michalklempa.avro.compose;

import com.fasterxml.jackson.databind.JsonNode;
import com.hubspot.jinjava.Jinjava;
import org.apache.avro.Schema;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TemplateName {
    private Jinjava jinjava;
    private String type;
    private SchemaFile.Parsed parsed;
    private String template;
    private String rendered;

    public TemplateName(String type, SchemaFile.Parsed parsed, String template) {
        this.type = type;
        this.parsed = parsed;
        this.template = template;
        this.rendered = null;
    }

    public String render() {
        if (rendered == null) {
            jinjava = new Jinjava();

            Map<String, String> contextSource = new HashMap<>();
            contextSource.put("basename", parsed.name());
            contextSource.put("full", parsed.filename());
            contextSource.put("absolute", new File(parsed.filename()).getAbsolutePath());

            Map<String, Object> context = new HashMap<>();
            context.put("source", contextSource);

            Schema schema = parsed.types().get(type);
            Map<String, Object> allProps = schema.getObjectProps();
            Map<String, String> contextProps = new HashMap<>();
            for (Map.Entry<String, Object> entry : allProps.entrySet()) {
                if (entry.getValue() instanceof String) {
                    contextProps.put(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof JsonNode) {
                    JsonNode value = (JsonNode) entry.getValue();
                    if (value != null && value.isTextual()) {
                        contextProps.put(entry.getKey(), value.textValue());
                    }
                } else {
                    throw new RuntimeException();
                }
            }

            Map<String, Object> contextSchema = new HashMap<>();
            contextSchema.put("name", schema.getName());
            contextSchema.put("fullname", schema.getFullName());
            contextSchema.put("namespace", schema.getNamespace());
            contextSchema.put("doc", schema.getDoc());
            contextSchema.put("props", contextProps);

            context.put("schema", contextSchema);
            rendered = jinjava.render(template, context);
        }
        return rendered;
    }
}
