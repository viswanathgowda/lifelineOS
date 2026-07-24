package lifelineOS.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local-only security audit log — no third-party telemetry.
 */
@Service
public class SecurityAuditService {

	private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

	private final SecurityAuditRepository repository;

	public SecurityAuditService(SecurityAuditRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public void record(String eventType, String actorId, String clientId, String detail, boolean success) {
		repository.save(new SecurityAuditEntry(eventType, actorId, clientId, detail, success));
		if (success) {
			log.info("audit eventType={} actor={} client={} detail={}", eventType, actorId, clientId, detail);
		}
		else {
			log.warn("audit FAIL eventType={} actor={} client={} detail={}", eventType, actorId, clientId, detail);
		}
	}
}
