# MongoDB Adapter Migration Guide
## Camel 2.17.4 to 3.22.2 Upgrade

### Overview

This document provides detailed migration instructions for upgrading the SAP CPI MongoDB adapter from Apache Camel 2.17.4 to 3.22.2 (LTS).

---

## Summary of Changes

### Version Upgrades
- **Apache Camel**: 2.17.4 → 3.22.2
- **MongoDB Driver**: 3.2.2 → 4.11.1
- **Jackson**: 2.7.8 → 2.15.3
- **Java**: 1.7 → 11

### Key API Changes

#### 1. MongoDB Driver Migration

| Old API (3.x) | New API (4.x) | Notes |
|---------------|---------------|-------|
| `Mongo` | `MongoClient` | Client interface changed |
| `DB` | `MongoDatabase` | Database interface changed |
| `DBCollection` | `MongoCollection<Document>` | Now generic typed |
| `DBObject` | `Document` | BSON document representation |
| `BasicDBObject` | `Document` | Constructor changed |
| `DBCursor` | `FindIterable<Document>` | Iterator pattern changed |
| `WriteResult` | `UpdateResult`, `DeleteResult`, `InsertOneResult` | Specific result types |
| `AggregationOutput` | `AggregateIterable<Document>` | Aggregation result changed |

#### 2. Camel API Migration

| Old Package/Class | New Package/Class |
|-------------------|-------------------|
| `org.apache.camel.impl.UriEndpointComponent` | `org.apache.camel.support.DefaultComponent` |
| `org.apache.camel.impl.DefaultEndpoint` | `org.apache.camel.support.DefaultEndpoint` |
| `org.apache.camel.impl.DefaultProducer` | `org.apache.camel.support.DefaultProducer` |
| `exchange.getOut()` | `exchange.getMessage()` | Deprecated method |

---

## Breaking Changes

### 1. Connection Configuration

**Before (Camel 2.x):**
```java
MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://host:port/db"));
```

**After (Camel 3.x):**
```java
ConnectionString connectionString = new ConnectionString("mongodb://host:port/db");
MongoClientSettings settings = MongoClientSettings.builder()
        .applyConnectionString(connectionString)
        .build();
MongoClient mongoClient = MongoClients.create(settings);
```

### 2. Database and Collection Access

**Before:**
```java
DB db = mongoClient.getDB("mydb");
DBCollection collection = db.getCollection("mycollection");
```

**After:**
```java
MongoDatabase db = mongoClient.getDatabase("mydb");
MongoCollection<Document> collection = db.getCollection("mycollection");
```

### 3. CRUD Operations

#### Insert Operation

**Before:**
```java
DBObject doc = new BasicDBObject("name", "John");
WriteResult result = collection.insert(doc);
```

**After:**
```java
Document doc = new Document("name", "John");
InsertOneResult result = collection.insertOne(doc);
```

#### Find Operation

**Before:**
```java
DBObject query = new BasicDBObject("name", "John");
DBCursor cursor = collection.find(query);
```

**After:**
```java
Document query = new Document("name", "John");
FindIterable<Document> cursor = collection.find(query);
```

#### Update Operation

**Before:**
```java
DBObject query = new BasicDBObject("name", "John");
DBObject update = new BasicDBObject("$set", new BasicDBObject("age", 30));
WriteResult result = collection.update(query, update);
```

**After:**
```java
Document query = new Document("name", "John");
Document update = new Document("$set", new Document("age", 30));
UpdateResult result = collection.updateOne(query, update);
```

#### Delete Operation

**Before:**
```java
DBObject query = new BasicDBObject("name", "John");
WriteResult result = collection.remove(query);
```

**After:**
```java
Document query = new Document("name", "John");
DeleteResult result = collection.deleteMany(query);
```

### 4. Aggregation

**Before:**
```java
DBObject match = new BasicDBObject("$match", new BasicDBObject("status", "A"));
AggregationOutput output = collection.aggregate(match);
```

**After:**
```java
Document match = new Document("$match", new Document("status", "A"));
List<Document> pipeline = Arrays.asList(match);
AggregateIterable<Document> output = collection.aggregate(pipeline);
```

### 5. Write Concerns

**Before:**
```java
collection.setWriteConcern(WriteConcern.ACKNOWLEDGED);
WriteResult result = collection.insert(doc);
```

**After:**
```java
MongoCollection<Document> coll = collection.withWriteConcern(WriteConcern.ACKNOWLEDGED);
InsertOneResult result = coll.insertOne(doc);
```

---

## Code Migration Examples

### Example 1: Simple Insert

**Before (Camel 2.x):**
```java
from("direct:insert")
    .setBody(constant(new BasicDBObject("name", "John")))
    .to("mongodb:myConnection?database=mydb&collection=users&operation=insert");
```

**After (Camel 3.x):**
```java
from("direct:insert")
    .setBody(constant(new Document("name", "John")))
    .to("mongodb:myConnection?database=mydb&collection=users&operation=insert");
```

### Example 2: Find with Query

**Before:**
```java
from("direct:find")
    .setBody(constant(new BasicDBObject("status", "active")))
    .to("mongodb:myConnection?database=mydb&collection=users&operation=findAll")
    .process(exchange -> {
        List<DBObject> results = exchange.getIn().getBody(List.class);
        // Process results
    });
```

**After:**
```java
from("direct:find")
    .setBody(constant(new Document("status", "active")))
    .to("mongodb:myConnection?database=mydb&collection=users&operation=findAll")
    .process(exchange -> {
        List<Document> results = exchange.getIn().getBody(List.class);
        // Process results
    });
```

### Example 3: Update Operation

**Before:**
```java
from("direct:update")
    .process(exchange -> {
        List<DBObject> updates = new ArrayList<>();
        updates.add(new BasicDBObject("_id", "123"));
        updates.add(new BasicDBObject("$set", new BasicDBObject("status", "inactive")));
        exchange.getIn().setBody(updates);
    })
    .to("mongodb:myConnection?database=mydb&collection=users&operation=update");
```

**After:**
```java
from("direct:update")
    .process(exchange -> {
        List<Document> updates = new ArrayList<>();
        updates.add(new Document("_id", "123"));
        updates.add(new Document("$set", new Document("status", "inactive")));
        exchange.getIn().setBody(updates);
    })
    .to("mongodb:myConnection?database=mydb&collection=users&operation=update");
```

---

## Configuration Changes

### Maven Dependencies

Update your `pom.xml`:

```xml
<properties>
    <camel.version>3.22.2</camel.version>
    <mongodb.driver.version>4.11.1</mongodb.driver.version>
    <jackson.version>2.15.3</jackson.version>
</properties>

<dependencies>
    <!-- Camel Core -->
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-core</artifactId>
        <version>${camel.version}</version>
    </dependency>
    
    <!-- MongoDB Driver -->
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongodb-driver-sync</artifactId>
        <version>${mongodb.driver.version}</version>
    </dependency>
    
    <!-- Jackson -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    
    <!-- JAXB for Java 11+ -->
    <dependency>
        <groupId>javax.xml.bind</groupId>
        <artifactId>jaxb-api</artifactId>
        <version>2.3.1</version>
    </dependency>
</dependencies>
```

### Java Compiler Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.11.0</version>
    <configuration>
        <source>11</source>
        <target>11</target>
        <release>11</release>
    </configuration>
</plugin>
```

---

## Testing Recommendations

### 1. Unit Tests
- Update all test cases to use `Document` instead of `DBObject`
- Update assertions for new result types
- Test all CRUD operations

### 2. Integration Tests
- Test with actual MongoDB instance
- Verify connection pooling behavior
- Test error handling and retry logic

### 3. Performance Tests
- Benchmark operations before and after upgrade
- Monitor memory usage
- Test under load

---

## Common Issues and Solutions

### Issue 1: ClassNotFoundException for DBObject

**Error:**
```
java.lang.ClassNotFoundException: com.mongodb.DBObject
```

**Solution:**
Replace all `DBObject` imports with `Document`:
```java
// Before
import com.mongodb.DBObject;
import com.mongodb.BasicDBObject;

// After
import org.bson.Document;
```

### Issue 2: Method not found errors

**Error:**
```
java.lang.NoSuchMethodError: com.mongodb.client.MongoCollection.insert
```

**Solution:**
Use the new API methods:
- `insert()` → `insertOne()` or `insertMany()`
- `update()` → `updateOne()` or `updateMany()`
- `remove()` → `deleteOne()` or `deleteMany()`

### Issue 3: Write Result handling

**Error:**
```
Cannot cast UpdateResult to WriteResult
```

**Solution:**
Use specific result types:
```java
// Before
WriteResult result = collection.update(query, update);
int count = result.getN();

// After
UpdateResult result = collection.updateOne(query, update);
long count = result.getModifiedCount();
```

---

## Rollback Procedure

If you need to rollback to Camel 2.x:

1. Revert `pom.xml` changes
2. Restore old Java source files from version control
3. Rebuild and redeploy
4. Test thoroughly

---

## Additional Resources

- [Apache Camel 3.x Migration Guide](https://camel.apache.org/manual/camel-3-migration-guide.html)
- [MongoDB Java Driver 4.x Documentation](https://www.mongodb.com/docs/drivers/java/sync/current/)
- [SAP CPI Adapter Development Guide](https://help.sap.com/docs/cloud-integration)

---

## Support

For issues or questions:
1. Check the UPGRADE_PLAN.md document
2. Review Apache Camel migration guides
3. Consult MongoDB driver documentation
4. Contact SAP Support if needed

---

**Last Updated:** December 4, 2025
**Version:** 1.0
