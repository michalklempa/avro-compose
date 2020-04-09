package com.michalklempa.avro.compose;

import com.hubspot.jinjava.Jinjava;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Maven {
    private Map<String, SchemaFile.Parsed> schemas;
    private String mavenTemplate;

    public Maven(final Map<String, SchemaFile.Parsed> schemas, final String mavenTemplate) {
        this.schemas = schemas;
        this.mavenTemplate = mavenTemplate;
    }

    public void output(OutputStream os) throws IOException {
        Jinjava jinjava = new Jinjava();

        Map<String, Object> context = new HashMap<>();
        List<String> imports = new ArrayList<>();
        Set<SchemaFile.Parsed> outputted = new HashSet<>();
        for (SchemaFile.Parsed parsed : schemas.values()) {
            append(outputted, imports, parsed);
        }
        context.put("imports", imports);

        IOUtils.write(jinjava.render(mavenTemplate, context), os, "utf-8");
    }

    private void append(Set<SchemaFile.Parsed> outputted, List<String> imports, SchemaFile.Parsed parsed) {
        if (outputted.contains(parsed)) {
            return;
        }
        for (SchemaFile.Parsed dependency : parsed.dependencies()) {
            append(outputted, imports, dependency);
        }
        imports.add(parsed.filename());
        outputted.add(parsed);
    }
}
