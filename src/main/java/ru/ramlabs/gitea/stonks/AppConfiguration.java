package ru.ramlabs.gitea.stonks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tech.ydb.auth.AuthRpcProvider;
import tech.ydb.auth.iam.CloudAuthHelper;
import tech.ydb.core.impl.auth.GrpcAuthRpc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebMvc
@EnableWebSecurity
@Slf4j
public class AppConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .cors(withDefaults())
                .headers(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("argon2", Map.of(
                "argon2", Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
        ));
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    @Bean
    @Profile("development")
    public AuthRpcProvider<GrpcAuthRpc> developmentDatabaseAuthProvider() {
        return rpc -> {
            var keyPath = Path.of("authorized_key.json");
            if (Files.exists(keyPath)) {
                try {
                    return CloudAuthHelper.getServiceAccountJsonAuthProvider(Files.readString(keyPath))
                            .createAuthIdentity(rpc);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return CloudAuthHelper.getMetadataAuthProvider().createAuthIdentity(rpc);
            }
        };
    }

    @Bean
    @Profile("production")
    public AuthRpcProvider<GrpcAuthRpc> productionDatabaseAuthProvider() {
        return rpc -> CloudAuthHelper.getMetadataAuthProvider().createAuthIdentity(rpc);
    }
}
