package ru.ramlabs.gitea.stonks.api;

import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tech.ydb.core.Issue;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;
import static ru.ramlabs.gitea.stonks.api.Users.UserToken.parseAuthCookie;
import static tech.ydb.table.values.PrimitiveValue.*;

@Slf4j
@Component
public class Users {

    private final SessionRetryContext db;
    private final String captchaKey;
    private final PasswordEncoder passwordEncoder;

    public Users(@Value("${stonks.captcha.server_key}") String captchaKey,
                 SessionRetryContext db,
                 PasswordEncoder passwordEncoder
    ) {
        this.db = db;
        this.passwordEncoder = passwordEncoder;
        this.captchaKey = captchaKey;
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("\\w+");

    public String register(String name, String password) throws ExecutionException, InterruptedException {
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new ResponseStatusException(BAD_REQUEST, "Illegal characters in name");
        }

        if (name.length() < 3 || name.length() > 32) {
            throw new ResponseStatusException(BAD_REQUEST, "Name length must be >=3 and <=32");
        }

        if (password.length() < 8 || password.length() > 256) {
            throw new ResponseStatusException(BAD_REQUEST, "Password length must be >=8 and <=256");
        }

        @Language("SQL")
        String insertUserQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $user_name AS utf8;
                DECLARE $user_password AS utf8;
                                
                select ensure(0, count(*) = 0 ,"USER_EXISTS")
                from users view user_name_index
                where user_name = $user_name;
                            
                INSERT INTO users (user_id, user_name,user_password)
                VALUES ($user_id, $user_name, $user_password);
                """;

        var insertResult = db.supplyResult(session -> session.executeDataQuery(
                insertUserQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(new SecureRandom().nextLong()),
                        "$user_name", newText(name.toLowerCase()),
                        "$user_password", newText(passwordEncoder.encode(password))
                ),
                new ExecuteDataQuerySettings().setReportCostInfo(true)
        )).get();

        if (!insertResult.isSuccess()) {
            log.info("Cannot register user {}", insertResult.getStatus());
            for (Issue issue : insertResult.getStatus().getIssues()) {
                if (issue.getMessage().contains("USER_EXISTS")) {
                    throw new ResponseStatusException(BAD_REQUEST, "User exists");
                }
            }
            throw new ResponseStatusException(BAD_REQUEST, "Cannot register");
        }

        if (insertResult.getStatus().hasConsumedRu() && insertResult.getStatus().getConsumedRu() > 0) {
            log.info("Register user consumed {} RU", insertResult.getStatus().getConsumedRu());
        }

        return name.toLowerCase();
    }

    public static String generateSessionToken() {
        //entropy log2(26*2+10)*32 = 190 bits
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

        var random = new SecureRandom();
        char[] salt = new char[32];
        for (int i = 0; i < salt.length; i++) {
            salt[i] = chars.charAt(random.nextInt(0, chars.length()));
        }
        return new String(salt);
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

        @Language("SQL")
        String getUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $session_token AS utf8;
                SELECT *
                FROM sessions
                WHERE user_id==$user_id and session_token==$session_token;
                """;

        var sessionQuery = db.supplyResult(session -> session.executeDataQuery(
                getUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(id),
                        "$session_token", newText(token)
                )
        )).get();

        if (sessionQuery.getValue().getRowCount(0) == 0) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        @Language("SQL")
        String getUserInfoQuery = """
                DECLARE $user_id AS uint64;
                SELECT user_id,user_name
                FROM users
                WHERE user_id=$user_id;
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

        return resultSet.getColumn("user_name").getText();
    }

    public String login(String name, String providedPassword) throws ExecutionException, InterruptedException {
        @Language("SQL")
        String getUserPasswordQuery = """
                DECLARE $user_name AS utf8;
                SELECT user_id, user_password
                FROM users
                WHERE user_name=$user_name
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
        var savedPassword = resultSet.getColumn("user_password").getText();
        var userId = resultSet.getColumn("user_id").getUint64();

        if (!passwordEncoder.matches(providedPassword, savedPassword)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Bad password");
        }

        var sessionToken = generateSessionToken();

        @Language("SQL")
        String setUserTokenQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $session_token AS utf8;
                DECLARE $session_expiry AS datetime;
                INSERT INTO sessions (user_id, session_token,session_expiry)
                VALUES ($user_id, $session_token, $session_expiry);
                """;

        var dataQueryResult = db.supplyResult(session -> session.executeDataQuery(
                setUserTokenQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId),
                        "$session_token", newText(sessionToken),
                        "$session_expiry", newDatetime(Instant.now().plus(Duration.ofDays(30)))
                )
        )).get();

        if (!dataQueryResult.isSuccess()) {
            log.warn("Cannot save session token to db: {}", dataQueryResult.getStatus());
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR);
        }

        return Long.toUnsignedString(userId) + "-" + sessionToken;
    }

    public void logout(String auth) throws ExecutionException, InterruptedException {
        var token = parseAuthCookie(auth);

        checkAuthAndGetUserId(auth);
        //token is valid at this point

        @Language("SQL")
        String removeUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $session_token AS utf8;
                DELETE from sessions
                WHERE user_id==$user_id and session_token==$session_token;
                """;
        db.supplyResult(session -> session.executeDataQuery(
                removeUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(token.userId()),
                        "$session_token", newText(token.token())
                )
        )).get();
    }

    /**
     * Check token validity and extract user id from it.
     *
     * @param auth auth string
     * @return user id
     * @throws ResponseStatusException when token is invalid
     */
    public long checkAuthAndGetUserId(String auth) throws ExecutionException, InterruptedException {
        var token = parseAuthCookie(auth);

        @Language("SQL")
        String getUserSessionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $session_token AS utf8;
                SELECT user_id, session_token, session_expiry
                FROM sessions
                WHERE user_id==$user_id and session_token==$session_token;
                """;

        var sessionQuery = db.supplyResult(session -> session.executeDataQuery(
                getUserSessionQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(token.userId()),
                        "$session_token", newText(token.token())
                )
        )).get();

        if (sessionQuery.getValue().getRowCount(0) == 0) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return token.userId;
    }

    public record UserToken(long userId, String token) {
        public static UserToken parseAuthCookie(String authCookie) {
            var authParts = authCookie.split("-");
            if (authParts.length != 2) {
                throw new IllegalArgumentException("Bad token format");
            }
            return new UserToken(Long.parseUnsignedLong(authParts[0]), authParts[1]);
        }
    }

}
