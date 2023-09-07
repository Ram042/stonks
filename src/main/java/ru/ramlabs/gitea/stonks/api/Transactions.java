package ru.ramlabs.gitea.stonks.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.ydb.table.SessionRetryContext;

@Component
public class Transactions {

    private static final Logger LOGGER = LoggerFactory.getLogger(Transactions.class);

    private final SessionRetryContext db;

    public Transactions(SessionRetryContext db) {
        this.db = db;
    }

}
