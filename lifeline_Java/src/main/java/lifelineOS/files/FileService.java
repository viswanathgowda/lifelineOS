package lifelineOS.files;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lifelineOS.common.BadRequestException;
import lifelineOS.common.NotFoundException;
import lifelineOS.persistence.VaultStorageService;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecurityAuditService;
import lifelineOS.storage.VolumeService;

@Service
public class FileService {

	private final FileEntryRepository repository;
	private final VaultStorageService vaultStorageService;
	private final SecurityAuditService auditService;

	public FileService(
			FileEntryRepository repository,
			VaultStorageService vaultStorageService,
			SecurityAuditService auditService) {
		this.repository = repository;
		this.vaultStorageService = vaultStorageService;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<FileMetadataDto> list(OwnerPrincipal principal, VaultNamespace namespace) {
		List<FileEntry> entries = namespace == null
				? repository.findByOwnerIdOrderByUpdatedAtDesc(principal.getOwnerId())
				: repository.findByOwnerIdAndNamespaceOrderByUpdatedAtDesc(principal.getOwnerId(), namespace);
		auditService.record("FILE_LIST", principal.getOwnerId(), principal.getClientId(),
				namespace == null ? "ALL" : namespace.name(), true);
		return entries.stream().map(FileMetadataDto::from).toList();
	}

	@Transactional(readOnly = true)
	public FileMetadataDto metadata(OwnerPrincipal principal, UUID id) {
		return FileMetadataDto.from(requireOwned(principal, id));
	}

	@Transactional(readOnly = true)
	public byte[] readContent(OwnerPrincipal principal, UUID id) {
		FileEntry entry = requireOwned(principal, id);
		try {
			byte[] data = vaultStorageService.load(entry.getVolumeId(), entry.getStorageKey());
			auditService.record("FILE_READ", principal.getOwnerId(), principal.getClientId(), id.toString(), true);
			return data;
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to read vault object", e);
		}
	}

	@Transactional
	public FileMetadataDto upload(
			OwnerPrincipal principal,
			VaultNamespace namespace,
			String volumeId,
			MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("File content is required");
		}
		String displayName = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
				? "unnamed"
				: file.getOriginalFilename();
		String targetVolume = volumeId == null || volumeId.isBlank() ? VolumeService.PRIMARY : volumeId;
		try {
			byte[] bytes = file.getBytes();
			String storageKey = vaultStorageService.store(targetVolume, bytes);
			FileEntry entry = new FileEntry(
					UUID.randomUUID(),
					namespace == null ? VaultNamespace.GENERAL : namespace,
					displayName,
					file.getContentType(),
					bytes.length,
					storageKey,
					targetVolume,
					principal.getOwnerId());
			repository.save(entry);
			auditService.record("FILE_WRITE", principal.getOwnerId(), principal.getClientId(),
					entry.getId().toString(), true);
			return FileMetadataDto.from(entry);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to store vault object", e);
		}
	}

	@Transactional
	public FileMetadataDto move(OwnerPrincipal principal, UUID id, VaultNamespace namespace, String displayName) {
		FileEntry entry = requireOwned(principal, id);
		if (namespace != null) {
			entry.setNamespace(namespace);
		}
		if (displayName != null && !displayName.isBlank()) {
			entry.setDisplayName(displayName.trim());
		}
		repository.save(entry);
		auditService.record("FILE_MOVE", principal.getOwnerId(), principal.getClientId(), id.toString(), true);
		return FileMetadataDto.from(entry);
	}

	@Transactional
	public void delete(OwnerPrincipal principal, UUID id) {
		FileEntry entry = requireOwned(principal, id);
		try {
			vaultStorageService.delete(entry.getVolumeId(), entry.getStorageKey());
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to delete vault object", e);
		}
		repository.delete(entry);
		auditService.record("FILE_DELETE", principal.getOwnerId(), principal.getClientId(), id.toString(), true);
	}

	private FileEntry requireOwned(OwnerPrincipal principal, UUID id) {
		return repository.findByIdAndOwnerId(id, principal.getOwnerId())
				.orElseThrow(() -> new NotFoundException("File not found: " + id));
	}
}
