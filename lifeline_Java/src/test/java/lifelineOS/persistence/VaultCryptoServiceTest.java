package lifelineOS.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import lifelineOS.config.LifelineProperties;

class VaultCryptoServiceTest {

	@Test
	void roundTripsPlaintext() {
		LifelineProperties properties = new LifelineProperties();
		properties.getStorage().setEncryptionEnabled(true);
		properties.getStorage().setEncryptionKey("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

		VaultCryptoService crypto = new VaultCryptoService(properties);
		byte[] original = "secret-life-data".getBytes();
		byte[] encrypted = crypto.encrypt(original);

		assertThat(encrypted).isNotEqualTo(original);
		assertThat(crypto.decrypt(encrypted)).isEqualTo(original);
	}
}
