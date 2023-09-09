package ru.ramlabs.gitea.stonks.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
public class UserTest  {

    @Autowired
    public PasswordEncoder passwordEncoder;

    @Test
    public void testBadPasswordEncoding() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append("a");
        }
        var encoded = passwordEncoder.encode(builder);
        assertThat(passwordEncoder.matches(builder.append("qqqq"), encoded)).isFalse();
    }

}
