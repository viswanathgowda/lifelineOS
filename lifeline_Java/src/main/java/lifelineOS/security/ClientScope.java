package lifelineOS.security;

/**
 * Least-privilege scopes granted to a tunnel client / device (single-owner).
 */
public enum ClientScope {
	FILES_READ,
	FILES_WRITE,
	HEALTH,
	FINANCE,
	INSIGHTS,
	BACKUP,
	STORAGE,
	ADMIN;

	public static java.util.Set<ClientScope> all() {
		return java.util.EnumSet.allOf(ClientScope.class);
	}

	public static java.util.Set<ClientScope> ownerDefault() {
		return all();
	}
}
