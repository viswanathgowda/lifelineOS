package lifelineOS.security;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.common.BadRequestException;
import lifelineOS.tunnel.ClientCertificateAuthFilter;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

	private final DeviceIdentityService deviceIdentityService;
	private final SecuritySupport securitySupport;

	public DeviceController(DeviceIdentityService deviceIdentityService, SecuritySupport securitySupport) {
		this.deviceIdentityService = deviceIdentityService;
		this.securitySupport = securitySupport;
	}

	@PostMapping("/enroll")
	public Map<String, Object> enroll(
			HttpServletRequest request,
			@RequestParam String clientId,
			@RequestParam(required = false) String displayName) {
		// Bootstrap with API token (Bearer) while presenting client cert over mTLS
		securitySupport.requirePrincipal();
		Object certAttr = request.getAttribute(ClientCertificateAuthFilter.ATTR_CLIENT_CERT);
		if (!(certAttr instanceof java.security.cert.X509Certificate certificate)) {
			throw new BadRequestException("Client certificate required for device enrollment (enable mTLS)");
		}
		DeviceIdentity identity = deviceIdentityService.enroll(
				clientId,
				displayName,
				certificate,
				ClientScope.ownerDefault());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("clientId", identity.getClientId());
		body.put("displayName", identity.getDisplayName());
		body.put("certFingerprintSha256", identity.getCertFingerprintSha256());
		body.put("scopes", identity.getScopes());
		body.put("deviceBound", true);
		return body;
	}

	@GetMapping("/me")
	public Map<String, Object> me() {
		OwnerPrincipal principal = securitySupport.requirePrincipal();
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ownerId", principal.getOwnerId());
		body.put("clientId", principal.getClientId());
		body.put("deviceBound", principal.isDeviceBound());
		body.put("mfaVerified", principal.isMfaVerified());
		body.put("scopes", principal.getScopes());
		return body;
	}
}
