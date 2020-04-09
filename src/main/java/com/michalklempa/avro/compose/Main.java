package com.michalklempa.avro.compose;

import ch.qos.logback.classic.Level;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    private static final ArgumentParser ARGUMENT_PARSER = ArgumentParsers.newFor("java -jar avro-compose.jar").build()
            .description("Avro Compose\n" +
                    "Utility to compose large Avro schemas from set of smaller 'classes' which can be re-used as types.\n" +
                    "\n" +
                    "Example:\n" +
                    "java -jar target/avro-compose-0.0.1-SNAPSHOT.jar \\\n" +
                    "\t --log.level DEBUG\\\n" +
                    "\t --output.maven.pom pom_generated.xml \\\n" +
                    "\t --output.maven.template.file pom_template.xml \\\n" +
                    "\t --output.schemas.format pretty \\\n" +
                    "\t --output.schemas.directory src/main/resources/generated/ \\\n" +
                    "\t --output.schemas.filename.template=\"{{ schema.namespace | replace('.', '/') }}/{{ schema.name }}{{ schema.props.outputFileSuffix }}.avsc\" \\\n" +
                    "\t src/main/resources/avro");

    private static Logger logger = LoggerFactory.getLogger(Main.class);

    static {
        ARGUMENT_PARSER.addArgument("input")
                .type(String.class)
                .nargs("+")
                .metavar("<path>")
                .setDefault(".")
                .help("List of schema files and/or directories with Avro schemas, files can be name *.avsc, *.json, *.avro.json, *.schema Default: Current directory.");
        ARGUMENT_PARSER.addArgument("--output.maven.template.file")
                .type(String.class)
                .metavar("<jinja2 xml template>")
                .required(false)
                .help("Jinja2 Template for Maven output avro-maven-plugin chunk or whole pom.xml with correct ordering of <import> statements");
        ARGUMENT_PARSER.addArgument("--output.maven.pom")
                .type(String.class)
                .metavar("<path>")
                .help("Whether to output Maven chunk (or whole pom.xml) for avro-maven-plugin configuration into file specified by this option.");
        ARGUMENT_PARSER.addArgument("--output.schemas.directory", "-o")
                .type(String.class)
                .metavar("<path>")
                .help("Target directory where all Avro type found will be outputted. If nothing provied, no schemas are outputted.");
        ARGUMENT_PARSER.addArgument("--output.schemas.filename.template")
                .type(String.class)
                .metavar("<template>")
                .setDefault("{{schema.fullname}}.avsc")
                .help("Jinja2 Template for output filenames. \n" +
                        "Examples:\n" +
                        "1. all files in single directory, dot notation (default):\n" +
                        "\t{{ schema.fullname }}.avsc\n" +
                        "2. put every file into sub-directory by namespace:\n" +
                        "\t{{ schema.namespace | replace('.', '/') }}/{{ schema.name }}.avsc\n" +
                        "3. having custom property at schema JSON, for example\n" +
                        "\t{\n" +
                        "\t\t\"type\": \"record\",\n" +
                        "\t\t\"name\": \"SomeType\",\n" +
                        "\t\t\"outputFile\": \"SomeTypeComposed.avsc\"\n" +
                        "\t\t...\n" +
                        "\t}\n" +
                        "\tWe can specify to use this custom property in template for output filename:\n" +
                        "\t{{ schema.props.outputFile }}\n" +
                        "\n\n" +
                        "Variables available:\n" +
                        "{{ source.basename }}:\t\t basename of input file, where the type was found\n" +
                        "{{ source.full }}:\t\t full name (including subdirectories starting at working directory) of input file, where the type was found\n" +
                        "{{ source.absolute }}:\t\t absolute path on local filesystem\n" +
                        "{{ schema.name }}:\t\t short schema type name, e.g. Employee\n" +
                        "{{ schema.fullname }}:\t\t short schema type name, e.g. com.michalklempa.avro.Employee\n" +
                        "{{ schema.namespace }}:\t\t namespace part of name, if any. e.g. com.michalklempa.avro\n" +
                        "{{ schema.doc }}:\t\t 'doc' part of root schema type. If you want to do some magic around encoding file name in doc field.\n" +
                        "{{ schema.props.<property> }}:\t\t any property you add into root schema, which is not recognized by Avro, is preserved, and you can use it to define output filename.\n");
        ARGUMENT_PARSER.addArgument("--output.schemas.format")
                .type(String.class)
                .choices("pretty", "oneline", "canonical")
                .setDefault("pretty")
                .help("Default: pretty. If oneline/canonical is used, output files are appended, so you can use appropriate output.filename.template to get multiple schemas in one file.\n" +
                        "Possible Values:\n" +
                        "\tpretty:\t pretty print Avro schemas\n" +
                        "\toneline:\t each schema is one line JSON\n" +
                        "\tcanonical:\t Parsing Canonical Form (one-line) see http://avro.apache.org/docs/current/spec.html#Parsing+Canonical+Form+for+Schemas\n");
        ARGUMENT_PARSER.addArgument("--log.level")
                .type(String.class)
                .choices("TRACE", "DEBUG", "INFO", "WARN", "ERROR")
                .setDefault("WARN")
                .help("Default: WARN. Log level: \"TRACE\", \"DEBUG\", \"INFO\", \"WARN\", \"ERROR\"");
    }

    public static void main(String[] args) throws Exception {
        Namespace res;
        try {
            res = ARGUMENT_PARSER.parseArgs(args);

            final Level level = Level.toLevel(res.getString("log.level"));
            ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(level);

            if (logger.isDebugEnabled()) {
                List<Map.Entry<String, Object>> list = new ArrayList<>();
                for (Map.Entry<String, Object> entry : res.getAttrs().entrySet()) {
                    list.add(entry);
                }
                list.sort((o1, o2) -> o1.getKey().compareTo(o2.getKey()));
                for (Map.Entry<String, Object> entry : list) {
                    logger.debug("Command line argument {}: {}", entry.getKey(), entry.getValue());
                }
            }
        } catch (ArgumentParserException ex) {
            ARGUMENT_PARSER.handleError(ex);
            throw ex;
        }

        List<String> inputFiles = new ArrayList<>();
        List<String> inputs = res.getList("input");
        for (String input : inputs) {
            if (new File(input).isFile()) {
                logger.trace("Specified File to parse: {}", input);
                inputFiles.add(input);
            } else if (new File(input).isDirectory()) {
                logger.trace("Specified Directory to search for files [*.avsc,*.json,*.schema]: {}", input);
                for (File file : FileUtils.listFiles(new File(input), new String[]{"avsc", "json", "schema"}, true)) {
                    logger.trace("Searching directory: {}. Found file to parse: {}", input, file.getPath());
                    inputFiles.add(file.getPath());
                }
            }
        }
        inputs.sort(new AlphanumComparator());

        Map<String, SchemaFile.Parsed> schemas = new Compose(inputFiles).compose();

        final String outputDirectory = res.getString("output.schemas.directory");
        if (outputDirectory != null) {
            final String template = res.getString("output.schemas.filename.template");
            new Schemas(schemas, outputDirectory, res.getString("output.schemas.format"), template).output();
        }

        final String mavenPom = res.getString("output.maven.pom");
        if (mavenPom != null) {
            String mavenTemplate;
            if (res.getString("output.maven.template.file") != null) {
                mavenTemplate = IOUtils.toString(new File(res.getString("output.maven.template.file")).toURI(), "utf-8");
            } else {
                try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("mavenTemplate.jinja2.xml")) {
                    mavenTemplate = IOUtils.toString(is, "utf-8");
                }
            }
            if ("-".equals(mavenPom)) {
                new Maven(schemas, mavenTemplate).output(System.out);
            } else {
                try (OutputStream os = new FileOutputStream(mavenPom)) {
                    new Maven(schemas, mavenTemplate).output(os);
                }
            }
        }
    }

    /*
     * The Alphanum Algorithm is an improved sorting algorithm for strings
     * containing numbers.  Instead of sorting numbers in ASCII order like
     * a standard sort, this algorithm sorts numbers in numeric order.
     *
     * The Alphanum Algorithm is discussed at http://www.DaveKoelle.com
     *
     * Released under the MIT License - https://opensource.org/licenses/MIT
     *
     * Copyright 2007-2017 David Koelle
     *
     * Permission is hereby granted, free of charge, to any person obtaining
     * a copy of this software and associated documentation files (the "Software"),
     * to deal in the Software without restriction, including without limitation
     * the rights to use, copy, modify, merge, publish, distribute, sublicense,
     * and/or sell copies of the Software, and to permit persons to whom the
     * Software is furnished to do so, subject to the following conditions:
     *
     * The above copyright notice and this permission notice shall be included
     * in all copies or substantial portions of the Software.
     *
     * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
     * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
     * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
     * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
     * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
     * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
     * USE OR OTHER DEALINGS IN THE SOFTWARE.
     */

    /**
     * This is an updated version with enhancements made by Daniel Migowski,
     * Andre Bogus, and David Koelle. Updated by David Koelle in 2017.
     * <p>
     * To use this class:
     * Use the static "sort" method from the java.util.Collections class:
     * Collections.sort(your list, new AlphanumComparator());
     */
    public static class AlphanumComparator implements Comparator<String> {
        /**
         * Shows an example of how the comparator works.
         * Feel free to delete this in your own code!
         */
        public static void main(String[] args) {
            List<String> values = Arrays.asList("dazzle2", "dazzle10", "dazzle1", "dazzle2.7", "dazzle2.10", "2", "10", "1", "EctoMorph6", "EctoMorph62", "EctoMorph7");
            System.out.println(values.stream().sorted(new AlphanumComparator()).collect(Collectors.joining(" ")));
        }

        private final boolean isDigit(char ch) {
            return ((ch >= 48) && (ch <= 57));
        }

        /**
         * Length of string is passed in for improved efficiency (only need to calculate it once)
         **/
        private final String getChunk(String s, int slength, int marker) {
            StringBuilder chunk = new StringBuilder();
            char c = s.charAt(marker);
            chunk.append(c);
            marker++;
            if (isDigit(c)) {
                while (marker < slength) {
                    c = s.charAt(marker);
                    if (!isDigit(c)) {
                        break;
                    }
                    chunk.append(c);
                    marker++;
                }
            } else {
                while (marker < slength) {
                    c = s.charAt(marker);
                    if (isDigit(c)) {
                        break;
                    }
                    chunk.append(c);
                    marker++;
                }
            }
            return chunk.toString();
        }

        public int compare(String s1, String s2) {
            if ((s1 == null) || (s2 == null)) {
                return 0;
            }

            int thisMarker = 0;
            int thatMarker = 0;
            int s1Length = s1.length();
            int s2Length = s2.length();

            while (thisMarker < s1Length && thatMarker < s2Length) {
                String thisChunk = getChunk(s1, s1Length, thisMarker);
                thisMarker += thisChunk.length();

                String thatChunk = getChunk(s2, s2Length, thatMarker);
                thatMarker += thatChunk.length();

                // If both chunks contain numeric characters, sort them numerically
                int result = 0;
                if (isDigit(thisChunk.charAt(0)) && isDigit(thatChunk.charAt(0))) {
                    // Simple chunk comparison by length.
                    int thisChunkLength = thisChunk.length();
                    result = thisChunkLength - thatChunk.length();
                    // If equal, the first different number counts
                    if (result == 0) {
                        for (int i = 0; i < thisChunkLength; i++) {
                            result = thisChunk.charAt(i) - thatChunk.charAt(i);
                            if (result != 0) {
                                return result;
                            }
                        }
                    }
                } else {
                    result = thisChunk.compareTo(thatChunk);
                }

                if (result != 0) {
                    return result;
                }
            }

            return s1Length - s2Length;
        }
    }
}
