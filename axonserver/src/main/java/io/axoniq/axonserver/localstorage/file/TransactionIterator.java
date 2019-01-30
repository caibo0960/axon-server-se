package io.axoniq.axonserver.localstorage.file;

import io.axoniq.axonserver.localstorage.SerializedTransactionWithToken;
import org.springframework.data.util.CloseableIterator;

/**
 * Author: marc
 */
public interface TransactionIterator extends CloseableIterator<SerializedTransactionWithToken> {

    @Override
    default void close() {
        // Default no action, defined here to avoid IOException
    }
}
