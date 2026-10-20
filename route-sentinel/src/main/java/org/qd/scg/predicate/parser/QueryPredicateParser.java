package org.qd.scg.predicate.parser;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public class QueryPredicateParser implements PredicateParser {

    @Override
    public boolean supports(String definition) {
        return definition != null && definition.startsWith("Query=");
    }

    @Override
    public RequestPredicate parse(String definition) {
        String param = definition.substring("Query=".length());
        int commaIdx = param.indexOf(',');
        if (commaIdx < 0) {
            // 只检查参数存在
            String key = param.trim();
            return request -> request.params().containsKey(key);
        }
        String key = param.substring(0, commaIdx).trim();
        String value = param.substring(commaIdx + 1).trim();
        // 检查参数值匹配
        return request -> {
            String actual = request.params().getFirst(key);
            return value.equals(actual);
        };
    }
}
