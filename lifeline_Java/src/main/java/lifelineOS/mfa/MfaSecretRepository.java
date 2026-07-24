package lifelineOS.mfa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaSecretRepository extends JpaRepository<MfaSecret, UUID> {

	Optional<MfaSecret> findByOwnerId(String ownerId);
}
