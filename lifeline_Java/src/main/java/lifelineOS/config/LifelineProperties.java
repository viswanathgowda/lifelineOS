package lifelineOS.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifeline")
public class LifelineProperties {

	private final Storage storage = new Storage();
	private final Security security = new Security();
	private final Tunnel tunnel = new Tunnel();
	private final Mfa mfa = new Mfa();
	private final Backup backup = new Backup();

	public Storage getStorage() {
		return storage;
	}

	public Security getSecurity() {
		return security;
	}

	public Tunnel getTunnel() {
		return tunnel;
	}

	public Mfa getMfa() {
		return mfa;
	}

	public Backup getBackup() {
		return backup;
	}

	public static class Storage {
		/** Primary data root (H2 + primary vault). Prefer removable card when available. */
		private String path = System.getProperty("user.home") + "/.lifeline/data";
		private boolean encryptionEnabled = true;
		private String encryptionKey = "";
		/** Extra volumes, e.g. D:/lifeline/ext or /media/lifeline/card */
		private List<Volume> volumes = new ArrayList<>();

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean isEncryptionEnabled() {
			return encryptionEnabled;
		}

		public void setEncryptionEnabled(boolean encryptionEnabled) {
			this.encryptionEnabled = encryptionEnabled;
		}

		public String getEncryptionKey() {
			return encryptionKey;
		}

		public void setEncryptionKey(String encryptionKey) {
			this.encryptionKey = encryptionKey;
		}

		public List<Volume> getVolumes() {
			return volumes;
		}

		public void setVolumes(List<Volume> volumes) {
			this.volumes = volumes;
		}
	}

	public static class Volume {
		private String id = "";
		private String path = "";
		private boolean writable = true;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public boolean isWritable() {
			return writable;
		}

		public void setWritable(boolean writable) {
			this.writable = writable;
		}
	}

	public static class Security {
		private String apiToken = "";
		private String ownerId = "owner";

		public String getApiToken() {
			return apiToken;
		}

		public void setApiToken(String apiToken) {
			this.apiToken = apiToken;
		}

		public String getOwnerId() {
			return ownerId;
		}

		public void setOwnerId(String ownerId) {
			this.ownerId = ownerId;
		}
	}

	public static class Tunnel {
		private boolean requireHttps = false;
		private boolean mtlsEnabled = false;
		private String allowedProtocols = "TLSv1.3";
		/** When true, only mTLS device identity (or MFA session) is accepted — bare API token denied. */
		private boolean requireDeviceIdentity = false;

		public boolean isRequireHttps() {
			return requireHttps;
		}

		public void setRequireHttps(boolean requireHttps) {
			this.requireHttps = requireHttps;
		}

		public boolean isMtlsEnabled() {
			return mtlsEnabled;
		}

		public void setMtlsEnabled(boolean mtlsEnabled) {
			this.mtlsEnabled = mtlsEnabled;
		}

		public String getAllowedProtocols() {
			return allowedProtocols;
		}

		public void setAllowedProtocols(String allowedProtocols) {
			this.allowedProtocols = allowedProtocols;
		}

		public boolean isRequireDeviceIdentity() {
			return requireDeviceIdentity;
		}

		public void setRequireDeviceIdentity(boolean requireDeviceIdentity) {
			this.requireDeviceIdentity = requireDeviceIdentity;
		}
	}

	public static class Mfa {
		private boolean enabled = false;
		private int sessionTtlMinutes = 30;
		private String issuer = "lifelineOS";

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public int getSessionTtlMinutes() {
			return sessionTtlMinutes;
		}

		public void setSessionTtlMinutes(int sessionTtlMinutes) {
			this.sessionTtlMinutes = sessionTtlMinutes;
		}

		public String getIssuer() {
			return issuer;
		}

		public void setIssuer(String issuer) {
			this.issuer = issuer;
		}
	}

	public static class Backup {
		private String exportDir = "";

		public String getExportDir() {
			return exportDir;
		}

		public void setExportDir(String exportDir) {
			this.exportDir = exportDir;
		}
	}
}
