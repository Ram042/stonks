package ru.ramlabs.gitea.stonks.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;
import ru.ramlabs.gitea.stonks.utils.DatabaseSupport;
import ru.ramlabs.gitea.stonks.utils.NullableParams;
import ru.ramlabs.gitea.stonks.utils.UnsignedLongToString;
import tech.ydb.table.SessionRetryContext;
import tech.ydb.table.query.Params;
import tech.ydb.table.transaction.TxControl;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static tech.ydb.table.values.OptionalValue.of;
import static tech.ydb.table.values.PrimitiveValue.*;

@Component
public class Assets {

    private final SessionRetryContext db;
    private final DatabaseSupport db2;

    public Assets(SessionRetryContext db, DatabaseSupport db2) {
        this.db = db;
        this.db2 = db2;
    }

    public record Asset(
            @UnsignedLongToString
            long id,
            String name,
            String comment,
            @JsonProperty("decimal_places")
            int decimalPlaces
    ) {

    }

    public record GetAssetsResult(List<Asset> assets) {

    }

    public GetAssetsResult getUserAssets(long userId) throws ExecutionException, InterruptedException {

        var getResult = db2.executeQuery("""
                        DECLARE $user_id AS uint64;
                        select * from assets
                        where user_id = $user_id
                        limit 51;
                        """,
                Params.of(
                        "$user_id", newUint64(userId)
                )
        ).getValue().getResultSet(0);

        List<Asset> assets = new ArrayList<>();

        while (getResult.next()) {
            assets.add(new Asset(
                    getResult.getColumn("asset_id").getUint64(),
                    getResult.getColumn("asset_name").getText(),
                    getResult.getColumn("asset_comment").isOptionalItemPresent() ?
                            getResult.getColumn("asset_comment").getText() : null,
                    getResult.getColumn("asset_decimal_places").getUint16()
            ));
        }

        return new GetAssetsResult(assets);
    }

    public boolean deleteUserAsset(long userId, long id) {
        throw new UnsupportedOperationException();
    }


    public Asset addUserAsset(
            long userId,
            String name,
            @Nullable String comment,
            int decimalPlaces) throws ExecutionException, InterruptedException {
        @Language("SQL")
        var insertBankQuery = """
                DECLARE $user_id AS uint64;
                DECLARE $asset_id AS uint64;
                DECLARE $asset_name AS utf8;
                DECLARE $asset_comment AS utf8?;
                DECLARE $asset_decimal_places AS uint16;
                                
                INSERT INTO assets
                    ( user_id, asset_id, asset_comment, asset_decimal_places, asset_name )
                VALUES ( $user_id, $asset_id, $asset_comment, $asset_decimal_places, $asset_name );
                """;

        var generatedAssetId = new SecureRandom().nextLong();
        db.supplyResult(session -> session.executeDataQuery(
                insertBankQuery,
                TxControl.serializableRw(),
                NullableParams.ofNullable(
                        "$user_id", newUint64(userId),
                        "$asset_id", newUint64(generatedAssetId),
                        "$asset_name", newText(name),
                        "$asset_comment", comment == null ? null : of(newText(comment)),
                        "$asset_decimal_places", newUint16(decimalPlaces)
                )
        )).get().getValue();
        return new Asset(
                generatedAssetId,
                name,
                comment,
                decimalPlaces
        );
    }


}
