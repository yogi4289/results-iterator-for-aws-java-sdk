package com.awslabs.lambda.data;

import org.immutables.gson.Gson;
import org.immutables.value.Value;

@Gson.TypeAdapters
@Value.Immutable
public abstract class FunctionAlias {
    public abstract String getAlias();
}
