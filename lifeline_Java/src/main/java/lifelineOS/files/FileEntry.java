package lifelineOS.files;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lifelineOS.storage.VolumeService;

@Entity
@Table(name = "vault_file_entry")
public class FileEntry {

	@Id
	private UUID id;

	@Column(nullable = false, length = 32)
	@Enumerated(EnumType.STRING)
	private VaultNamespace namespace;

	@Column(nullable = false, length = 512)
	private String displayName;

	@Column(length = 255)
	private String contentType;

	@Column(nullable = false)
	private long sizeBytes;

	/** Internal opaque storage key — never exposed via API. */
	@Column(nullable = false, length = 128)
	private String storageKey;

	/** Volume id (primary or extendable card mount). Never expose absolute paths. */
	@Column(nullable = false, length = 64)
	private String volumeId = VolumeService.PRIMARY;

	@Column(nullable = false, length = 64)
	private String ownerId;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	protected FileEntry() {
	}

	public FileEntry(
			UUID id,
			VaultNamespace namespace,
			String displayName,
			String contentType,
			long sizeBytes,
			String storageKey,
			String volumeId,
			String ownerId) {
		Instant now = Instant.now();
		this.id = id;
		this.namespace = namespace;
		this.displayName = displayName;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.storageKey = storageKey;
		this.volumeId = volumeId == null || volumeId.isBlank() ? VolumeService.PRIMARY : volumeId;
		this.ownerId = ownerId;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public VaultNamespace getNamespace() {
		return namespace;
	}

	public void setNamespace(VaultNamespace namespace) {
		this.namespace = namespace;
		this.updatedAt = Instant.now();
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
		this.updatedAt = Instant.now();
	}

	public String getContentType() {
		return contentType;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public String getVolumeId() {
		return volumeId;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
