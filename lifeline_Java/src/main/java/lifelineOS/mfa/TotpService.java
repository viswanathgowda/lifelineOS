package lifelineOS.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

/**
 * RFC 6238 TOTP (HMAC-SHA1, 30s step, 6 digits).
 */
@Service
public class TotpService {

	private static final int STEP_SECONDS = 30;
	private static final int CODE_DIGITS = 6;
	private static final String HMAC = "HmacSHA1";
	private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private final SecureRandom random = new SecureRandom();

	public String generateSecret() {
		byte[] bytes = new byte[20];
		random.nextBytes(bytes);
		return base32Encode(bytes);
	}

	public String otpAuthUri(String issuer, String account, String secret) {
		return "otpauth://totp/" + url(issuer) + ":" + url(account)
				+ "?secret=" + secret
				+ "&issuer=" + url(issuer)
				+ "&digits=" + CODE_DIGITS
				+ "&period=" + STEP_SECONDS;
	}

	public boolean verify(String base32Secret, String code) {
		if (code == null || !code.matches("\\d{6}")) {
			return false;
		}
		long timestep = Instant.now().getEpochSecond() / STEP_SECONDS;
		for (long skew = -1; skew <= 1; skew++) {
			if (generateCode(base32Secret, timestep + skew).equals(code)) {
				return true;
			}
		}
		return false;
	}

	public String generateCode(String base32Secret, long timestep) {
		try {
			byte[] key = base32Decode(base32Secret);
			byte[] data = ByteBuffer.allocate(8).putLong(timestep).array();
			Mac mac = Mac.getInstance(HMAC);
			mac.init(new SecretKeySpec(key, HMAC));
			byte[] hash = mac.doFinal(data);
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
					| ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8)
					| (hash[offset + 3] & 0xFF);
			int otp = binary % 1_000_000;
			return String.format(Locale.ROOT, "%06d", otp);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("TOTP generation failed", e);
		}
	}

	private static String url(String value) {
		return value.replace(" ", "%20");
	}

	static String base32Encode(byte[] data) {
		StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bitsLeft = 0;
		for (byte b : data) {
			buffer = (buffer << 8) | (b & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				sb.append(BASE32[(buffer >> (bitsLeft - 5)) & 31]);
				bitsLeft -= 5;
			}
		}
		if (bitsLeft > 0) {
			sb.append(BASE32[(buffer << (5 - bitsLeft)) & 31]);
		}
		return sb.toString();
	}

	static byte[] base32Decode(String encoded) {
		String normalized = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "");
		ByteBuffer out = ByteBuffer.allocate((normalized.length() * 5) / 8);
		int buffer = 0;
		int bitsLeft = 0;
		for (char c : normalized.toCharArray()) {
			int val = indexOfBase32(c);
			if (val < 0) {
				throw new IllegalArgumentException("Invalid base32 character");
			}
			buffer = (buffer << 5) | val;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				out.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
				bitsLeft -= 8;
			}
		}
		byte[] result = new byte[out.position()];
		out.rewind();
		out.get(result);
		return result;
	}

	private static int indexOfBase32(char c) {
		if (c >= 'A' && c <= 'Z') {
			return c - 'A';
		}
		if (c >= '2' && c <= '7') {
			return 26 + (c - '2');
		}
		return -1;
	}
}
