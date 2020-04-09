package com.michalklempa.avro.compose;

import com.fasterxml.jackson.core.JsonParseException;
import org.apache.avro.SchemaParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Compose {
    private static final String avroNameRegex = "((?:[A-Za-z_][A-Za-z0-9_]*\\.)*(?:[A-Za-z_][A-Za-z0-9_]*))";
    private static final Pattern recordField = Pattern.compile("\"" + avroNameRegex + "\" is not a defined name.*");
    private static final Pattern unionField = Pattern.compile("Undefined name: \"" + avroNameRegex + "\"");
    private static final Pattern arrayField = Pattern.compile("Type not supported: " + avroNameRegex);
    private static final Pattern[] patterns = new Pattern[]{recordField, unionField, arrayField};
    private static Logger logger = LoggerFactory.getLogger(Compose.class);

    private List<String> inputFiles;


    public Compose(List<String> inputFiles) {
        this.inputFiles = inputFiles;
    }

    public static String extractType(String message) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(message);
            if (m.matches()) {
                return m.group(1);
            }
        }
        return null;
    }

    public Map<String, SchemaFile.Parsed> compose() throws IOException, Exception {
        ParsedIndex parsedIndex = new ParsedIndex();
        BlockedRegistry blockedRegistry = new BlockedRegistry();

        Deque<SchemaFile.Attempt> remaining = new LinkedList<>();
        for (String inputFileName : inputFiles) {
            remaining.offerLast(SchemaFile.Factory.attempt(inputFileName));
        }

        do {
            SchemaFile.Attempt attempt = remaining.pollFirst();
            logger.trace("Trying to parse file: {}", attempt.name());
            try {
                SchemaFile.Parsed parsed = SchemaFile.Factory.parsed(attempt);
                logger.debug("Success parsing file: {}, found declared types: {}", parsed.name(), parsed.declarations());
                parsedIndex.add(parsed);
                for (SchemaFile.Attempt unblockedAttempt : blockedRegistry.unblock(parsed)) {
                    logger.trace("Unblocking file and queuing: {}", unblockedAttempt.name());
                    remaining.offerLast(unblockedAttempt);
                }
            } catch (SchemaParseException ex) {
                if (ex.getCause() != null) {
                    if (ex.getCause() instanceof JsonParseException) {
                        throw ex;
                    }
                }
                String requiredType = extractType(ex.getMessage());
                logger.debug("Error parsing file: {}, depends on type: {}", attempt.name(), requiredType);
                if (parsedIndex.contains(requiredType)) {
                    SchemaFile.Attempt repeatAttempt = SchemaFile.Factory.attempt(attempt, parsedIndex.get(requiredType));
                    logger.trace("Type {} seen before, file {} adding dependency {} and queuing.", requiredType, repeatAttempt.name(), parsedIndex.get(requiredType).name());
                    remaining.offerLast(repeatAttempt);
                } else {
                    logger.trace("Blocking file {}.", attempt.name());
                    blockedRegistry.block(requiredType, ex, attempt);
                }
            }
            // until ( nothing remains ) )
        } while (!remaining.isEmpty());

        if (blockedRegistry.isEmpty()) {
            logger.info("Successfully parsed all files");
        } else {
            logger.error("Error parsing files, remaining files with errors:");
            for (SchemaFile.Blocked blocked : blockedRegistry.all()) {
                logger.error("File {} is requiring type: {}, parsing ended with exception.", blocked.name(), blocked.requiredType(), blocked.exception());
            }
            throw new Exception("Error parsing files.");
        }

        return parsedIndex.all();
    }

    public static class ParsedIndex {
        private Map<String, SchemaFile.Parsed> types = new HashMap<>();

        public void add(SchemaFile.Parsed parsed) {
            for (String type : parsed.declarations()) {
                types.put(type, parsed);
            }
        }

        public boolean contains(String type) {
            return types.keySet().contains(type);
        }

        public SchemaFile.Parsed get(String type) {
            return types.get(type);
        }

        public Map<String, SchemaFile.Parsed> all() {
            return types;
        }
    }

    public static class BlockedRegistry {
        private Map<String, List<SchemaFile.Blocked>> typeToBlocked = new HashMap<>();

        public void block(final String requiredType, final Exception ex, final SchemaFile.Attempt attempt) {
            if (!typeToBlocked.containsKey(requiredType)) {
                typeToBlocked.put(requiredType, new LinkedList<>());
            }
            typeToBlocked.get(requiredType).add(SchemaFile.Factory.blocked(attempt, ex, requiredType));
        }

        public List<SchemaFile.Attempt> unblock(final SchemaFile.Parsed schemaFile) {
            List<SchemaFile.Attempt> unblocked = new LinkedList<>();
            for (String type : schemaFile.declarations()) {
                if (typeToBlocked.containsKey(type)) {
                    for (SchemaFile.Blocked blocked : typeToBlocked.get(type)) {
                        unblocked.add(SchemaFile.Factory.attempt(blocked, schemaFile));
                    }
                    typeToBlocked.get(type).clear();
                    typeToBlocked.remove(type);
                }
            }
            return unblocked;
        }

        public boolean isEmpty() {
            return typeToBlocked.isEmpty();
        }

        public List<SchemaFile.Blocked> all() {
            List<SchemaFile.Blocked> all = new LinkedList<>();
            for (List<SchemaFile.Blocked> perType : typeToBlocked.values()) {
                all.addAll(perType);
            }
            return all;
        }
    }
}
