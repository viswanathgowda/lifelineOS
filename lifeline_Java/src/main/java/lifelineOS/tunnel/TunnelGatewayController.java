package lifelineOS.tunnel;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import lifelineOS.config.LifelineProperties;
import lifelineOS.mfa.MfaService;

/**
 * Tunnel gateway status — clients must still authenticate for all data APIs.
 */
@RestController
@RequestMapping("/api/tunnel")
public class TunnelGatewayController {

	private final LifelineProperties properties;
	private final TunnelClientRegistry clientRegistry;
	private final MfaService mfaService;

	public TunnelGatewayController(
			LifelineProperties properties,
			TunnelClientRegistry clientRegistry,
			MfaService mfaService) {
		this.properties = properties;
		this.clientRegistry = clientRegistry;
		this.mfaService = mfaService;
	}

	@GetMapping("/status")
	public Map<String, Object> status(HttpServletRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("gateway", "lifeline-tunnel");
		body.put("secure", request.isSecure());
		body.put("requireHttps", properties.getTunnel().isRequireHttps());
		body.put("mtlsEnabled", properties.getTunnel().isMtlsEnabled());
		body.put("requireDeviceIdentity", properties.getTunnel().isRequireDeviceIdentity());
		body.put("allowedProtocols", properties.getTunnel().getAllowedProtocols());
		body.put("registeredClients", clientRegistry.size());
		body.put("deviceBoundClients", clientRegistry.deviceBoundCount());
		body.put("mfaRequired", mfaService.isRequired());
		body.put("auth", "mTLS device cert and/or Bearer token; MFA session when enabled");
		Object fp = request.getAttribute(ClientCertificateAuthFilter.ATTR_CERT_FINGERPRINT);
		if (fp != null) {
			body.put("presentedClientCertFingerprint", fp);
		}
		return body;
	}
}
