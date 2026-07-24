package lifelineOS.mfa;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecuritySupport;

@RestController
@RequestMapping("/api/auth/mfa")
@Validated
public class MfaController {

	public record CodeRequest(@NotBlank String code) {
	}

	private final MfaService mfaService;
	private final SecuritySupport securitySupport;

	public MfaController(MfaService mfaService, SecuritySupport securitySupport) {
		this.mfaService = mfaService;
		this.securitySupport = securitySupport;
	}

	@GetMapping("/status")
	public Map<String, Object> status() {
		OwnerPrincipal principal = securitySupport.requirePrincipal();
		return mfaService.status(principal.getOwnerId());
	}

	@PostMapping("/setup")
	public Map<String, Object> setup() {
		OwnerPrincipal principal = securitySupport.requirePrincipal();
		MfaService.SetupResult result = mfaService.setup(principal);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("secret", result.secret());
		body.put("otpAuthUri", result.otpAuthUri());
		body.put("enabled", result.enabled());
		body.put("hint", "Scan otpAuthUri in an authenticator app, then POST /api/auth/mfa/enable with a code");
		return body;
	}

	@PostMapping("/enable")
	public Map<String, Object> enable(@RequestBody CodeRequest request) {
		OwnerPrincipal principal = securitySupport.requirePrincipal();
		mfaService.enable(principal, request.code());
		return Map.of("enabled", true);
	}

	@PostMapping("/verify")
	public Map<String, Object> verify(@RequestBody CodeRequest request) {
		OwnerPrincipal principal = securitySupport.requirePrincipal();
		MfaService.SessionInfo session = mfaService.verifyAndIssueSession(principal, request.code());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sessionToken", session.token());
		body.put("expiresAt", session.expiresAt().toString());
		body.put("hint", "Use Authorization: Bearer <sessionToken> for subsequent API calls");
		return body;
	}
}
