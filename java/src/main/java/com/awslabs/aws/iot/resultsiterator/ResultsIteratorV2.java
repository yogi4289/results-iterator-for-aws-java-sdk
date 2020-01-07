package com.awslabs.aws.iot.resultsiterator;

import com.google.common.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.AwsRequest;
import software.amazon.awssdk.awscore.AwsResponse;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class ResultsIteratorV2<T> {
    private final Logger log = LoggerFactory.getLogger(ResultsIteratorV2.class);
    private final SdkClient sdkClient;
    private final Class<? extends AwsRequest> awsRequestClass;
    private final List<String> tokenMethodNames = new ArrayList<>(Arrays.asList("nextToken", "nextMarker"));
    private final List<String> methodsToIgnore = new ArrayList<>(Arrays.asList("sdkFields", "commonPrefixes"));
    private Optional<? extends Class<? extends AwsResponse>> optionalResponseClass = Optional.empty();
    private AwsRequest awsRequest;
    private AwsResponse awsResponse;
    // NOTE: This is initialized to null so we can determine if we have tried to initialize it already
    private Optional<Method> clientMethodReturningResult = null;
    // NOTE: This is initialized to null so we can determine if we have tried to initialize it already
    private Optional<Method> clientMethodReturningListT = null;
    // NOTE: This is initialized to null so we can determine if we have tried to initialize it already
    private Optional<Method> clientGetMethodReturningString = null;
    // NOTE: This is initialized to null so we can determine if we have tried to initialize it already
    private Optional<Method> clientSetMethodAcceptingString = null;

    public ResultsIteratorV2(SdkClient sdkClient, Class<? extends AwsRequest> awsRequestClass) {
        this.sdkClient = sdkClient;
        this.awsRequestClass = awsRequestClass;
    }

    public ResultsIteratorV2(SdkClient sdkClient, AwsRequest awsRequest) {
        this.sdkClient = sdkClient;
        this.awsRequestClass = awsRequest.getClass();
        this.awsRequest = awsRequest;
    }

    public List<T> iterateOverResults() {
        if (awsRequest == null) {
            try {
                // Get a new request object.  If this can't be done with a default constructor it will fail.
                Method method = awsRequestClass.getMethod("builder", AwsRequest.Builder.class);
                awsRequest = (AwsRequest) method.invoke(null);
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                e.printStackTrace();
                throw new UnsupportedOperationException(e);
            }
        }

        List<T> output = new ArrayList<>();
        String nextToken = null;

        do {
            awsResponse = queryNextResults();

            output.addAll(getResultData());

            nextToken = getNextToken();

            // Is there a next token?
            if (nextToken == null) {
                // No, we're done
                break;
            }

            // There is a next token, use it to get the next set of topic rules
            setNextToken(nextToken);
        } while (true);

        return output;
    }

    private AwsResponse queryNextResults() {
        if (clientMethodReturningResult == null) {
            // Look for a public method in the client (AWSIot, etc) that takes a AwsRequest and returns a V.  If zero or more than one exists, fail.
            clientMethodReturningResult = getMethodWithParameterAndReturnType(sdkClient.getClass(), awsRequestClass, getResponseClass());
        }

        if (!clientMethodReturningResult.isPresent()) {
            throw new UnsupportedOperationException("Failed to find a method returning the expected response type, this should never happen.");
        }

        try {
            // This is necessary because these methods are not accessible by default
            clientMethodReturningResult.get().setAccessible(true);
            return (AwsResponse) clientMethodReturningResult.get().invoke(sdkClient, awsRequest);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof SdkClientException) {
                SdkClientException sdkClientException = (SdkClientException) e.getTargetException();

                if (sdkClientException.getMessage().contains("Unable to execute HTTP request")) {
                    log.error("Unable to connect to the API.  Do you have an Internet connection?");
                    return null;
                }
            }

            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    private Class<? extends AwsResponse> getResponseClass() {
        synchronized (this) {
            if (!optionalResponseClass.isPresent()) {
                String requestClassName = awsRequestClass.getName();
                String responseClassName = requestClassName.replaceAll("Request$", "Response");

                try {
                    optionalResponseClass = Optional.of((Class<? extends AwsResponse>) Class.forName(responseClassName));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    throw new UnsupportedOperationException(e);
                }
            }
        }

        return optionalResponseClass.get();
    }

    private List<T> getResultData() {
        if (clientMethodReturningListT == null) {
            // Look for a public method that takes no arguments and returns a List<T>.  If zero or more than one exists, fail.
            clientMethodReturningListT = getMethodWithParameterAndReturnType(getResponseClass(), null, new TypeToken<List<T>>(getClass()) {
            }.getRawType());
        }

        if (!clientMethodReturningListT.isPresent()) {
            throw new UnsupportedOperationException("Failed to find a method returning the expected list type, this should never happen.");
        }

        try {
            return (List<T>) clientMethodReturningListT.get().invoke(awsResponse);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    private String getNextToken() {
        if (clientGetMethodReturningString == null) {
            // Look for a public method that takes no arguments and returns a string that matches our list of expected names.  If zero or more than one exists, fail.
            clientGetMethodReturningString = getMethodWithParameterReturnTypeAndNames(getResponseClass(), null, String.class, tokenMethodNames);
        }

        if (!clientGetMethodReturningString.isPresent()) {
            // Some methods like S3's listBuckets do not have pagination
            return null;
        }
        try {
            return (String) clientGetMethodReturningString.get().invoke(awsResponse);
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    private void setNextToken(String nextToken) {
        if (!clientGetMethodReturningString.isPresent()) {
            throw new UnsupportedOperationException("Trying to set the next token on a method that does not support pagination, this should never happen.");
        }

        if (clientSetMethodAcceptingString == null) {
            // Look for a public method that takes a string and returns a builder class that matches our list of expected names.  If zero or more than one exists, fail.
            Class<? extends AwsRequest.Builder> builderClass = awsRequest.toBuilder().getClass();
            clientSetMethodAcceptingString = getMethodWithParameterReturnTypeAndNames(builderClass, String.class, builderClass, tokenMethodNames);
        }

        if (!clientSetMethodAcceptingString.isPresent()) {
            throw new UnsupportedOperationException("Failed to find the set next token method, this should never happen.");
        }

        try {
            AwsRequest.Builder builder = awsRequest.toBuilder();
            clientSetMethodAcceptingString.get().setAccessible(true);
            clientSetMethodAcceptingString.get().invoke(builder, nextToken);
            awsRequest = builder.build();
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            throw new UnsupportedOperationException(e);
        }
    }

    private Optional<Method> getMethodWithParameterAndReturnType(Class clazz, Class parameter, Class returnType) {
        return getMethodWithParameterReturnTypeAndName(clazz, parameter, returnType, null);
    }

    private Optional<Method> getMethodWithParameterReturnTypeAndName(Class clazz, Class parameter, Class returnType, String name) {
        List<String> names = new ArrayList<>();

        if (name != null) {
            names.add(name);
        }

        return getMethodWithParameterReturnTypeAndNames(clazz, parameter, returnType, names);
    }

    private Optional<Method> getMethodWithParameterReturnTypeAndNames(Class clazz, Class parameter, Class returnType, List<String> names) {
        Method returnMethod = null;

        for (Method method : clazz.getMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                // Not public, ignore
                continue;
            }

            String methodName = method.getName();

            if (methodsToIgnore.contains(methodName)) {
                // Always ignore these methods
                continue;
            }

            if ((names.size() > 0) && (!names.contains(method.getName()))) {
                // Not an expected name, ignore
                continue;
            }

            if (parameter != null) {
                if (method.getParameterCount() != 1) {
                    // Not the right number of parameters, ignore
                    continue;
                }

                if (!method.getParameterTypes()[0].equals(parameter)) {
                    // Not the right parameter type, ignore
                    continue;
                }
            }

            if (!method.getReturnType().isAssignableFrom(returnType)) {
                // Not the right return type, ignore
                continue;
            }

            if (returnMethod != null) {
                // More than one match found, fail
                throw new UnsupportedOperationException("Multiple methods found, cannot continue");
            }

            returnMethod = method;
        }

        return Optional.ofNullable(returnMethod);
    }
}
