package com.chinasofti.huateng.micro.web.client;

import io.micrometer.common.KeyValue;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientHttpObservationDocumentation;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;

@Component
public class SimpleClientRequestObservationConvention extends DefaultClientRequestObservationConvention {
    @Override
    protected KeyValue exception(ClientRequestObservationContext context) {
        Throwable error = context.getError();
        if (error != null) {
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, error.getClass().getSimpleName() + "-" + error.getMessage());
        } else {
            return KeyValue.of(ClientHttpObservationDocumentation.LowCardinalityKeyNames.EXCEPTION, "none");
        }
    }
}
