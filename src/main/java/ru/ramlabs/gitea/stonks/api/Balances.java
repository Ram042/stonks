package ru.ramlabs.gitea.stonks.api;

import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.stereotype.Component;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class Balances {

    private final SessionRetryContext db;

    public Balances(SessionRetryContext db) {
        this.db = db;
    }

    public void getAccountBalance(long userId, long accountId) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var getDeltaTypesQuery = """
                            
                select transaction_deltas.asset_id,
                sum(transaction_deltas.delta_amount *cast(delta_types.delta_type_multiplier as int64))
                from transaction_deltas
                left join delta_types on transaction_deltas.delta_type_id = delta_types.delta_type_id
                where account_id = $accountId
                group by transaction_deltas.asset_id;
                """;

        var queryResult = db.supplyResult(session -> session.executeDataQuery(
                getDeltaTypesQuery, TxControl.serializableRw(), Params.empty(),
                new ExecuteDataQuerySettings().setReportCostInfo(true))).get();
        var transactions = queryResult.getValue().getResultSet(0);

        var result = new ArrayList<Transactions.DeltaType>();

        while (transactions.next()) {
            result.add(new Transactions.DeltaType(
                    transactions.getColumn("delta_type_id").getUint64(),
                    transactions.getColumn("delta_type_name").getText(),
                    transactions.getColumn("delta_type_multiplier").getInt8()
            ));
        }

        log.info("Consumed {} RU", queryResult.getStatus().getConsumedRu());
        
    }


}
