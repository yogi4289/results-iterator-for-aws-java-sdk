package com.awslabs.s3.helpers.data;

import com.awslabs.data.NoToString;
import org.immutables.gson.Gson;
import org.immutables.value.Value;

@Gson.TypeAdapters
@Value.Immutable
public abstract class S3Bucket extends NoToString {
    public abstract String bucket();
}