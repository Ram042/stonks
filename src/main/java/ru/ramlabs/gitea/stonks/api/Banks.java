package ru.ramlabs.gitea.stonks.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import ru.ramlabs.gitea.stonks.controllers.BanksController;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.transaction.TxControl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static tech.ydb.table.values.PrimitiveValue.newText;
import static tech.ydb.table.values.PrimitiveValue.newUint64;

@Slf4j
@Component
public class Banks {

    private final SessionRetryContext db;

    public Banks(SessionRetryContext db) {
        this.db = db;
    }

    public Bank updateUserBank(long userId, long bankId, BanksController.BankInfo bankInfo) {

        return null;
    }

    public Bank addUserBank(long userId, @NotNull String name, @NotNull String comment) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var insertBankQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $bank_id AS uint64;
                DECLARE $bank_name AS utf8;
                DECLARE $bank_comment AS utf8;
                INSERT INTO banks
                ( user_id, bank_id, bank_name, bank_comment )
                VALUES ($user_id, $bank_id, $bank_name, $bank_comment );
                """;

        var generatedBankId = new SecureRandom().nextLong();
        db.supplyResult(session -> session.executeDataQuery(
                insertBankQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId),
                        "$bank_id", newUint64(generatedBankId),
                        "$bank_name", newText(name),
                        "$bank_comment", newText(comment)
                )
        )).get().getValue();
        return new Bank(
                generatedBankId,
                name,
                comment
        );
    }


    public record Bank(
            @JsonProperty("bank_id")
            @JsonSerialize(using = UnsignedLongToString.Serializer.class)
            long bankId,
            @JsonProperty("bank_name") String name,
            @JsonProperty("bank_comment") String comment
    ) {

    }

    public List<Bank> getUserBanks(long userId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        String getBanksQuery = """
                DECLARE $user_id AS uint64;
                select * from banks
                where user_id = $user_id
                limit 51;
                """;

        var getResult = db.supplyResult(session -> session.executeDataQuery(
                getBanksQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId)
                )
        )).get().getValue().getResultSet(0);

        List<Bank> banks = new ArrayList<>();

        while (getResult.next()) {
            banks.add(new Bank(
                    getResult.getColumn("bank_id").getUint64(),
                    getResult.getColumn("bank_name").getText(),
                    getResult.getColumn("bank_comment").getText()
            ));
        }

        return banks;
    }

    public boolean deleteUserBank(long userId, long bankId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        String deleteBankQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $bank_id AS uint64;
                                
                select count(*)
                from banks
                where user_id = $user_id and bank_id = $bank_id;
                                
                delete from banks
                where user_id = $user_id and bank_id = $bank_id;
                """;

        var result = db.supplyResult(session -> session.executeDataQuery(
                deleteBankQuery,
                TxControl.serializableRw(),
                Params.of(
                        "$user_id", newUint64(userId),
                        "$bank_id", newUint64(bankId)
                )
        )).get().getValue().getResultSet(0);

        result.next();

        return result.getColumn(0).getUint64() > 0;
    }

}
