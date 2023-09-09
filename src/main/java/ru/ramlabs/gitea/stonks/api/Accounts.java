package ru.ramlabs.gitea.stonks.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.ramlabs.gitea.stonks.utils.NullableParams;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;
import tech.ydb.core.Issue;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;
import tech.ydb.table.values.PrimitiveValue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static tech.ydb.table.values.PrimitiveValue.newUint64;

@Component
@Slf4j
public class Accounts {

    private final SessionRetryContext db;

    public Accounts(SessionRetryContext db) {
        this.db = db;
    }

    public Account createUserAccount(long userId, String name, String description, @Nullable Long bankId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var insertAccountQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id AS uint64;
                DECLARE $account_description AS utf8?;
                DECLARE $account_name AS utf8;
                DECLARE $bank_id AS uint64?;
                 
                /*                
                 discard select ensure(0, count(*)>0, 'bank_id does not exist')
                from accounts
                where user_id=$user_id and bank_id=$bank_id;
                */   
                                
                INSERT INTO accounts
                    ( user_id, account_id, account_description, account_name, bank_id )
                VALUES ($user_id, $account_id,$account_description,$account_name,$bank_id);
                """;

        var newAccountId = new SecureRandom().nextLong();

        var insertResult = db.supplyResult(session -> session.executeDataQuery(
                        insertAccountQuery, TxControl.serializableRw(), NullableParams.ofNullable(
                                "$user_id", PrimitiveValue.newUint64(userId),
                                "$account_id", PrimitiveValue.newUint64(newAccountId),
                                "$account_name", PrimitiveValue.newText(name),
                                "$account_description", description == null ? null : PrimitiveValue.newText(description),
                                "$bank_id", bankId == null ? null : PrimitiveValue.newUint64(bankId)
                        ),
                        new ExecuteDataQuerySettings().setReportCostInfo(true)))
                .get();

        if (!insertResult.isSuccess()) {
            for (Issue issue : insertResult.getStatus().getIssues()) {
                if (issue.getMessage().contains("bank_id does not exist")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank does not exist");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, insertResult.getStatus().toString());
        }

        log.info("Consumed {} RU", insertResult.getStatus().getConsumedRu());

        return new Account(
                newAccountId,
                bankId,
                name,
                description
        );
    }

    public boolean deleteUserAccount(long userId, long accountId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        String deleteAccountQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id AS uint64;
                                
                select count(*)
                from accounts
                where user_id = $user_id and account_id = $account_id;
                                
                delete from accounts
                where user_id = $user_id and account_id = $account_id;
                """;

        var result = db.supplyResult(session -> session.executeDataQuery(
                deleteAccountQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId),
                        "$account_id", newUint64(accountId)
                )
        )).get().getValue().getResultSet(0);

        result.next();

        return result.getColumn(0).getUint64() > 0;
    }

    public record Account(
            @JsonProperty("account_id")
            @JsonSerialize(using = UnsignedLongToString.Serializer.class)
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long accountId,
            @JsonProperty("bank_id")
            @JsonSerialize(using = UnsignedLongToString.Serializer.class)
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            Long bankId,
            @JsonProperty("account_name")
            String name,
            @JsonProperty("description")
            String description
    ) {
    }

    public record GetAccountsResult(
            List<Account> accounts,
            @JsonProperty("start_at")
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            Long startAt
    ) {

    }

    public GetAccountsResult getUserAccounts(long userId, long start) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getAccountsQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id_start AS uint64;
                                
                select *
                from accounts
                where user_id=$user_id and account_id >= $account_id_start
                limit 11;
                """;
        var queryResult = db.supplyResult(session -> session.executeDataQuery(
                getAccountsQuery, TxControl.serializableRw(), Params.of(
                        "$user_id", PrimitiveValue.newUint64(userId),
                        "$account_id_start", PrimitiveValue.newUint64(start)
                ), new ExecuteDataQuerySettings().setReportCostInfo(true))).get();
        var accounts = queryResult.getValue().getResultSet(0);

        var result = new ArrayList<Account>();

        int counter = 0;
        Long startAt = null;

        while (accounts.next()) {
            counter++;
            if (counter <= 10) {
                result.add(new Account(
                        accounts.getColumn("account_id").getUint64(),
                        accounts.getColumn("bank_id").isOptionalItemPresent()
                                ? accounts.getColumn("bank_id").getUint64() : null,
                        accounts.getColumn("account_name").getText(),
                        accounts.getColumn("account_description").isOptionalItemPresent()
                                ? accounts.getColumn("account_description").getText() : null
                ));
            } else {
                startAt = accounts.getColumn("account_id").getUint64();
            }
        }

        log.info("Consumed {} RU", queryResult.getStatus().getConsumedRu());

        return new GetAccountsResult(
                result, startAt
        );
    }
}
