package ru.ramlabs.gitea.stonks.utils;

import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.springframework.stereotype.Component;
import tech.ydb.core.Result;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.DataQueryResult;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;

import java.util.concurrent.ExecutionException;

@Component
@Slf4j
public class DatabaseSupport {

    private final SessionRetryContext db;

    public DatabaseSupport(SessionRetryContext db) {
        this.db = db;
    }


    public Result<DataQueryResult> executeQuery(@Language("SQL") String query, Params params)
            throws ExecutionException, InterruptedException {
        var result = db.supplyResult(session -> session.executeDataQuery(
                query, TxControl.serializableRw(), params,
                new ExecuteDataQuerySettings().setReportCostInfo(true)
        )).get();

        if (result.getStatus().getConsumedRu() != null && result.getStatus().getConsumedRu() > 0) {
            log.info("Consumed {} RU", result.getStatus().getConsumedRu());
        }
        return result;
    }
}
