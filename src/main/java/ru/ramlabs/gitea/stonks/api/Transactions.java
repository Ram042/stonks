package ru.ramlabs.gitea.stonks.api;

import com.fasterxml.jackson.annotation.JsonFormat;
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
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;
import tech.ydb.table.values.OptionalValue;
import tech.ydb.table.values.PrimitiveValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class Transactions {

    private final SessionRetryContext db;

    public Transactions(SessionRetryContext db) {
        this.db = db;
    }

    public Transaction createTransaction(
            long userId,
            @Nullable String name,
            @Nullable Instant timestamp,
            @Nullable String comment) throws ExecutionException, InterruptedException {
        long newTransactionId = new Random().nextLong();
        @Language("SQL")
        var insertTransactionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $transaction_id AS uint64;
                DECLARE $transaction_name AS utf8?;
                DECLARE $transaction_comment AS utf8?;
                DECLARE $transaction_timestamp AS timestamp?;
                                
                insert INTO transactions
                    ( user_id, transaction_id, transaction_comment, transaction_name, transaction_timestamp )
                VALUES ( $user_id, $transaction_id, $transaction_comment, $transaction_name, cast($transaction_timestamp as timestamp));
                """;

        var insertResult = db.supplyResult(session -> session.executeDataQuery(
                        insertTransactionQuery, TxControl.serializableRw(), NullableParams.ofNullable(
                                "$user_id", PrimitiveValue.newUint64(userId),
                                "$transaction_id", PrimitiveValue.newUint64(newTransactionId),
                                "$transaction_name", name == null ? null : OptionalValue.of(PrimitiveValue.newText(name)),
                                "$transaction_comment", comment == null ? null : OptionalValue.of(PrimitiveValue.newText(comment)),
                                "$transaction_timestamp", timestamp == null ? null :
                                        OptionalValue.of(PrimitiveValue.newTimestamp(timestamp))
                        ),
                        new ExecuteDataQuerySettings().setReportCostInfo(true)))
                .get();

        if (!insertResult.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, insertResult.getStatus().toString());
        }

        log.info("Consumed {} RU", insertResult.getStatus().getConsumedRu());

        return new Transaction(
                newTransactionId,
                name,
                timestamp,
                comment
        );
    }

    public long addTransactionDelta(long user,long accountId, long txId, long assetId, int amount, long deltaTypeId)
            throws ExecutionException, InterruptedException {
        long newDeltaId = new Random().nextLong();
        @Language("SQL")
        var insertTransactionQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $account_id as uint64;
                DECLARE $transaction_id AS uint64;
                DECLARE $delta_id AS uint64;
                DECLARE $asset_id AS uint64;
                DECLARE $delta_amount AS uint64;
                DECLARE $delta_type_id AS uint64;
                DECLARE $delta_comment AS utf8?;
                
                                
                discard select ensure(0, count(transaction_id) > 0,"TX_NOT_FOUND")
                from transactions
                where user_id = $user_id and transaction_id = $transaction_id;                
                       
                UPSERT INTO transaction_deltas
                    ( user_id, account_id, asset_id, transaction_id, delta_id, delta_amount, delta_comment, delta_type_id )
                VALUES ($user_id, $account_id, $asset_id, $transaction_id, $delta_id, $delta_amount, $delta_comment, $delta_type_id );
                """;

        var insertResult = db.supplyResult(session -> session.executeDataQuery(
                        insertTransactionQuery, TxControl.serializableRw(), NullableParams.ofNullable(
                                "$user_id", PrimitiveValue.newUint64(user),
                                "$transaction_id", PrimitiveValue.newUint64(txId),
                                "$delta_id", PrimitiveValue.newUint64(newDeltaId),
                                "$asset_id", PrimitiveValue.newUint64(assetId),
                                "$delta_amount", PrimitiveValue.newUint64(amount),
                                "$delta_type_id", PrimitiveValue.newUint64(deltaTypeId),
                                "$account_id", PrimitiveValue.newUint64(accountId)
                        ),
                        new ExecuteDataQuerySettings().setReportCostInfo(true)))
                .get();

        if (!insertResult.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, insertResult.getStatus().toString());
        }

        log.info("Add transaction delta consumed {} RU", insertResult.getStatus().getConsumedRu());

        return newDeltaId;
    }

    public record DeltaType(
            long id,
            String name,
            int multiplier
    ) {

    }

    public List<DeltaType> getDeltaTypes() throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getDeltaTypesQuery = """            
                select *
                from delta_types;
                """;

        var queryResult = db.supplyResult(session -> session.executeDataQuery(
                getDeltaTypesQuery, TxControl.serializableRw(), Params.empty(),
                new ExecuteDataQuerySettings().setReportCostInfo(true))).get();
        var transactions = queryResult.getValue().getResultSet(0);

        var result = new ArrayList<DeltaType>();

        while (transactions.next()) {
            result.add(new DeltaType(
                    transactions.getColumn("delta_type_id").getUint64(),
                    transactions.getColumn("delta_type_name").getText(),
                    transactions.getColumn("delta_type_multiplier").getInt8()
            ));
        }

        log.info("Consumed {} RU", queryResult.getStatus().getConsumedRu());

        return result;
    }


    public record TransactionDelta(
            long deltaId,
            long amount

    ) {

    }

    public List<TransactionDelta> getTransactionDeltas(long userId, long transactionId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getDeltasQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $transaction_id AS uint64;
                                
                discard select ensure(0, count(transaction_id) > 0, "TX_NOT_FOUND")
                from transactions
                where user_id = $user_id and transaction_id = $transaction_id;
                                
                select *
                from transaction_deltas
                where transaction_id = $transaction_id;
                """;

        var queryResult = db.supplyResult(session -> session.executeDataQuery(
                getDeltasQuery, TxControl.serializableRw(), Params.of(
                        "$user_id", PrimitiveValue.newUint64(userId),
                        "$transaction_id", PrimitiveValue.newUint64(transactionId)
                ), new ExecuteDataQuerySettings().setReportCostInfo(true))).get();
        var deltas = queryResult.getValue().getResultSet(1);

        var result = new ArrayList<TransactionDelta>();

        while (deltas.next()) {
            result.add(new TransactionDelta(
                    deltas.getColumn("delta_id").getUint64(),
                    deltas.getColumn("delta_amount").getUint64()
            ));
        }

        return result;
    }

    public record Transaction(
            @JsonProperty("transaction_id")
            @JsonSerialize(using = UnsignedLongToString.Serializer.class)
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            long transactionId,
            String name,
            @JsonFormat(shape = JsonFormat.Shape.STRING,
                    timezone = "UTC",
                    pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"
            )
            Instant timestamp,
            String comment
    ) {

    }

    public record GetTransactionsResult(
            List<Transaction> transactions,
            @JsonProperty("start_at")
            @JsonSerialize(using = UnsignedLongToString.Serializer.class)
            @JsonDeserialize(using = UnsignedLongToString.Deserializer.class)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Long startAt
    ) {

    }

    public GetTransactionsResult getTransactions(long userId, long start) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getTransactionsQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $transaction_id_start AS uint64;
                                
                select *
                from transactions
                where user_id=$user_id and transaction_id >= $transaction_id_start
                limit 11;
                """;
        var queryResult = db.supplyResult(session -> session.executeDataQuery(
                getTransactionsQuery, TxControl.serializableRw(), Params.of(
                        "$user_id", PrimitiveValue.newUint64(userId),
                        "$transaction_id_start", PrimitiveValue.newUint64(start)
                ), new ExecuteDataQuerySettings().setReportCostInfo(true))).get();
        var transactions = queryResult.getValue().getResultSet(0);

        var result = new ArrayList<Transaction>();

        int counter = 0;
        Long startAt = null;

        while (transactions.next()) {
            counter++;
            if (counter <= 10) {
                result.add(new Transaction(
                        transactions.getColumn("transaction_id").getUint64(),
                        transactions.getColumn("transaction_name").isOptionalItemPresent() ?
                                transactions.getColumn("transaction_name").getText() : null,
                        transactions.getColumn("transaction_timestamp").isOptionalItemPresent() ?
                                transactions.getColumn("transaction_timestamp").getTimestamp() : null,
                        transactions.getColumn("transaction_comment").isOptionalItemPresent() ?
                                transactions.getColumn("transaction_comment").getText() : null
                ));
            } else {
                startAt = transactions.getColumn("transaction_id").getUint64();
            }
        }

        log.info("Consumed {} RU", queryResult.getStatus().getConsumedRu());

        return new GetTransactionsResult(
                result, startAt
        );
    }

}
