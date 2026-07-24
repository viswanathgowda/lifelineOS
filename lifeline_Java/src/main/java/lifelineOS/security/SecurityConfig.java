package lifelineOS.security;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import lifelineOS.tunnel.ClientCertificateAuthFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			ClientCertificateAuthFilter clientCertificateAuthFilter,
			ApiTokenAuthFilter apiTokenAuthFilter) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(cors -> {})
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/", "/api/tunnel/status").permitAll()
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
						.accessDeniedHandler((request, response, accessDeniedException) ->
								response.sendError(HttpStatus.FORBIDDEN.value())))
				.addFilterBefore(clientCertificateAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(apiTokenAuthFilter, ClientCertificateAuthFilter.class);
		return http.build();
	}

	@Bean
	FilterRegistrationBean<ClientCertificateAuthFilter> clientCertFilterRegistration(
			ClientCertificateAuthFilter filter) {
		FilterRegistrationBean<ClientCertificateAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	FilterRegistrationBean<ApiTokenAuthFilter> apiTokenFilterRegistration(ApiTokenAuthFilter filter) {
		FilterRegistrationBean<ApiTokenAuthFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}
}
