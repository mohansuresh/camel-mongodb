# Apache Camel MongoDB Adapter - Migration to Camel 4.x

## Overview
This document outlines the changes made to upgrade the SAP CPI MongoDB adapter from Apache Camel 2.17.4 to Camel 4.4.0, including MongoDB driver upgrade from 3.2.2 to 5.1.0.

## Summary of Changes

### 1. Dependency Upgrades

#### Apache Camel
- **From:** 2.17.4 (Released 2016)
- **To:** 4.4.0 (Latest stable)
- **Impact:** Major version upgrade with breaking API changes

#### MongoDB Java Driver
- **From:** mongo-java-driver 3.2.2 (Legacy driver)
- **To:** mongodb-driver-sync 5.1.0 (Modern sync driver)
- **Impact:** Complete API rewrite from legacy to modern driver

#### Jackson
- **From:** 2.7.8
- **To:** 2.17.0
- **Impact:** Security fixes and performance improvements

#### Java Runtime
- **From:** Java 7
- **To:** Java 17 (LTS)
- **Impact:** Modern language features and improved performance

### 2. Code Changes

#### Package Migrations (Camel 2.x → 4.x)

| Old Package (Camel 2.x) | New Package (Camel 4.x) |
|-------------------------|-------------------------|
| `org.apache.camel.impl.UriEndpointComponent` | `org.apache.camel.support.DefaultComponent` |
| `org.apache.camel.impl.DefaultEndpoint` | `org.apache.camel.support.DefaultEndpoint` |
| `org.apache.camel.impl.DefaultProducer` | `org.apache.camel.support.DefaultProducer` |
| `org.apache.camel.impl.DefaultConsumer` | `org.apache.camel.support.DefaultConsumer` |
| `org.apache.camel.util.CamelContextHelper` | `org.apache.camel.support.CamelContextHelper` |

#### MongoDB Driver API Changes

| Legacy API (3.x) | Modern API (5.x) | Notes |
|------------------|------------------|-------|
| `com.mongodb.Mongo` | `com.mongodb.client.MongoClient` | Main client interface |
| `com.mongodb.MongoClient` | `com.mongodb.client.MongoClients` | Factory for creating clients |
| `com.mongodb.MongoClientURI` | `com.mongodb.ConnectionString` | Connection string parsing |
| `com.mongodb.DB` | `com.mongodb.client.MongoDatabase` | Database interface |
| `com.mongodb.DBCollection` | `com.mongodb.client.MongoCollection<Document>` | Collection interface with generics |
| `com.mongodb.DBObject` | `org.bson.Document` | Document representation |
| `com.mongodb.BasicDBObject` | `org.bson.Document` | Document creation |
| `com.mongodb.WriteResult` | `com.mongodb.client.result.*` | Multiple result types |
| `db.getDB(name)` | `client.getDatabase(name)` | Get database |
| `db.collectionExists()` | Check via exception handling | Collection existence check |
| `collection.insert()` | `collection.insertOne()` / `insertMany()` | Insert operations |
| `collection.update()` | `collection.updateOne()` / `updateMany()` | Update operations |
| `collection.remove()` | `collection.deleteOne()` / `deleteMany()` | Delete operations |

### 3. Key Architectural Changes

#### 1. Component Base Class
```java
// Old (Camel 2.x)
public class MongoDbComponent extends UriEndpointComponent {
    public MongoDbComponent() {
        super(MongoDbEndpoint.class);
    }
}

// New (Camel 4.x)
public class MongoDbComponent extends DefaultComponent {
    public MongoDbComponent() {
        super();
    }
}
```

#### 2. MongoDB Client Creation
```java
// Old (MongoDB 3.x)
MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://host:port/db"));
Mongo db = mongoClient;

// New (MongoDB 5.x)
ConnectionString connectionString = new ConnectionString("mongodb://host:port/db");
MongoClient mongoClient = MongoClients.create(connectionString);
```

#### 3. Database and Collection Access
```java
// Old (MongoDB 3.x)
DB db = mongo.getDB("database");
DBCollection collection = db.getCollection("collection");
boolean exists = db.collectionExists("collection");

// New (MongoDB 5.x)
MongoDatabase db = mongoClient.getDatabase("database");
MongoCollection<Document> collection = db.getCollection("collection");
// Collection existence checked via exception handling
```

#### 4. Document Operations
```java
// Old (MongoDB 3.x)
DBObject doc = new BasicDBObject("key", "value");
collection.insert(doc);

// New (MongoDB 5.x)
Document doc = new Document("key", "value");
InsertOneResult result = collection.insertOne(doc);
```

#### 5. Registry Access (Camel API)
```java
// Old (Camel 2.x)
WriteConcern wc = getCamelContext().getRegistry()
    .lookupByNameAndType(name, WriteConcern.class);

// New (Camel 4.x)
WriteConcern wc = getCamelContext().getRegistry()
    .findByTypeWithName(WriteConcern.class).get(name);
```

### 4. Files Modified

1. **pom.xml**
   - Updated Camel version to 4.4.0
   - Updated MongoDB driver to 5.1.0
   - Updated Jackson to 2.17.0
   - Updated Java compiler to 17
   - Updated maven-compiler-plugin to 3.11.0

2. **MongoDbComponent.java**
   - Changed base class from `UriEndpointComponent` to `DefaultComponent`
   - Updated MongoDB client creation
   - Changed `Mongo` to `MongoClient`
   - Updated imports

3. **MongoDbEndpoint.java**
   - Changed base class from `DefaultEndpoint` (impl) to `DefaultEndpoint` (support)
   - Updated all MongoDB types (DB → MongoDatabase, DBCollection → MongoCollection<Document>)
   - Updated collection existence checking logic
   - Removed deprecated WriteConcern/ReadPreference setting on client
   - Updated registry access methods
   - Updated document counting method

### 5. Breaking Changes & Migration Notes

#### For Adapter Users:
1. **Java 17 Required:** The adapter now requires Java 17 or higher
2. **MongoDB 4.0+:** The new driver requires MongoDB server 4.0 or higher
3. **Connection Strings:** Ensure connection strings are compatible with modern format
4. **Write Concerns:** WriteConcern and ReadPreference are now set per-operation

#### For Developers:
1. **API Changes:** All MongoDB operations now use the modern driver API
2. **Type Safety:** Collections are now generic: `MongoCollection<Document>`
3. **Result Types:** Write operations return specific result types (InsertOneResult, UpdateResult, DeleteResult)
4. **Error Handling:** Exception types and handling may differ

### 6. Testing Recommendations

1. **Unit Tests:** Update all unit tests to use modern MongoDB driver
2. **Integration Tests:** Test against MongoDB 4.0+ servers
3. **Performance Tests:** Verify performance with new driver
4. **Compatibility Tests:** Test with SAP CPI runtime

### 7. Additional Files Requiring Updates

The following files still need to be updated to complete the migration:

1. **MongoDbProducer.java** - Update producer operations
2. **MongoDbTailableCursorConsumer.java** - Update consumer operations
3. **MongoDbTailingProcess.java** - Update tailing cursor logic
4. **MongoDbTailTrackingManager.java** - Update tracking operations
5. **MongoDbBasicConverters.java** - Update type converters
6. **Test files** - Update all test cases

### 8. Benefits of Upgrade

1. **Security:** Latest versions include critical security fixes
2. **Performance:** Improved performance in both Camel and MongoDB driver
3. **Features:** Access to new Camel 4.x and MongoDB 5.x features
4. **Support:** Active community support for current versions
5. **Compatibility:** Better compatibility with modern Java and cloud platforms

### 9. Rollback Plan

If issues arise:
1. Revert to previous commit
2. The old code is preserved in git history
3. Can maintain separate branches for Camel 2.x and 4.x versions

### 10. Next Steps

1. Complete remaining file updates (Producer, Consumer, etc.)
2. Update and run all tests
3. Perform integration testing with SAP CPI
4. Update documentation
5. Create release notes

## References

- [Apache Camel 4.x Migration Guide](https://camel.apache.org/manual/camel-4-migration-guide.html)
- [Apache Camel 3.x to 4.0 Migration Guide](https://camel.apache.org/manual/camel-3x-upgrade-guide-4_0.html)
- [MongoDB Java Driver 5.x Documentation](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [MongoDB Driver Migration Guide](https://www.mongodb.com/docs/drivers/java/sync/current/upgrade/)
