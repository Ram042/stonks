package ru.ramlabs.gitea.stonks.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.ramlabs.gitea.stonks.api.utils.UserUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
public class TransactionTest {

    @Autowired
    Transactions transactions;
    @Autowired
    Assets assets;
    @Autowired
    UserUtils users;
    @Autowired
    Balances balances;
    @Autowired
    Accounts accounts;

    @Test
    public void testTransactions() throws ExecutionException, InterruptedException {
        var user = users.generateUser();
        var now = Instant.now();
        var tx = transactions.createTransaction(user, "test-tx", now, "test transaction");

        assertThat(tx.name()).isEqualTo("test-tx");
        assertThat(tx.timestamp()).isEqualTo(now);
        assertThat(tx.timestamp()).isEqualTo(now);

        var dbTx = transactions.getTransactions(user, 0).transactions();
        assertThat(dbTx)
                .hasSize(1);

        assertThat(dbTx.get(0).name()).isEqualTo("test-tx");
        assertThat(dbTx.get(0).timestamp()).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
        assertThat(dbTx.get(0).comment()).isEqualTo("test transaction");
    }


    @Test
    public void testDeltas() throws ExecutionException, InterruptedException {
        var user = users.generateUser();
        var tx = transactions.createTransaction(user, null, Instant.now(), null).transactionId();
        var asset = assets.addUserAsset(user, "USD", null, 2).id();
        var account = accounts.createUserAccount(user, "", null, null).accountId();

        assertThat(transactions.getTransactionDeltas(user, tx)).isEmpty();

        transactions.addTransactionDelta(user, account, tx, asset, 100, 0);

        var deltas = transactions.getTransactionDeltas(user, tx);
        assertThat(deltas).hasSize(1);
        assertThat(deltas.get(0).amount()).isEqualTo(100);
    }

    @Test
    public void testGetDeltaTypes() throws ExecutionException, InterruptedException {
        transactions.getDeltaTypes();
        assertThat(transactions.getDeltaTypes())
                .allMatch(dt -> dt.multiplier() == -1 || dt.multiplier() == 0 || dt.multiplier() == 1,
                        "check multiplier");
    }

    @Test
    public void getAccountBalance() throws ExecutionException, InterruptedException {
        var user = users.generateUser();
        var tx = transactions.createTransaction(user, null, Instant.now(), null).transactionId();
        var account = accounts.createUserAccount(user, "Broker account", "", null).accountId();
        var asset = assets.addUserAsset(user, "USD", null, 2).id();
        var stock = assets.addUserAsset(user, "Stock", null, 0).id();
        //buy
        transactions.addTransactionDelta(user, account, tx, stock, 1, 100);
        //pay
        transactions.addTransactionDelta(user, account, tx, asset, 100, 101);

        balances.getAccountBalance(user, account);
    }


}
