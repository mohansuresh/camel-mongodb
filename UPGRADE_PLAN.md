# SAP CPI MongoDB Adapter - Upgrade Plan

## Executive Summary

This document outlines the upgrade plan for the MongoDB adapter from Apache Camel 2.17.4 to 3.22.x (LTS).

## Current Configuration

- **Camel Runtime**: 2.17.4
- **Java Version**: 1.7
- **MongoDB Driver**: 3.2.2 (mongo-java-driver)
- **Jackson**: 2.7.8
- **Component Version**: 2.17.4

## Target Configuration

- **Camel Runtime**: 3.22.2 (Latest LTS)
- **Java Version**: 11 (minimum for Camel 3.x)
- **MongoDB Driver**: 4.11.1 (Latest stable)
- **Jackson**: 2.15.3
- **Component Version**: 3.22.2

## Major Changes Required

### 1. POM.xml Updates
- Upgrade Camel version from 2.17.4 to 3.22.2
- Upgrade MongoDB driver from 3.2.2 to 4.11.1
- Upgrade Jackson from 2.7.8 to 2.15.3
- Update Java compiler from 1.7 to 11
- Add required JAXB dependencies for Java 11

### 2. MongoDB Driver Migration
The MongoDB Java driver has undergone major changes:

**Deprecated (2.x/3.x driver):**
- `Mongo` → `MongoClient`
- `DB` → `MongoDatabase`
- `DBCollection` → `MongoCollection`
- `DBObject` → `Document` or `Bson`
- `BasicDBObject` → `Document`
- `DBCursor` → `FindIterable`
- `WriteResult` → `UpdateResult`, `DeleteResult`, `InsertOneResult`

### 3. Camel API Migration

**Package Changes:**
- `org.apache.camel.impl.UriEndpointComponent` → `org.apache.camel.support.DefaultComponent`
- `org.apache.camel.impl.DefaultEndpoint` → `org.apache.camel.support.DefaultEndpoint`
- `org.apache.camel.impl.DefaultProducer` → `org.apache.camel.support.DefaultProducer`

**API Changes:**
- Component lifecycle methods no longer throw checked exceptions
- `getOut()` deprecated in favor of `getMessage()`
- Registry API changes: `put()` → `bind()`

### 4. Code Structure Changes

**MongoDbComponent.java:**
- Update to use new MongoDB 4.x driver APIs
- Remove hardcoded connection string
- Implement proper connection management
- Update to extend `DefaultComponent`

**MongoDbEndpoint.java:**
- Migrate from `DB`/`DBCollection` to `MongoDatabase`/`MongoCollection`
- Update all MongoDB operations to use new driver APIs
- Change from `DefaultEndpoint` to proper base class

**MongoDbProducer.java:**
- Update all CRUD operations to use new MongoDB 4.x APIs
- Replace `DBObject` with `Document`
- Update `WriteResult` handling
- Change from `DefaultProducer` to proper base class

### 5. Metadata Updates
- Update metadata.xml to reflect Camel 3.22.2
- Update component version information

## Implementation Steps

1. **Phase 1: POM Updates**
   - Update all dependency versions
   - Add JAXB dependencies for Java 11
   - Update compiler plugin configuration

2. **Phase 2: MongoDB Driver Migration**
   - Replace all deprecated MongoDB APIs
   - Update connection management
   - Migrate CRUD operations

3. **Phase 3: Camel API Migration**
   - Update package imports
   - Migrate to new base classes
   - Update lifecycle methods

4. **Phase 4: Testing**
   - Unit test updates
   - Integration testing
   - Compatibility verification

5. **Phase 5: Documentation**
   - Update README
   - Document breaking changes
   - Provide migration guide

## Breaking Changes

1. **MongoDB Connection**: Connection string must now be provided via endpoint URI or configuration
2. **API Changes**: Some method signatures have changed
3. **Java 11 Required**: Minimum Java version is now 11
4. **Package Changes**: Import statements need updates

## Benefits

1. **Security**: Latest versions with security patches
2. **Performance**: Improved MongoDB driver performance
3. **Compatibility**: Compatible with latest SAP CPI runtimes
4. **Support**: Active support and maintenance
5. **Features**: Access to new Camel 3.x features

## Risks and Mitigation

| Risk | Impact | Mitigation |
|------|--------|------------|
| Breaking API changes | High | Comprehensive testing, backward compatibility layer |
| MongoDB driver behavior changes | Medium | Thorough testing of all operations |
| Java 11 compatibility | Medium | Test on target runtime environment |
| Performance regression | Low | Performance testing and benchmarking |

## Timeline

- **Phase 1-2**: 2-3 days
- **Phase 3**: 2-3 days  
- **Phase 4**: 3-4 days
- **Phase 5**: 1 day

**Total Estimated Time**: 8-11 days

## Rollback Plan

1. Maintain separate branch for Camel 2.x version
2. Tag current version before upgrade
3. Document rollback procedures
4. Keep old artifacts in version control
