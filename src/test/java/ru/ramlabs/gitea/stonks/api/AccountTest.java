package ru.ramlabs.gitea.stonks.api;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

import java.security.NoSuchAlgorithmException;

public class AccountTest {

    @Test
    public void testValidPasswordChecking() throws NoSuchAlgorithmException {
        String password = "123456";

        var hashedPassword = Users.hashPassword(password);

        Assertions.assertThat(Users.checkPassword(hashedPassword, password)).isTrue();
    }

    @Test
    public void testInvalidPasswordChecking() throws NoSuchAlgorithmException {
        String password = "123456";

        var hashedPassword = Users.hashPassword(password);

        Assertions.assertThat(Users.checkPassword(hashedPassword, "bad password")).isFalse();
    }


}
