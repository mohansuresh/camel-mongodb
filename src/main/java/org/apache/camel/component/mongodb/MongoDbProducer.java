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
import java.util.List;

import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.TypeConverter;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.ObjectHelper;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The MongoDb producer.
 */
public class MongoDbProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbProducer.class);
    private MongoDbEndpoint endpoint;

    public MongoDbProducer(MongoDbEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        MongoDbOperation operation = endpoint.getOperation();
        Object header = exchange.getIn().getHeader(MongoDbConstants.OPERATION_HEADER);
        if (header != null) {
            LOG.debug("Overriding default operation with operation specified on header: {}", header);
            try {
                if (header instanceof MongoDbOperation) {
                    operation = ObjectHelper.cast(MongoDbOperation.class, header);
                } else {
                    // evaluate as a String
                    operation = MongoDbOperation.valueOf(exchange.getIn().getHeader(MongoDbConstants.OPERATION_HEADER, String.class));
                }
            } catch (Exception e) {
                throw new CamelMongoDbException("Operation specified on header is not supported. Value: " + header, e);
            }
        }

        try {
            invokeOperation(operation, exchange);
        } catch (Exception e) {
            throw MongoDbComponent.wrapInCamelMongoDbException(e);
        }

    }

    /**
     * Entry method that selects the appropriate MongoDB operation and executes it
     * 
     * @param operation
     * @param exchange
     * @throws Exception
     */
    protected void invokeOperation(MongoDbOperation operation, Exchange exchange) throws Exception {
        switch (operation) {
        case count:
            doCount(exchange);
            break;

        case findOneByQuery:
            doFindOneByQuery(exchange);
            break;

        case findById:
            doFindById(exchange);
            break;

        case findAll:
            doFindAll(exchange);
            break;

        case insert:
            doInsert(exchange);
            break;

        case save:
            doSave(exchange);
            break;

        case update:
            doUpdate(exchange);
            break;

        case remove:
            doRemove(exchange);
            break;
        
        case aggregate:
            doAggregate(exchange);
            break;
        
        case getDbStats:
            doGetStats(exchange, MongoDbOperation.getDbStats);
            break;

        case getColStats:
            doGetStats(exchange, MongoDbOperation.getColStats);
            break;
        case command:
            doCommand(exchange);
            break;
        default:
            throw new CamelMongoDbException("Operation not supported. Value: " + operation);
        }
    }

    // ----------- MongoDB operations ----------------

    protected void doCommand(Exchange exchange) throws Exception {
        Document result = null;
        MongoDatabase db = calculateDb(exchange);
        Document cmdObj = exchange.getIn().getMandatoryBody(Document.class);

        // Execute command with read preference if specified
        ReadPreference readPref = endpoint.getReadPreference();
        if (readPref != null) {
            result = db.runCommand(cmdObj, readPref);
        } else {
            result = db.runCommand(cmdObj);
        }

        Message responseMessage = prepareResponseMessage(exchange, MongoDbOperation.command);
        responseMessage.setBody(result);
    }

    protected void doGetStats(Exchange exchange, MongoDbOperation operation) throws Exception {
        Document result = null;

        if (operation == MongoDbOperation.getColStats) {
            MongoCollection<Document> collection = calculateCollection(exchange);
            // Collection stats using collStats command
            Document command = new Document("collStats", collection.getNamespace().getCollectionName());
            result = collection.getNamespace().getDatabaseName() != null 
                ? endpoint.getMongoConnection().getDatabase(collection.getNamespace().getDatabaseName()).runCommand(command)
                : endpoint.getDb().runCommand(command);
        } else if (operation == MongoDbOperation.getDbStats) {
            MongoDatabase db = calculateDb(exchange);
            Document command = new Document("dbStats", 1);
            result = db.runCommand(command);
        } else {
            throw new CamelMongoDbException("Internal error: wrong operation for getStats variant" + operation);
        }

        Message responseMessage = prepareResponseMessage(exchange, operation);
        responseMessage.setBody(result);
    }

    protected void doRemove(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Document removeObj = exchange.getIn().getMandatoryBody(Document.class);

        WriteConcern wc = extractWriteConcern(exchange);
        DeleteResult result;
        if (wc != null) {
            result = dbCol.withWriteConcern(wc).deleteMany(removeObj);
        } else {
            result = dbCol.deleteMany(removeObj);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.remove);
        processAndTransferWriteResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getDeletedCount());
    }

    @SuppressWarnings("unchecked")
    protected void doUpdate(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        List<Document> saveObj = exchange.getIn().getMandatoryBody((Class<List<Document>>)(Class<?>)List.class);
        if (saveObj.size() != 2) {
            throw new CamelMongoDbException("MongoDB operation = update, failed because body is not a List of Document objects with size = 2");
        }

        Document updateCriteria = saveObj.get(0);
        Document objNew = saveObj.get(1);

        Boolean multi = exchange.getIn().getHeader(MongoDbConstants.MULTIUPDATE, Boolean.class);
        Boolean upsert = exchange.getIn().getHeader(MongoDbConstants.UPSERT, Boolean.class);

        UpdateResult result;
        WriteConcern wc = extractWriteConcern(exchange);
        UpdateOptions options = new UpdateOptions()
                .upsert(calculateBooleanValue(upsert));

        MongoCollection<Document> collection = wc == null ? dbCol : dbCol.withWriteConcern(wc);
        
        if (calculateBooleanValue(multi)) {
            result = collection.updateMany(updateCriteria, objNew, options);
        } else {
            result = collection.updateOne(updateCriteria, objNew, options);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.update);
        processAndTransferWriteResult(result, exchange);
        resultMessage.setHeader(MongoDbConstants.RECORDS_AFFECTED, result.getModifiedCount());
    }

    protected void doSave(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Document saveObj = exchange.getIn().getMandatoryBody(Document.class);

        WriteConcern wc = extractWriteConcern(exchange);
        
        // In MongoDB 4.x, save is deprecated. Use replaceOne with upsert=true
        Document filter = new Document("_id", saveObj.get("_id"));
        UpdateOptions options = new UpdateOptions().upsert(true);
        
        UpdateResult result;
        if (wc != null) {
            result = dbCol.withWriteConcern(wc).replaceOne(filter, saveObj, options);
        } else {
            result = dbCol.replaceOne(filter, saveObj, options);
        }
        
        exchange.getIn().setHeader(MongoDbConstants.OID, saveObj.get("_id"));

        prepareResponseMessage(exchange, MongoDbOperation.save);
        processAndTransferWriteResult(result, exchange);
    }

    protected void doFindById(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Object id = exchange.getIn().getMandatoryBody();
        Document ret;

        Document fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, Document.class);
        Document query = new Document("_id", id);
        
        if (fieldFilter == null) {
            ret = dbCol.find(query).first();
        } else {
            ret = dbCol.find(query).projection(fieldFilter).first();
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findById);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void doInsert(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        boolean singleInsert = true;
        Object insert = exchange.getIn().getBody(String.class);
        
        // body could not be converted to Document, check to see if it's of type List<Document>
        if (insert == null) {
            insert = exchange.getIn().getBody(List.class);
            // if the body of type List was obtained, ensure that all items are of type Document
            if (insert != null) {
                singleInsert = false;
                insert = attemptConvertToList((List)insert, exchange);
            } else {
                throw new CamelMongoDbException("MongoDB operation = insert, Body is not conversible to type Document nor List<Document>");
            }
        }

        WriteConcern wc = extractWriteConcern(exchange);
        MongoCollection<Document> collection = wc == null ? dbCol : dbCol.withWriteConcern(wc);
        
        if (singleInsert) {
            Document insertObject = new Document();
            insertObject.put("message", insert);
            InsertOneResult result = collection.insertOne(insertObject);
            exchange.getIn().setHeader(MongoDbConstants.OID, insertObject.get("_id"));
        } else {
            List<Document> insertObjects = (List<Document>) insert;
            collection.insertMany(insertObjects);
            List<Object> oids = new ArrayList<>(insertObjects.size());
            for (Document insertObject : insertObjects) {
                oids.add(insertObject.get("_id"));
            }
            exchange.getIn().setHeader(MongoDbConstants.OID, oids);
        }

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.insert);
        resultMessage.setBody("Insert successful");
    }

    protected void doFindAll(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        
        // do not use getMandatoryBody, because if the body is empty we want to retrieve all objects in the collection
        Document query = null;
        if (exchange.getIn().getBody() != null) {
            query = exchange.getIn().getBody(Document.class);
        }
        if (query == null) {
            query = new Document();
        }
        
        Document fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, Document.class);

        // get the batch size and number to skip
        Integer batchSize = exchange.getIn().getHeader(MongoDbConstants.BATCH_SIZE, Integer.class);
        Integer numToSkip = exchange.getIn().getHeader(MongoDbConstants.NUM_TO_SKIP, Integer.class);
        Integer limit = exchange.getIn().getHeader(MongoDbConstants.LIMIT, Integer.class);
        Document sortBy = exchange.getIn().getHeader(MongoDbConstants.SORT_BY, Document.class);
        
        FindIterable<Document> ret = null;
        try {
            if (fieldFilter == null) {
                ret = dbCol.find(query);
            } else {
                ret = dbCol.find(query).projection(fieldFilter);
            }

            if (sortBy != null) {
                ret = ret.sort(sortBy);
            }

            if (batchSize != null) {
                ret = ret.batchSize(batchSize.intValue());
            }

            if (numToSkip != null) {
                ret = ret.skip(numToSkip.intValue());
            }

            if (limit != null) {
                ret = ret.limit(limit.intValue());
            }

            Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findAll);
            if (MongoDbOutputType.DBCursor.equals(endpoint.getOutputType())) {
                resultMessage.setBody(ret.iterator());
            } else {
                List<Document> resultList = new ArrayList<>();
                ret.into(resultList);
                resultMessage.setBody(resultList);
                resultMessage.setHeader(MongoDbConstants.RESULT_PAGE_SIZE, resultList.size());
            }
        } catch (Exception e) {
            throw new CamelMongoDbException("Error in findAll operation", e);
        }
    }

    protected void doFindOneByQuery(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Document query = exchange.getIn().getMandatoryBody(Document.class);
        Document ret;

        Document sortBy = exchange.getIn().getHeader(MongoDbConstants.SORT_BY, Document.class);
        Document fieldFilter = exchange.getIn().getHeader(MongoDbConstants.FIELDS_FILTER, Document.class);

        FindIterable<Document> iterable = dbCol.find(query);
        
        if (fieldFilter != null) {
            iterable = iterable.projection(fieldFilter);
        }
        
        if (sortBy != null) {
            iterable = iterable.sort(sortBy);
        }
        
        ret = iterable.first();
        
        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.findOneByQuery);
        resultMessage.setBody(ret);
        resultMessage.setHeader(MongoDbConstants.RESULT_TOTAL_SIZE, ret == null ? 0 : 1);
    }

    protected void doCount(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Document query = exchange.getIn().getBody(Document.class);
        Long answer;
        if (query == null) {
            answer = dbCol.countDocuments();
        } else {
            answer = dbCol.countDocuments(query);
        }
        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.count);
        resultMessage.setBody(answer);
    }
    
    /**
    * All headers except collection and database are not available for this operation.
    * 
    * @param exchange
    * @throws Exception
    */
    protected void doAggregate(Exchange exchange) throws Exception {
        MongoCollection<Document> dbCol = calculateCollection(exchange);
        Object body = exchange.getIn().getMandatoryBody();

        List<Document> pipeline = new ArrayList<>();
        
        // Allow body to be a pipeline
        if (body instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> bodyList = (List<Object>) body;
            for (Object item : bodyList) {
                if (item instanceof Document) {
                    pipeline.add((Document) item);
                } else {
                    throw new CamelMongoDbException("Aggregation pipeline must contain Document objects");
                }
            }
        } else if (body instanceof Document) {
            pipeline.add((Document) body);
        } else {
            throw new CamelMongoDbException("Body must be a Document or List<Document> for aggregation");
        }

        AggregateIterable<Document> aggregationResult = dbCol.aggregate(pipeline);
        
        List<Document> results = new ArrayList<>();
        aggregationResult.into(results);

        Message resultMessage = prepareResponseMessage(exchange, MongoDbOperation.aggregate);
        resultMessage.setBody(results);
    }
    
    // --------- Convenience methods -----------------------
    private MongoDatabase calculateDb(Exchange exchange) throws Exception {
        // dynamic calculation is an option
        if (!endpoint.isDynamicity()) {
            return endpoint.getDb();
        }

        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        MongoDatabase db = null;

        if (dynamicDB == null) {
            db = endpoint.getDb();
        } else {
            db = endpoint.getMongoConnection().getDatabase(dynamicDB);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database selected: {}", db.getName());
        }
        return db;
    }

    private MongoCollection<Document> calculateCollection(Exchange exchange) throws Exception {
        // dynamic calculation is an option
        if (!endpoint.isDynamicity()) {
            return endpoint.getDbCollection();
        }
        
        String dynamicDB = exchange.getIn().getHeader(MongoDbConstants.DATABASE, String.class);
        String dynamicCollection = exchange.getIn().getHeader(MongoDbConstants.COLLECTION, String.class);
                
        @SuppressWarnings("unchecked")
        List<Bson> dynamicIndex = exchange.getIn().getHeader(MongoDbConstants.COLLECTION_INDEX, List.class);

        MongoCollection<Document> dbCol = null;
        
        if (dynamicDB == null && dynamicCollection == null) {
            dbCol = endpoint.getDbCollection();
        } else {
            MongoDatabase db = calculateDb(exchange);

            if (dynamicCollection == null) {
                dbCol = db.getCollection(endpoint.getCollection());
            } else {
                dbCol = db.getCollection(dynamicCollection);

                // on the fly add index
                if (dynamicIndex == null) {
                    endpoint.ensureIndex(dbCol, endpoint.createIndex());
                } else {
                    endpoint.ensureIndex(dbCol, dynamicIndex);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dynamic database and/or collection selected: {}->{}", 
                    dbCol.getNamespace().getDatabaseName(), 
                    dbCol.getNamespace().getCollectionName());
        }
        return dbCol;
    }
    
    private boolean calculateBooleanValue(Boolean b) {
        return b != null && b.booleanValue();      
    }
    
    private void processAndTransferWriteResult(Object result, Exchange exchange) {
        // determine where to set the result: as the OUT body or as an IN message header
        if (endpoint.isWriteResultAsHeader()) {
            exchange.getMessage().setHeader(MongoDbConstants.WRITERESULT, result);
        } else {
            exchange.getMessage().setBody(result);
        }
    }

    private WriteConcern extractWriteConcern(Exchange exchange) throws CamelMongoDbException {
        Object o = exchange.getIn().getHeader(MongoDbConstants.WRITECONCERN);

        if (o == null) {
            return null;
        } else if (o instanceof WriteConcern) {
            return ObjectHelper.cast(WriteConcern.class, o);
        } else if (o instanceof String) {
            WriteConcern answer = WriteConcern.valueOf(ObjectHelper.cast(String.class, o));
            if (answer == null) {
                throw new CamelMongoDbException("WriteConcern specified in the " + MongoDbConstants.WRITECONCERN + " header, with value " + o
                                                + " could not be resolved to a WriteConcern type");
            }
            return answer;
        }

        // should never get here
        LOG.warn("A problem occurred while resolving the Exchange's Write Concern");
        return null;
    }

    @SuppressWarnings("rawtypes")
    private List<Document> attemptConvertToList(List insertList, Exchange exchange) throws CamelMongoDbException {
        List<Document> documentList = new ArrayList<>(insertList.size());
        TypeConverter converter = exchange.getContext().getTypeConverter();
        for (Object item : insertList) {
            try {
                Document document = converter.mandatoryConvertTo(Document.class, item);
                documentList.add(document);
            } catch (Exception e) {
                throw new CamelMongoDbException("MongoDB operation = insert, Assuming List variant of MongoDB insert operation, but List contains non-Document items", e);
            }
        }
        return documentList;
    }

    private Message prepareResponseMessage(Exchange exchange, MongoDbOperation operation) {
        Message answer = exchange.getMessage();
        MessageHelper.copyHeaders(exchange.getIn(), answer, false);
        if (isWriteOperation(operation) && endpoint.isWriteResultAsHeader()) {
            answer.setBody(exchange.getIn().getBody());
        }
        return answer;
    }

    private boolean isWriteOperation(MongoDbOperation operation) {
        return MongoDbComponent.WRITE_OPERATIONS.contains(operation);
    }

}
