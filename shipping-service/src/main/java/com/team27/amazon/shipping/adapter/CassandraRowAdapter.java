package com.team27.amazon.shipping.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public Object adapt(Row row) {
        return row;
    }
}