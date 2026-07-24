package lifelineOS.security;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "device_identity")
public class DeviceIdentity {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 128)
	private String clientId;

	@Column(nullable = false, unique = true, length = 128)
	private String certFingerprintSha256;

	@Column(nullable = false, length = 256)
	private String displayName;

	@Column(nullable = false, length = 512)
	private String scopesCsv;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(nullable = false)
	private Instant createdAt;

	@Column
	private Instant lastSeenAt;

	protected DeviceIdentity() {
	}

	public DeviceIdentity(String clientId, String certFingerprintSha256, String displayName, Set<ClientScope> scopes) {
		this.id = UUID.randomUUID();
		this.clientId = clientId;
		this.certFingerprintSha256 = certFingerprintSha256.toLowerCase();
		this.displayName = displayName;
		this.scopesCsv = scopes.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
		this.enabled = true;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getClientId() {
		return clientId;
	}

	public String getCertFingerprintSha256() {
		return certFingerprintSha256;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Set<ClientScope> getScopes() {
		if (scopesCsv == null || scopesCsv.isBlank()) {
			return EnumSet.noneOf(ClientScope.class);
		}
		return Arrays.stream(scopesCsv.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(ClientScope::valueOf)
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(ClientScope.class)));
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public void touch() {
		this.lastSeenAt = Instant.now();
	}
}
