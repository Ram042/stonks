package ru.ramlabs.gitea.stonks.controllers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.ramlabs.gitea.stonks.api.Assets;
import ru.ramlabs.gitea.stonks.api.Users;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;

import java.util.concurrent.ExecutionException;

@RestController
@Slf4j
public class AssetController {

    public AssetController(Users users, Assets assets) {
        this.users = users;
        this.assets = assets;
    }

    private final Users users;
    private final Assets assets;

    public record AddAssetParams(
            String name,
            String comment,
            @JsonProperty("decimal_places")
            int decimalPlaces
    ) {

    }

    @PostMapping(path = "/api/assets",
            produces = "application/json")
    public Assets.GetAssetsResult getUserBanks(@CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        return assets.getUserAssets(userId);
    }

    @PutMapping(path = "/api/assets",
            consumes = "application/json",
            produces = "application/json")
    public Assets.Asset addUserAsset(@RequestBody AddAssetParams params, @CookieValue String auth) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        if (params.name == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Asset name is null");
        }
        return assets.addUserAsset(
                userId,
                params.name,
                params.comment,
                params.decimalPlaces
        );
    }

    public record DeleteUserAssetParams(
            @JsonProperty("bank_id")
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long id
    ) {

    }

    @DeleteMapping(path = "/api/assets",
            consumes = "application/json",
            produces = "application/json")
    public void deleteUserBank(@CookieValue String auth, @RequestBody DeleteUserAssetParams params) throws ExecutionException, InterruptedException {
        long userId = users.checkAuthAndGetUserId(auth);
        var deleted = assets.deleteUserAsset(userId, params.id);
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found");
        }
    }

}
