package com.team27.amazon.product.adapter;

import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchHitAdapter {

    public Object adapt(SearchHit<?> hit) {
        return hit.getContent();
    }
}