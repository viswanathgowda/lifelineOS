package lifelineOS.security;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "security_audit_log")
public class SecurityAuditEntry {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64)
	private String eventType;

	@Column(length = 128)
	private String actorId;

	@Column(length = 128)
	private String clientId;

	@Column(length = 512)
	private String detail;

	@Column(nullable = false)
	private boolean success;

	@Column(nullable = false)
	private Instant createdAt;

	protected SecurityAuditEntry() {
	}

	public SecurityAuditEntry(String eventType, String actorId, String clientId, String detail, boolean success) {
		this.id = UUID.randomUUID();
		this.eventType = eventType;
		this.actorId = actorId;
		this.clientId = clientId;
		this.detail = detail;
		this.success = success;
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getEventType() {
		return eventType;
	}

	public String getActorId() {
		return actorId;
	}

	public String getClientId() {
		return clientId;
	}

	public String getDetail() {
		return detail;
	}

	public boolean isSuccess() {
		return success;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
