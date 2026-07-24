package lifelineOS.files;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lifelineOS.security.ClientScope;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecuritySupport;

@RestController
@RequestMapping("/api/files")
public class FileController {

	private final FileService fileService;
	private final SecuritySupport securitySupport;

	public FileController(FileService fileService, SecuritySupport securitySupport) {
		this.fileService = fileService;
		this.securitySupport = securitySupport;
	}

	@GetMapping
	public List<FileMetadataDto> list(@RequestParam(required = false) VaultNamespace namespace) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_READ);
		return fileService.list(principal, namespace);
	}

	@GetMapping("/{id}")
	public FileMetadataDto metadata(@PathVariable UUID id) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_READ);
		return fileService.metadata(principal, id);
	}

	@GetMapping("/{id}/content")
	public ResponseEntity<byte[]> content(@PathVariable UUID id) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_READ);
		FileMetadataDto meta = fileService.metadata(principal, id);
		byte[] body = fileService.readContent(principal, id);
		MediaType mediaType = meta.contentType() == null
				? MediaType.APPLICATION_OCTET_STREAM
				: MediaType.parseMediaType(meta.contentType());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + meta.displayName() + "\"")
				.contentType(mediaType)
				.body(body);
	}

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public FileMetadataDto upload(
			@RequestParam(required = false, defaultValue = "GENERAL") VaultNamespace namespace,
			@RequestParam(required = false, defaultValue = "primary") String volumeId,
			@RequestParam("file") MultipartFile file) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_WRITE);
		return fileService.upload(principal, namespace, volumeId, file);
	}

	@PutMapping("/{id}")
	public FileMetadataDto move(
			@PathVariable UUID id,
			@RequestParam(required = false) VaultNamespace namespace,
			@RequestParam(required = false) String displayName) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_WRITE);
		return fileService.move(principal, id, namespace, displayName);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FILES_WRITE);
		fileService.delete(principal, id);
		return ResponseEntity.noContent().build();
	}
}
