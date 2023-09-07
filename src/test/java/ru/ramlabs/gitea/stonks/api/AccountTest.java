package ru.ramlabs.gitea.stonks.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class AccountTest extends AbstractTestNGSpringContextTests {

    @Autowired
    public PasswordEncoder passwordEncoder;

    @Test
    public void testGoodPasswordEncoding() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            builder.append("a");
        }
        var encoded = passwordEncoder.encode(builder);
        assertThat(passwordEncoder.matches(builder.append("qqqq"), encoded)).isFalse();
    }


}
