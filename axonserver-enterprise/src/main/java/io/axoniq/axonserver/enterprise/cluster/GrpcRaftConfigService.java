package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.grpc.Confirmation;
import io.axoniq.axonserver.grpc.internal.Application;
import io.axoniq.axonserver.grpc.internal.Context;
import io.axoniq.axonserver.grpc.internal.ContextMember;
import io.axoniq.axonserver.grpc.internal.ContextNames;
import io.axoniq.axonserver.grpc.internal.NodeContext;
import io.axoniq.axonserver.grpc.internal.NodeInfo;
import io.axoniq.axonserver.grpc.internal.RaftConfigServiceGrpc;
import io.axoniq.axonserver.grpc.internal.User;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Author: marc
 */
@Service
public class GrpcRaftConfigService extends RaftConfigServiceGrpc.RaftConfigServiceImplBase {
    private final GrpcRaftController raftController;

    public GrpcRaftConfigService(GrpcRaftController raftController) {
        this.raftController = raftController;
    }

    @Override
    public void initCluster(ContextNames request, StreamObserver<Confirmation> responseObserver) {
        raftController.localRaftConfigService().init(request.getContextsList());
        responseObserver.onNext(Confirmation.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void joinCluster(NodeInfo request, StreamObserver<Confirmation> responseObserver) {
        raftController.localRaftConfigService().join(request);
        responseObserver.onNext(Confirmation.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void createContext(Context request, StreamObserver<Confirmation> responseObserver) {
        raftController.localRaftConfigService().addContext(request.getName(),
                                                           request.getMembersList()
                                                                  .stream()
                                                                  .map(ContextMember::getNodeId)
                                                                  .collect(Collectors.toList()));
        responseObserver.onNext(Confirmation.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void addNodeToContext(NodeContext request, StreamObserver<Confirmation> responseObserver) {
        raftController.localRaftConfigService().addNodeToContext(request.getContext(), request.getNode());
        responseObserver.onNext(Confirmation.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteNodeFromContext(NodeContext request, StreamObserver<Confirmation> responseObserver) {
        raftController.localRaftConfigService().deleteNodeFromContext(request.getContext(), request.getNode());
        responseObserver.onNext(Confirmation.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void addApplication(Application request, StreamObserver<Confirmation> responseObserver) {
        super.addApplication(request, responseObserver);
    }

    @Override
    public void updateApplication(Application request, StreamObserver<Confirmation> responseObserver) {
        super.updateApplication(request, responseObserver);
    }

    @Override
    public void deleteApplication(Application request, StreamObserver<Confirmation> responseObserver) {
        super.deleteApplication(request, responseObserver);
    }

    @Override
    public void addUser(User request, StreamObserver<Confirmation> responseObserver) {
        super.addUser(request, responseObserver);
    }

    @Override
    public void updateUser(User request, StreamObserver<Confirmation> responseObserver) {
        super.updateUser(request, responseObserver);
    }

    @Override
    public void deleteUser(User request, StreamObserver<Confirmation> responseObserver) {
        super.deleteUser(request, responseObserver);
    }
}