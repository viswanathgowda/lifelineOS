package lifelineOS.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LifelineProperties.class)
public class LifelineConfig {
}
