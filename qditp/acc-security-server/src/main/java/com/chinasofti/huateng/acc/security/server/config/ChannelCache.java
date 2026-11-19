package com.chinasofti.huateng.acc.security.server.config;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 加密机 TCP 连接池。
 *
 * <p>协议为同步命令-响应、报文无流水号（见 SJJ1312-B 程序员手册），
 * 一条连接同一时刻只能承载一个 raw 请求，因此 raw 通路必须独占连接。</p>
 *
 * <p>获取语义：{@link #acquireExclusive(long)} 在池内无空闲连接时
 * <b>带超时阻塞等待</b>（有连接释放即被唤醒），超时仍未拿到才抛
 * {@link HsmUnavailableException}——把「立刻失败」变成「排队等一会儿」。</p>
 */
public class ChannelCache {
    private static final Logger logger = LoggerFactory.getLogger(ChannelCache.class);

    private static final CopyOnWriteArrayList<Channel> cacheList = new CopyOnWriteArrayList<>();
    private static final Set<String> exclusiveChannelIds = new HashSet<>();
    private static final Map<String, Long> lastActiveMap = new ConcurrentHashMap<>();

    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition channelReleased = lock.newCondition();

    private static int index = 0;

    /**
     * 新连接注册进池（连接建立成功后调用）。
     */
    public static void set(Channel channel) {
        lock.lock();
        try {
            cacheList.add(channel);
            touch(channel);
            channelReleased.signal();
        } finally {
            lock.unlock();
        }
        logger.info("add channel {}, current size {}", channel.id().asLongText(), getSize());
    }

    /**
     * 旧版非独占轮询获取（legacy sendMsg 通路），保持原有 fail-fast 语义。
     */
    public static Channel get() {
        lock.lock();
        try {
            if (cacheList.isEmpty()) {
                throw new HsmUnavailableException("no available channel");
            }
            Channel channel = findIdleLocked();
            if (channel == null) {
                throw new HsmUnavailableException("no idle channel");
            }
            return channel;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 独占获取一条连接：无空闲时阻塞等待，最长 timeoutMs；超时仍无空闲抛
     * {@link HsmUnavailableException}。timeoutMs <= 0 时退化为立即失败（旧行为）。
     */
    public static Channel acquireExclusive(long timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(timeoutMs, 0));
        lock.lock();
        try {
            for (; ; ) {
                Channel channel = findIdleLocked();
                if (channel != null) {
                    exclusiveChannelIds.add(channel.id().asLongText());
                    touch(channel);
                    return channel;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new HsmUnavailableException("no idle channel");
                }
                try {
                    channelReleased.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new HsmUnavailableException("interrupted while waiting for idle channel");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 独占指定的这一条连接：不阻塞、不轮询，拿不到就返回 false。
     *
     * <p>供空闲心跳等「必须逐条保活」的场景使用——{@link #acquireExclusive(long)} 会
     * 从池里另挑一条，无法定位到调用方正在遍历的那条连接。</p>
     *
     * <p>返回 false 的三种情况：连接为 null、不在池内或已失活、已被业务独占。</p>
     */
    public static boolean tryAcquireExclusive(Channel channel) {
        if (channel == null) {
            return false;
        }
        lock.lock();
        try {
            if (!channel.isActive() || !cacheList.contains(channel)) {
                return false;
            }
            if (!exclusiveChannelIds.add(channel.id().asLongText())) {
                return false;
            }
            touch(channel);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 归还独占连接，唤醒一个等待者。
     */
    public static void releaseExclusive(Channel channel) {
        if (channel == null) {
            return;
        }
        lock.lock();
        try {
            exclusiveChannelIds.remove(channel.id().asLongText());
            touch(channel);
            channelReleased.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 当前池内连接数（含独占中的）。
     */
    public static int getSize() {
        return cacheList.size();
    }

    /**
     * 摘除并关闭连接（断连/异常时调用），幂等。
     */
    public static void clear(Channel channel) {
        if (channel == null) {
            return;
        }
        boolean removed;
        lock.lock();
        try {
            removed = removeLocked(channel);
        } finally {
            lock.unlock();
        }
        channel.close();
        if (removed) {
            logger.info("remove channel {}, current size {}", channel.id().asLongText(), getSize());
        }
    }

    /**
     * 刷新连接的最近活跃时间（发送/收到响应时调用）。
     */
    public static void touch(Channel channel) {
        if (channel != null) {
            lastActiveMap.put(channel.id().asLongText(), System.currentTimeMillis());
        }
    }

    /**
     * 连接最近活跃时间（epoch millis），无记录返回 0。
     */
    public static long getLastActive(Channel channel) {
        if (channel == null) {
            return 0L;
        }
        return lastActiveMap.getOrDefault(channel.id().asLongText(), 0L);
    }

    /**
     * 当前池内连接快照（供心跳等遍历使用）。
     *
     * <p>返回独立副本，遍历期间池状态变化不影响循环。若调用方仅需计数或简单遍历
     *（如心跳），可直接用 {@link #forEachActiveChannel(java.util.function.Consumer)} 避免拷贝。</p>
     */
    public static List<Channel> snapshot() {
        return new ArrayList<>(cacheList);
    }

    /**
     * 遍历池内所有活跃连接（无拷贝，单次遍历）。
     *
     * <p>适合「对每条连接执行操作」的场景（如空闲心跳），避免 snapshot() 的全量拷贝开销。
     * 遍历期间若连接被 clear，会安全跳过（CopyOnWriteArrayList 的迭代器是弱一致的）。</p>
     *
     * <p><b>注意</b>：调用方不得在 consumer 内修改池结构（add/remove），仅可读取或执行副作用。</p>
     */
    public static void forEachActiveChannel(java.util.function.Consumer<Channel> consumer) {
        for (Channel channel : cacheList) {
            if (channel.isActive()) {
                consumer.accept(channel);
            }
        }
    }

    /**
     * 轮询找一条活跃且未被独占的连接；顺路剔除已失活的连接。须在 lock 内调用。
     */
    private static Channel findIdleLocked() {
        int scanned = 0;
        while (scanned < cacheList.size()) {
            if (index >= cacheList.size()) {
                index = 0;
            }
            Channel channel = cacheList.get(index++);
            if (!channel.isActive()) {
                removeLocked(channel);
                channel.close();
                continue;
            }
            scanned++;
            if (!exclusiveChannelIds.contains(channel.id().asLongText())) {
                return channel;
            }
        }
        return null;
    }

    /**
     * 须在 lock 内调用。
     */
    private static boolean removeLocked(Channel channel) {
        String id = channel.id().asLongText();
        exclusiveChannelIds.remove(id);
        lastActiveMap.remove(id);
        boolean removed = cacheList.remove(channel);
        if (index > cacheList.size()) {
            index = 0;
        }
        return removed;
    }
}
