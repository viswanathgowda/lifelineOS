package lifelineOS.finance;

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
@RequestMapping("/api/finance")
@Validated
public class FinanceController {

	public record FinanceEntryRequest(
			@NotBlank String label,
			@NotNull Double amount,
			String currency) {
	}

	public record FinanceEntryDto(UUID id, String label, double amount, String currency, Instant recordedAt) {
		static FinanceEntryDto from(FinanceEntry e) {
			return new FinanceEntryDto(e.getId(), e.getLabel(), e.getAmount(), e.getCurrency(), e.getRecordedAt());
		}
	}

	private final FinanceEntryRepository repository;
	private final SecuritySupport securitySupport;

	public FinanceController(FinanceEntryRepository repository, SecuritySupport securitySupport) {
		this.repository = repository;
		this.securitySupport = securitySupport;
	}

	@GetMapping
	public List<FinanceEntryDto> list() {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FINANCE);
		return repository.findByOwnerIdOrderByRecordedAtDesc(principal.getOwnerId()).stream()
				.map(FinanceEntryDto::from)
				.toList();
	}

	@PostMapping
	public FinanceEntryDto create(@RequestBody FinanceEntryRequest request) {
		OwnerPrincipal principal = securitySupport.requireScope(ClientScope.FINANCE);
		FinanceEntry saved = repository.save(new FinanceEntry(
				principal.getOwnerId(),
				request.label(),
				request.amount(),
				request.currency()));
		return FinanceEntryDto.from(saved);
	}
}
