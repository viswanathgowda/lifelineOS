package lifelineOS.mfa;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_secret")
public class MfaSecret {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 64)
	private String ownerId;

	/** Encrypted-at-rest TOTP secret (AES via vault crypto). Stored as Base64 ciphertext. */
	@Column(nullable = false, length = 512)
	private String secretCiphertext;

	@Column(nullable = false)
	private boolean enabled;

	@Column(nullable = false)
	private Instant createdAt;

	protected MfaSecret() {
	}

	public MfaSecret(String ownerId, String secretCiphertext) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.secretCiphertext = secretCiphertext;
		this.enabled = false;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getSecretCiphertext() {
		return secretCiphertext;
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
}
