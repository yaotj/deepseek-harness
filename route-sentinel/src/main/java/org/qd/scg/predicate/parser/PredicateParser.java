package org.qd.scg.predicate.parser;

import org.springframework.web.servlet.function.RequestPredicate;

public interface PredicateParser {

    boolean supports(String definition);

    RequestPredicate parse(String definition);
}
