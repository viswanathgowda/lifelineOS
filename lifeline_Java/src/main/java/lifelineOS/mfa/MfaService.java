package lifelineOS.mfa;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lifelineOS.common.BadRequestException;
import lifelineOS.common.ForbiddenException;
import lifelineOS.config.LifelineProperties;
import lifelineOS.persistence.VaultCryptoService;
import lifelineOS.security.ClientScope;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecurityAuditService;

@Service
public class MfaService {

	public record SessionInfo(String token, Instant expiresAt, String clientId, Set<ClientScope> scopes) {
	}

	public record SetupResult(String secret, String otpAuthUri, boolean enabled) {
	}

	private final MfaSecretRepository repository;
	private final TotpService totpService;
	private final VaultCryptoService cryptoService;
	private final LifelineProperties properties;
	private final SecurityAuditService auditService;
	private final ConcurrentHashMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();
	private final SecureRandom secureRandom = new SecureRandom();

	public MfaService(
			MfaSecretRepository repository,
			TotpService totpService,
			VaultCryptoService cryptoService,
			LifelineProperties properties,
			SecurityAuditService auditService) {
		this.repository = repository;
		this.totpService = totpService;
		this.cryptoService = cryptoService;
		this.properties = properties;
		this.auditService = auditService;
	}

	public boolean isRequired() {
		if (!properties.getMfa().isEnabled()) {
			return false;
		}
		return repository.findByOwnerId(properties.getSecurity().getOwnerId())
				.map(MfaSecret::isEnabled)
				.orElse(false);
	}

	@Transactional
	public SetupResult setup(OwnerPrincipal principal) {
		repository.findByOwnerId(principal.getOwnerId()).ifPresent(repository::delete);
		String secret = totpService.generateSecret();
		String ciphertext = Base64.getEncoder().encodeToString(
				cryptoService.encrypt(secret.getBytes(StandardCharsets.UTF_8)));
		MfaSecret entity = new MfaSecret(principal.getOwnerId(), ciphertext);
		repository.save(entity);
		String uri = totpService.otpAuthUri(
				properties.getMfa().getIssuer(),
				principal.getOwnerId(),
				secret);
		auditService.record("MFA_SETUP", principal.getOwnerId(), principal.getClientId(), "secret-rotated", true);
		return new SetupResult(secret, uri, false);
	}

	@Transactional
	public void enable(OwnerPrincipal principal, String code) {
		MfaSecret entity = repository.findByOwnerId(principal.getOwnerId())
				.orElseThrow(() -> new BadRequestException("Run MFA setup first"));
		String secret = decryptSecret(entity);
		if (!totpService.verify(secret, code)) {
			auditService.record("MFA_ENABLE_FAIL", principal.getOwnerId(), principal.getClientId(), null, false);
			throw new ForbiddenException("Invalid MFA code");
		}
		entity.setEnabled(true);
		repository.save(entity);
		auditService.record("MFA_ENABLED", principal.getOwnerId(), principal.getClientId(), null, true);
	}

	public SessionInfo verifyAndIssueSession(OwnerPrincipal principal, String code) {
		MfaSecret entity = repository.findByOwnerId(principal.getOwnerId())
				.orElseThrow(() -> new BadRequestException("MFA not configured"));
		if (!entity.isEnabled()) {
			throw new BadRequestException("MFA not enabled yet — call /api/auth/mfa/enable");
		}
		String secret = decryptSecret(entity);
		if (!totpService.verify(secret, code)) {
			auditService.record("MFA_VERIFY_FAIL", principal.getOwnerId(), principal.getClientId(), null, false);
			throw new ForbiddenException("Invalid MFA code");
		}
		String token = "mfa_" + HexFormat.of().formatHex(randomBytes(24));
		Instant expires = Instant.now().plusSeconds(properties.getMfa().getSessionTtlMinutes() * 60L);
		SessionInfo session = new SessionInfo(token, expires, principal.getClientId(), principal.getScopes());
		sessions.put(token, session);
		auditService.record("MFA_VERIFY_OK", principal.getOwnerId(), principal.getClientId(), null, true);
		return session;
	}

	public Optional<SessionInfo> findValidSession(String token) {
		SessionInfo session = sessions.get(token);
		if (session == null) {
			return Optional.empty();
		}
		if (session.expiresAt().isBefore(Instant.now())) {
			sessions.remove(token);
			return Optional.empty();
		}
		return Optional.of(session);
	}

	public Map<String, Object> status(String ownerId) {
		boolean configured = repository.findByOwnerId(ownerId).isPresent();
		boolean enabled = repository.findByOwnerId(ownerId).map(MfaSecret::isEnabled).orElse(false);
		return Map.of(
				"featureEnabled", properties.getMfa().isEnabled(),
				"configured", configured,
				"enabled", enabled,
				"required", isRequired(),
				"sessionTtlMinutes", properties.getMfa().getSessionTtlMinutes());
	}

	private String decryptSecret(MfaSecret entity) {
		byte[] plain = cryptoService.decrypt(Base64.getDecoder().decode(entity.getSecretCiphertext()));
		return new String(plain, StandardCharsets.UTF_8);
	}

	private byte[] randomBytes(int len) {
		byte[] bytes = new byte[len];
		secureRandom.nextBytes(bytes);
		return bytes;
	}
}
