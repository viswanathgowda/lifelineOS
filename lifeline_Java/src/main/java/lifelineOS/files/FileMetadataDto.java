package lifelineOS.files;

import java.time.Instant;
import java.util.UUID;

public record FileMetadataDto(
		UUID id,
		VaultNamespace namespace,
		String displayName,
		String contentType,
		long sizeBytes,
		String volumeId,
		Instant createdAt,
		Instant updatedAt) {

	public static FileMetadataDto from(FileEntry entry) {
		return new FileMetadataDto(
				entry.getId(),
				entry.getNamespace(),
				entry.getDisplayName(),
				entry.getContentType(),
				entry.getSizeBytes(),
				entry.getVolumeId(),
				entry.getCreatedAt(),
				entry.getUpdatedAt());
	}
}
