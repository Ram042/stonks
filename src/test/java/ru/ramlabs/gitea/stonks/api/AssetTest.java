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
public class AssetTest {

    @Autowired
    Assets assets;
    @Autowired
    UserUtils users;

    @Test
    public void testDefaultFlow() throws ExecutionException, InterruptedException {
        var user = users.generateUser();
        assets.addUserAsset(user, "test-asset", "money", 2);

        var dbAssets = assets.getUserAssets(user).assets();
        assertThat(dbAssets).hasSize(1);
        assertThat(dbAssets.get(0).name()).isEqualTo("test-asset");
        assertThat(dbAssets.get(0).comment()).isEqualTo("money");
    }

}
