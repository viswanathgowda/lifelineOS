package lifelineOS.tunnel;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import lifelineOS.config.LifelineProperties;

/**
 * Enforces HTTPS when configured (production / device profile).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class HttpsEnforcementFilter extends OncePerRequestFilter {

	private final LifelineProperties properties;

	public HttpsEnforcementFilter(LifelineProperties properties) {
		this.properties = properties;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		if (properties.getTunnel().isRequireHttps() && !request.isSecure()) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.getWriter().write("{\"detail\":\"HTTPS required for tunnel access\"}");
			return;
		}
		filterChain.doFilter(request, response);
	}
}
