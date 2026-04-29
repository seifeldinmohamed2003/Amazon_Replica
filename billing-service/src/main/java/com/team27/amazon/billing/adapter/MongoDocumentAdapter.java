package com.team27.amazon.billing.adapter;

import com.team27.amazon.billing.dto.LifecycleEventDTO;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MongoDocumentAdapter {

    public LifecycleEventDTO adapt(Document doc, String source) {
        LifecycleEventDTO dto = new LifecycleEventDTO();
        dto.setSource(source);
        dto.setAction(doc.getString("action"));

        Object ts = doc.get("timestamp");
        if (ts != null) dto.setTimestamp(LocalDateTime.parse(ts.toString()));
        Object details = doc.get("details");
        if (details instanceof Document) {
            dto.setDetails(((Document) details));
        }
        return dto;
    }
}