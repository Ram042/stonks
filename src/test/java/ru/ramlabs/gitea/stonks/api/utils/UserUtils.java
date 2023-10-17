package ru.ramlabs.gitea.stonks.api.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.ramlabs.gitea.stonks.api.Users;

import java.util.Random;
import java.util.concurrent.ExecutionException;

@Component
public class UserUtils {

    @Autowired
    public Users users;

    public static String generateRandomString() {
        var rnd = new Random();
        var chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = switch (rnd.nextInt(5)) {
                case 0, 1, 2 -> (char) ('0' + rnd.nextInt(10));
                case 3 -> (char) ('A' + rnd.nextInt(25));
                case 4 -> (char) ('a' + rnd.nextInt(25));
                default -> '_';
            };
            char c = (char) 123;
        }
        return new String(chars);
    }

    public long generateUser() throws ExecutionException, InterruptedException {
        var name = "testuser_" + generateRandomString();
        var pass = generateRandomString();
        users.register(name, pass);
        return users.checkAuthAndGetUserId(users.login(name, pass));
    }
}
