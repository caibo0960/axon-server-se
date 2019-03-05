package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.grpc.cluster.Node;
import io.axoniq.axonserver.grpc.internal.Context;
import io.axoniq.axonserver.grpc.internal.ContextApplication;
import io.axoniq.axonserver.grpc.internal.LoadBalanceStrategy;
import io.axoniq.axonserver.grpc.internal.ProcessorLBStrategy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Author: marc
 */
public interface RaftGroupService {

    CompletableFuture<Void> addNodeToContext(String context, Node node);

    void getStatus(Consumer<Context> contextConsumer);

    CompletableFuture<Void> initContext(String context, List<Node> nodes);

    CompletableFuture<Void> deleteNode(String context, String node);

    default void stepDown(String context) {
    }

    CompletableFuture<Void> updateApplication(ContextApplication application);

    CompletableFuture<Void> updateLoadBalancingStrategy(String context, LoadBalanceStrategy loadBalancingStrategy);

    CompletableFuture<Void> updateProcessorLoadBalancing(String context, ProcessorLBStrategy processorLBStrategy);

    CompletableFuture<Void> deleteLoadBalancingStrategy(String context, LoadBalanceStrategy loadBalancingStrategy);

    CompletableFuture<Void> deleteContext(String context);

    CompletableFuture<Void> appendEntry(String context, String name, byte[] bytes);
}
