package lifelineOS.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceIdentityRepository extends JpaRepository<DeviceIdentity, UUID> {

	Optional<DeviceIdentity> findByCertFingerprintSha256(String certFingerprintSha256);

	Optional<DeviceIdentity> findByClientId(String clientId);
}
