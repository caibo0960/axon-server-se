package io.axoniq.axonserver.localstorage;

import com.google.protobuf.ByteString;
import io.axoniq.axonserver.grpc.event.Event;

import java.util.List;
import java.util.Objects;

/**
 * Author: marc
 */
public class SerializedTransactionWithToken {

    private final long token;
    private final byte version;
    private final List<SerializedEvent> events;
    private final long index;

    public SerializedTransactionWithToken(long token, byte version,
                                          List<SerializedEvent> events, long index) {
        this.token = token;
        this.version = version;
        this.events = events;
        this.index = index;
    }

    public long getToken() {
        return token;
    }

    public List<SerializedEvent> getEvents() {
        return events;
    }

    public int getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerializedTransactionWithToken that = (SerializedTransactionWithToken) o;
        return token == that.token &&
                Objects.equals(events, that.events);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token, events);
    }

    public int getEventsCount() {
        return events.size();
    }

    public TransactionInformation getTransactionInformation() {
        return new TransactionInformation(version, index);
    }

    public long getIndex() {
        return index;
    }

    public Event getEvents(int i) {
        return events.get(i).asEvent();
    }
}