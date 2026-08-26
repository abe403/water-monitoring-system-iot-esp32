package com.watermonitor.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(
            HttpSecurity http,
            @Value("${wtm.security.enabled:true}") boolean securityEnabled) throws Exception {
        http.csrf(csrf -> csrf.disable());

        if (securityEnabled) {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> {}));
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }

        return http.build();
    }
}
