package ru.ramlabs.gitea.stonks.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.web.bind.annotation.*;
import ru.ramlabs.gitea.stonks.api.Transactions;
import ru.ramlabs.gitea.stonks.api.Users;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
public class TransactionsController {

    private final Transactions transactions;
    private final Users users;

    public TransactionsController(Transactions transactions, Users users) {
        this.transactions = transactions;
        this.users = users;
    }

    public record GetUserTransactionsParams(
            @JsonProperty("start_at")
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long startAt
    ) {

    }

    @PostMapping(
            path = "/api/transactions",
            produces = "application/json",
            consumes = "application/json"
    )
    public Transactions.GetTransactionsResult getTransaction(
            @CookieValue String auth,
            @RequestBody(required = false) GetUserTransactionsParams params)
            throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return transactions.getTransactions(userId, params == null ? 0 : params.startAt);
    }


    public record CreateTransactionsParams(
            String name,
            String comment,
            Instant timestamp
    ) {

    }

    @PutMapping(
            path = "/api/transactions",
            produces = "application/json",
            consumes = "application/json"
    )
    public Transactions.Transaction createTransaction(@CookieValue String auth, @RequestBody CreateTransactionsParams params)
            throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return transactions.createTransaction(userId, params.name, params.timestamp, params.comment);
    }

    public record GetTransactionDeltasParams(
            @JsonProperty("transaction_id")
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long transactionId
    ) {

    }

    @PostMapping(
            path = "/api/transactions/deltas",
            produces = "application/json",
            consumes = "application/json"
    )
    public List<Transactions.TransactionDelta> getTransactionDeltas(@CookieValue String auth, @RequestBody GetTransactionDeltasParams params)
            throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return transactions.getTransactionDeltas(userId, params.transactionId);
    }

    @PostMapping(
            path = "/api/transactions/deltas/types",
            produces = "application/json",
            consumes = "application/json"
    )
    public List<Transactions.DeltaType> getDeltaTypes(@CookieValue String auth) throws ExecutionException, InterruptedException {
        users.checkAuthAndGetUserId(auth);
        return transactions.getDeltaTypes();
    }

}
