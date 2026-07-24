package lifelineOS.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TotpServiceTest {

	@Test
	void generatesAndVerifiesCode() {
		TotpService totp = new TotpService();
		String secret = totp.generateSecret();
		long step = java.time.Instant.now().getEpochSecond() / 30;
		String code = totp.generateCode(secret, step);
		assertThat(code).hasSize(6);
		assertThat(totp.verify(secret, code)).isTrue();
	}
}
