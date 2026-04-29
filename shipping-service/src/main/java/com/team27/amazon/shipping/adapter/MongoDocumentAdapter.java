package com.team27.amazon.shipping.adapter;

import org.bson.Document;
import org.springframework.stereotype.Component;

@Component
public class MongoDocumentAdapter {

    public Object adapt(Document document) {
        return document;
    }
}