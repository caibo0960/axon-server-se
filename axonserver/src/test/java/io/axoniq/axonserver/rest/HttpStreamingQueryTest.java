package io.axoniq.axonserver.rest;

import io.axoniq.axonserver.grpc.event.Event;
import io.axoniq.axonserver.grpc.event.EventWithToken;
import io.axoniq.axonserver.localstorage.EventStorageEngine;
import io.axoniq.axonserver.localstorage.EventStoreFactory;
import io.axoniq.axonserver.localstorage.EventTypeContext;
import io.axoniq.axonserver.localstorage.LocalEventStore;
import io.axoniq.axonserver.localstorage.SerializedEvent;
import io.axoniq.axonserver.localstorage.SerializedEventWithToken;
import io.axoniq.axonserver.localstorage.SerializedTransactionWithToken;
import io.axoniq.axonserver.localstorage.transaction.PreparedTransaction;
import io.axoniq.axonserver.localstorage.transaction.StorageTransactionManager;
import io.axoniq.axonserver.topology.DefaultEventStoreLocator;
import io.axoniq.axonserver.topology.EventStoreLocator;
import io.axoniq.axonserver.topology.Topology;
import org.junit.*;
import org.springframework.data.util.CloseableIterator;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;

/**
 * @author Marc Gathier
 */
public class HttpStreamingQueryTest {
    private HttpStreamingQuery testSubject;

    @Before
    public void setUp() {
        EventStorageEngine engine = new EventStorageEngine() {
            @Override
            public void init(boolean validate) {

            }

            @Override
            public PreparedTransaction prepareTransaction(List<SerializedEvent> eventList) {
                return null;
            }

            @Override
            public Optional<Long> getLastSequenceNumber(String aggregateIdentifier) {
                return Optional.empty();
            }

            @Override
            public Optional<SerializedEvent> getLastEvent(String aggregateId, long minSequenceNumber) {
                return Optional.empty();
            }

            @Override
            public void streamByAggregateId(String aggregateId, long actualMinSequenceNumber,
                                            Consumer<SerializedEvent> eventConsumer) {

            }

            @Override
            public void streamByAggregateId(String aggregateId, long actualMinSequenceNumber,
                                            long actualMaxSequenceNumber, int maxResults,
                                            Consumer<SerializedEvent> eventConsumer) {

            }

            @Override
            public EventTypeContext getType() {
                return null;
            }

            @Override
            public Iterator<SerializedTransactionWithToken> transactionIterator(long firstToken) {
                return null;
            }

            @Override
            public Iterator<SerializedTransactionWithToken> transactionIterator(long firstToken, long limitToken) {
                return null;
            }

            @Override
            public void query(long minToken, long minTimestamp, Predicate<EventWithToken> consumer) {
                Event event = Event.newBuilder().setAggregateIdentifier("demo").build();
                int i = 100000;
                EventWithToken eventWithToken;
                do {
                    i--;
                    eventWithToken = EventWithToken.newBuilder().setToken(i).setEvent(event).build();
                } while( consumer.test(eventWithToken));

            }

            @Override
            public long getFirstToken() {
                return 0;
            }

            @Override
            public long getTokenAt(long instant) {
                return 0;
            }

            @Override
            public CloseableIterator<SerializedEventWithToken> getGlobalIterator(long start) {
                return null;
            }

            @Override
            public long nextToken() {
                return 0;
            }
        };
        LocalEventStore localEventStore = new LocalEventStore(new EventStoreFactory() {
            @Override
            public EventStorageEngine createEventStorageEngine(String context) {
                return engine;
            }

            @Override
            public EventStorageEngine createSnapshotStorageEngine(String context) {
                return engine;
            }

            @Override
            public StorageTransactionManager createTransactionManager(EventStorageEngine eventStorageEngine) {
                return null;
            }
        });
        localEventStore.initContext(Topology.DEFAULT_CONTEXT, false);
        EventStoreLocator eventStoreLocator = new DefaultEventStoreLocator(localEventStore);
        testSubject = new HttpStreamingQuery(eventStoreLocator);
    }

    @Test
    public void query() throws InterruptedException {
        List<Object> messages = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);


        SseEmitter emitter = new SseEmitter(5000L) {
            @Override
            public void send(Object object) throws IOException {
                messages.add(messages);
            }

            @Override
            public void send(SseEventBuilder builder) throws IOException {
                messages.add(builder.build());
            }

        };
        emitter.onError( t-> {
            t.printStackTrace();
            latch.countDown();
        });

        emitter.onCompletion(()-> {
            latch.countDown();
        });

        emitter.onTimeout(() -> latch.countDown());
        testSubject.query(Topology.DEFAULT_CONTEXT, "aggregateIdentifier = \"demo\" | limit( 10)", "token", emitter);

        latch.await(1, TimeUnit.SECONDS);
        assertEquals(13, messages.size());


    }
}