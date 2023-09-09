package ru.ramlabs.gitea.stonks.utils;

import com.google.common.base.Preconditions;
import tech.ydb.table.query.Params;
import tech.ydb.table.values.Value;

public final class NullableParams {

    public static Params ofNullable(Object... params) {
        Preconditions.checkArgument(params.length % 2 == 0, "bad parameter count");
        var result = Params.create(params.length / 2);

        for (int i = 0; i < params.length / 2; i++) {
            var k = params[i * 2];
            var v = params[i * 2 + 1];
            if (v != null) {
                result.put((String) k, (Value) v);
            }
        }

        return result;
    }

}
