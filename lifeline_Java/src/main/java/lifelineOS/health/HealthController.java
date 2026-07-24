package lifelineOS.health;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lifelineOS.security.ClientScope;
import lifelineOS.security.OwnerPrincipal;
import lifelineOS.security.SecuritySupport;

@RestController
@RequestMapping("/api/health")
@Validated
public class HealthController {

	public record HealthRecordRequest(
			@NotBlank String metric,
			@NotNull Double value,
			String unit) {
	}

	public record HealthRecordDto(UUID id, String metric, double value, String unit, Instant recordedAt) {
		static HealthRecordDto from(HealthRecord r) {
			return new HealthRecordDto(r.getId(), r.getMetric(), r.getMetricValue(), r.getUnit(), r.getRecordedAt());
		}
	}

	private final HealthRecordRepository repository;
	private final SecuritySupport securitySupport;

	public HealthController(HealthRecordRepository repository, SecuritySupport securitySupport) {
		this.repository = repository;
		this.securitySupport = securitySupport;
	}

	@GetMapping
	public List<HealthRecordDto> list() {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.HEALTH);
		return repository.findByOwnerIdOrderByRecordedAtDesc(principal.getOwnerId()).stream()
				.map(HealthRecordDto::from)
				.toList();
	}

	@PostMapping
	public HealthRecordDto create(@RequestBody HealthRecordRequest request) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.HEALTH);
		HealthRecord saved = repository.save(new HealthRecord(
				principal.getOwnerId(),
				request.metric(),
				request.value(),
				request.unit()));
		return HealthRecordDto.from(saved);
	}
}
