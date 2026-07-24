package lifelineOS.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lifelineOS.storage.VolumeService;

/**
 * Local encrypted vault on disk across primary + extendable volumes. Paths never leave this layer.
 */
@Service
public class VaultStorageService {

	private static final Logger log = LoggerFactory.getLogger(VaultStorageService.class);

	private final VolumeService volumeService;
	private final VaultCryptoService cryptoService;

	public VaultStorageService(VolumeService volumeService, VaultCryptoService cryptoService) {
		this.volumeService = volumeService;
		this.cryptoService = cryptoService;
	}

	@PostConstruct
	void init() {
		log.info("Vault storage primary={} encryption={}", getRoot(), cryptoService.isEnabled());
	}

	public Path getRoot() {
		return volumeService.primaryRoot();
	}

	public String store(byte[] plaintext) throws IOException {
		return store(VolumeService.PRIMARY, plaintext);
	}

	public String store(String volumeId, byte[] plaintext) throws IOException {
		VolumeService.VolumeInfo volume = volumeService.require(volumeId);
		if (!volume.writable()) {
			throw new IOException("Volume is read-only: " + volumeId);
		}
		String storageKey = UUID.randomUUID() + ".enc";
		Path target = resolveInternal(volume, storageKey);
		Files.write(target, cryptoService.encrypt(plaintext));
		return storageKey;
	}

	public byte[] load(String volumeId, String storageKey) throws IOException {
		VolumeService.VolumeInfo volume = volumeService.require(volumeId);
		Path target = resolveInternal(volume, storageKey);
		if (!Files.isRegularFile(target)) {
			throw new IOException("Vault object missing: " + storageKey);
		}
		return cryptoService.decrypt(Files.readAllBytes(target));
	}

	public void delete(String volumeId, String storageKey) throws IOException {
		VolumeService.VolumeInfo volume = volumeService.require(volumeId);
		Files.deleteIfExists(resolveInternal(volume, storageKey));
	}

	private Path resolveInternal(VolumeService.VolumeInfo volume, String storageKey) {
		if (storageKey == null || storageKey.isBlank() || storageKey.contains("..") || storageKey.contains("/")
				|| storageKey.contains("\\")) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		Path resolved = volume.vaultDir().resolve(storageKey).normalize();
		if (!resolved.startsWith(volume.vaultDir())) {
			throw new IllegalArgumentException("Storage key escapes vault root");
		}
		return resolved;
	}
}
