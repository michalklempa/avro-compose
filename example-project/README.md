<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Avro Compose - Example Maven Project](#avro-compose---example-maven-project)
  - [Requirements](#requirements)
  - [Download](#download)
    - [Maven setup](#maven-setup)
    - [Usage with Maven](#usage-with-maven)
  - [Feeding the imports for avro-maven-plugin](#feeding-the-imports-for-avro-maven-plugin)
  - [References](#references)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Avro Compose - Example Maven Project
This example assumes you are familiar with entity structure of examples and basic command-line usage of avro-compose.jar.
If you have not read the [simple example README](../example-simple/README.md), we recommend reading it before proceeding.

## Requirements
- Java 8 JDK, various options: [Zulu](https://www.azul.com/downloads/zulu-community), or [OpenJDK](https://openjdk.java.net/)
- [Maven](https://maven.apache.org/)

## Download
### Maven setup
You may incorporate the artifact into your Java project as a dependency and then run the schema generation as a part of project build:
```
        <dependency>
            <groupId>com.michalklempa</groupId>
            <artifactId>avro-compose</artifactId>
            <version>0.0.1</version>
        </dependency>
```

### Usage with Maven
Configure the build phase execution.
```
<build>
    <plugins>
         <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <executions>
                <execution>
                    <id>compose-avro</id>
                    <phase>prepare-package</phase>
                    <goals>
                        <goal>java</goal>
                    </goals>
                    <configuration>
                        <mainClass>com.michalklempa.avro.compose.Main</mainClass>
                        <arguments>
                            <argument>--log.level</argument>
                            <argument>INFO</argument>
                            <argument>--output.schemas.directory</argument>
                            <argument>${project.basedir}/src/main/resources/generated</argument>
                            <argument>--output.schemas.format</argument>
                            <argument>pretty</argument>
                            <argument>--output.schemas.filename.template</argument>
                            <argument>{{ schema.namespace | replace('.', '/') }}/{{ schema.name }}{{ schema.props.outputFileSuffix }}.avsc</argument>
                            <argument>${project.basedir}/src/main/resources/avro</argument>
                        </arguments>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
Full example `pom.xml` can be found in this project.

## Feeding the imports for avro-maven-plugin
When using the [avro-maven-plugin](https://avro.apache.org/docs/current/gettingstartedjava.html#download_install) it is important
specify the imports in correct order for the plugin.

Setting up a basic avro-maven-plugin is covered in [1](#references), imports are specifically discussed in [2](#references).
Ensuring correct imports ordering can become tedious with growing number of schema files.
Avro-compose tool is here to help. From command-line, we can output the default avro-maven-plugin build phase part of pom.xml with imports in correct order:
```bash
java -jar avro-compose-0.0.1.jar --output.maven.pom -  ./src/main/resources/avro/
```
Should output:
```
...

                        <imports>
                                <import>./src/main/resources/avro/common/Address.avsc</import>
                                <import>./src/main/resources/avro/common/GPS.avsc</import>
                                <import>./src/main/resources/avro/common/Location.avsc</import>
                                <import>./src/main/resources/avro/common/PersonalInformation.avsc</import>
                                <import>./src/main/resources/avro/common/Department.avsc</import>
                                <import>./src/main/resources/avro/keys/EmployeeKey.avsc</import>
                                <import>./src/main/resources/avro/values/EmployeeValue.avsc</import>
                        </imports>

...
```
This is correct ordering.

But whats more, you can render the whole `pom.xml`, just like it is done in this example project.
There  is [pom_template.xml](./pom_template.xml), which has the import template part:
```
                            <imports>
                                {%- for file in imports %}
                                <import>{{ file }}</import>
                                {%- endfor %}
                            </imports>
```

This template is transformed into `pom.xml` by running the avro-compose during the build (snippet from actual pom.xml):
```
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <executions>
                    <execution>
                        <id>compose-avro</id>
                        <phase>prepare-package</phase>
                        <goals>
                            <goal>java</goal>
                        </goals>
                        <configuration>
                            <mainClass>com.michalklempa.avro.compose.Main</mainClass>
                            <arguments>
                                <argument>--log.level</argument>
                                <argument>TRACE</argument>
                                <argument>--output.maven.pom</argument>
                                <argument>pom.xml</argument>
                                <argument>--output.maven.template.file</argument>
                                <argument>pom_template.xml</argument>
                                <argument>--output.schemas.directory</argument>
                                <argument>${project.basedir}/src/main/resources/generated</argument>
                                <argument>--output.schemas.format</argument>
                                <argument>pretty</argument>
                                <argument>--output.schemas.filename.template</argument>
                                <argument>{{ schema.namespace | replace('.', '/') }}/{{ schema.name }}{{ schema.props.outputFileSuffix }}.avsc</argument>
                                <argument>${project.basedir}/src/main/resources/avro</argument>
                            </arguments>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
```
So this pom.xml file actually rewrites itself during the build. The `pom_template.xml` is specified using `--output.maven.template.file` argument.

## References
[[1] Alex Holmes: Using Avro's code generation from Maven](https://dzone.com/articles/using-avros-code-generation)

[[2] Juan Luis Sánchez Vázquez: Use of avro-maven-plugin with complex schemas defined in several files to be reused in different typed messages](https://feitam.es/use-of-avro-maven-plugin-with-complex-schemas-defined-in-several-files-to-be-reused-in-different-typed-messages/)