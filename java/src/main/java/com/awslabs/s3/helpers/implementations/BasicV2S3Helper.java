package com.awslabs.s3.helpers.implementations;

import com.awslabs.resultsiterator.v2.implementations.V2ResultsIterator;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import io.vavr.control.Try;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Object;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Stream;

public class BasicV2S3Helper implements V2S3Helper {
    @Inject
    S3Client s3Client;

    @Inject
    public BasicV2S3Helper() {
    }

    @Override
    public boolean bucketExists(String bucket) {
        GetBucketLocationRequest getBucketLocationRequest = GetBucketLocationRequest.builder()
                .bucket(bucket)
                .build();

        return Try.of(() -> s3Client.getBucketLocation(getBucketLocationRequest) != null)
                .recover(NoSuchBucketException.class, false)
                .get();
    }

    @Override
    public boolean objectExists(String bucket, String key) {
        if (!bucketExists(bucket)) {
            return false;
        }

        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .bucket(bucket)
                .prefix(key)
                .build();

        Stream<S3Object> s3Objects = new V2ResultsIterator<S3Object>(s3Client, listObjectsRequest).stream();

        Optional<S3Object> optionalS3Object = s3Objects
                // Require an exact match on the name
                .filter(object -> object.key().equals(key))
                .findFirst();

        return optionalS3Object.isPresent();
    }
}