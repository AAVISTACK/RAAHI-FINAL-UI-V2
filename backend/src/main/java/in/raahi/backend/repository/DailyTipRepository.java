package in.raahi.backend.repository;

import in.raahi.backend.entity.DailyTip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTipRepository extends JpaRepository<DailyTip, Integer> {
}
