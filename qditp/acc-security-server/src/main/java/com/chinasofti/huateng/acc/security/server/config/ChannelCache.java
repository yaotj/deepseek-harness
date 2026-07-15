package com.chinasofti.huateng.acc.security.server.config;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChannelCache {
    private static final Logger logger = LoggerFactory.getLogger(ChannelCache.class);

    private static final CopyOnWriteArrayList<Channel> cacheList = new CopyOnWriteArrayList<>();
    private static final Set<String> exclusiveChannelIds = new HashSet<>();

    private static int index = 0;
    private static int size = 0;

    public static synchronized void set(Channel channel) {
        cacheList.add(channel);
        size++;
        logger.info("add channel {}, current size {}", channel, getSize());
    }

    public static synchronized Channel get() {
        if (cacheList.isEmpty()) {
            throw new HsmUnavailableException("no available channel");
        }
        int currentSize = cacheList.size();
        for (int i = 0; i < currentSize; i++) {
            if (index == currentSize) {
                index = 0;
            }
            Channel channel = cacheList.get(index++);
            if (channel.isActive() && !exclusiveChannelIds.contains(channel.id().asLongText())) {
                return channel;
            }
        }
        throw new HsmUnavailableException("no idle channel");
    }

    public static synchronized Channel acquireExclusive() {
        Channel channel = get();
        exclusiveChannelIds.add(channel.id().asLongText());
        return channel;
    }

    public static synchronized void releaseExclusive(Channel channel) {
        if (channel != null) {
            exclusiveChannelIds.remove(channel.id().asLongText());
        }
    }

    public static synchronized int getSize() {
        return size;
    }

    public static synchronized void clear(Channel channel) {
        if (channel == null) {
            return;
        }
        exclusiveChannelIds.remove(channel.id().asLongText());
        channel.close();
        if (cacheList.remove(channel)) {
            size--;
        }
        logger.info("remove channel {}, current size {}", channel, getSize());
    }
}
