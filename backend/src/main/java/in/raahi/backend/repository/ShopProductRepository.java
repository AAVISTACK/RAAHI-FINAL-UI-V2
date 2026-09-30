package in.raahi.backend.repository;

import in.raahi.backend.entity.ShopProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShopProductRepository extends JpaRepository<ShopProduct, UUID> {
    List<ShopProduct> findByActiveTrueAndCategory(String category);
    List<ShopProduct> findByActiveTrue();
}
