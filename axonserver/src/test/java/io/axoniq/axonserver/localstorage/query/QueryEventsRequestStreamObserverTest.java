/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.localstorage.query;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.QueryEventsRequest;
import io.axoniq.axonserver.grpc.event.QueryEventsResponse;
import io.axoniq.axonserver.localstorage.AggregateReader;
import io.axoniq.axonserver.localstorage.DefaultEventDecorator;
import io.axoniq.axonserver.localstorage.EventStreamReader;
import io.axoniq.axonserver.localstorage.EventWriteStorage;
import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.grpc.stub.StreamObserver;
import org.junit.*;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author Marc Gathier
 */
public class QueryEventsRequestStreamObserverTest {

    private QueryEventsRequestStreamObserver testSubject;
    private final EventStreamReader eventStreamReader = mock(EventStreamReader.class);
    private final AggregateReader aggregateReader = mock(AggregateReader.class);
    private final CompletableFuture<List<QueryEventsResponse>> completableResult = new CompletableFuture<>();

    @Before
    public void setUp() throws Exception {
        EventWriteStorage eventWriteStorage = mock(EventWriteStorage.class);
        StreamObserver<QueryEventsResponse> responseObserver = new StreamObserver<QueryEventsResponse>() {
            private List<QueryEventsResponse> responses = new LinkedList<>();

            @Override
            public void onNext(QueryEventsResponse queryEventsResponse) {
                responses.add(queryEventsResponse);
            }

            @Override
            public void onError(Throwable throwable) {
                completableResult.completeExceptionally(throwable);
            }

            @Override
            public void onCompleted() {
                completableResult.complete(responses);
            }
        };

        testSubject = new QueryEventsRequestStreamObserver(eventWriteStorage,
                                                           eventStreamReader,
                                                           aggregateReader,
                                                           100,
                                                           1000,
                                                           new DefaultEventDecorator(),
                                                           responseObserver);
    }

    @Test
    public void onNext() throws InterruptedException, ExecutionException, TimeoutException {
        doAnswer(invocation -> {
            String aggregateId = invocation.getArgument(0);
            Consumer<SerializedEvent> consumer = invocation.getArgument(3);
            for (int i = 0; i < 10; i++) {
                Event event = Event.newBuilder().setAggregateSequenceNumber(i).setAggregateIdentifier(aggregateId)
                                   .build();
                consumer.accept(new SerializedEvent(event));
            }
            return null;
        }).when(aggregateReader).readEvents(anyString(), anyBoolean(), anyLong(), any(Consumer.class));
        testSubject.onNext(QueryEventsRequest.newBuilder()
                                             .setQuery("aggregateIdentifier = \"12345\"")
                                             .setNumberOfPermits(1000)
                                             .build());

        List<QueryEventsResponse> responses = completableResult.get(2, TimeUnit.SECONDS);
        assertEquals(12, responses.size());
    }
}