package lifelineOS;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.config.LifelineProperties;
import lifelineOS.persistence.VaultStorageService;

@RestController
public class HomeController {

	private final LifelineProperties properties;
	private final VaultStorageService vaultStorageService;

	public HomeController(LifelineProperties properties, VaultStorageService vaultStorageService) {
		this.properties = properties;
		this.vaultStorageService = vaultStorageService;
	}

	@GetMapping("/")
	public Map<String, Object> home() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", "Lifeline OS Running");
		body.put("core", "lifeline_Java");
		body.put("storageRoot", vaultStorageService.getRoot().toString());
		body.put("encryptionAtRest", properties.getStorage().isEncryptionEnabled());
		body.put("mtlsEnabled", properties.getTunnel().isMtlsEnabled());
		body.put("auth", "Bearer token required for /api/** (except /api/tunnel/status)");
		return body;
	}
}
