package ru.ramlabs.gitea.stonks;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import tech.ydb.auth.AuthRpcProvider;
import tech.ydb.core.grpc.GrpcTransport;
import tech.ydb.core.impl.auth.GrpcAuthRpc;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.TableClient;

@Slf4j
@Component
public class Database {

    private final SessionRetryContext context;

    public Database(@Value("${stonks.db.endpoint}") String databaseEndpoint,
                    AuthRpcProvider<GrpcAuthRpc> dbAuthProvider
    ) {
        GrpcTransport transport = GrpcTransport.forConnectionString(databaseEndpoint)
                .withAuthProvider(dbAuthProvider)
                .build();
        var client = TableClient.newClient(transport)
                .sessionPoolSize(1, 20)
                .build();
        context = SessionRetryContext.create(client).build();
    }

    @Bean
    public SessionRetryContext getContext() {
        return context;
    }
}
