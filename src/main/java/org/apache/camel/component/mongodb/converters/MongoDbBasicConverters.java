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
package org.apache.camel.component.mongodb.converters;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.support.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter
public final class MongoDbBasicConverters {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbBasicConverters.class);

    // Jackson's ObjectMapper is thread-safe, so no need to create a pool nor synchronize access to it
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MongoDbBasicConverters() {
    }
    
    @Converter
    public static Document fromMapToDocument(Map<?, ?> map) {
        return new Document(map);
    }
    
    @Converter
    public static Map<String, Object> fromDocumentToMap(Document document) {
        return document;
    }
    
    @Converter
    public static Document fromStringToDocument(String s) {
        Document answer = null;
        try {
            answer = Document.parse(s);
        } catch (Exception e) {
            LOG.warn("String -> Document conversion selected, but the following exception occurred. Returning null.", e);
        }
        
        return answer;
    }
   
    @Converter
    public static Document fromFileToDocument(File f, Exchange exchange) throws FileNotFoundException {
        return fromInputStreamToDocument(new FileInputStream(f), exchange);
    }
    
    @Converter
    public static Document fromInputStreamToDocument(InputStream is, Exchange exchange) {
        Document answer = null;
        try {
            byte[] input = IOConverter.toBytes(is);
            String jsonString = IOConverter.toString(input, exchange);
            answer = Document.parse(jsonString);
        } catch (Exception e) {
            LOG.warn("InputStream -> Document conversion selected, but the following exception occurred. Returning null.", e);
        } finally {
            // we need to make sure to close the input stream
            IOHelper.close(is, "InputStream", LOG);
        }
        return answer;
    }

    @Converter
    public static Document fromAnyObjectToDocument(Object value) {
        Document answer;
        try {
            Map<?, ?> m = OBJECT_MAPPER.convertValue(value, Map.class);
            answer = new Document(m);
        } catch (Exception e) {
            LOG.warn("Conversion has fallen back to generic Object -> Document, but unable to convert type {}. Returning null.", 
                    value.getClass().getCanonicalName());
            return null;
        }
        return answer;
    }
    
}
