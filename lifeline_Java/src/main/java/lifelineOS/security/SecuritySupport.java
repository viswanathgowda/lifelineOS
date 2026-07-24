package lifelineOS.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import lifelineOS.common.ForbiddenException;

@Component
public class SecuritySupport {

	public OwnerPrincipal requirePrincipal() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !(auth.getPrincipal() instanceof OwnerPrincipal principal)) {
			throw new ForbiddenException("Authentication required");
		}
		return principal;
	}

	public OwnerPrincipal requireScope(ClientScope scope) {
		OwnerPrincipal principal = requirePrincipal();
		if (!principal.hasScope(scope)) {
			throw new ForbiddenException("Missing scope: " + scope.name());
		}
		return principal;
	}
}
