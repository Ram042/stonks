package ru.ramlabs.gitea.stonks.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.transaction.TxControl;
import tech.ydb.table.values.PrimitiveValue;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class Accounts {

    private static final Logger LOGGER = LoggerFactory.getLogger(Accounts.class);

    private final SessionRetryContext db;

    public Accounts(SessionRetryContext db) {
        this.db = db;
    }

    public void createUserAccount(long userId, String name, String description, long bankId) {
        @Language("SQL")
        var insertAccountQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id AS uint64;
                DECLARE $account_description AS utf8;
                DECLARE $account_name AS utf8;
                DECLARE $bank_id AS uint64;
                discard select ensure(0, count(*)>0, 'bank_id does not exist')\s
                from accounts
                where user_id=$user_id and bank_id=$bank_id;
                INSERT INTO accounts
                    ( user_id, account_id, account_description, account_name, bank_id )
                VALUES ($user_id, $account_id,$account_description,$account_name,$bank_id);
                """;
        db.supplyResult(session -> session.executeDataQuery(
                insertAccountQuery, TxControl.serializableRw(), Params.of(
                        "$user_id", PrimitiveValue.newUint64(userId),
                        "$account_id", PrimitiveValue.newUint64(new SecureRandom().nextLong()),
                        "$account_name", PrimitiveValue.newText(name),
                        "$account_description", PrimitiveValue.newText(description),
                        "$bank_id", PrimitiveValue.newUint64(bankId)
                )));
    }

    public record Account(
            @JsonProperty("id")
            long id,
            @JsonProperty("bank_id")
            long bankId,
            @JsonProperty("name")
            String name,
            @JsonProperty("description")
            String description
    ) {
    }

    public List<Account> getAccounts(long userId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getAccountsQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id AS uint64;
                DECLARE $account_description AS utf8;
                DECLARE $account_name AS utf8;
                DECLARE $bank_id AS uint64;
                select *
                from accounts
                where user_id=$user_id
                limit 10;
                """;
        var accounts = db.supplyResult(session -> session.executeDataQuery(
                getAccountsQuery, TxControl.serializableRw(), Params.of(
                        "$user_id", PrimitiveValue.newUint64(userId)
                ))).get().getValue().getResultSet(0);

        var result = new ArrayList<Account>();

        while (accounts.next()) {
            result.add(new Account(
                    accounts.getColumn("account_id").getUint64(),
                    accounts.getColumn("bank_id").getUint64(),
                    accounts.getColumn("account_name").getText(),
                    accounts.getColumn("account_description").getText()
            ));
        }

        return result;
    }
}
