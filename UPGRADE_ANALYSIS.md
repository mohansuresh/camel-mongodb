# SAP CPI MongoDB Custom Adapter - Apache Camel Upgrade Analysis

## Executive Summary

This document provides a comprehensive analysis of the MongoDB custom adapter for SAP Cloud Platform Integration (CPI) and outlines the required changes to upgrade from Apache Camel 2.17.4 to Apache Camel 4.x.

---

## Current State Analysis

### 1. Current Versions Identified

**Apache Camel Components:**
- **Camel Core**: 2.17.4
- **Camel Jackson**: 2.17.4
- **Camel MongoDB Component**: Custom implementation based on 2.17.4

**Java Runtime:**
- **Java Version**: 1.7 (source and target compatibility)

**Maven Dependencies:**
- `org.apache.camel:camel-core:2.17.4` (provided scope)
- `org.apache.camel:camel-jackson:2.17.4` (provided scope)
- `org.mongodb:mongo-java-driver:3.2.2`
- `com.fasterxml.jackson.core:jackson-databind:2.7.8`

**SAP ADK Plugin:**
- `com.sap.cloud.adk:com.sap.cloud.adk.build.archive:1.25.0`

**Metadata:**
- Component ID: `ctype::Adapter/cname::MongoDB/vendor::ApacheCamel/version::2.17.4`
- Variants: MongoDB Component Sender and Receiver

---

## MCP Server Tools Used

### ADK Copilot MCP Server Tools:
1. **get_agent_instructions** - Retrieved agent workflow and reference documentation list
2. **list_reference_documents** - Listed 18 available migration guides and best practices documents

### Local Analysis Tools:
- **read_file** - Analyzed pom.xml, metadata.xml, and all Java source files
- **list_files** - Explored project structure
- **execute_command** - Retrieved git remote URL and created upgrade branch

---

## Critical Compatibility Issues

### 1. **Deprecated MongoDB Java Driver API**

**Issue**: The code uses the legacy MongoDB Java Driver 3.2.2 with deprecated classes:
- `com.mongodb.Mongo` (deprecated)
- `com.mongodb.DB` (deprecated)
- `com.mongodb.DBCollection` (deprecated)
- `com.mongodb.DBObject` (deprecated)
- `com.mongodb.DBCursor` (deprecated)

**Impact**: These APIs were removed in MongoDB Java Driver 4.x and are incompatible with modern MongoDB deployments.

### 2. **Apache Camel API Changes**

**Camel 2.x → 3.x Breaking Changes:**
- `org.apache.camel.impl.UriEndpointComponent` → `org.apache.camel.support.DefaultComponent`
- `org.apache.camel.impl.DefaultEndpoint` → `org.apache.camel.support.DefaultEndpoint`
- `org.apache.camel.impl.DefaultProducer` → `org.apache.camel.support.DefaultProducer`
- Package restructuring: `org.apache.camel.impl.*` → `org.apache.camel.support.*`

**Camel 3.x → 4.x Breaking Changes:**
- Java 17 minimum requirement
- Further API refinements and deprecation removals
- Enhanced type safety and null-safety annotations

### 3. **Java Version Incompatibility**

**Current**: Java 1.7
**Required for Camel 4.x**: Java 17 minimum

### 4. **Jackson Version Incompatibility**

**Current**: jackson-databind 2.7.8 (released 2016)
**Required**: jackson-databind 2.15.x+ for Camel 4.x compatibility

---

## Recommended Upgrade Path

### Phase 1: Upgrade to Camel 3.x (Intermediate Step)
- Upgrade to Camel 3.20.x (last 3.x version with Java 11 support)
- Update MongoDB Java Driver to 4.x
- Refactor deprecated API usage
- Update Java to 11

### Phase 2: Upgrade to Camel 4.x (Target)
- Upgrade to Camel 4.8.x (latest stable)
- Update Java to 17
- Apply final API changes
- Update all dependencies

---

## Required Code Changes

### 1. Update pom.xml Dependencies

**Current State:**
```xml
<camel.version>2.17.4</camel.version>
<source>1.7</source>
<target>1.7</target>
<mongo-java-driver>3.2.2</mongo-java-driver>
<jackson-databind>2.7.8</jackson-databind>
```

**Target State (Camel 4.x):**
```xml
<camel.version>4.8.0</camel.version>
<source>17</source>
<target>17</target>
<mongodb-driver-sync>5.2.0</mongodb-driver-sync>
<jackson-databind>2.17.2</jackson-databind>
```

### 2. Update MongoDbComponent.java

**Issue**: Uses deprecated `UriEndpointComponent` and `Mongo` class

**Current Code:**
```java
import org.apache.camel.impl.UriEndpointComponent;
import com.mongodb.Mongo;
import com.mongodb.MongoClient;

public class MongoDbComponent extends UriEndpointComponent {
    private volatile Mongo db;
    
    MongoClient mongoClient = new MongoClient(new MongoClientURI("mongodb://..."));
    db = mongoClient;
}
```

**Updated Code:**
```java
import org.apache.camel.support.DefaultComponent;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

public class MongoDbComponent extends DefaultComponent {
    private volatile MongoClient mongoClient;
    
    mongoClient = MongoClients.create("mongodb://35.243.167.31:27017/mydb");
}
```

### 3. Update MongoDbEndpoint.java

**Issue**: Uses deprecated `DefaultEndpoint`, `DB`, `DBCollection`, `DBObject` classes

**Current Code:**
```java
import org.apache.camel.impl.DefaultEndpoint;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

public class MongoDbEndpoint extends DefaultEndpoint {
    private Mongo mongoConnection;
    private DB db;
    private DBCollection dbCollection;
    
    db = mongoConnection.getDB(database);
    dbCollection = db.getCollection(collection);
}
```

**Updated Code:**
```java
import org.apache.camel.support.DefaultEndpoint;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class MongoDbEndpoint extends DefaultEndpoint {
    private MongoClient mongoConnection;
    private MongoDatabase db;
    private MongoCollection<Document> dbCollection;
    
    db = mongoConnection.getDatabase(database);
    dbCollection = db.getCollection(collection);
}
```

### 4. Update MongoDbProducer.java

**Issue**: Uses deprecated `DefaultProducer`, `DBObject`, `DBCursor`, `WriteResult` classes

**Current Code:**
```java
import org.apache.camel.impl.DefaultProducer;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.WriteResult;
import com.mongodb.BasicDBObject;

DBObject query = new BasicDBObject();
DBCursor cursor = dbCol.find(query);
WriteResult result = dbCol.insert(insertObject);
```

**Updated Code:**
```java
import org.apache.camel.support.DefaultProducer;
import org.bson.Document;
import com.mongodb.client.FindIterable;
import com.mongodb.client.result.InsertOneResult;

Document query = new Document();
FindIterable<Document> cursor = dbCol.find(query);
InsertOneResult result = dbCol.insertOne(insertObject);
```

### 5. Update MongoDbBasicConverters.java

**Issue**: Uses deprecated `DBObject`, `BasicDBObject`, and legacy JSON parsing

**Current Code:**
```java
import com.mongodb.DBObject;
import com.mongodb.BasicDBObject;
import com.mongodb.util.JSON;

@Converter
public static DBObject fromStringToDBObject(String s) {
    return (DBObject) JSON.parse(s);
}
```

**Updated Code:**
```java
import org.bson.Document;

@Converter
public static Document fromStringToDocument(String s) {
    return Document.parse(s);
}
```

### 6. Update Type Converter Registration

**Current File**: `src/main/resources/META-INF/services/org/apache/camel/TypeConverter`

**Content**: `org.apache.camel.component.mongodb.converters.MongoDbBasicConverters`

**Action**: Verify this file remains compatible with Camel 4.x type converter discovery mechanism.

---

## Migration Strategy

### Step 1: Update Build Configuration
1. Update Java compiler version to 17
2. Update Camel version to 4.8.0
3. Update MongoDB driver to 5.2.0
4. Update Jackson to 2.17.2
5. Update maven-compiler-plugin to 3.11.0

### Step 2: Refactor Core Classes
1. Replace all `org.apache.camel.impl.*` imports with `org.apache.camel.support.*`
2. Replace MongoDB legacy driver classes with new driver classes
3. Update all `DBObject` references to `Document`
4. Update all `DBCollection` references to `MongoCollection<Document>`
5. Update all `DB` references to `MongoDatabase`

### Step 3: Update Operations
1. Refactor CRUD operations to use new MongoDB driver API
2. Update query builders from `BasicDBObject` to `Document`
3. Update result handling from `WriteResult` to specific result types
4. Update cursor handling from `DBCursor` to `FindIterable<Document>`

### Step 4: Update Converters
1. Replace `DBObject` converters with `Document` converters
2. Update JSON parsing from `JSON.parse()` to `Document.parse()`
3. Update BSON handling to use new BSON codecs

### Step 5: Testing
1. Update test dependencies
2. Verify all operations work with new driver
3. Test with actual MongoDB instance
4. Validate SAP CPI integration

---

## Risk Assessment

### High Risk Areas:
1. **MongoDB Driver API Changes**: Complete rewrite of data access layer required
2. **Type Conversions**: All converters need updating
3. **Error Handling**: Exception types have changed
4. **Connection Management**: Connection pooling and lifecycle management differs

### Medium Risk Areas:
1. **Camel API Changes**: Well-documented migration path exists
2. **Java Version**: Standard upgrade process
3. **Jackson Updates**: Mostly backward compatible

### Low Risk Areas:
1. **Metadata Configuration**: Should remain compatible
2. **SAP ADK Plugin**: Version 1.25.0 should support newer Camel versions

---

## Testing Recommendations

1. **Unit Tests**: Create comprehensive unit tests for all operations
2. **Integration Tests**: Test with real MongoDB instance
3. **SAP CPI Tests**: Deploy to test tenant and validate
4. **Performance Tests**: Ensure no performance degradation
5. **Backward Compatibility**: Verify existing integrations continue to work

---

## Timeline Estimate

- **Analysis & Planning**: 1 day (Complete)
- **Code Refactoring**: 3-5 days
- **Testing**: 2-3 days
- **Documentation**: 1 day
- **Deployment & Validation**: 1-2 days

**Total**: 8-12 days

---

## References

### Migration Guides (Available via MCP Server):
1. APACHE CAMEL 2.X TO 3.0 MIGRATION GUIDE.docx
2. APACHE CAMEL 3.X TO 4.0 MIGRATION GUIDE.docx
3. APACHE CAMEL 3.X UPGRADE GUIDE.docx
4. APACHE CAMEL 4.X UPGRADE GUIDE.docx
5. 3326553 - Apache Camel Runtime 3.14.7 Upgrade.docx

### External Resources:
- Apache Camel 4.x Documentation: https://camel.apache.org/manual/camel-4x-upgrade-guide.html
- MongoDB Java Driver 5.x Documentation: https://www.mongodb.com/docs/drivers/java/sync/current/
- SAP Integration Suite Documentation: https://help.sap.com/docs/cloud-integration

---

## Conclusion

The upgrade from Apache Camel 2.17.4 to 4.x requires significant refactoring due to:
1. Complete MongoDB driver API overhaul (3.x → 5.x)
2. Camel framework API changes (impl → support package)
3. Java version upgrade (7 → 17)

However, the upgrade is essential for:
- Security updates and bug fixes
- Modern MongoDB features support
- Long-term maintainability
- SAP CPI platform compatibility

The recommended approach is a phased migration with thorough testing at each stage.
