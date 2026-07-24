package lifelineOS.backup;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lifelineOS.common.BadRequestException;
import lifelineOS.config.LifelineProperties;
import lifelineOS.files.FileEntry;
import lifelineOS.files.FileEntryRepository;
import lifelineOS.files.VaultNamespace;
import lifelineOS.persistence.VaultCryptoService;
import lifelineOS.persistence.VaultStorageService;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecurityAuditService;
import lifelineOS.storage.VolumeService;

/**
 * User-controlled encrypted backup/export of vault file payloads + metadata manifest.
 */
@Service
public class BackupService {

	private final FileEntryRepository fileEntryRepository;
	private final VaultStorageService vaultStorageService;
	private final VaultCryptoService cryptoService;
	private final VolumeService volumeService;
	private final LifelineProperties properties;
	private final SecurityAuditService auditService;

	public BackupService(
			FileEntryRepository fileEntryRepository,
			VaultStorageService vaultStorageService,
			VaultCryptoService cryptoService,
			VolumeService volumeService,
			LifelineProperties properties,
			SecurityAuditService auditService) {
		this.fileEntryRepository = fileEntryRepository;
		this.vaultStorageService = vaultStorageService;
		this.cryptoService = cryptoService;
		this.volumeService = volumeService;
		this.properties = properties;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> exportBackup(OwnerPrincipal principal) throws IOException {
		List<FileEntry> entries = fileEntryRepository.findByOwnerIdOrderByUpdatedAtDesc(principal.getOwnerId());
		ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(zipBytes)) {
			StringBuilder manifest = new StringBuilder();
			manifest.append("id,namespace,displayName,contentType,sizeBytes,volumeId\n");
			for (FileEntry entry : entries) {
				byte[] plaintext = vaultStorageService.load(entry.getVolumeId(), entry.getStorageKey());
				String entryName = "files/" + entry.getId() + ".bin";
				zip.putNextEntry(new ZipEntry(entryName));
				zip.write(plaintext);
				zip.closeEntry();
				manifest.append(entry.getId()).append(',')
						.append(entry.getNamespace()).append(',')
						.append(escape(entry.getDisplayName())).append(',')
						.append(entry.getContentType() == null ? "" : entry.getContentType()).append(',')
						.append(entry.getSizeBytes()).append(',')
						.append(entry.getVolumeId()).append('\n');
			}
			zip.putNextEntry(new ZipEntry("manifest.csv"));
			zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("meta.txt"));
			zip.write(("owner=" + principal.getOwnerId() + "\nexportedAt=" + Instant.now() + "\n")
					.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}

		byte[] encrypted = cryptoService.encrypt(zipBytes.toByteArray());
		Path exportDir = resolveExportDir();
		Files.createDirectories(exportDir);
		String filename = "lifeline-backup-"
				+ DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())
				+ ".lifelinebak";
		Path target = exportDir.resolve(filename);
		Files.write(target, encrypted);

		auditService.record("BACKUP_EXPORT", principal.getOwnerId(), principal.getClientId(), filename, true);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("filename", filename);
		body.put("bytes", encrypted.length);
		body.put("fileCount", entries.size());
		body.put("exportDir", exportDir.toString());
		body.put("hint", "Opaque encrypted archive — restore via POST /api/backup/import");
		return body;
	}

	@Transactional
	public Map<String, Object> importBackup(OwnerPrincipal principal, byte[] encryptedArchive) throws IOException {
		if (encryptedArchive == null || encryptedArchive.length == 0) {
			throw new BadRequestException("Backup payload required");
		}
		byte[] zipPlain = cryptoService.decrypt(encryptedArchive);
		int restored = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipPlain))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.isDirectory() || !entry.getName().startsWith("files/") || !entry.getName().endsWith(".bin")) {
					continue;
				}
				byte[] content = zip.readAllBytes();
				String storageKey = vaultStorageService.store(VolumeService.PRIMARY, content);
				String name = entry.getName().substring(entry.getName().lastIndexOf('/') + 1);
				FileEntry fe = new FileEntry(
						UUID.randomUUID(),
						VaultNamespace.BACKUPS,
						name,
						"application/octet-stream",
						content.length,
						storageKey,
						VolumeService.PRIMARY,
						principal.getOwnerId());
				fileEntryRepository.save(fe);
				restored++;
			}
		}
		auditService.record("BACKUP_IMPORT", principal.getOwnerId(), principal.getClientId(),
				"restored=" + restored, true);
		return Map.of("restoredFiles", restored);
	}

	private Path resolveExportDir() {
		String configured = properties.getBackup().getExportDir();
		if (configured != null && !configured.isBlank()) {
			return Path.of(configured).toAbsolutePath().normalize();
		}
		return volumeService.primaryRoot().resolve("backup");
	}

	private static String escape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace(",", " ");
	}
}
