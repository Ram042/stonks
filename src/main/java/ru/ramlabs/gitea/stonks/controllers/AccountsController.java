package ru.ramlabs.gitea.stonks.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.ramlabs.gitea.stonks.api.Accounts;
import ru.ramlabs.gitea.stonks.api.Users;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;

import java.util.concurrent.ExecutionException;

@RestController
public class AccountsController {

    private final Accounts accounts;
    private final Users users;

    public AccountsController(Accounts accounts, Users users) {
        this.accounts = accounts;
        this.users = users;
    }

    public record GetUserAccountsParams(
            @JsonProperty("start_at")
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long startAt
    ) {

    }

    @GetMapping(
            path = "/api/accounts",
            produces = "application/json",
            consumes = "application/json"
    )
    @PostMapping(
            path = "/api/accounts",
            produces = "application/json",
            consumes = "application/json"
    )
    public Accounts.GetAccountsResult getUserAccounts(@CookieValue String auth, @RequestBody(required = false) GetUserAccountsParams params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return accounts.getUserAccounts(userId, params == null ? 0 : params.startAt);
    }

    public record CreateUserAccountParameters(
            String description,
            @JsonProperty("account_name")
            String accountName,
            @JsonProperty("bank_id")
            Long bankId
    ) {

    }

    @PutMapping(
            path = "/api/accounts",
            consumes = "application/json",
            produces = "application/json"
    )
    public Accounts.Account createUserAccount(@CookieValue String auth, @RequestBody CreateUserAccountParameters params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        if (params.accountName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "account_name not set");
        }
        return accounts.createUserAccount(userId, params.accountName, params.description, params.bankId);
    }

    public record DeleteUserAccountParameters(
            @JsonProperty("account_id")
            long accountId
    ) {

    }

    @DeleteMapping(path = "/api/accounts",
            consumes = "application/json"
    )
    public void deleteUserAccount(@CookieValue String auth, @RequestBody DeleteUserAccountParameters params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        var deleted = accounts.deleteUserAccount(userId, params.accountId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
    }


}
