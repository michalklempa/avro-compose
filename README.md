<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [Avro Compose](#avro-compose)
  - [Requirements](#requirements)
  - [Motivation](#motivation)
  - [Download](#download)
    - [Simple JAR](#simple-jar)
    - [Maven setup](#maven-setup)
  - [References](#references)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Avro Compose
Utility framework for composing Avro Schemas. From smaller components (types) specified in separate files into large schemas ready to be deployed to schema registry or your application.

## Requirements
- Java 8 JDK, various options: [Zulu](https://www.azul.com/downloads/zulu-community), or [OpenJDK](https://openjdk.java.net/)
- [Maven](https://maven.apache.org/)

## Motivation
With groving number of schema files, one quickly finds out, that decomposing the schemas into smaller components (types) is needed.
With schema definition language (the JSON), there is no possibility to import other schemas into current file.
When we do not want to rewrite schema into Avro [Interface Definition Language](https://avro.apache.org/docs/current/idl.html) as proposed in [1](#references),
we have two options to ensure correct ordering of input files before parsing (either by avro-maven-plugin or our own code):
1. name the files so they order lexicographically and ensure proper loading
2. write our own tool to parse the files and match dependencies (= this project)

In this project we do not require any particular ordering of input files. If they can parse as a whole, we find correct ordering programatically.
All you need is to provide the input schemas.

## Download
### Simple JAR
To download the artifact run:
```
mvn dependency:copy -Dartifact=com.michalklempa:avro-compose:0.0.1 -DoutputDirectory=.
```
For a simple example with composing schemas, look into [example-simple](./example-simple/README.md)

### Maven setup
You may incorporate the artifact into your Java project as a dependency and then run the schema generation as a part of project build:
```
        <dependency>
            <groupId>com.michalklempa</groupId>
            <artifactId>avro-compose</artifactId>
            <version>0.0.1</version>
            <type>jar</type>
        </dependency>
```
Full example project with `pom.xml` schema generation and even the `pom.xml` itself templated can be found in `[example-project](example-project/README.md)

## References
[[1] Björn Beskow: Serialization, Schema Compositionality and Apache Avro](https://callistaenterprise.se/blogg/teknik/2019/09/24/avro-schemas-and-compositionality/)