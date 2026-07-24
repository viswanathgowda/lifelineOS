package lifelineOS.tunnel;

import java.io.IOException;
import java.security.cert.X509Certificate;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lifelineOS.config.LifelineProperties;
import lifelineOS.security.DeviceIdentity;
import lifelineOS.security.DeviceIdentityService;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecurityAuditService;

/**
 * Terminates mTLS client identity: binds X.509 certificate fingerprint to enrolled device.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class ClientCertificateAuthFilter extends OncePerRequestFilter {

	public static final String ATTR_CLIENT_CERT = "lifeline.client.cert";
	public static final String ATTR_CERT_FINGERPRINT = "lifeline.client.cert.fp";

	private final LifelineProperties properties;
	private final DeviceIdentityService deviceIdentityService;
	private final TunnelClientRegistry clientRegistry;
	private final SecurityAuditService auditService;

	public ClientCertificateAuthFilter(
			LifelineProperties properties,
			DeviceIdentityService deviceIdentityService,
			TunnelClientRegistry clientRegistry,
			SecurityAuditService auditService) {
		this.properties = properties;
		this.deviceIdentityService = deviceIdentityService;
		this.clientRegistry = clientRegistry;
		this.auditService = auditService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		X509Certificate cert = extractClientCert(request);
		if (cert != null) {
			String fingerprint = DeviceIdentityService.fingerprintSha256(cert);
			request.setAttribute(ATTR_CLIENT_CERT, cert);
			request.setAttribute(ATTR_CERT_FINGERPRINT, fingerprint);

			var registered = clientRegistry.findByCertFingerprint(fingerprint);
			if (registered.isPresent()) {
				var client = registered.get();
				var principal = new OwnerPrincipal(
						properties.getSecurity().getOwnerId(),
						client.clientId(),
						client.scopes(),
						true,
						false);
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
				auditService.record("MTLS_AUTH_SUCCESS", principal.getOwnerId(), principal.getClientId(),
						fingerprint, true);
			}
			else {
				try {
					DeviceIdentity identity = deviceIdentityService.touchByFingerprint(fingerprint);
					var principal = new OwnerPrincipal(
							properties.getSecurity().getOwnerId(),
							identity.getClientId(),
							identity.getScopes(),
							true,
							false);
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
					auditService.record("MTLS_AUTH_SUCCESS", principal.getOwnerId(), principal.getClientId(),
							fingerprint, true);
				}
				catch (RuntimeException ex) {
					auditService.record("MTLS_AUTH_UNKNOWN", null, null, fingerprint, false);
					if (properties.getTunnel().isMtlsEnabled() && properties.getTunnel().isRequireDeviceIdentity()) {
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Device certificate not enrolled");
						return;
					}
				}
			}
		}
		else if (properties.getTunnel().isMtlsEnabled() && properties.getTunnel().isRequireDeviceIdentity()
				&& !isPublic(request)) {
			// Allow MFA setup/verify and enroll endpoints without device yet when using token bootstrap
			if (!isBootstrapPath(request)) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Client certificate required");
				return;
			}
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI();
		return "/".equals(path) || "/api/tunnel/status".equals(path);
	}

	private static boolean isBootstrapPath(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/api/auth/") || path.startsWith("/api/devices/enroll");
	}

	private static X509Certificate extractClientCert(HttpServletRequest request) {
		Object attr = request.getAttribute("jakarta.servlet.request.X509Certificate");
		if (attr instanceof X509Certificate[] certs && certs.length > 0) {
			return certs[0];
		}
		// Older attribute name used by some containers
		Object legacy = request.getAttribute("javax.servlet.request.X509Certificate");
		if (legacy instanceof X509Certificate[] certs && certs.length > 0) {
			return certs[0];
		}
		return null;
	}
}
