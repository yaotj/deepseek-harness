package com.chinasofti.huateng.micro.web.utils;

import org.springframework.util.AntPathMatcher;

import java.util.List;

public class PathMatcher {

    public static boolean match(List<String> patterns, String path) {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        for (String pattern : patterns) {
            boolean b = antPathMatcher.match(pattern, path);
            if (b) {
                return true;
            }
        }
        return false;
    }

}
