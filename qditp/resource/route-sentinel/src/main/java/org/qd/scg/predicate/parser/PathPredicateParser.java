package org.qd.scg.predicate.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;

@Component
public class PathPredicateParser implements PredicateParser {

    @Override
    public boolean supports(String definition) {
        return definition != null && definition.startsWith("Path=");
    }

    @Override
    public RequestPredicate parse(String definition) {
        String path = definition.substring("Path=".length());
        return RequestPredicates.path(path);
    }
}
