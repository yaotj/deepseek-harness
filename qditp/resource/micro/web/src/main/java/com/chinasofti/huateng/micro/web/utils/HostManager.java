package com.chinasofti.huateng.micro.web.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class HostManager {

    public static Logger log = LoggerFactory.getLogger(HostManager.class);

    private final Map<String, Boolean> validHostMap = new ConcurrentHashMap<>();

    private final Map<String, Boolean> brokenHostMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static final long DEFAULT_CHECK_INTERVAL_SECONDS = 15;
    public static final int PING_TIMEOUT_MILLIS = 2000;

    public HostManager() {
        this(DEFAULT_CHECK_INTERVAL_SECONDS);
    }


    public HostManager(long checkIntervalSeconds) {
        scheduler.scheduleAtFixedRate(this::checkAndRecoverBrokenHosts, 0, checkIntervalSeconds, TimeUnit.SECONDS);
    }


    public void markHostAsBroken(String host) {
        if (host == null || host.isBlank()) return;
        validHostMap.remove(host);
        brokenHostMap.put(host, Boolean.TRUE);
    }


    public void addValidHost(String host) {
        if (host == null || host.isBlank()) return;
        validHostMap.put(host, Boolean.TRUE);
        brokenHostMap.remove(host);
    }

    public void addNewHost(String host) {
        if (host == null || host.isBlank()) return;
        validHostMap.putIfAbsent(host, Boolean.TRUE);
    }


    public boolean isHostValid(String host) {
        if (host == null || host.isBlank()) return false;
        return !brokenHostMap.containsKey(host) && validHostMap.containsKey(host);
    }


    private void checkAndRecoverBrokenHosts() {
        Set<String> brokenHosts = Set.copyOf(brokenHostMap.keySet());
        if (brokenHosts.size() > 0) {
            for (String host : brokenHosts) {
                try {
                    InetAddress address = InetAddress.getByName(host);
                    boolean reachable = address.isReachable(PING_TIMEOUT_MILLIS);
                    if (reachable) {
                        brokenHostMap.remove(host);
                        validHostMap.put(host, Boolean.TRUE);
                        log.debug("【恢复】host={} 恢复连通，已移入有效列表", host);
                    } else {
                        log.debug("【失效】host={} 仍不可达", host);
                    }
                } catch (Exception e) {
                    log.debug("【异常】探测host={} 异常: {}", host, e.getMessage());
                }
            }
        }
    }

    private final Lock checkLock = new ReentrantLock();

    public void checkValidHostMap(boolean skip) {
        if (skip) {
            return;
        }
        if (!checkLock.tryLock()) {
            return;
        }
        try {
            Set<String> validHosts = Set.copyOf(validHostMap.keySet());
            for (String host : validHosts) {
                try {
                    InetAddress address = InetAddress.getByName(host);
                    boolean reachable = address.isReachable(PING_TIMEOUT_MILLIS);
                    if (!reachable) {
                        validHostMap.remove(host);
                        brokenHostMap.put(host, Boolean.TRUE);
                    }
                } catch (Exception e) {
                    validHostMap.remove(host);
                    brokenHostMap.put(host, Boolean.TRUE);
                }
            }
        } finally {
            checkLock.unlock();
        }
    }

    public Set<String> getValidHosts() {
        return Collections.unmodifiableSet(validHostMap.keySet());
    }

    public Set<String> getBrokenHosts() {
        return Collections.unmodifiableSet(brokenHostMap.keySet());
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
