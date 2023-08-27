package ru.ramlabs.gitea.stonks;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tech.ydb.auth.iam.CloudAuthHelper;
import tech.ydb.core.grpc.GrpcTransport;
import tech.ydb.table.Session;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.TableClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Component
public class Database {

    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    private final SessionRetryContext context;

    public Database() {
        GrpcTransport transport = GrpcTransport.forConnectionString("grpcs://ydb.serverless.yandexcloud.net:2135" +
                        "?database=/ru-central1/b1grjtggdv5a082vgitq/etngjp5coftoqntqob9g")
                .withAuthProvider(rpc -> {
                    var keyPath = Path.of("authorized_key.json");
                    if (Files.exists(keyPath)) {
                        try {
                            return CloudAuthHelper.getServiceAccountJsonAuthProvider(Files.readString(keyPath))
                                    .createAuthIdentity(rpc);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        return CloudAuthHelper.getMetadataAuthProvider().createAuthIdentity(rpc);
                    }
                })
                .build();
        var client = TableClient.newClient(transport).build();
        context = SessionRetryContext.create(client).build();
    }

    @Bean
    public SessionRetryContext getContext() {
        return context;
    }
}
