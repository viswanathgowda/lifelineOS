package lifelineOS.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lifelineOS.config.LifelineProperties;
import lifelineOS.mfa.MfaService;
import lifelineOS.tunnel.TunnelClientRegistry;

/**
 * Bearer authentication: MFA session tokens, registered client tokens, or owner API token.
 */
@Component
public class ApiTokenAuthFilter extends OncePerRequestFilter {

	private final LifelineProperties properties;
	private final TunnelClientRegistry clientRegistry;
	private final SecurityAuditService auditService;
	private final MfaService mfaService;

	public ApiTokenAuthFilter(
			LifelineProperties properties,
			TunnelClientRegistry clientRegistry,
			SecurityAuditService auditService,
			MfaService mfaService) {
		this.properties = properties;
		this.clientRegistry = clientRegistry;
		this.auditService = auditService;
		this.mfaService = mfaService;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		// mTLS may already have authenticated
		if (SecurityContextHolder.getContext().getAuthentication() != null
				&& SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof OwnerPrincipal existing
				&& existing.isDeviceBound()) {
			if (mfaBlocks(existing, request)) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MFA session required");
				return;
			}
			filterChain.doFilter(request, response);
			return;
		}

		String token = extractToken(request);
		if (token != null && !token.isBlank()) {
			var session = mfaService.findValidSession(token);
			if (session.isPresent()) {
				var s = session.get();
				var principal = new OwnerPrincipal(
						properties.getSecurity().getOwnerId(),
						s.clientId(),
						s.scopes(),
						false,
						true);
				SecurityContextHolder.getContext().setAuthentication(
						new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
			}
			else {
				var client = clientRegistry.findByToken(token);
				if (client.isPresent()) {
					var registered = client.get();
					var principal = new OwnerPrincipal(
							properties.getSecurity().getOwnerId(),
							registered.clientId(),
							registered.scopes(),
							registered.deviceBound(),
							false);
					if (mfaBlocks(principal, request)) {
						SecurityContextHolder.getContext().setAuthentication(
								new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
						// Allow MFA endpoints through; block others below
						if (!isMfaPath(request)) {
							response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MFA session required");
							return;
						}
					}
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
					auditService.record("AUTH_SUCCESS", principal.getOwnerId(), principal.getClientId(),
							request.getMethod() + " " + request.getRequestURI(), true);
				}
				else if (constantTimeEquals(token, properties.getSecurity().getApiToken())) {
					var principal = new OwnerPrincipal(
							properties.getSecurity().getOwnerId(),
							"default-owner-client",
							ClientScope.ownerDefault(),
							false,
							false);
					if (mfaBlocks(principal, request) && !isMfaPath(request) && !isDeviceEnroll(request)) {
						SecurityContextHolder.getContext().setAuthentication(
								new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
						response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MFA session required");
						return;
					}
					SecurityContextHolder.getContext().setAuthentication(
							new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
					auditService.record("AUTH_SUCCESS", principal.getOwnerId(), principal.getClientId(),
							request.getMethod() + " " + request.getRequestURI(), true);
				}
				else {
					auditService.record("AUTH_FAILURE", null, null,
							request.getMethod() + " " + request.getRequestURI(), false);
				}
			}
		}

		filterChain.doFilter(request, response);
	}

	private boolean mfaBlocks(OwnerPrincipal principal, HttpServletRequest request) {
		return mfaService.isRequired() && !principal.isMfaVerified() && !isMfaPath(request) && !isDeviceEnroll(request);
	}

	private static boolean isMfaPath(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/auth/mfa");
	}

	private static boolean isDeviceEnroll(HttpServletRequest request) {
		return request.getRequestURI().startsWith("/api/devices/enroll");
	}

	private static String extractToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return header.substring(7).trim();
		}
		String alt = request.getHeader("X-Lifeline-Token");
		return alt != null ? alt.trim() : null;
	}

	private static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		byte[] left = a.getBytes(StandardCharsets.UTF_8);
		byte[] right = b.getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(left, right);
	}
}
