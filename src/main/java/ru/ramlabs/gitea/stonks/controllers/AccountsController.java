package ru.ramlabs.gitea.stonks.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.bind.annotation.*;
import ru.ramlabs.gitea.stonks.api.Accounts;
import ru.ramlabs.gitea.stonks.api.Users;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
public class AccountsController {

    private final Accounts accounts;
    private final Users users;

    public AccountsController(Accounts accounts, Users users) {
        this.accounts = accounts;
        this.users = users;
    }

    @GetMapping(
            path = "/api/accounts",
            produces = "application/json"
    )
    public List<Accounts.Account> getUserAccounts(@CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return accounts.getAccounts(userId);
    }

    public record CreateUserAccountParameters(
            String description,
            @JsonProperty(required = true)
            String name,
            @JsonProperty("bank_id")
            long bankId
    ) {

    }

    @PutMapping(
            path = "/api/accounts",
            consumes = "application/json"
    )
    public void createUserAccount(@CookieValue String auth, CreateUserAccountParameters params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        accounts.createUserAccount(userId, params.name, params.description, params.bankId);
    }

    public record DeleteUserAccountParameters(
            @JsonProperty("bank_id")
            long bankId
    ){

    }

    @DeleteMapping(path = "/api/accounts",
            consumes = "application/json"
    )
    public void deleteUserAccount(@CookieValue String auth,DeleteUserAccountParameters params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
//        accounts.deleteUserAccount(userId);
    }


}
