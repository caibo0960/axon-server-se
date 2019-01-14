package io.axoniq.axonserver.message.command;

import com.google.common.collect.Sets;
import io.axoniq.axonserver.ProcessingInstructionHelper;
import io.axoniq.axonserver.applicationevents.TopologyEvents;
import io.axoniq.axonserver.grpc.SerializedCommand;
import io.axoniq.axonserver.grpc.SerializedCommandProviderInbound;
import io.axoniq.axonserver.grpc.SerializedCommandResponse;
import io.axoniq.axonserver.grpc.command.Command;
import io.axoniq.axonserver.grpc.command.CommandResponse;
import io.axoniq.axonserver.message.ClientIdentification;
import io.axoniq.axonserver.metric.DefaultMetricCollector;
import io.axoniq.axonserver.topology.Topology;
import io.axoniq.axonserver.util.CountingStreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.*;
import org.junit.runner.*;
import org.mockito.*;
import org.mockito.runners.*;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Author: marc
 */
@RunWith(MockitoJUnitRunner.class)
public class CommandDispatcherTest {
    private CommandDispatcher commandDispatcher;
    private CommandMetricsRegistry metricsRegistry;
    @Mock
    private CommandCache commandCache;
    @Mock
    private CommandRegistrationCache registrations;

    @Before
    public void setup() {
        metricsRegistry = new CommandMetricsRegistry(new SimpleMeterRegistry(),
                                                     new DefaultMetricCollector());
        commandDispatcher = new CommandDispatcher(registrations, commandCache, metricsRegistry);
        ConcurrentMap<CommandHandler, Set<CommandRegistrationCache.RegistrationEntry>> dummyRegistrations = new ConcurrentHashMap<>();
        Set<CommandRegistrationCache.RegistrationEntry> commands =
                Sets.newHashSet(new CommandRegistrationCache.RegistrationEntry(Topology.DEFAULT_CONTEXT, "Command"));
        dummyRegistrations.put(new DirectCommandHandler(new CountingStreamObserver<>(), new ClientIdentification(Topology.DEFAULT_CONTEXT, "client"),"component"),
                commands);
        when( registrations.getAll()).thenReturn(dummyRegistrations);
    }

    @Test
    public void unregisterCommandHandler()  {
        when(registrations.getCommandsFor(anyObject())).thenReturn(Collections.singleton("One"));
        commandDispatcher.on(new TopologyEvents.ApplicationDisconnected(null, null, "client"));
    }

    @Test
    public void dispatch()  {
        CountingStreamObserver<SerializedCommandResponse> responseObserver = new CountingStreamObserver<>();
        Command request = Command.newBuilder()
                .addProcessingInstructions(ProcessingInstructionHelper.routingKey("1234"))
                .setName("Command")
                .setMessageIdentifier("12")
                .build();
        CountingStreamObserver<SerializedCommandProviderInbound> commandProviderInbound = new CountingStreamObserver<>();
        DirectCommandHandler result = new DirectCommandHandler(commandProviderInbound, new ClientIdentification( Topology.DEFAULT_CONTEXT, "client"),"component");
        when(registrations.getHandlerForCommand(eq(Topology.DEFAULT_CONTEXT), anyObject(), anyObject())).thenReturn(result);

        commandDispatcher.dispatch(Topology.DEFAULT_CONTEXT, new SerializedCommand(request), response -> {
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }, false);
        assertEquals(1, commandDispatcher.getCommandQueues().getSegments().get("default/client").size());
        assertEquals(0, responseObserver.count);
        Mockito.verify(commandCache, times(1)).put(eq("12"), anyObject());

    }
    @Test
    public void dispatchNotFound() {
        CountingStreamObserver<SerializedCommandResponse> responseObserver = new CountingStreamObserver<>();
        Command request = Command.newBuilder()
                .addProcessingInstructions(ProcessingInstructionHelper.routingKey("1234"))
                .setName("Command")
                .setMessageIdentifier("12")
                .build();
        when(registrations.getHandlerForCommand(any(), anyObject(), anyObject())).thenReturn(null);

        commandDispatcher.dispatch(Topology.DEFAULT_CONTEXT, new SerializedCommand(request), response -> {
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }, false);
        assertEquals(1, responseObserver.count);
        assertNotEquals("", responseObserver.responseList.get(0).getErrorCode());
        Mockito.verify(commandCache, times(0)).put(eq("12"), anyObject());

    }

    @Test
    public void dispatchProxied() throws Exception {
        CountingStreamObserver<SerializedCommandResponse> responseObserver = new CountingStreamObserver<>();
        Command request = Command.newBuilder()
                .addProcessingInstructions(ProcessingInstructionHelper.targetClient("client"))
                .setName("Command")
                .setMessageIdentifier("12")
                .build();
        ClientIdentification clientIdentification = new ClientIdentification(Topology.DEFAULT_CONTEXT,"client");
        CountingStreamObserver<SerializedCommandProviderInbound> commandProviderInbound = new CountingStreamObserver<>();
        DirectCommandHandler result = new DirectCommandHandler(commandProviderInbound, clientIdentification, "component");
        when(registrations.findByClientAndCommand(eq(clientIdentification), anyObject())).thenReturn(result);

        commandDispatcher.dispatch(Topology.DEFAULT_CONTEXT, new SerializedCommand(request.toByteArray(), "client", request.getMessageIdentifier()), responseObserver::onNext, true);
        assertEquals(1, commandDispatcher.getCommandQueues().getSegments().get(clientIdentification.toString()).size());
        assertEquals("12", commandDispatcher.getCommandQueues().take(clientIdentification.toString()).command().getMessageIdentifier());
        assertEquals(0, responseObserver.count);
        Mockito.verify(commandCache, times(1)).put(eq("12"), anyObject());
    }

    @Test
    public void dispatchProxiedClientNotFound()  {
        CountingStreamObserver<SerializedCommandResponse> responseObserver = new CountingStreamObserver<>();
        Command request = Command.newBuilder()
                .addProcessingInstructions(ProcessingInstructionHelper.routingKey("1234"))
                .setName("Command")
                .setMessageIdentifier("12")
                .build();
        ClientIdentification clientIdentification = new ClientIdentification(Topology.DEFAULT_CONTEXT,"1234");
        when(registrations.findByClientAndCommand(eq(clientIdentification), anyObject())).thenReturn(null);

        commandDispatcher.dispatch(Topology.DEFAULT_CONTEXT, new SerializedCommand(request), responseObserver::onNext, true);
        assertEquals(1, responseObserver.count);
        Mockito.verify(commandCache, times(0)).put(eq("12"), anyObject());
    }

    @Test
    public void handleResponse() {
        AtomicBoolean responseHandled = new AtomicBoolean(false);
        CommandInformation commandInformation = new CommandInformation("TheCommand", (r) -> responseHandled.set(true),
                                                                       new ClientIdentification(Topology.DEFAULT_CONTEXT, "Client"), "Component");
        when(commandCache.remove(any(String.class))).thenReturn(commandInformation);

        commandDispatcher.handleResponse(new SerializedCommandResponse(CommandResponse.newBuilder().build()), false);
        assertTrue(responseHandled.get());
        assertEquals(1, metricsRegistry.commandMetric("TheCommand", "default/Client", "Component").getCount());

    }
}