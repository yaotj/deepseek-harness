package org.qd.scg.predicate.parser;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.ServerRequest;

@Component
public class HeaderPredicateParser implements PredicateParser {

    @Override
    public boolean supports(String definition) {
        return definition != null && definition.startsWith("Header=");
    }

    @Override
    public RequestPredicate parse(String definition) {
        String header = definition.substring("Header=".length());
        int commaIdx = header.indexOf(',');
        if (commaIdx < 0) {
            // 只检查 header 存在
            String name = header.trim();
            return request -> request.headers().header(name) != null;
        }
        String name = header.substring(0, commaIdx).trim();
        String value = header.substring(commaIdx + 1).trim();
        // 检查 header 值匹配
        return request -> {
            var headers = request.headers().header(name);
            return headers != null && !headers.isEmpty() && value.equals(headers.get(0));
        };
    }
}
