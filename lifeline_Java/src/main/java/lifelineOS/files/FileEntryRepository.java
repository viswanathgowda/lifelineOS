package lifelineOS.files;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FileEntryRepository extends JpaRepository<FileEntry, UUID> {

	List<FileEntry> findByOwnerIdAndNamespaceOrderByUpdatedAtDesc(String ownerId, VaultNamespace namespace);

	List<FileEntry> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

	Optional<FileEntry> findByIdAndOwnerId(UUID id, String ownerId);
}
