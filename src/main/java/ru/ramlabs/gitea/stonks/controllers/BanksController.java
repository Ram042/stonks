package ru.ramlabs.gitea.stonks.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.ramlabs.gitea.stonks.api.Banks;
import ru.ramlabs.gitea.stonks.api.Users;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;

import java.util.List;
import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
public class BanksController {

    public BanksController(Users users, Banks banks) {
        this.users = users;
        this.banks = banks;
    }

    private final Users users;
    private final Banks banks;

    public record GetBanksResult(List<Banks.Bank> banks) {

    }

    public record BankInfo(
            @JsonProperty("bank_name")
            String bankName,
            @JsonProperty("bank_comment")
            String bankComment
    ) {

    }

    @GetMapping(path = "/api/banks",
            produces = "application/json")
    public GetBanksResult getUserBanks(@CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        var userBanks = banks.getUserBanks(userId);
        return new GetBanksResult(userBanks);
    }

    @PutMapping(path = "/api/banks",
            consumes = "application/json",
            produces = "application/json")
    public Banks.Bank addUserBank(@RequestBody BankInfo bankInfo, @CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        if (bankInfo.bankName == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank name is null");
        }
        return banks.addUserBank(
                userId,
                bankInfo.bankName,
                bankInfo.bankComment == null ? "" : bankInfo.bankComment
        );
    }

    public record UpdateUserBank(
            @JsonProperty("bank_id")
            long bankId,
            @JsonProperty("bank_info")
            BankInfo bankInfo
    ) {

    }

    @PatchMapping(path = "/api/banks",
            consumes = "application/json",
            produces = "application/json")
    public Banks.Bank updateUserBank(@RequestBody UpdateUserBank updateUserBank, @CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return banks.updateUserBank(userId, updateUserBank.bankId, updateUserBank.bankInfo);
    }

    public record DeleteUserBankParameters(
            @JsonProperty("bank_id")
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long bankId
    ) {

    }

    @DeleteMapping(path = "/api/banks",
            consumes = "application/json",
            produces = "application/json")
    public void deleteUserBank(@CookieValue String auth, @RequestBody DeleteUserBankParameters updateUserBank) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        var deleted = banks.deleteUserBank(userId, updateUserBank.bankId);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank not found");
        }
    }

}
