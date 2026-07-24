package lifelineOS.storage;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lifelineOS.common.BadRequestException;
import lifelineOS.common.NotFoundException;
import lifelineOS.config.LifelineProperties;

/**
 * Primary + extendable volumes (e.g. D: memory card, Linux /media mount).
 */
@Service
public class VolumeService {

	public static final String PRIMARY = "primary";

	public record VolumeInfo(String id, Path root, Path vaultDir, boolean writable, boolean available) {
	}

	private static final Logger log = LoggerFactory.getLogger(VolumeService.class);

	private final LifelineProperties properties;
	private final ConcurrentHashMap<String, VolumeInfo> volumes = new ConcurrentHashMap<>();

	public VolumeService(LifelineProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void init() throws IOException {
		Path primaryRoot = Path.of(properties.getStorage().getPath()).toAbsolutePath().normalize();
		Files.createDirectories(primaryRoot.resolve("vault"));
		Files.createDirectories(primaryRoot.resolve("db"));
		Files.createDirectories(primaryRoot.resolve("backup"));
		volumes.put(PRIMARY, new VolumeInfo(PRIMARY, primaryRoot, primaryRoot.resolve("vault"), true, true));
		log.info("Primary storage volume at {}", primaryRoot);

		for (LifelineProperties.Volume configured : properties.getStorage().getVolumes()) {
			if (configured.getId() == null || configured.getId().isBlank() || PRIMARY.equals(configured.getId())) {
				continue;
			}
			mount(configured.getId(), configured.getPath(), configured.isWritable());
		}
	}

	public synchronized VolumeInfo mount(String id, String path, boolean writable) throws IOException {
		if (id == null || id.isBlank() || PRIMARY.equals(id)) {
			throw new BadRequestException("Invalid volume id");
		}
		if (path == null || path.isBlank()) {
			throw new BadRequestException("Volume path required");
		}
		Path root = Path.of(path).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root.resolve("vault"));
		}
		catch (IOException e) {
			log.warn("Volume {} unavailable at {}: {}", id, root, e.getMessage());
			VolumeInfo unavailable = new VolumeInfo(id, root, root.resolve("vault"), writable, false);
			volumes.put(id, unavailable);
			return unavailable;
		}
		VolumeInfo info = new VolumeInfo(id, root, root.resolve("vault"), writable, true);
		volumes.put(id, info);
		log.info("Mounted extendable volume id={} path={} writable={}", id, root, writable);
		return info;
	}

	public VolumeInfo require(String id) {
		VolumeInfo info = volumes.get(id == null || id.isBlank() ? PRIMARY : id);
		if (info == null) {
			throw new NotFoundException("Unknown volume: " + id);
		}
		if (!info.available()) {
			throw new BadRequestException("Volume not available (is the memory card mounted?): " + info.id());
		}
		return info;
	}

	public Optional<VolumeInfo> find(String id) {
		return Optional.ofNullable(volumes.get(id));
	}

	public List<Map<String, Object>> listStatus() {
		List<Map<String, Object>> list = new ArrayList<>();
		for (VolumeInfo v : volumes.values()) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", v.id());
			row.put("path", v.root().toString());
			row.put("writable", v.writable());
			row.put("available", v.available());
			try {
				if (v.available() && Files.exists(v.root())) {
					FileStore store = Files.getFileStore(v.root());
					row.put("totalBytes", store.getTotalSpace());
					row.put("usableBytes", store.getUsableSpace());
				}
			}
			catch (IOException ignored) {
				// status best-effort
			}
			list.add(row);
		}
		return list;
	}

	public Path primaryRoot() {
		return require(PRIMARY).root();
	}
}
