package org.qd.scg.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
public class RouteFilterFactory {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RouteFilterFactory.class);
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public RouteFilterFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Function<ServerRequest, ServerRequest>> parse(String filtersJson) {
        List<Function<ServerRequest, ServerRequest>> result = new ArrayList<>();
        if (filtersJson == null || filtersJson.isBlank() || "[]".equals(filtersJson.trim())) {
            return result;
        }
        try {
            List<String> definitions = objectMapper.readValue(filtersJson, STRING_LIST_TYPE);
            for (String definition : definitions) {
                Function<ServerRequest, ServerRequest> filter = parseOne(definition);
                if (filter != null) {
                    result.add(filter);
                }
            }
        } catch (Exception e) {
            log.warn("解析 filters 失败: {}", filtersJson, e);
        }
        return result;
    }

    private Function<ServerRequest, ServerRequest> parseOne(String definition) {
        if (definition == null || definition.isBlank()) {
            return null;
        }
        int eqIdx = definition.indexOf('=');
        if (eqIdx < 0) {
            log.warn("不支持的 filter 定义（缺少 '='）: {}", definition);
            return null;
        }
        String name = definition.substring(0, eqIdx).trim();
        String args = definition.substring(eqIdx + 1).trim();
        return switch (name) {
            case "RewritePath" -> parseRewritePath(args, definition);
            default -> {
                log.warn("不支持的 filter 类型: {}", name);
                yield null;
            }
        };
    }

    private Function<ServerRequest, ServerRequest> parseRewritePath(String args, String definition) {
        // Format: RewritePath=regex,replacement
        int commaIdx = args.indexOf(',');
        if (commaIdx < 0) {
            log.warn("RewritePath filter 参数格式错误（缺少 ','）: {}", definition);
            return null;
        }
        String regex = args.substring(0, commaIdx).trim();
        String replacement = args.substring(commaIdx + 1).trim();
        if (regex.isEmpty() || replacement.isEmpty()) {
            log.warn("RewritePath filter 参数为空: {}", definition);
            return null;
        }
        return BeforeFilterFunctions.rewritePath(regex, replacement);
    }
}
