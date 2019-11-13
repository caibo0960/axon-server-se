package io.axoniq.axonserver.cluster.election;

import io.axoniq.axonserver.cluster.RaftGroup;
import io.axoniq.axonserver.cluster.RaftPeer;
import io.axoniq.axonserver.grpc.cluster.RequestVoteRequest;
import io.axoniq.axonserver.grpc.cluster.RequestVoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static java.lang.String.format;

/**
 * @author Sara Pellegrini
 * @since 4.1
 */
public class DefaultElection implements Election {

    private final Logger logger = LoggerFactory.getLogger(DefaultElection.class);

    private final RequestVoteRequest requestPrototype;
    private final BiConsumer<Long, String> termUpdateHandler;
    private final ElectionStore electionStore;
    private final Iterable<RaftPeer> otherNodes;
    private final VoteStrategy voteStrategy;
    private final boolean disruptLeader;

    public DefaultElection(RaftGroup raftGroup, BiConsumer<Long, String> termUpdateHandler,
                           Iterable<RaftPeer> otherNodes, boolean disruptLeader) {
        this(RequestVoteRequest.newBuilder()
                               .setGroupId(raftGroup.raftConfiguration().groupId())
                               .setCandidateId(raftGroup.localNode().nodeId())
                               .setTerm(raftGroup.localElectionStore().currentTerm()+1)
                               .setLastLogIndex(raftGroup.localLogEntryStore().lastLog().getIndex())
                               .setLastLogTerm(raftGroup.localLogEntryStore().lastLog().getTerm())
                               .build(),
             termUpdateHandler,
             raftGroup.localElectionStore(),
             otherNodes, () -> raftGroup.minActiveBackups(), disruptLeader);
    }

    public DefaultElection(RequestVoteRequest requestPrototype,
                           BiConsumer<Long, String> termUpdateHandler,
                           ElectionStore electionStore,
                           Iterable<RaftPeer> otherNodes,

                           Supplier<Integer> minActiveBackupsProvider,
                           boolean disruptLeader) {
        this(requestPrototype,
             termUpdateHandler,
             electionStore,
             otherNodes,
             new PrimaryAndVotingMajorityStrategy(otherNodes, minActiveBackupsProvider), disruptLeader);
    }

    public DefaultElection(RequestVoteRequest requestPrototype,
                           BiConsumer<Long, String> termUpdateHandler,
                           ElectionStore electionStore,
                           Iterable<RaftPeer> otherNodes, VoteStrategy voteStrategy, boolean disruptLeader) {
        this.requestPrototype = requestPrototype;
        this.termUpdateHandler = termUpdateHandler;
        this.electionStore = electionStore;
        this.otherNodes = otherNodes;
        this.voteStrategy = voteStrategy;
        this.disruptLeader = disruptLeader;
    }

    public Mono<Result> result(){
        return Mono.create(sink -> {
            String cause = format("%s is starting a new election, so increases its term from %s to %s", me(), currentTerm(), electionTerm());
            updateCurrentTerm(electionTerm(), cause);
            electionStore.markVotedFor(me());
            logger.info("{}: Starting election from {} in term {}", groupId(), me(), currentTerm());
            voteStrategy.isWon().thenAccept(isWon -> notifyElectionCompleted(isWon.won(), sink));
            voteStrategy.registerVoteReceived(me(), true);
            otherNodes.forEach(node -> requestVote(request(), node, sink));
        });
    }

    private void requestVote(RequestVoteRequest request, RaftPeer node, MonoSink<Result> sink) {
        node.requestVote(request).thenAccept(response -> this.onVoteResponse(response, sink));
    }

    private void onVoteResponse(RequestVoteResponse response, MonoSink<Result> sink){
        String voter = response.getResponseHeader().getNodeId();
        logger.trace("{} - currentTerm {} VoteResponse {}", voter, currentTerm(), response);
        if (response.getTerm() > currentTerm()) {
            String message = format("%s received RequestVoteResponse with greater term (%s > %s) from %s",
                                    me(), response.getTerm(), currentTerm(), voter);
            updateCurrentTerm(response.getTerm(), message);
            sink.success(result(false, message));
            return;
        }

        //The candidate can receive a response with lower term if the voter is receiving regular heartbeat from a leader.
        //In this case, the voter recognizes any request of vote as disruptive, refuses the vote and does't update its term.
        if (response.getTerm() < currentTerm()) {
            return;
        }
        voteStrategy.registerVoteReceived(voter, response.getVoteGranted());
    }

    private RequestVoteRequest request(){
        return RequestVoteRequest.newBuilder(requestPrototype)
                                 .setDisruptAllowed(disruptLeader)
                                 .setRequestId(UUID.randomUUID().toString()).build();
    }

    private String me(){
        return requestPrototype.getCandidateId();
    }

    private String groupId(){
        return requestPrototype.getGroupId();
    }

    private long electionTerm(){
        return requestPrototype.getTerm();
    }

    private long currentTerm(){
        return electionStore.currentTerm();
    }

    private void updateCurrentTerm(long term, String cause){
        termUpdateHandler.accept(term, cause);
    }

    private void notifyElectionCompleted(boolean result, MonoSink<Result> sink) {
        String electionResult = result ? "won" : "lost";
        String msg = format("%s: Election for term %s is %s by %s (%s)",
                            groupId(), electionTerm(), electionResult, me(), voteStrategy);
        sink.success(result(result, msg));
    }


    private Result result(boolean won, String cause) {
        return new Result() {
            @Override
            public boolean won() {
                return won;
            }

            @Override
            public String cause() {
                return cause;
            }
        };
    }
}
