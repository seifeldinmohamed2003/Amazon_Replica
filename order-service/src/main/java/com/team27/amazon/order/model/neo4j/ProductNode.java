package com.team27.amazon.order.model.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Product")
public class ProductNode {

    @Id
    private Long productId;

    private String name;
    private String category;

    @Relationship(type = "BOUGHT_TOGETHER", direction = Relationship.Direction.OUTGOING)
    private Set<BoughtTogetherRelationship> boughtTogether = new HashSet<>();

    public ProductNode() {}

    public ProductNode(Long productId, String name, String category) {
        this.productId = productId;
        this.name = name;
        this.category = category;
    }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Set<BoughtTogetherRelationship> getBoughtTogether() { return boughtTogether; }
    public void setBoughtTogether(Set<BoughtTogetherRelationship> boughtTogether) { this.boughtTogether = boughtTogether; }
}