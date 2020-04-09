package com.michalklempa.avro.compose;

import org.apache.avro.Schema;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public interface SchemaFile {
    String name();

    String filename();

    interface Attempt extends SchemaFile {
        Set<SchemaFile.Parsed> dependencies();
    }

    interface Parsed extends Attempt {
        Set<String> declarations();

        Map<String, Schema> types();
    }

    interface Blocked extends Attempt {
        Exception exception();

        String requiredType();
    }

    abstract class AbstractSchemaFile implements SchemaFile {
        protected final String filename;

        public AbstractSchemaFile(final String filename) {
            this.filename = filename;
        }

        public String name() {
            return new File(filename).getName();
        }

        public String filename() {
            return filename;
        }

        @Override
        public String toString() {
            return new ReflectionToStringBuilder(this, ToStringStyle.JSON_STYLE).toString();
        }

    }

    class AttemptSchemaFile extends AbstractSchemaFile implements Attempt {
        protected final Set<SchemaFile.Parsed> dependencies;

        public AttemptSchemaFile(final String filename) {
            this(filename, Collections.emptySet());
        }

        public AttemptSchemaFile(final String filename, final Set<SchemaFile.Parsed> dependencies) {
            super(filename);
            this.dependencies = Collections.unmodifiableSet(dependencies);
        }

        public Set<SchemaFile.Parsed> dependencies() {
            return dependencies;
        }
    }

    class BlockedSchemaFile extends AttemptSchemaFile implements Blocked {
        protected final Exception exception;
        protected final String requiredType;

        public BlockedSchemaFile(final String filename, final Set<SchemaFile.Parsed> dependencies, final Exception exception, final String requiredType) {
            super(filename, dependencies);
            this.exception = exception;
            this.requiredType = requiredType;
        }

        public Exception exception() {
            return exception;
        }

        @Override
        public String requiredType() {
            return requiredType;
        }
    }

    class ParsedSchemaFile extends AttemptSchemaFile implements SchemaFile.Parsed {
        private final Map<String, Schema> types;
        private Set<String> declarations;

        public ParsedSchemaFile(SchemaFile.Attempt attempt, Map<String, Schema> types, Set<String> declarations) {
            this(attempt.filename(), attempt.dependencies(), types, declarations);
        }

        public ParsedSchemaFile(final String filename, final Set<SchemaFile.Parsed> dependencies, Map<String, Schema> types, Set<String> declarations) {
            super(filename, dependencies);
            this.types = Collections.unmodifiableMap(types);
            this.declarations = Collections.unmodifiableSet(declarations);
        }

        public Set<String> declarations() {
            return declarations;
        }

        public Map<String, Schema> types() {
            return types;
        }
    }

    class Factory {

        public static Attempt attempt(String filename) {
            return new AttemptSchemaFile(filename);
        }

        public static Attempt attempt(Attempt attempt, SchemaFile.Parsed dependency) {
            Set<SchemaFile.Parsed> dependencies = new HashSet<>();
            dependencies.addAll(attempt.dependencies());
            dependencies.add(dependency);
            return new AttemptSchemaFile(attempt.filename(), dependencies);
        }

        public static Parsed parsed(Attempt schemaFile) throws IOException {
            Schema.Parser parser = new Schema.Parser();
            for (SchemaFile.Parsed dependency : schemaFile.dependencies()) {
                parser.addTypes(dependency.types());
            }
            try (InputStream is = new FileInputStream(schemaFile.filename())) {
                Set<String> typeNames = new HashSet<>();
                typeNames.addAll(parser.getTypes().keySet());

                parser.parse(is);

                Set<String> declarations = new HashSet<>();
                declarations.addAll(parser.getTypes().keySet());
                declarations.removeAll(typeNames);

                return new ParsedSchemaFile(schemaFile, parser.getTypes(), declarations);
            }
        }

        public static Blocked blocked(final SchemaFile.Attempt attempt, final Exception ex, final String requiredType) {
            return new BlockedSchemaFile(attempt.filename(), attempt.dependencies(), ex, requiredType);
        }
    }

}
