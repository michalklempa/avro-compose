package com.michalklempa.avro.compose;

import org.apache.avro.SchemaNormalization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Map;

public class Schemas {
    private static Logger logger = LoggerFactory.getLogger(Schemas.class);

    private Map<String, SchemaFile.Parsed> schemas;
    private String outputDirectory;
    private String outputFormat;
    private String template;

    public Schemas(final Map<String, SchemaFile.Parsed> schemas, final String outputDirectory, final String outputFormat, final String template) {
        this.schemas = schemas;
        this.outputDirectory = outputDirectory;
        this.outputFormat = outputFormat;
        this.template = template;
    }

    public void output() throws IOException {
        for (Map.Entry<String, SchemaFile.Parsed> entry : schemas.entrySet()) {
            logger.debug("Type: {} from file: {}", entry.getKey(), entry.getValue().name());
            String type = entry.getKey();
            SchemaFile.Parsed parsed = entry.getValue();

            String outputFilename = new TemplateName(type, parsed, template).render();
            logger.trace("Using output filename template: {} renders into output filename: {}", template, outputFilename);

            File outputFile = new File(outputDirectory, outputFilename);
            outputFile.getParentFile().mkdirs();
            try (PrintStream os = new PrintStream(new FileOutputStream(outputFile, !"pretty".equals(outputFormat)))) {
                if ("pretty".equals(outputFormat)) {
                    os.print(parsed.types().get(type).toString(true));
                } else if ("oneline".equals(outputFormat)) {
                    os.print(parsed.types().get(type).toString(false));
                } else {// canonical
                    os.print(SchemaNormalization.toParsingForm(parsed.types().get(type)));
                }
            }
        }
    }
}
