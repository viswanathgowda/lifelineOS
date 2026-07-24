package lifelineOS.tunnel;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lifelineOS.security.ClientScope;

/**
 * Registry of tunnel clients: API tokens and device-bound certificate identities.
 */
@Component
public class TunnelClientRegistry {

	public record RegisteredClient(
			String clientId,
			String token,
			String certFingerprintSha256,
			Set<ClientScope> scopes,
			boolean deviceBound) {
	}

	private final ConcurrentHashMap<String, RegisteredClient> byToken = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, RegisteredClient> byId = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, RegisteredClient> byCertFingerprint = new ConcurrentHashMap<>();

	public void register(String clientId, String token, Set<ClientScope> scopes) {
		registerDevice(clientId, token, null, scopes);
	}

	public void registerDevice(String clientId, String token, String certFingerprintSha256, Set<ClientScope> scopes) {
		var client = new RegisteredClient(
				clientId,
				token,
				certFingerprintSha256,
				Set.copyOf(scopes),
				certFingerprintSha256 != null && !certFingerprintSha256.isBlank());
		byId.put(clientId, client);
		if (token != null && !token.isBlank()) {
			byToken.put(token, client);
		}
		if (client.deviceBound()) {
			byCertFingerprint.put(certFingerprintSha256.toLowerCase(), client);
		}
	}

	public Optional<RegisteredClient> findByToken(String token) {
		return Optional.ofNullable(byToken.get(token));
	}

	public Optional<RegisteredClient> findById(String clientId) {
		return Optional.ofNullable(byId.get(clientId));
	}

	public Optional<RegisteredClient> findByCertFingerprint(String fingerprintSha256) {
		if (fingerprintSha256 == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(byCertFingerprint.get(fingerprintSha256.toLowerCase()));
	}

	public int size() {
		return byId.size();
	}

	public int deviceBoundCount() {
		return byCertFingerprint.size();
	}
}
