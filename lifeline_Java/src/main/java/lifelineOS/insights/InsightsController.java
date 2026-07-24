package lifelineOS.insights;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.finance.FinanceEntryRepository;
import lifelineOS.health.HealthRecordRepository;
import lifelineOS.security.ClientScope;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecuritySupport;

/**
 * Cross-domain insights stub — grows once health/finance history accumulates.
 */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

	private final HealthRecordRepository healthRecordRepository;
	private final FinanceEntryRepository financeEntryRepository;
	private final SecuritySupport securitySupport;

	public InsightsController(
			HealthRecordRepository healthRecordRepository,
			FinanceEntryRepository financeEntryRepository,
			SecuritySupport securitySupport) {
		this.healthRecordRepository = healthRecordRepository;
		this.financeEntryRepository = financeEntryRepository;
		this.securitySupport = securitySupport;
	}

	@GetMapping("/summary")
	public Map<String, Object> summary() {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.INSIGHTS);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ownerId", principal.getOwnerId());
		body.put("healthRecordCount", healthRecordRepository.findByOwnerIdOrderByRecordedAtDesc(principal.getOwnerId()).size());
		body.put("financeEntryCount", financeEntryRepository.findByOwnerIdOrderByRecordedAtDesc(principal.getOwnerId()).size());
		body.put("message", "Insights engine placeholder — suggestions will derive from local history only");
		return body;
	}
}
