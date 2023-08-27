package ru.ramlabs.gitea.stonks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;

import java.util.Map;
import java.util.Optional;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        var app = new SpringApplication(App.class);
        app.setDefaultProperties(Map.of(
                "server.port",
                Optional.ofNullable(System.getenv("PORT")).orElse("8080")
        ));
        app.run(args);
    }
}