package org.qd.scg.predicate;

import java.util.List;

import org.qd.scg.predicate.parser.PredicateParser;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class RoutePredicateFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RoutePredicateFactory.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final List<PredicateParser> parsers;
    private final ObjectMapper objectMapper;

    public RoutePredicateFactory(List<PredicateParser> parsers, ObjectMapper objectMapper) {
        this.parsers = parsers;
        this.objectMapper = objectMapper;
    }

    public RequestPredicate parse(String predicatesJson) {
        if (predicatesJson == null || predicatesJson.isBlank()) {
            return null;
        }
        try {
            List<String> definitions = objectMapper.readValue(predicatesJson, STRING_LIST_TYPE);
            RequestPredicate result = null;
            for (String definition : definitions) {
                boolean matched = false;
                for (PredicateParser parser : parsers) {
                    if (parser.supports(definition)) {
                        RequestPredicate predicate = parser.parse(definition);
                        result = result == null ? predicate : result.or(predicate);
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    log.warn("未找到支持该 predicate 定义的解析器: {}", definition);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 predicates 失败: {}", predicatesJson, e);
            return null;
        }
    }
}
