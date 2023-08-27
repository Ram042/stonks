package ru.ramlabs.gitea.stonks.api;

import com.github.ram042.json.Json;
import com.github.ram042.json.JsonObject;
import com.google.common.base.Preconditions;
import com.google.common.io.BaseEncoding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.transaction.TxControl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.github.ram042.json.Json.object;
import static org.springframework.http.HttpStatus.*;
import static tech.ydb.table.values.PrimitiveValue.*;

@Component
public class Users {

    private static final Logger LOGGER = LoggerFactory.getLogger(Users.class);

    private final SessionRetryContext db;
    private final String captchaKey;

    public Users(SessionRetryContext db) {
        this.db = db;
        captchaKey = Preconditions.checkNotNull(System.getenv("CAPTCHA_KEY"), "Captcha key not set");
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("\\w+");

    public String register(String name, String password) throws ExecutionException, InterruptedException {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal characters in name");
        }

        if (name.length() < 3 || name.length() > 32) {
            throw new ResponseStatusException(BAD_REQUEST, "Name length must be >=3 and <=32");
        }

        if (password.length() < 6 || password.length() > 256) {
            throw new ResponseStatusException(BAD_REQUEST, "Password length must be >=6 and <=256");
        }

        String getUserQuery = """
                DECLARE $user_name AS utf8;
                SELECT `user_id`
                FROM `users`
                WHERE `user_name`=$user_name
                """;

        var ids = db.supplyResult(session -> session.executeDataQuery(
                getUserQuery,
                TxControl.serializableRw(),
                Params.of("$user_name", newText(name.toLowerCase()))
        )).get();

        if (ids.getValue().getRowCount(0) > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "User already exists");
        }

        String putUserQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_name AS utf8;
                DECLARE $user_password AS json;
                                
                INSERT INTO `users` (`user_id`, `user_name`,`user_password`)
                VALUES ($user_id, $user_name, $user_password);
                """;


        boolean success = false;
        int tries = 3;
        while (!success && tries > 0) {
            var dataQueryResultResult = db.supplyResult(session -> session.executeDataQuery(
                    putUserQuery,
                    TxControl.serializableRw(),
                    Params.of(
                            "$user_id", newUint64(new SecureRandom().nextLong()),
                            "$user_name", newText(name.toLowerCase()),
                            "$user_password", newJson(hashPassword(password).toString())
                    )
            )).get();
            LOGGER.info("Status {}", dataQueryResultResult.getStatus());
            LOGGER.info("Put {}", dataQueryResultResult.getValue());
            success = dataQueryResultResult.isSuccess();
            tries--;
        }

        return name.toLowerCase();
    }

    public static String generateSalt() {
        //entropy log2(26*2+10)*32 = 190 bits
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        var random = new SecureRandom();
        char[] salt = new char[32];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = chars.charAt(random.nextInt(0, chars.length()));
        }
        return new String(salt);
    }

    public static JsonObject hashPassword(String password) {
        String salt = generateSalt();
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        digest.update(":".getBytes(StandardCharsets.UTF_8));
        digest.update(password.getBytes(StandardCharsets.UTF_8));

        var hash = BaseEncoding.base64().encode(digest.digest());

        return object(
                "alg", "SHA-256",
                "hash", hash,
                "salt", salt
        );
    }

    public static boolean checkPassword(JsonObject hashedPassword, String password) {
        String salt = hashedPassword.getString("salt").string;
        String hash = hashedPassword.getString("hash").string;
        String alg = hashedPassword.getString("alg").string;

        if (!alg.equals("SHA-256")) {
            throw new IllegalArgumentException("Unsupported algorithm: " + alg);
        }

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        digest.update(":".getBytes(StandardCharsets.UTF_8));
        digest.update(password.getBytes(StandardCharsets.UTF_8));

        var newHash = BaseEncoding.base64().encode(digest.digest());

        return hash.equals(newHash);
    }

    public String getAccount(String auth) throws ExecutionException, InterruptedException {
        var authParts = auth.split("-");
        if (authParts.length != 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Bad token format");
        }

        long id;
        String token;

        try {
            id = Long.parseUnsignedLong(authParts[0]);
            token = authParts[1];
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Bad token");
        }


        String getUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_session_token AS utf8;
                SELECT `user_id`, `user_session_token`, `user_session_expiry`
                FROM `sessions`
                WHERE `user_id`==$user_id and `user_session_token`==$user_session_token;
                """;

        var sessionQuery = db.supplyResult(session -> session.executeDataQuery(
                getUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(id),
                        "$user_session_token", newText(token)
                )
        )).get();

        if (sessionQuery.getValue().getRowCount(0) == 0) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        String getUserInfoQuery = """
                DECLARE $user_id AS uint64;
                SELECT `user_id`,`user_name`
                FROM `users`
                WHERE `user_id`=$user_id;
                """;
        var userQuery = db.supplyResult(session -> session.executeDataQuery(
                getUserInfoQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(id)
                )
        )).get();

        var resultSet = userQuery.getValue().getResultSet(0);
        resultSet.next();

        var userName = resultSet.getColumn("user_name").getText();

        return userName;
    }

    public String login(String name, String providedPassword) throws ExecutionException, InterruptedException {
        String getUserPasswordQuery = """
                DECLARE $user_name AS utf8;
                SELECT `user_id`, `user_password`
                FROM `users`
                WHERE `user_name`=$user_name
                """;

        var user = db.supplyResult(session -> session.executeDataQuery(
                getUserPasswordQuery,
                TxControl.serializableRw(),
                Params.of("$user_name", newText(name.toLowerCase()))
        )).get().getValue();

        if (user.getRowCount(0) == 0) {
            throw new ResponseStatusException(NOT_FOUND, "User not found");
        }
        var resultSet = user.getResultSet(0);
        resultSet.next();
        var userPasswordJson = resultSet.getColumn("user_password").getJson();
        var userId = resultSet.getColumn("user_id").getUint64();

        if (!checkPassword(Json.parse(userPasswordJson).getAsObject(), providedPassword)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Bad password");
        }

        var sessionToken = generateSalt();

        String setUserTokenQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_session_token AS utf8;
                DECLARE $user_session_expiry AS datetime;
                INSERT INTO `sessions` (`user_id`, `user_session_token`,`user_session_expiry`)
                VALUES ($user_id, $user_session_token, $user_session_expiry);
                """;

        var dataQueryResult = db.supplyResult(session -> session.executeDataQuery(
                setUserTokenQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId),
                        "$user_session_token", newText(sessionToken),
                        "$user_session_expiry", newDatetime(Instant.now().plus(Duration.ofDays(30)))
                )
        )).get();

        if (!dataQueryResult.isSuccess()) {
            LOGGER.warn("Cannot save session token to db: {}", dataQueryResult.getStatus());
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }

        return Long.toUnsignedString(userId) + "-" + sessionToken;
    }

    public void logout(String auth) throws ExecutionException, InterruptedException {
        var authParts = auth.split("-");
        if (authParts.length != 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Bad token format");
        }

        long id;
        String token;

        try {
            id = Long.parseUnsignedLong(authParts[0]);
            token = authParts[1];
        } catch (Exception e) {
            throw new ResponseStatusException(BAD_REQUEST, "Bad token");
        }


        String getUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_session_token AS utf8;
                SELECT `user_id`, `user_session_token`, `user_session_expiry`
                FROM `sessions`
                WHERE `user_id`==$user_id and `user_session_token`==$user_session_token;
                """;

        var sessionQuery = db.supplyResult(session -> session.executeDataQuery(
                getUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(id),
                        "$user_session_token", newText(token)
                )
        )).get();

        if (sessionQuery.getValue().getRowCount(0) == 0) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        String removeUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_session_token AS utf8;
                DELETE from `sessions`
                WHERE `user_id`==$user_id and `user_session_token`==$user_session_token;
                """;
        db.supplyResult(session -> session.executeDataQuery(
                removeUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(id),
                        "$user_session_token", newText(token)
                )
        )).get();
    }
}
