package io.axoniq.axonserver.enterprise.cluster;

import io.axoniq.axonserver.applicationevents.SubscriptionEvents;
import io.axoniq.axonserver.applicationevents.TopologyEvents;
import io.axoniq.axonserver.enterprise.cluster.events.ClusterEvents;
import io.axoniq.axonserver.grpc.command.CommandSubscription;
import io.axoniq.axonserver.grpc.internal.CommandHandlerStatus;
import io.axoniq.axonserver.grpc.internal.ConnectorCommand;
import io.axoniq.axonserver.grpc.internal.QueryHandlerStatus;
import io.axoniq.axonserver.grpc.query.QuerySubscription;
import io.axoniq.axonserver.message.ClientIdentification;
import io.axoniq.axonserver.message.command.CommandRegistrationCache;
import io.axoniq.axonserver.message.command.DirectCommandHandler;
import io.axoniq.axonserver.message.query.DirectQueryHandler;
import io.axoniq.axonserver.message.query.QueryDefinition;
import io.axoniq.axonserver.message.query.QueryRegistrationCache;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ensures that subscriptions from this AxonHub node are known on all connected AxonHub nodes
 * @author Marc Gathier
 */
@Component
public class SubscriptionSynchronizer {
    private final CommandRegistrationCache commandRegistrationCache;
    private final QueryRegistrationCache queryRegistrationCache;
    private final ClusterController clusterController;
    private final Map<ClientIdentification, ContextComponent> connectedClients = new ConcurrentHashMap<>();

    public SubscriptionSynchronizer(CommandRegistrationCache commandRegistrationCache,
                                    QueryRegistrationCache queryRegistrationCache,
                                    ClusterController clusterController) {
        this.commandRegistrationCache = commandRegistrationCache;
        this.queryRegistrationCache = queryRegistrationCache;
        this.clusterController = clusterController;
    }

    @EventListener
    public void on(ClusterEvents.AxonServerInstanceConnected event) {

        connectedClients.forEach((key, value) ->
                                         event.getRemoteConnection().clientStatus(key.getContext(),
                                                                                  value.getComponent(),
                                                                                  key.getClient(),
                                                                                  true));

        commandRegistrationCache.getAll().forEach((member, commands) -> {
            if (member instanceof DirectCommandHandler) {
                commands.forEach(command ->
                                         event.getRemoteConnection().subscribeCommand(command.getContext(),
                                                                                      command.getCommand(),
                                                                                      member.getClient().getClient(),
                                                                                      member.getComponentName()));
            }
        });

        queryRegistrationCache.getAll().forEach(
                (query, handlersPerComponentMap) -> handlersPerComponentMap.forEach(
                        (component, handlers) -> handlers.forEach(handler -> {
                            if (handler instanceof DirectQueryHandler) {
                                event.getRemoteConnection().subscribeQuery(query, queryRegistrationCache.getResponseTypes(query), component, handler.getClient().getClient());
                            }
                        })));

    }

    @EventListener
    public void on(ClusterEvents.AxonServerInstanceDisconnected event) {
        clusterController.closeConnection(event.getNodeName());
    }

    @EventListener
    public void on(SubscriptionEvents.SubscribeQuery event) {
        if (!event.isProxied()) {
            QuerySubscription subscription = event.getSubscription();
            QueryDefinition queryDefinition = new QueryDefinition(event.getContext(), subscription);
            clusterController.activeConnections()
                             .forEach(listener -> listener.subscribeQuery(queryDefinition,
                                                                          Collections.singleton(subscription
                                                                                                        .getResultName()),
                                                                          subscription.getComponentName(),
                                                                          subscription.getClientId()));
        }
    }

    @EventListener
    public void on(SubscriptionEvents.UnsubscribeQuery event) {
        if (!event.isProxied()) {
            QuerySubscription subscription = event.getUnsubscribe();
            QueryDefinition queryDefinition = new QueryDefinition(event.getContext(), subscription);
            clusterController.activeConnections()
                             .forEach(listener -> listener.unsubscribeQuery(queryDefinition,
                                                                            subscription.getComponentName(),
                                                                            subscription.getClientId()));
        }
    }

    @EventListener
    public void on(SubscriptionEvents.SubscribeCommand event) {
        if (!event.isProxied()) {
            CommandSubscription request = event.getRequest();
            clusterController.activeConnections()
                             .forEach(remoteConnection ->
                                              remoteConnection.subscribeCommand(event.getContext(),
                                                                                request.getCommand(),
                                                                                request.getClientId(),
                                                                                request.getComponentName()));
        }
    }

    @EventListener
    public void on(SubscriptionEvents.UnsubscribeCommand event) {
        if (!event.isProxied()) {
            CommandSubscription request = event.getRequest();
            clusterController.activeConnections().forEach(remoteConnection -> remoteConnection
                    .unsubscribeCommand(event.getContext(),
                                        request.getCommand(),
                                        request.getClientId(),
                                        request.getComponentName()));
        }
    }

    @EventListener
    public void on(TopologyEvents.ApplicationDisconnected event) {
        if (!event.isProxied()) {
            clusterController.activeConnections().forEach(remoteConnection -> remoteConnection
                    .clientStatus(event.getContext(), event.getComponentName(),
                                  event.getClient(), false));
            connectedClients.remove(event.clientIdentification());
        }
    }

    @EventListener
    public void on(TopologyEvents.QueryHandlerDisconnected event) {
        if (!event.isProxied()) {
            QueryHandlerStatus status = QueryHandlerStatus.newBuilder()
                                                          .setClientName(event.getClient())
                                                          .setContext(event.getContext())
                                                          .setConnected(false)
                                                          .build();
            ConnectorCommand command = ConnectorCommand.newBuilder().setQueryHandlerStatus(status).build();
            clusterController.activeConnections().forEach(remoteConnection -> remoteConnection.publish(command));
        }
    }

    @EventListener
    public void on(TopologyEvents.CommandHandlerDisconnected event) {
        if (!event.isProxied()) {
            CommandHandlerStatus status = CommandHandlerStatus.newBuilder()
                                                              .setClientName(event.getClient())
                                                              .setContext(event.getContext())
                                                              .setConnected(false)
                                                              .build();
            ConnectorCommand command = ConnectorCommand.newBuilder().setCommandHandlerStatus(status).build();
            clusterController.activeConnections().forEach(remoteConnection -> remoteConnection.publish(command));
        }
    }

    @EventListener
    public void on(TopologyEvents.ApplicationConnected event) {
        if (!event.isProxied()) {
            clusterController.activeConnections().forEach(remoteConnection ->
                                                                  remoteConnection.clientStatus(event.getContext(),
                                                                                                event.getComponentName(),
                                                                                                event.getClient(),
                                                                                                true));
            connectedClients.put(event.clientIdentification(), new ContextComponent(event.getContext(), event.getComponentName()));
        }
    }


    private static class ContextComponent {

        private final String context;
        private final String component;


        private ContextComponent(String context, String component) {
            this.context = context;
            this.component = component;
        }

        public String getContext() {
            return context;
        }

        public String getComponent() {
            return component;
        }
    }
}