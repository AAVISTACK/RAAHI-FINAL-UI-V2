package in.raahi.backend.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "shop_products")
public class ShopProduct {

    @Id
    @GeneratedValue
    private UUID id;

    private String name;
    private String category;
    private BigDecimal price;

    @Column(name = "discount_price")
    private BigDecimal discountPrice;

    private String asin;
    private String brand;
    private BigDecimal rating;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Lob
    private String description;

    private boolean active = true;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getDiscountPrice() { return discountPrice; }
    public String getAsin() { return asin; }
    public String getBrand() { return brand; }
    public BigDecimal getRating() { return rating; }
    public Integer getReviewCount() { return reviewCount; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
}
