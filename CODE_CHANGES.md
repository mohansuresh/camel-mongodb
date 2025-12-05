# Code Changes for Apache Camel 4.x Upgrade

## Overview
This document details all code changes made to upgrade the MongoDB adapter from Apache Camel 2.17.4 to 4.8.0.

## 1. pom.xml Changes

### Camel Version Upgrade
```xml
<!-- BEFORE -->
<camel.version>2.17.4</camel.version>

<!-- AFTER -->
<camel.version>4.8.0</camel.version>
<mongodb.driver.version>4.11.1</mongodb.driver.version>
<jackson.version>2.15.3</jackson.version>
```

**Explanation**: Upgraded to latest stable Camel 4.x version with property-based version management for consistency.

### MongoDB Driver Upgrade
```xml
<!-- BEFORE -->
<dependency>
  <groupId>org.mongodb</groupId>
  <artifactId>mongo-java-driver</artifactId>
  <version>3.2.2</version>
</dependency>

<!-- AFTER -->
<dependency>
  <groupId>org.mongodb</groupId>
  <artifactId>mongodb-driver-sync</artifactId>
  <version>${mongodb.driver.version}</version>
</dependency>
```

**Explanation**: The legacy `mongo-java-driver` artifact has been replaced with `mongodb-driver-sync` which is the modern synchronous driver for MongoDB 4.x.

### Java Version Upgrade
```xml
<!-- BEFORE -->
<source>1.7</source>
<target>1.7</target>

<!-- AFTER -->
<source>17</source>
<target>17</target>
<release>17</release>
```

**Explanation**: Camel 4.x requires Java 17 minimum. Added `<release>` tag for better cross-compilation support.

### Maven Compiler Plugin Upgrade
```xml
<!-- BEFORE -->
<version>2.3.2</version>

<!-- AFTER -->
<version>3.11.0</version>
```

**Explanation**: Updated to latest maven-compiler-plugin for Java 17 support.

## 2. MongoDbComponent.java Changes

### Import Changes
```java
// BEFORE
import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.CamelContextHelper;

// AFTER
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.spi.annotations.Component;
```

**Explanation**: 
- `Mongo` and `MongoClient` (legacy) replaced with new `MongoClient` interface
- `MongoClientURI` replaced with `ConnectionString`
- Camel's `impl` package classes moved to `support` package
- Added `@Component` annotation for auto-discovery

### Class Declaration
```java
// BEFORE
public class MongoDbComponent extends UriEndpointComponent {
    private volatile Mongo db;
    
    public MongoDbComponent() {
        super(MongoDbEndpoint.class);
    }

// AFTER
@Component("mongodb")
public class MongoDbComponent extends DefaultComponent {
    private volatile MongoClient mongoClient;
    
    public MongoDbComponent() {
        super();
    }
```

**Explanation**:
- `UriEndpointComponent` removed in Camel 3.x, replaced with `DefaultComponent`
- Changed field from `Mongo` to `MongoClient`
- Removed endpoint class parameter from constructor (no longer needed)

### Connection Creation
```java
// BEFORE
MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://35.243.167.31:27017/mydb"));
db = mongoClient;

// AFTER
ConnectionString connectionString = new ConnectionString("mongodb://35.243.167.31:27017/mydb");
MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .build();
mongoClient = MongoClients.create(settings);
```

**Explanation**: MongoDB 4.x driver uses builder pattern with `MongoClientSettings` for better configuration control.

## 3. MongoDbEndpoint.java Changes

### Import Changes
```java
// BEFORE
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteResult;
import org.apache.camel.impl.DefaultEndpoint;

// AFTER
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import org.apache.camel.support.DefaultEndpoint;
import org.bson.Document;
import org.bson.conversions.Bson;
```

**Explanation**:
- All legacy `DB*` classes replaced with modern equivalents
- `DBObject` replaced with `Document` (BSON document)
- Camel's `DefaultEndpoint` moved to `support` package

### Field Type Changes
```java
// BEFORE
private Mongo mongoConnection;
private DBCollection dbCollection;
private DB db;

// AFTER
private MongoClient mongoConnection;
private MongoCollection<Document> dbCollection;
private MongoDatabase db;
```

**Explanation**: All MongoDB types updated to use modern driver API with generic `Document` type.

### Database Access
```java
// BEFORE
db = mongoConnection.getDB(database);
if (!db.collectionExists(collection)) { ... }
dbCollection = db.getCollection(collection);

// AFTER
db = mongoConnection.getDatabase(database);
// Check collection existence
boolean collectionExists = false;
for (String name : db.listCollectionNames()) {
    if (name.equals(collection)) {
        collectionExists = true;
        break;
    }
}
if (!collectionExists && createCollection) {
    db.createCollection(collection);
}
dbCollection = db.getCollection(collection);
```

**Explanation**: 
- `getDB()` replaced with `getDatabase()`
- `collectionExists()` method removed, must iterate collection names
- Explicit collection creation when needed

### Index Creation
```java
// BEFORE
public List<DBObject> createIndex() throws Exception {
    List<DBObject> indexList = new ArrayList<DBObject>();
    for (Map.Entry<String, String> set : indexMap.entrySet()) {
        DBObject index = new BasicDBObject();
        index.put(set.getKey(), set.getValue());
        indexList.add(index);
    }
    return indexList;
}

// AFTER
public List<Bson> createIndex() throws Exception {
    List<Bson> indexList = new ArrayList<Bson>();
    for (Map.Entry<String, String> set : indexMap.entrySet()) {
        int order = Integer.parseInt(set.getValue());
        Bson index = order > 0 ? Indexes.ascending(set.getKey()) : Indexes.descending(set.getKey());
        indexList.add(index);
    }
    return indexList;
}
```

**Explanation**: MongoDB 4.x uses `Bson` interface and builder methods (`Indexes.ascending/descending`) instead of `DBObject`.

### Exchange Creation
```java
// BEFORE
public Exchange createMongoDbExchange(DBObject dbObj) {
    message.setBody(dbObj);
    return exchange;
}

// AFTER
public Exchange createMongoDbExchange(Document document) {
    message.setBody(document);
    return exchange;
}
```

**Explanation**: Changed parameter type from `DBObject` to `Document`.

## 4. MongoDbProducer.java Changes (Partial - Full update in next commit)

The producer file requires extensive changes to replace all `DBObject`, `DBCollection`, `DB`, `DBCursor` usage with their modern equivalents:

- `DBObject` → `Document`
- `DBCollection` → `MongoCollection<Document>`
- `DB` → `MongoDatabase`
- `DBCursor` → `FindIterable<Document>`
- `WriteResult` → `UpdateResult`, `InsertOneResult`, `DeleteResult`
- `BasicDBObject` → `Document`
- `BasicDBList` → `List<Document>`

## 5. MongoDbBasicConverters.java Changes (Planned)

Type converters need updates:
- `DBObject` → `Document`
- `BasicDBObject` → `Document`
- JSON parsing using `Document.parse()` instead of `JSON.parse()`
- BSON handling with new driver APIs

## Summary of Breaking Changes

### API Migrations
1. **Camel Framework**: `impl.*` → `support.*`
2. **MongoDB Driver**: Legacy API → Modern 4.x API
3. **Type System**: `DBObject` → `Document`
4. **Collections**: `DBCollection` → `MongoCollection<Document>`
5. **Database**: `DB` → `MongoDatabase`
6. **Client**: `Mongo` → `MongoClient`

### Configuration Changes
1. **Connection String**: `MongoClientURI` → `ConnectionString`
2. **Client Settings**: Direct constructor → Builder pattern
3. **Index Creation**: `BasicDBObject` → `Indexes` builder methods

### Method Signature Changes
1. Collection existence check: `collectionExists()` → iterate `listCollectionNames()`
2. Database access: `getDB()` → `getDatabase()`
3. Collection access: Returns generic `MongoCollection<Document>`

## Testing Recommendations

1. **Unit Tests**: Update all test cases to use new APIs
2. **Integration Tests**: Test with MongoDB 4.x or 5.x server
3. **SAP CPI Tests**: Deploy and test in SAP CPI environment
4. **Performance Tests**: Compare with previous version
5. **Backward Compatibility**: Ensure existing integrations work

## Migration Path

For teams upgrading existing adapters:

1. **Phase 1**: Update pom.xml dependencies
2. **Phase 2**: Update component and endpoint classes
3. **Phase 3**: Update producer and consumer classes
4. **Phase 4**: Update type converters
5. **Phase 5**: Update tests
6. **Phase 6**: Deploy and validate

## References

- [Apache Camel 4.x Migration Guide](https://camel.apache.org/manual/camel-4-migration-guide.html)
- [MongoDB Java Driver 4.x Documentation](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [SAP CPI Adapter Development Guide](https://help.sap.com/docs/cloud-integration)
