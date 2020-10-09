/*
 * Copyright (c) 2017-2020 AxonIQ B.V. and/or licensed to AxonIQ B.V.
 * under one or more contributor license agreements.
 *
 *  Licensed under the AxonIQ Open Source License Agreement v1.0;
 *  you may not use this file except in compliance with the license.
 *
 */

package io.axoniq.axonserver.interceptor;

import io.axoniq.axonserver.grpc.SerializedCommand;
import io.axoniq.axonserver.grpc.SerializedCommandResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * @author Marc Gathier
 */
@Component
@ConditionalOnMissingBean(CommandInterceptors.class)
public class NoOpCommandInterceptors implements CommandInterceptors {

    @Override
    public SerializedCommand commandRequest(InterceptorContext interceptorContext,
                                            SerializedCommand serializedCommand) {
        return serializedCommand;
    }

    @Override
    public SerializedCommandResponse commandResponse(InterceptorContext interceptorContext,
                                                     SerializedCommandResponse serializedResponse) {
        return serializedResponse;
    }
}
