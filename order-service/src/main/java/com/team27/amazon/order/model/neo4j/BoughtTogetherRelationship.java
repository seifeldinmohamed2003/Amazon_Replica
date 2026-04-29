package com.team27.amazon.order.model.neo4j;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RelationshipProperties
public class BoughtTogetherRelationship {

    @RelationshipId
    @GeneratedValue
    private Long id;

    @TargetNode
    private ProductNode targetProduct;

    private Integer coPurchaseCount;
    private LocalDateTime lastCoPurchaseDate;

    @Property("recorded_order_ids")
    private List<Long> recordedOrderIds = new ArrayList<>();

    public BoughtTogetherRelationship() {}

    public BoughtTogetherRelationship(ProductNode targetProduct, Integer coPurchaseCount,
                                      LocalDateTime lastCoPurchaseDate, List<Long> recordedOrderIds) {
        this.targetProduct = targetProduct;
        this.coPurchaseCount = coPurchaseCount;
        this.lastCoPurchaseDate = lastCoPurchaseDate;
        this.recordedOrderIds = recordedOrderIds;
    }

    public Long getId() { return id; }

    public ProductNode getTargetProduct() { return targetProduct; }
    public void setTargetProduct(ProductNode targetProduct) { this.targetProduct = targetProduct; }

    public Integer getCoPurchaseCount() { return coPurchaseCount; }
    public void setCoPurchaseCount(Integer coPurchaseCount) { this.coPurchaseCount = coPurchaseCount; }

    public LocalDateTime getLastCoPurchaseDate() { return lastCoPurchaseDate; }
    public void setLastCoPurchaseDate(LocalDateTime lastCoPurchaseDate) { this.lastCoPurchaseDate = lastCoPurchaseDate; }

    public List<Long> getRecordedOrderIds() { return recordedOrderIds; }
    public void setRecordedOrderIds(List<Long> recordedOrderIds) { this.recordedOrderIds = recordedOrderIds; }
}