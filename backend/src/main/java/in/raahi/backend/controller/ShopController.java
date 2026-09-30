package in.raahi.backend.controller;

import in.raahi.backend.dto.ApiResponse;
import in.raahi.backend.dto.CommerceDtos.ShopProductDto;
import in.raahi.backend.entity.ShopProduct;
import in.raahi.backend.repository.ShopProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/shop")
public class ShopController {

    private final ShopProductRepository productRepository;
    private final String affiliateTag;

    public ShopController(ShopProductRepository productRepository,
                           @Value("${raahi.shop.amazon-affiliate-tag:}") String affiliateTag) {
        this.productRepository = productRepository;
        this.affiliateTag = affiliateTag;
    }

    @GetMapping("/products")
    public ApiResponse<List<ShopProductDto>> products(@RequestParam(required = false) String category) {
        List<ShopProduct> products = (category == null || category.isBlank())
                ? productRepository.findByActiveTrue()
                : productRepository.findByActiveTrueAndCategory(category);
        return ApiResponse.ok(products.stream().map(this::toDto).collect(Collectors.toList()));
    }

    private ShopProductDto toDto(ShopProduct p) {
        ShopProductDto dto = new ShopProductDto();
        dto.id = p.getId().toString();
        dto.name = p.getName();
        dto.category = p.getCategory();
        dto.price = p.getPrice().doubleValue();
        dto.discountPrice = p.getDiscountPrice() != null ? p.getDiscountPrice().doubleValue() : null;
        dto.brand = p.getBrand();
        dto.rating = p.getRating() != null ? p.getRating().doubleValue() : null;
        dto.reviewCount = p.getReviewCount();
        dto.description = p.getDescription();
        dto.imageUrl = "https://images-na.ssl-images-amazon.com/images/P/" + p.getAsin() + ".01.L.jpg";
        String url = "https://www.amazon.in/dp/" + p.getAsin();
        dto.affiliateUrl = (affiliateTag != null && !affiliateTag.isBlank()) ? url + "?tag=" + affiliateTag : url;
        return dto;
    }
}
