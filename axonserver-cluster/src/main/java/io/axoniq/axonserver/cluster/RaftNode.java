package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.grpc.cluster.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class RaftNode {
    private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t= new Thread(r);
            t.setName("Apply-" + raftGroup.raftConfiguration().groupId());
            return t;
        }
    });

    private static final Logger logger = LoggerFactory.getLogger(RaftNode.class);

    private final String nodeId;
    private final RaftGroup raftGroup;
    private final MembershipStateFactory stateFactory;
    private final AtomicReference<MembershipState> state = new AtomicReference<>();
    private final List<Consumer<Entry>> entryConsumer = new CopyOnWriteArrayList<>();
    private final List<Registration> registrations = new CopyOnWriteArrayList<>();
    private volatile Future<?> applyTask;
    private volatile boolean running;

    public RaftNode(String nodeId, RaftGroup raftGroup) {
        this.nodeId = nodeId;
        this.raftGroup = raftGroup;
        stateFactory = new CachedStateFactory(new DefaultStateFactory(raftGroup, this::updateState));
        updateState(stateFactory.idleState(nodeId));
    }

    private synchronized void updateState(MembershipState newState) {
        Optional.ofNullable(state.get()).ifPresent(MembershipState::stop);
        logger.debug("Updating state of {} from {} to {}", nodeId, state.get(), newState);
        state.set(newState);
        newState.start();
    }

    public void start() {
        running = true;
        updateState(stateFactory.followerState());
        applyTask = executor.submit(() -> applyEntries());
    }

    public synchronized AppendEntriesResponse appendEntries(AppendEntriesRequest request) {
        return state.get().appendEntries(request);
    }

    public synchronized RequestVoteResponse requestVote(RequestVoteRequest request) {
        return state.get().requestVote(request);
    }

    public synchronized InstallSnapshotResponse installSnapshot(InstallSnapshotRequest request) {
        return state.get().installSnapshot(request);
    }

    public void stop() {
        running = false;
        applyTask.cancel(true);
        applyTask = null;
        updateState(stateFactory.idleState(nodeId));
        registrations.forEach(Registration::cancel);
    }

    String nodeId() {
        return nodeId;
    }

    public boolean isLeader() {
        return state.get().isLeader();
    }

    public Runnable registerEntryConsumer(Consumer<Entry> entryConsumer) {
        this.entryConsumer.add(entryConsumer);
        return () -> this.entryConsumer.remove(entryConsumer);
    }

    private void applyEntries() {
        raftGroup.localLogEntryStore().registerCommitListener(Thread.currentThread());
        while(running) {
            int retries = 1;
            while( retries > 0) {
                int applied = raftGroup.localLogEntryStore().applyEntries(e -> applyEntryConsumers(e));
                if( applied > 0 ) {
                    retries = 0;
                } else {
                    retries--;
                }

                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
        }
    }

    private void applyEntryConsumers(Entry e) {
        logger.trace("{}: apply {}", nodeId, e.getIndex());
        entryConsumer.forEach(consumer -> consumer.accept(e));
        state.get().applied(e);
    }


    public CompletableFuture<Void> appendEntry(String entryType, byte[] entryData) {
        logger.trace("{}: append entry {}", nodeId, entryType);
        return state.get().appendEntry(entryType, entryData);
    }

    public CompletableFuture<Void> addNode(Node node) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Void> removeNode(String nodeId) {
        throw new UnsupportedOperationException();
    }

    public String groupId() {
        return raftGroup.raftConfiguration().groupId();
    }
}
