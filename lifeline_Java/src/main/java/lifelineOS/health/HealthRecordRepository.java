package lifelineOS.health;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, UUID> {

	List<HealthRecord> findByOwnerIdOrderByRecordedAtDesc(String ownerId);
}
