package lifelineOS.storage;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.security.ClientScope;
import lifelineOS.security.SecuritySupport;

@RestController
@RequestMapping("/api/storage")
public class StorageController {

	private final VolumeService volumeService;
	private final SecuritySupport securitySupport;

	public StorageController(VolumeService volumeService, SecuritySupport securitySupport) {
		this.volumeService = volumeService;
		this.securitySupport = securitySupport;
	}

	@GetMapping("/volumes")
	public Map<String, Object> volumes() {
		securitySupport.requireScope(ClientScope.STORAGE);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("volumes", volumeService.listStatus());
		body.put("hint", "Mount a memory card (e.g. D:/lifeline/ext or /media/lifeline/card) via POST /api/storage/volumes/mount");
		return body;
	}

	@PostMapping("/volumes/mount")
	public Map<String, Object> mount(
			@RequestParam String id,
			@RequestParam String path,
			@RequestParam(defaultValue = "true") boolean writable) throws IOException {
		securitySupport.requireScope(ClientScope.STORAGE);
		VolumeService.VolumeInfo info = volumeService.mount(id, path, writable);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("id", info.id());
		body.put("path", info.root().toString());
		body.put("writable", info.writable());
		body.put("available", info.available());
		return body;
	}
}
