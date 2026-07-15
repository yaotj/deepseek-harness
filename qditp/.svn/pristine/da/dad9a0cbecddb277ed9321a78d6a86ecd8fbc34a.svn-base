package com.chinasofti.huateng.micro.web.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperatingSystemDetector {
    public static Logger log = LoggerFactory.getLogger(OperatingSystemDetector.class);

    public static String getEnvVar(String envVarName) {
        return System.getenv(envVarName);
    }

    public static boolean isLinuxLog() {
        if (OperatingSystemDetector.isUnixOrLinux()) {
            return true;
        }
        if (getEnvVar("log.profile") == null) {
            return false;
        }
        if (getEnvVar("log.profile").equals("linux")) {
            return true;
        }
        return false;
    }

    private static String detectOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win")) {
            return "Windows";
        } else if (osName.contains("mac")) {
            return "Mac OS";
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            return "Unix/Linux";
        } else {
            return "Unknown";
        }
    }

    public static boolean isWindows() {
        return "Windows".equals(detectOperatingSystem());
    }

    public static boolean isMacOS() {
        return "Mac OS".equals(detectOperatingSystem());
    }

    public static boolean isUnixOrLinux() {
        return "Unix/Linux".equals(detectOperatingSystem());
    }
}
