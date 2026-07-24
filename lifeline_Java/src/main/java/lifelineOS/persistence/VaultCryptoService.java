package lifelineOS.persistence;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import lifelineOS.config.LifelineProperties;

/**
 * AES-256-GCM encryption at rest for vault file payloads.
 */
@Service
public class VaultCryptoService {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;

	private final boolean enabled;
	private final SecretKey key;
	private final SecureRandom secureRandom = new SecureRandom();

	public VaultCryptoService(LifelineProperties properties) {
		this.enabled = properties.getStorage().isEncryptionEnabled();
		byte[] keyBytes = Base64.getDecoder().decode(properties.getStorage().getEncryptionKey());
		if (keyBytes.length != 32) {
			throw new IllegalStateException(
					"lifeline.storage.encryption-key must be Base64 for exactly 32 bytes (AES-256)");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public byte[] encrypt(byte[] plaintext) {
		if (!enabled) {
			return Arrays.copyOf(plaintext, plaintext.length);
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			secureRandom.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext);
			ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
			buffer.put(iv);
			buffer.put(ciphertext);
			return buffer.array();
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Vault encryption failed", e);
		}
	}

	public byte[] decrypt(byte[] payload) {
		if (!enabled) {
			return Arrays.copyOf(payload, payload.length);
		}
		try {
			ByteBuffer buffer = ByteBuffer.wrap(payload);
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			return cipher.doFinal(ciphertext);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("Vault decryption failed", e);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}
}
