package lifelineOS.backup;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lifelineOS.security.ClientScope;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecuritySupport;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

	private final BackupService backupService;
	private final SecuritySupport securitySupport;

	public BackupController(BackupService backupService, SecuritySupport securitySupport) {
		this.backupService = backupService;
		this.securitySupport = securitySupport;
	}

	@PostMapping("/export")
	public Map<String, Object> export() throws IOException {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.BACKUP);
		return backupService.exportBackup(principal);
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Map<String, Object> importBackup(@RequestParam("file") MultipartFile file) throws IOException {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.BACKUP);
		return backupService.importBackup(principal, file.getBytes());
	}
}
