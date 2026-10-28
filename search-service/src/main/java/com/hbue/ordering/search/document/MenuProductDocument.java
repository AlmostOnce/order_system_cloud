package com.hbue.ordering.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;
import java.math.BigDecimal;

/** Elasticsearch 中的一份可售菜品快照；ID 由窗口和菜品 ID 组成。 */
@Document(indexName = "menu-products-v1", createIndex = false)
@Setting(shards = 1, replicas = 0)
public class MenuProductDocument {
    @Id private String id;
    @Field(type = FieldType.Long) private Long productId;
    @Field(type = FieldType.Long) private Long categoryId;
    @Field(type = FieldType.Long) private Long windowId;
    @Field(type = FieldType.Long) private Long restaurantId;
    @Field(type = FieldType.Text, analyzer = "cjk") private String restaurantName;
    @Field(type = FieldType.Keyword) private String restaurantLocation;
    @Field(type = FieldType.Text, analyzer = "cjk") private String windowName;
    @Field(type = FieldType.Text, analyzer = "cjk") private String categoryName;
    @Field(type = FieldType.Search_As_You_Type, analyzer = "cjk") private String productName;
    @Field(type = FieldType.Text, analyzer = "cjk") private String description;
    @Field(type = FieldType.Keyword, index = false) private String imageUrl;
    @Field(type = FieldType.Double) private BigDecimal price;
    @Field(type = FieldType.Integer) private Integer sortOrder;

    public MenuProductDocument() {}
    public String getId() { return id; } public void setId(String id) { this.id = id; }
    public Long getProductId() { return productId; } public void setProductId(Long v) { productId = v; }
    public Long getCategoryId() { return categoryId; } public void setCategoryId(Long v) { categoryId = v; }
    public Long getWindowId() { return windowId; } public void setWindowId(Long v) { windowId = v; }
    public Long getRestaurantId() { return restaurantId; } public void setRestaurantId(Long v) { restaurantId = v; }
    public String getRestaurantName() { return restaurantName; } public void setRestaurantName(String v) { restaurantName = v; }
    public String getRestaurantLocation() { return restaurantLocation; } public void setRestaurantLocation(String v) { restaurantLocation = v; }
    public String getWindowName() { return windowName; } public void setWindowName(String v) { windowName = v; }
    public String getCategoryName() { return categoryName; } public void setCategoryName(String v) { categoryName = v; }
    public String getProductName() { return productName; } public void setProductName(String v) { productName = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public String getImageUrl() { return imageUrl; } public void setImageUrl(String v) { imageUrl = v; }
    public BigDecimal getPrice() { return price; } public void setPrice(BigDecimal v) { price = v; }
    public Integer getSortOrder() { return sortOrder; } public void setSortOrder(Integer v) { sortOrder = v; }
}
