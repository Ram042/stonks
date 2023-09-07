package ru.ramlabs.gitea.stonks.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.ydb.table.SessionRetryContext;

public class Balances {

    private static final Logger LOGGER = LoggerFactory.getLogger(Balances.class);

    private final SessionRetryContext db;

    public Balances(SessionRetryContext db) {
        this.db = db;
    }

}
