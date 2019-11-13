package io.axoniq.axonserver.enterprise.taskscheduler;

import io.axoniq.axonserver.enterprise.jpa.Task;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * @author Marc Gathier
 * @since 4.3
 */
@Service
public class ScheduledTaskExecutorImpl implements ScheduledTaskExecutor {

    private final ApplicationContext applicationContext;
    private final TaskPayloadSerializer serializer;

    public ScheduledTaskExecutorImpl(ApplicationContext applicationContext,
                                     TaskPayloadSerializer serializer) {
        this.applicationContext = applicationContext;
        this.serializer = serializer;
    }


    @Override
    public CompletableFuture<Void> executeTask(Task task) {
        try {
            ScheduledTask job = (ScheduledTask) applicationContext.getBean(Class.forName(task.getTaskExecutor()));
            Object payload = serializer.deserialize(task.getPayload());
            return job.execute(payload);
        } catch (ClassNotFoundException | BeansException ex) {
            throw new RuntimeException("Could not find handler for task as a Spring bean: " + task.getTaskExecutor(),
                                       ex);
        }
    }
}
