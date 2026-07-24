package lifelineOS.security;

import java.security.MessageDigest;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lifelineOS.common.BadRequestException;
import lifelineOS.common.NotFoundException;
import lifelineOS.config.LifelineProperties;
import lifelineOS.tunnel.TunnelClientRegistry;

@Service
public class DeviceIdentityService {

	private final DeviceIdentityRepository repository;
	private final TunnelClientRegistry clientRegistry;
	private final LifelineProperties properties;
	private final SecurityAuditService auditService;

	public DeviceIdentityService(
			DeviceIdentityRepository repository,
			TunnelClientRegistry clientRegistry,
			LifelineProperties properties,
			SecurityAuditService auditService) {
		this.repository = repository;
		this.clientRegistry = clientRegistry;
		this.properties = properties;
		this.auditService = auditService;
	}

	@Transactional
	public DeviceIdentity enroll(String clientId, String displayName, X509Certificate certificate, Set<ClientScope> scopes) {
		String fingerprint = fingerprintSha256(certificate);
		if (repository.findByCertFingerprintSha256(fingerprint).isPresent()) {
			throw new BadRequestException("Certificate already enrolled");
		}
		if (repository.findByClientId(clientId).isPresent()) {
			touchAndReturn(clientId, fingerprint);
		}
		DeviceIdentity identity = new DeviceIdentity(
				clientId,
				fingerprint,
				displayName == null || displayName.isBlank() ? clientId : displayName,
				scopes == null || scopes.isEmpty() ? ClientScope.ownerDefault() : scopes);
		repository.save(identity);
		clientRegistry.registerDevice(clientId, null, fingerprint, identity.getScopes());
		auditService.record("DEVICE_ENROLL", properties.getSecurity().getOwnerId(), clientId, fingerprint, true);
		return identity;
	}

	@Transactional(readOnly = true)
	public DeviceIdentity requireByFingerprint(String fingerprint) {
		return repository.findByCertFingerprintSha256(fingerprint.toLowerCase(Locale.ROOT))
				.filter(DeviceIdentity::isEnabled)
				.orElseThrow(() -> new NotFoundException("Unknown or disabled device certificate"));
	}

	@Transactional
	public DeviceIdentity touchByFingerprint(String fingerprint) {
		DeviceIdentity identity = requireByFingerprint(fingerprint);
		identity.touch();
		clientRegistry.registerDevice(identity.getClientId(), null, identity.getCertFingerprintSha256(), identity.getScopes());
		return repository.save(identity);
	}

	public static String fingerprintSha256(X509Certificate certificate) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(certificate.getEncoded());
			return HexFormat.of().formatHex(hash);
		}
		catch (CertificateEncodingException | java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException("Unable to fingerprint certificate", e);
		}
	}

	private DeviceIdentity touchAndReturn(String clientId, String fingerprint) {
		throw new BadRequestException("Client id already enrolled: " + clientId + " (fp=" + fingerprint + ")");
	}

	public static String subjectSummary(X509Certificate certificate) {
		return certificate.getSubjectX500Principal().getName();
	}
}
