package com.team27.amazon.order.adapter;

import org.neo4j.driver.Record;
import org.springframework.stereotype.Component;

@Component
public class Neo4jRecordAdapter {

    public Object adapt(Record record) {
        return record;
    }
}