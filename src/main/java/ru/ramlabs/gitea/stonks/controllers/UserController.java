package ru.ramlabs.gitea.stonks.controllers;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.ramlabs.gitea.stonks.api.Users;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

@RestController
public class UserController {

    private final Users users;

    public UserController(Users users) {
        this.users = users;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(UserController.class);

    public record UserInfo(String username, String password) {
    }

    public record RegisterResult(String username) {
    }

    @PostMapping(path = "/api/user/register",
            consumes = "application/json",
            produces = "application/json")
    public RegisterResult register(@RequestBody UserInfo registrationInfo) throws ExecutionException, InterruptedException {
        if (registrationInfo.username == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No username");
        }
        if (registrationInfo.password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No password");
        }

        var name = users.register(registrationInfo.username, registrationInfo.password);
        LOGGER.info("Registered user: {}", registrationInfo.username);
        return new RegisterResult(name);
    }

    public record LoginResponse(String auth) {
    }

    @PostMapping(path = "/api/user/login",
            consumes = "application/json",
            produces = "application/json")
    public LoginResponse login(HttpServletResponse response, @RequestBody UserInfo loginInfo) throws ExecutionException, InterruptedException {
        if (loginInfo.username == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No username");
        }
        if (loginInfo.password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No password");
        }

        LOGGER.info("Register {}:{}", loginInfo.username, loginInfo.password);
        var token = users.login(loginInfo.username, loginInfo.password);

        var cookie = ResponseCookie.from("auth", token)
                .secure(true)
                .maxAge(Duration.ofDays(30))
                .httpOnly(true)
                .sameSite("strict")
                .path("/")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return new LoginResponse(token);
    }

    public record GetUserResult(String username) {

    }

    @GetMapping(path = "/api/user",
            produces = "application/json")
    public GetUserResult getAccount(@CookieValue String auth) throws ExecutionException, InterruptedException {
        String username = users.getAccount(auth);
        return new GetUserResult(username);
    }

    @PostMapping(path = "/api/user/logout")
    public void logout(HttpServletResponse response, @CookieValue String auth) throws ExecutionException, InterruptedException {
        users.logout(auth);
        var cookie = ResponseCookie.from("auth", "0-00000000000000000000000000000000")
                .secure(true)
                .maxAge(0)
                .httpOnly(true)
                .sameSite("strict")
                .path("/")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

}
