package com.awslabs.resultsiterator.v2.implementations;

import com.awslabs.resultsiterator.v2.V2HelperModule;
import com.awslabs.s3.helpers.interfaces.V2S3Helper;
import dagger.Component;
import software.amazon.awssdk.services.greengrass.GreengrassClient;
import software.amazon.awssdk.services.iot.IotClient;
import software.amazon.awssdk.services.s3.S3Client;

import javax.inject.Singleton;

@Singleton
@Component(modules = {V2HelperModule.class})
public interface V2TestInjector {
    IotClient iotClient();

    GreengrassClient greengrassClient();

    V2S3Helper v2S3Helper();

    S3Client s3Client();
}
