package io.axoniq.axonserver.cluster;

import io.axoniq.axonserver.cluster.election.InMemoryElectionStore;
import io.axoniq.axonserver.cluster.replication.InMemoryLogEntryStore;
import io.axoniq.axonserver.grpc.cluster.AppendEntriesRequest;
import io.axoniq.axonserver.grpc.cluster.Entry;
import io.axoniq.axonserver.grpc.cluster.RequestVoteRequest;
import io.axoniq.axonserver.grpc.cluster.RequestVoteResponse;
import org.junit.*;

import java.util.function.Consumer;

import static io.axoniq.axonserver.cluster.TestUtils.assertWithin;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FollowerState}.
 *
 * @author Milan Savic
 */
public class FollowerStateTest {

    private Consumer<MembershipState> transitionHandler;
    private RaftConfiguration raftConfiguration;
    private RaftGroup raftGroup;
    private FollowerState followerState;

    @Before
    public void setup() {
        transitionHandler = mock(Consumer.class);

        raftConfiguration = mock(RaftConfiguration.class);
        when(raftConfiguration.groupId()).thenReturn("defaultGroup");

        raftGroup = mock(RaftGroup.class);
        when(raftGroup.lastAppliedEventSequence()).thenReturn(2L);
        when(raftGroup.localLogEntryStore()).thenReturn(new InMemoryLogEntryStore());
        when(raftGroup.localElectionStore()).thenReturn(new InMemoryElectionStore());
        when(raftGroup.raftConfiguration()).thenReturn(raftConfiguration);

        followerState = FollowerState.builder()
                                     .transitionHandler(transitionHandler)
                                     .raftGroup(raftGroup)
                                     .build();
        followerState.start();
    }

    @Test
    public void testTransitionToCandidateState() throws InterruptedException {
        assertWithin(2, SECONDS, () -> verify(transitionHandler).accept(any(CandidateState.class)));
    }

    @Test
    public void testRequestVoteGranted() {
        RequestVoteResponse response = followerState.requestVote(RequestVoteRequest.newBuilder()
                                                                                   .setGroupId("defaultGroup")
                                                                                   .setTerm(1)
                                                                                   .build());
        assertTrue(response.getVoteGranted());
        assertEquals(1L, response.getTerm());
        assertEquals("defaultGroup", response.getGroupId());
    }

    @Test
    public void testRequestVoteGrantedAfterAppend() {
        followerState.appendEntries(AppendEntriesRequest.newBuilder()
                                                        .setTerm(0L)
                                                        .setCommitIndex(0L)
                                                        .setPrevLogIndex(0L)
                                                        .setPrevLogTerm(0L)
                                                        .setLeaderId("node1")
                                                        .setGroupId("defaultGroup")
                                                        .addEntries(Entry.newBuilder()
                                                                         .setIndex(1L)
                                                                         .setTerm(0L)
                                                                         .build())
                                                        .build());

        RequestVoteResponse response = followerState.requestVote(RequestVoteRequest.newBuilder()
                                                                                   .setGroupId("defaultGroup")
                                                                                   .setLastLogTerm(0L)
                                                                                   .setLastLogIndex(1L)
                                                                                   .setTerm(1)
                                                                                   .build());

        assertTrue(response.getVoteGranted());
        assertEquals(1L, response.getTerm());
        assertEquals("defaultGroup", response.getGroupId());
    }
}
