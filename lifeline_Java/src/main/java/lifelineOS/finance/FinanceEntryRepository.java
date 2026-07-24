package lifelineOS.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceEntryRepository extends JpaRepository<FinanceEntry, UUID> {

	List<FinanceEntry> findByOwnerIdOrderByRecordedAtDesc(String ownerId);
}
