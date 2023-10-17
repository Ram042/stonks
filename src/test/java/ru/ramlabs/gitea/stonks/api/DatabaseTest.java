package ru.ramlabs.gitea.stonks.api;

import lombok.extern.slf4j.Slf4j;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.settings.ExecuteDataQuerySettings;
import tech.ydb.table.transaction.TxControl;

import java.util.concurrent.ExecutionException;

@SpringBootTest
@ActiveProfiles("development")
@Slf4j
public class DatabaseTest {

    @Autowired
    SessionRetryContext db;

    @Test
    public void testSelectMinMax() throws ExecutionException, InterruptedException {
        @Language("SQL")
        var query = """
                discard select 1;
                discard select ensure(2,true,"");                    
                select 3;
                """;
        var result = db.supplyResult(session -> session.executeDataQuery(
                query,
                TxControl.serializableRw(),
                Params.empty(),
                new ExecuteDataQuerySettings().setReportCostInfo(true)
        )).get();
        log.info("Received {} results. Must be 1", result.getValue().getResultSetCount());
        for (int i = 0; i < result.getValue().getResultSetCount(); i++) {
            var set = result.getValue().getResultSet(i);
            if (set.next()) {
                log.info("R: {} V: {}", i, set.getColumn(0).getInt32());

            } else {
                log.info("R: {} empty", i);
            }

        }
        log.info("Consumed {} RU", result.getStatus().getConsumedRu());
    }





}
