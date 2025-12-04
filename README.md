# camel-mongodb

## Overview

camel-mongodb is an Apache Camel component that enables seamless integration between Camel routes and MongoDB databases. It allows you to perform a wide range of MongoDB operations directly from your Camel routes, supporting both read and write operations, aggregation, and administrative commands.

## Features
- Perform CRUD operations on MongoDB collections from Camel routes
- Support for advanced MongoDB operations (aggregate, count, stats, command)
- Flexible configuration via URI and message headers
- Supports both producer and consumer endpoints
- Integration with Camel's type conversion and data formats

## Installation

Add the following dependency to your Maven `pom.xml`:

```xml
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-mongodb</artifactId>
    <version>2.17.4</version>
</dependency>
```

You will also need the MongoDB Java driver and Camel core dependencies:

```xml
<dependency>
    <groupId>org.mongodb</groupId>
    <artifactId>mongo-java-driver</artifactId>
    <version>3.2.2</version>
</dependency>
<dependency>
    <groupId>org.apache.camel</groupId>
    <artifactId>camel-core</artifactId>
    <version>2.17.4</version>
</dependency>
```

## Usage

You can use the MongoDB component in your Camel routes using the `mongodb` endpoint URI. Example:

```java
from("direct:start")
    .to("mongodb:myDb?database=test&collection=users&operation=insert");
```

Or in XML DSL:

```xml
<route>
    <from uri="direct:start"/>
    <to uri="mongodb:myDb?database=test&amp;collection=users&amp;operation=insert"/>
</route>
```

## Configuration

The MongoDB component is configured via URI parameters and message headers.

**Common URI parameters:**
- `database`: Name of the MongoDB database
- `collection`: Name of the collection
- `operation`: Operation to perform (see Supported Operations)

**Example URI:**
```
mongodb:myDb?database=test&collection=users&operation=findAll
```

**Common headers (from `MongoDbConstants`):**
- `CamelMongoDbOperation`: Override the operation per message
- `CamelMongoDbDatabase`: Override the database per message
- `CamelMongoDbCollection`: Override the collection per message
- `CamelMongoDbSortBy`: Specify sort order
- `CamelMongoDbLimit`: Limit the number of results
- `CamelMongoDbBatchSize`: Set batch size for queries
- `CamelMongoDbUpsert`: Enable upsert for update operations
- `CamelMongoDbMultiUpdate`: Enable multi-update

## Supported Operations

The following operations are supported (see `MongoDbOperation`):
- `findById`: Find a document by its ID
- `findOneByQuery`: Find a single document matching a query
- `findAll`: Find all documents in a collection
- `insert`: Insert a new document
- `save`: Save (insert or update) a document
- `update`: Update existing documents
- `remove`: Remove documents
- `aggregate`: Run an aggregation pipeline
- `getDbStats`: Get database statistics
- `getColStats`: Get collection statistics
- `count`: Count documents
- `command`: Run a database command

## Advanced Options

You can further control MongoDB operations using message headers. See `MongoDbConstants.java` for a full list of supported headers.

## Testing

To run the tests, use Maven:

```sh
mvn test
```

Test resources and example configurations are available in `src/test/resources`.

## Contributing

Contributions are welcome! Please open issues or submit pull requests via GitHub. Make sure to follow the project's coding standards and include tests for new features.

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE.txt](src/main/resources/META-INF/LICENSE.txt) and [NOTICE.txt](src/main/resources/META-INF/NOTICE.txt) files for details.

## Support

For questions, please use the Apache Camel user mailing list or file an issue on the GitHub repository.