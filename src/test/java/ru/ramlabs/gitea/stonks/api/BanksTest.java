package ru.ramlabs.gitea.stonks.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ru.ramlabs.gitea.stonks.api.utils.UserUtils;

import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("development")
public class BanksTest {

    @Autowired
    UserUtils users;
    @Autowired
    Banks banks;


    @Test
    public void test() throws ExecutionException, InterruptedException {
        var user = users.generateUser();
        var addedBank = banks.addUserBank(user, "Bank", "Test bank");

        assertThat(addedBank).isNotNull();
        assertThat(addedBank.name()).isEqualTo("Bank");
        assertThat(addedBank.comment()).isEqualTo("Test bank");

        var dbBanks = banks.getUserBanks(user);
        assertThat(dbBanks).isNotNull();
        assertThat(dbBanks)
                .hasSize(1)
                .first()
                .matches(bank -> bank.name().equals("Bank"))
                .matches(bank -> bank.comment().equals("Test bank"));
    }

}
