/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.mongodb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component for working with documents stored in MongoDB database.
 */
@UriEndpoint(scheme = "mongodb", title = "MongoDB", syntax = "mongodb:connectionBean", consumerClass = MongoDbTailableCursorConsumer.class, label = "database,nosql")
public class MongoDbEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDbEndpoint.class);

    private MongoClient mongoConnection;

    @UriPath @Metadata(required = "true")
    private String connectionBean;
    @UriParam
    private String database;
    @UriParam
    private String collection;
    @UriParam
    private String collectionIndex;
    @UriParam
    private MongoDbOperation operation;
    @UriParam(defaultValue = "true")
    private boolean createCollection = true;
    @UriParam(enums = "ACKNOWLEDGED,W1,W2,W3,UNACKNOWLEDGED,JOURNALED,MAJORITY")
    private WriteConcern writeConcern;
    private WriteConcern writeConcernRef;
    @UriParam
    private ReadPreference readPreference;
    @UriParam
    private boolean dynamicity;
    @UriParam
    private boolean writeResultAsHeader;
    // tailable cursor consumer by default
    private MongoDbConsumerType consumerType;
    @UriParam(defaultValue = "1000")
    private long cursorRegenerationDelay = 1000L;
    @UriParam
    private String tailTrackIncreasingField;

    // persistent tail tracking
    @UriParam
    private boolean persistentTailTracking;
    @UriParam
    private String persistentId;
    @UriParam
    private String tailTrackDb;
    @UriParam
    private String tailTrackCollection;
    @UriParam
    private String tailTrackField;
    private MongoDbTailTrackingConfig tailTrackingConfig;

    @UriParam
    private MongoDbOutputType outputType;

    private MongoCollection<Document> dbCollection;
    private MongoDatabase db;

    // ======= Constructors ===============================================

    public MongoDbEndpoint() {
    }

    public MongoDbEndpoint(String uri, MongoDbComponent component) {
        super(uri, component);
    }

    // ======= Implementation methods =====================================

    public Producer createProducer() throws Exception {
        validateOptions('P');
        initializeConnection();
        return new MongoDbProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        validateOptions('C');
        // we never create the collection
        createCollection = false;
        initializeConnection();

        // select right consumer type
        if (consumerType == null) {
            consumerType = MongoDbConsumerType.tailable;
        }

        Consumer consumer;
        if (consumerType == MongoDbConsumerType.tailable) {
            consumer = new MongoDbTailableCursorConsumer(this, processor);
        } else {
            throw new CamelMongoDbException("Consumer type not supported: " + consumerType);
        }

        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Check if outputType is compatible with operation. DBCursor and DocumentList applies to findAll. Document applies to others.
     */
    private void validateOutputType() {
        if (!ObjectHelper.isEmpty(outputType)) {
            if (MongoDbOutputType.DBObjectList.equals(outputType) && !(MongoDbOperation.findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType DBObjectList is only compatible with operation findAll");
            }
            if (MongoDbOutputType.DBCursor.equals(outputType) && !(MongoDbOperation.findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType DBCursor is only compatible with operation findAll");
            }
            if (MongoDbOutputType.DBObject.equals(outputType) && (MongoDbOperation.findAll.equals(operation))) {
                throw new IllegalArgumentException("outputType DBObject is not compatible with operation findAll");
            }
        }
    }

    private void validateOptions(char role) throws IllegalArgumentException {
        // make our best effort to validate, options with defaults are checked against their defaults
        if (role == 'P') {
            if (!ObjectHelper.isEmpty(consumerType) || persistentTailTracking || !ObjectHelper.isEmpty(tailTrackDb)
                    || !ObjectHelper.isEmpty(tailTrackCollection) || !ObjectHelper.isEmpty(tailTrackField) || cursorRegenerationDelay != 1000L) {
                throw new IllegalArgumentException("consumerType, tailTracking, cursorRegenerationDelay options cannot appear on a producer endpoint");
            }
        } else if (role == 'C') {
            if (!ObjectHelper.isEmpty(operation) || !ObjectHelper.isEmpty(writeConcern) || writeConcernRef != null
                   || dynamicity || outputType != null) {
                throw new IllegalArgumentException("operation, writeConcern, writeConcernRef, dynamicity, outputType "
                        + "options cannot appear on a consumer endpoint");
            }
            if (consumerType == MongoDbConsumerType.tailable) {
                if (tailTrackIncreasingField == null) {
                    throw new IllegalArgumentException("tailTrackIncreasingField option must be set for tailable cursor MongoDB consumer endpoint");
                }
                if (persistentTailTracking && (ObjectHelper.isEmpty(persistentId))) {
                    throw new IllegalArgumentException("persistentId is compulsory for persistent tail tracking");
                }
            }

        } else {
            throw new IllegalArgumentException("Unknown endpoint role");
        }
    }

    public boolean isSingleton() {
        return true;
    }

    /**
     * Initialises the MongoDB connection using the MongoClient object provided to the endpoint
     * 
     * @throws CamelMongoDbException
     */
    public void initializeConnection() throws CamelMongoDbException {
        LOG.info("Initialising MongoDb endpoint: {}", this.toString());
        if (database == null || (collection == null && !(MongoDbOperation.getDbStats.equals(operation) || MongoDbOperation.command.equals(operation)))) {
            throw new CamelMongoDbException("Missing required endpoint configuration: database and/or collection");
        }
        db = mongoConnection.getDatabase(database);
        if (db == null) {
            throw new CamelMongoDbException("Could not initialise MongoDbComponent. Database " + database + " does not exist.");
        }
        if (collection != null) {
            // Check if collection exists
            boolean collectionExists = false;
            for (String name : db.listCollectionNames()) {
                if (name.equals(collection)) {
                    collectionExists = true;
                    break;
                }
            }
            
            if (!createCollection && !collectionExists) {
                throw new CamelMongoDbException("Could not initialise MongoDbComponent. Collection " + collection + " and createCollection is false.");
            }
            
            if (!collectionExists && createCollection) {
                db.createCollection(collection);
            }
            
            dbCollection = db.getCollection(collection);

            LOG.debug("MongoDb component initialised and endpoint bound to MongoDB collection with the following parameters. Db: {}, Collection: {}",
                    db.getName(), dbCollection.getNamespace().getCollectionName());

            try {
                if (ObjectHelper.isNotEmpty(collectionIndex)) {
                    ensureIndex(dbCollection, createIndex());
                }
            } catch (Exception e) {
                throw new CamelMongoDbException("Error creating index", e);
            }
        }
    }

    /**
     * Add Index
     *
     * @param collection
     */
    public void ensureIndex(MongoCollection<Document> collection, List<Bson> dynamicIndex) {
        if (dynamicIndex != null && !dynamicIndex.isEmpty()) {
            for (Bson index : dynamicIndex) {
                LOG.debug("create Index {}", index);
                collection.createIndex(index, new IndexOptions());
            }
        }
    }

    /**
     * Create technical list index
     *
     * @return technical list index
     */
    @SuppressWarnings("unchecked")
    public List<Bson> createIndex() throws Exception {
        List<Bson> indexList = new ArrayList<>();

        if (ObjectHelper.isNotEmpty(collectionIndex)) {
            HashMap<String, String> indexMap = new ObjectMapper().readValue(collectionIndex, HashMap.class);

            for (Map.Entry<String, String> set : indexMap.entrySet()) {
                Document index = new Document();
                index.put(set.getKey(), set.getValue());
                indexList.add(index);
            }
        }
        return indexList;
    }

    /**
     * Applies validation logic specific to this endpoint type. If everything succeeds, continues initialization
     */
    @Override
    protected void doStart() throws Exception {
        if (writeConcern != null && writeConcernRef != null) {
            String msg = "Cannot set both writeConcern and writeConcernRef at the same time. Respective values: " + writeConcern
                    + ", " + writeConcernRef + ". Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }

        setWriteReadOptionsOnConnection();
        super.doStart();
    }

    public Exchange createMongoDbExchange(Document doc) {
        Exchange exchange = createExchange();
        Message message = exchange.getIn();
        message.setHeader(MongoDbConstants.DATABASE, database);
        message.setHeader(MongoDbConstants.COLLECTION, collection);
        message.setHeader(MongoDbConstants.FROM_TAILABLE, true);
        message.setBody(doc);
        return exchange;
    }

    private void setWriteReadOptionsOnConnection() {
        // Write and read preferences are now set per operation in MongoDB 4.x driver
        // This method is kept for compatibility but options are applied at operation level
    }
    
    
    // ======= Getters and setters ===============================================


    public String getConnectionBean() {
        return connectionBean;
    }

    /**
     * Name of {@link com.mongodb.client.MongoClient} to use.
     */
    public void setConnectionBean(String connectionBean) {
        this.connectionBean = connectionBean;
    }

    /**
     * Sets the name of the MongoDB collection to bind to this endpoint
     *
     * @param collection collection name
     */
    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String getCollection() {
        return collection;
    }

    /**
     * Sets the collection index (JSON FORMAT : { "field1" : order1, "field2" : order2})
     */
    public void setCollectionIndex(String collectionIndex) {
        this.collectionIndex = collectionIndex;
    }

    public String getCollectionIndex() {
        return collectionIndex;
    }

    /**
     * Sets the operation this endpoint will execute against MongoDB. For possible values, see {@link MongoDbOperation}.
     *
     * @param operation name of the operation as per catalogued values
     * @throws CamelMongoDbException
     */
    public void setOperation(String operation) throws CamelMongoDbException {
        try {
            this.operation = MongoDbOperation.valueOf(operation);
        } catch (IllegalArgumentException e) {
            throw new CamelMongoDbException("Operation not supported", e);
        }
    }

    public MongoDbOperation getOperation() {
        return operation;
    }

    /**
     * Sets the name of the MongoDB database to target
     * 
     * @param database name of the MongoDB database
     */
    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDatabase() {
        return database;
    }

    /**
     * Create collection during initialisation if it doesn't exist. Default is true.
     * 
     * @param createCollection true or false
     */
    public void setCreateCollection(boolean createCollection) {
        this.createCollection = createCollection;
    }

    public boolean isCreateCollection() {
        return createCollection;
    }

    public MongoDatabase getDb() {
        return db;
    }

    public MongoCollection<Document> getDbCollection() {
        return dbCollection;
    }

    /**
     * Sets the MongoClient instance that represents the backing connection
     * 
     * @param mongoConnection the connection to the database
     */
    public void setMongoConnection(MongoClient mongoConnection) {
        this.mongoConnection = mongoConnection;
    }

    public MongoClient getMongoConnection() {
        return mongoConnection;
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB using the standard ones.
     * Resolved from the fields of the WriteConcern class by calling the {@link WriteConcern#valueOf(String)} method.
     * 
     * @param writeConcern the standard name of the WriteConcern
     */
    public void setWriteConcern(String writeConcern) {
        this.writeConcern = WriteConcern.valueOf(writeConcern);
    }

    public WriteConcern getWriteConcern() {
        return writeConcern;
    }

    /**
     * Set the {@link WriteConcern} for write operations on MongoDB, passing in the bean ref to a custom WriteConcern which exists in the Registry.
     * 
     * @param writeConcernRef the name of the bean in the registry that represents the WriteConcern to use
     */
    public void setWriteConcernRef(String writeConcernRef) {
        WriteConcern wc = this.getCamelContext().getRegistry().lookupByNameAndType(writeConcernRef, WriteConcern.class);
        if (wc == null) {
            String msg = "Camel MongoDB component could not find the WriteConcern in the Registry. Verify that the "
                    + "provided bean name (" + writeConcernRef + ")  is correct. Aborting initialization.";
            throw new IllegalArgumentException(msg);
        }

        this.writeConcernRef = wc;
    }

    public WriteConcern getWriteConcernRef() {
        return writeConcernRef;
    }

    /** 
     * Sets a MongoDB {@link ReadPreference} on the Mongo connection.
     * 
     * @param readPreference the name of the read preference to set
     */
    public void setReadPreference(String readPreference) {
        this.readPreference = ReadPreference.valueOf(readPreference);
    }

    public ReadPreference getReadPreference() {
        return readPreference;
    }

    /**
     * Sets whether this endpoint will attempt to dynamically resolve the target database and collection from the incoming Exchange properties.
     * 
     * @param dynamicity true or false indicated whether target database and collection should be calculated dynamically
     */
    public void setDynamicity(boolean dynamicity) {
        this.dynamicity = dynamicity;
    }

    public boolean isDynamicity() {
        return dynamicity;
    }

    /**
     * Reserved for future use, when more consumer types are supported.
     *
     * @param consumerType key of the consumer type
     * @throws CamelMongoDbException
     */
    public void setConsumerType(String consumerType) throws CamelMongoDbException {
        try {
            this.consumerType = MongoDbConsumerType.valueOf(consumerType);
        } catch (IllegalArgumentException e) {
            throw new CamelMongoDbException("Consumer type not supported", e);
        }
    }

    public MongoDbConsumerType getConsumerType() {
        return consumerType;
    }

    public String getTailTrackDb() {
        return tailTrackDb;
    }

    /**
     * Indicates what database the tail tracking mechanism will persist to.
     * 
     * @param tailTrackDb database name
     */
    public void setTailTrackDb(String tailTrackDb) {
        this.tailTrackDb = tailTrackDb;
    }

    public String getTailTrackCollection() {
        return tailTrackCollection;
    }

    /**
     * Collection where tail tracking information will be persisted.
     * 
     * @param tailTrackCollection collection name
     */
    public void setTailTrackCollection(String tailTrackCollection) {
        this.tailTrackCollection = tailTrackCollection;
    }

    public String getTailTrackField() {
        return tailTrackField;
    }

    /**
     * Field where the last tracked value will be placed.
     * 
     * @param tailTrackField field name
     */
    public void setTailTrackField(String tailTrackField) {
        this.tailTrackField = tailTrackField;
    }

    /**
     * Enable persistent tail tracking.
     * 
     * @param persistentTailTracking true or false
     */
    public void setPersistentTailTracking(boolean persistentTailTracking) {
        this.persistentTailTracking = persistentTailTracking;
    }

    public boolean isPersistentTailTracking() {
        return persistentTailTracking;
    }

    /**
     * Correlation field in the incoming record which is of increasing nature.
     * 
     * @param tailTrackIncreasingField field name
     */
    public void setTailTrackIncreasingField(String tailTrackIncreasingField) {
        this.tailTrackIncreasingField = tailTrackIncreasingField;
    }

    public String getTailTrackIncreasingField() {
        return tailTrackIncreasingField;
    }

    public MongoDbTailTrackingConfig getTailTrackingConfig() {
        if (tailTrackingConfig == null) {
            tailTrackingConfig = new MongoDbTailTrackingConfig(persistentTailTracking, tailTrackIncreasingField, tailTrackDb == null ? database : tailTrackDb, tailTrackCollection,
                    tailTrackField, getPersistentId());
        }
        return tailTrackingConfig;
    }

    /**
     * MongoDB tailable cursors will block until new data arrives.
     * 
     * @param cursorRegenerationDelay delay specified in milliseconds
     */
    public void setCursorRegenerationDelay(long cursorRegenerationDelay) {
        this.cursorRegenerationDelay = cursorRegenerationDelay;
    }

    public long getCursorRegenerationDelay() {
        return cursorRegenerationDelay;
    }

    /**
     * One tail tracking collection can host many trackers for several tailable consumers.
     * 
     * @param persistentId the value of the persistent ID to use for this tailable consumer
     */
    public void setPersistentId(String persistentId) {
        this.persistentId = persistentId;
    }

    public String getPersistentId() {
        return persistentId;
    }

    public boolean isWriteResultAsHeader() {
        return writeResultAsHeader;
    }

    /**
     * In write operations, determines whether to return result as header.
     * 
     * @param writeResultAsHeader flag to indicate if this option is enabled
     */
    public void setWriteResultAsHeader(boolean writeResultAsHeader) {
        this.writeResultAsHeader = writeResultAsHeader;
    }

    public MongoDbOutputType getOutputType() {
        return outputType;
    }

    /**
     * Convert the output of the producer to the selected type.
     * 
     * @param outputType output type
     */
    public void setOutputType(MongoDbOutputType outputType) {
        this.outputType = outputType;
    }
}
