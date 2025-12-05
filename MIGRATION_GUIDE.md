# Apache Camel MongoDB Adapter - Migration to Camel 3.22 LTS

## Overview
This document details the migration of the SAP CPI custom MongoDB adapter from Apache Camel 2.17.4 to Camel 3.22.2 (LTS), including upgrades to Java 11, MongoDB Driver 4.x, and related dependencies.

## MCP Server Tools Used

The following MCP server tools were utilized during the analysis and migration process:

### ADK Copilot MCP Server Tools:
1. **get_agent_instructions** - Retrieved ADK Copilot workflow guidelines and best practices
2. **list_camel_artifacts** - Identified Camel components used (camel-core, camel-jackson)
3. **list_maven_artifacts** - Listed all Maven dependencies in the project
4. **get_metadata_xml** - Retrieved adapter metadata showing Camel version 2.17.4
5. **get_all_java_files_content** - Analyzed all Java source files for compatibility issues
6. **list_reference_documents** - Listed 18 available migration guides and documentation
7. **get_reference_document** - Accessed migration guide references

### Other Tools:
8. **web_fetch** - Retrieved official Apache Camel 2.x to 3.0 migration guide from camel.apache.org
9. **git commands** - Created feature branch for migration changes

## Version Upgrades

### Before (Camel 2.17.4):
- **Apache Camel:** 2.17.4 (released 2016)
- **Java:** 1.7
- **MongoDB Driver:** mongo-java-driver 3.2.2 (deprecated legacy driver)
- **Jackson:** 2.7.8
- **Maven Compiler:** 2.3.2

### After (Camel 3.22.2 LTS):
- **Apache Camel:** 3.22.2 (LTS release)
- **Java:** 11 (minimum requirement for Camel 3.x)
- **MongoDB Driver:** mongodb-driver-sync 4.11.1 (modern sync driver)
- **Jackson:** 2.15.3
- **Maven Compiler:** 3.11.0
- **Maven Bundle Plugin:** 5.1.9

## Key Changes

### 1. POM.xml Changes

#### Dependencies Added:
```xml
<!-- Support classes for custom components -->
<dependency>
  <groupId>org.apache.camel</groupId>
  <artifactId>camel-support</artifactId>
  <version>${camel.version}</version>
  <scope>provided</scope>
</dependency>

<!-- JAXB dependencies for Java 11+ (removed from JDK) -->
<dependency>
  <groupId>javax.xml.bind</groupId>
  <artifactId>jaxb-api</artifactId>
  <version>2.3.1</version>
</dependency>
```

#### Dependencies Updated:
- MongoDB driver changed from `mongo-java-driver` to `mongodb-driver-sync`
- Jackson updated from 2.7.8 to 2.15.3
- All Camel dependencies updated to 3.22.2

#### Build Configuration:
- Java source/target changed from 1.7 to 11
- Added `<release>11</release>` for proper Java 11 compilation
- Updated maven-compiler-plugin to 3.11.0
- Updated maven-bundle-plugin to 5.1.9

### 2. Java Code Changes

#### MongoDbComponent.java

**Import Changes:**
```java
// OLD (Camel 2.x + MongoDB 3.x)
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CamelContextHelper;

// NEW (Camel 3.x + MongoDB 4.x)
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.CamelContextHelper;
```

**Class Changes:**
- Extended class changed from `UriEndpointComponent` to `DefaultComponent`
- `Mongo` type changed to `MongoClient` (new driver interface)
- Constructor simplified (no longer needs to pass endpoint class)

**MongoDB Connection:**
```java
// OLD
MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://..."));

// NEW
ConnectionString connectionString = new ConnectionString("mongodb://...");
MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .build();
MongoClient mongoClient = MongoClients.create(settings);
```

#### MongoDbEndpoint.java

**Import Changes:**
```java
// OLD (MongoDB 3.x legacy API)
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteResult;
import org.apache.camel.impl.DefaultEndpoint;

// NEW (MongoDB 4.x modern API)
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.apache.camel.support.DefaultEndpoint;
```

**Type Changes:**
- `Mongo` → `MongoClient`
- `DB` → `MongoDatabase`
- `DBCollection` → `MongoCollection<Document>`
- `DBObject` → `Document` (BSON Document)
- `WriteResult` → `InsertOneResult`, `UpdateResult`, `DeleteResult`

**API Method Changes:**
```java
// OLD
db = mongoConnection.getDB(database);
dbCollection = db.getCollection(collection);
mongoConnection.getAllAddress().toString()

// NEW
db = mongoConnection.getDatabase(database);
dbCollection = db.getCollection(collection);
dbCollection.getNamespace().getCollectionName()
```

**Method Overrides:**
- Added `@Override` annotations to `createProducer()` and `createConsumer()` methods

### 3. MongoDB Driver 4.x Migration

#### Key API Changes:

1. **Database Access:**
   - `getDB()` → `getDatabase()`
   - Returns `MongoDatabase` instead of `DB`

2. **Collection Access:**
   - Collections are now typed: `MongoCollection<Document>`
   - Automatic collection creation on first write (no need for `collectionExists()`)

3. **Document Model:**
   - `DBObject` replaced with `Document` (implements `Bson`)
   - `BasicDBObject` replaced with `Document`
   - More type-safe and modern API

4. **Write Results:**
   - `WriteResult` split into specific result types:
     - `InsertOneResult` / `InsertManyResult`
     - `UpdateResult`
     - `DeleteResult`

5. **Index Creation:**
   - Method signature remains similar but uses `Document` instead of `DBObject`

### 4. Camel 3.x Migration

#### Component Development Changes:

1. **Base Classes Moved:**
   - `org.apache.camel.impl.DefaultEndpoint` → `org.apache.camel.support.DefaultEndpoint`
   - `org.apache.camel.impl.UriEndpointComponent` → `org.apache.camel.support.DefaultComponent`
   - `org.apache.camel.util.CamelContextHelper` → `org.apache.camel.support.CamelContextHelper`

2. **Support Module:**
   - Custom component support classes moved to `camel-support` JAR
   - Must add `camel-support` as dependency

3. **Exception Handling:**
   - Most exceptions are now unchecked (extend `RuntimeException`)
   - `CamelMongoDbException` remains as custom exception

4. **Lifecycle Methods:**
   - `doShutdown()` no longer throws checked exceptions

## Breaking Changes

### For Adapter Users:

1. **Java Version:** Minimum Java 11 required (was Java 7)
2. **MongoDB Server:** Should use MongoDB 3.6+ (MongoDB 4.x recommended)
3. **Data Types:** Internal representation changed from `DBObject` to `Document`
4. **Write Results:** Result objects have different structure

### For Developers:

1. **Import Statements:** Many package relocations require import updates
2. **Type System:** Stronger typing with generics (`MongoCollection<Document>`)
3. **API Methods:** Several MongoDB API methods renamed or restructured
4. **Camel Support:** Must include `camel-support` dependency

## Testing Recommendations

1. **Unit Tests:** Update to use MongoDB 4.x test containers
2. **Integration Tests:** Test with actual MongoDB 4.x server
3. **Performance Tests:** Verify performance with new driver
4. **Compatibility Tests:** Test with SAP CPI runtime environment

## Deployment Considerations

1. **SAP CPI Compatibility:** Verify Camel 3.22 is supported by target SAP CPI version
2. **MongoDB Server:** Ensure MongoDB server version is 3.6 or higher
3. **Java Runtime:** Ensure Java 11+ is available in deployment environment
4. **Dependencies:** All transitive dependencies updated automatically

## Additional Notes

### JAXB Dependencies
Java 11 removed JAXB from the JDK. The following dependencies were added:
- `jaxb-api` 2.3.1
- `jaxb-core` 2.3.0.1
- `jaxb-impl` 2.3.2

These are required for XML DSL and metadata processing.

### MongoDB Driver Benefits
The new MongoDB 4.x driver provides:
- Better performance and connection pooling
- Modern async capabilities (though using sync driver)
- Improved type safety with `Document` API
- Better error handling and diagnostics
- Active maintenance and security updates

### Camel 3.22 LTS Benefits
- Long-term support release
- Security updates and bug fixes
- Better performance and memory usage
- Modern Java support (11, 17, 21)
- Improved component ecosystem

## References

1. [Apache Camel 2.x to 3.0 Migration Guide](https://camel.apache.org/manual/camel-3-migration-guide.html)
2. [Apache Camel 3.x Upgrade Guide](https://camel.apache.org/manual/camel-3x-upgrade-guide.html)
3. [MongoDB Java Driver 4.x Documentation](https://www.mongodb.com/docs/drivers/java/sync/current/)
4. [SAP Cloud Integration - Apache Camel Upgrade](https://help.sap.com/docs/cloud-integration/sap-cloud-integration/apache-camel-3-14-upgrade)

## Migration Checklist

- [x] Update pom.xml with new versions
- [x] Add camel-support dependency
- [x] Add JAXB dependencies for Java 11
- [x] Update MongoDB driver to 4.x
- [x] Update MongoDbComponent imports and base class
- [x] Update MongoDbEndpoint imports and types
- [x] Replace DBObject with Document
- [x] Replace DB with MongoDatabase
- [x] Replace DBCollection with MongoCollection<Document>
- [x] Update MongoDB API method calls
- [x] Add @Override annotations
- [x] Update Java version to 11
- [ ] Update MongoDbProducer (requires additional changes)
- [ ] Update MongoDbTailableCursorConsumer (requires additional changes)
- [ ] Update MongoDbTailingProcess (requires additional changes)
- [ ] Update MongoDbTailTrackingManager (requires additional changes)
- [ ] Update converters (requires additional changes)
- [ ] Update metadata.xml version
- [ ] Run unit tests
- [ ] Run integration tests
- [ ] Deploy to test environment
- [ ] Validate with SAP CPI

## Next Steps

1. Complete remaining Java file updates (Producer, Consumer, etc.)
2. Update unit tests for MongoDB 4.x
3. Update metadata.xml to reflect new version
4. Build and test the adapter
5. Create comprehensive test suite
6. Document any behavioral changes
7. Create deployment guide for SAP CPI

## Support

For issues or questions regarding this migration:
- Review Apache Camel migration guides
- Check MongoDB driver documentation
- Consult SAP CPI documentation for Camel version compatibility
- Review ADK Copilot reference documents
