package lifelineOS.health;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "health_record")
public class HealthRecord {

	@Id
	private UUID id;

	@Column(nullable = false, length = 64)
	private String ownerId;

	@Column(nullable = false, length = 64)
	private String metric;

	@Column(name = "metric_value", nullable = false)
	private double metricValue;

	@Column(length = 32)
	private String unit;

	@Column(nullable = false)
	private Instant recordedAt;

	protected HealthRecord() {
	}

	public HealthRecord(String ownerId, String metric, double metricValue, String unit) {
		this.id = UUID.randomUUID();
		this.ownerId = ownerId;
		this.metric = metric;
		this.metricValue = metricValue;
		this.unit = unit;
		this.recordedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public String getMetric() {
		return metric;
	}

	public double getMetricValue() {
		return metricValue;
	}

	public String getUnit() {
		return unit;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}
}
