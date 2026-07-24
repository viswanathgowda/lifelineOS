package lifelineOS.finance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "finance_entry")
public class FinanceEntry {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64)
	private String ownerId;

	@Column(nullable = false, length = 128)
	private String label;

	@Column(nullable = false)
	private double amount;

	@Column(length = 8)
	private String currency;

	@Column(nullable = false)
	private Instant recordedAt;

	protected FinanceEntry() {
	}

	public FinanceEntry(String ownerId, String label, double amount, String currency) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.label = label;
		this.amount = amount;
		this.currency = currency == null || currency.isBlank() ? "USD" : currency;
		this.recordedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getLabel() {
		return label;
	}

	public double getAmount() {
		return amount;
	}

	public String getCurrency() {
		return currency;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
