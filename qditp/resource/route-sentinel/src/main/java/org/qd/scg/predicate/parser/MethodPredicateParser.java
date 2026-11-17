package org.qd.scg.predicate.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;

@Component
public class MethodPredicateParser implements PredicateParser {

    @Override
    public boolean supports(String definition) {
        return definition != null && definition.startsWith("Method=");
    }

    @Override
    public RequestPredicate parse(String definition) {
        String methods = definition.substring("Method=".length());
        String[] parts = methods.split(",");
        if (parts.length == 1) {
            return RequestPredicates.method(org.springframework.http.HttpMethod.valueOf(parts[0].trim().toUpperCase()));
        }
        RequestPredicate result = null;
        for (String part : parts) {
            RequestPredicate p = RequestPredicates.method(
                org.springframework.http.HttpMethod.valueOf(part.trim().toUpperCase()));
            result = result == null ? p : result.or(p);
        }
        return result;
    }
}
