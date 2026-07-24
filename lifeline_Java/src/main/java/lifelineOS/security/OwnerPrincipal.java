package lifelineOS.security;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated single-owner / tunnel-client principal.
 */
public class OwnerPrincipal implements UserDetails {

	private final String ownerId;
	private final String clientId;
	private final Set<ClientScope> scopes;
	private final boolean deviceBound;
	private final boolean mfaVerified;

	public OwnerPrincipal(String ownerId, String clientId, Set<ClientScope> scopes) {
		this(ownerId, clientId, scopes, false, false);
	}

	public OwnerPrincipal(
			String ownerId,
			String clientId,
			Set<ClientScope> scopes,
			boolean deviceBound,
			boolean mfaVerified) {
		this.ownerId = ownerId;
		this.clientId = clientId;
		this.scopes = Set.copyOf(scopes);
		this.deviceBound = deviceBound;
		this.mfaVerified = mfaVerified;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getClientId() {
		return clientId;
	}

	public Set<ClientScope> getScopes() {
		return scopes;
	}

	public boolean isDeviceBound() {
		return deviceBound;
	}

	public boolean isMfaVerified() {
		return mfaVerified;
	}

	public boolean hasScope(ClientScope scope) {
		return scopes.contains(ClientScope.ADMIN) || scopes.contains(scope);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return scopes.stream()
				.map(s -> new SimpleGrantedAuthority("SCOPE_" + s.name()))
				.collect(Collectors.toSet());
	}

	@Override
	public String getPassword() {
		return "";
	}

	@Override
	public String getUsername() {
		return ownerId;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
