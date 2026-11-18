package com.chinasofti.huateng.acc.security.server.config;

import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChannelCache} 的池语义特征测试。
 *
 * <p>锁定两件事：① {@code tryAcquireExclusive} 只认「指定的那一条」连接，
 * 是空闲心跳逐条保活的前提；② 独占期间该连接对 {@code acquireExclusive} 不可见。</p>
 *
 * <p><b>坑</b>：Netty 的 {@code EmbeddedChannel()} 默认共用同一个
 * {@code EmbeddedChannelId.INSTANCE}，而池按 {@code channel.id().asLongText()} 去重——
 * 直接 new 出来的多个通道会被当成同一条。故此处显式传入唯一 ChannelId。</p>
 */
class ChannelCacheTest {

    private static final AtomicLong SEQ = new AtomicLong();

    private final List<Channel> registered = new ArrayList<>();

    /** 唯一 id：池的独占集合与活跃时间都按 id 索引，两条通道 MUST 不同 id。 */
    private static ChannelId uniqueId() {
        long n = SEQ.incrementAndGet();
        String text = "test-channel-" + n;
        return new ChannelId() {
            @Override
            public String asShortText() {
                return text;
            }

            @Override
            public String asLongText() {
                return text;
            }

            @Override
            public int compareTo(ChannelId o) {
                return text.compareTo(o.asLongText());
            }
        };
    }

    private Channel register() {
        EmbeddedChannel channel = new EmbeddedChannel(uniqueId());
        ChannelCache.set(channel);
        registered.add(channel);
        return channel;
    }

    @AfterEach
    void cleanup() {
        for (Channel channel : registered) {
            ChannelCache.clear(channel);
        }
        registered.clear();
    }

    @Test
    @DisplayName("tryAcquireExclusive 认指定连接，且同一条不能被重复独占")
    void tryAcquireExclusiveIsPerChannel() {
        Channel first = register();
        Channel second = register();

        assertTrue(ChannelCache.tryAcquireExclusive(first), "首次独占应成功");
        assertFalse(ChannelCache.tryAcquireExclusive(first), "同一条重复独占应失败");

        ChannelCache.releaseExclusive(first);
        assertTrue(ChannelCache.tryAcquireExclusive(first), "释放后可再次独占");

        // 独占 first 期间，池里按游标取到的必须是 second
        ChannelCache.releaseExclusive(first);
        assertTrue(ChannelCache.tryAcquireExclusive(first));
        Channel acquired = ChannelCache.acquireExclusive(0);
        assertNotSame(first, acquired, "已被独占的连接不应被 acquireExclusive 取到");
        assertSame(second, acquired);
        ChannelCache.releaseExclusive(acquired);
    }

    @Test
    @DisplayName("tryAcquireExclusive 对池外连接与失活连接返回 false，不抛异常")
    void tryAcquireExclusiveRejectsForeignAndClosedChannel() {
        assertFalse(ChannelCache.tryAcquireExclusive(null), "null 应返回 false");

        EmbeddedChannel foreign = new EmbeddedChannel(uniqueId());
        try {
            assertFalse(ChannelCache.tryAcquireExclusive(foreign), "池外连接应返回 false");
        } finally {
            foreign.finishAndReleaseAll();
        }

        Channel closed = register();
        ChannelCache.clear(closed);
        registered.remove(closed);
        assertFalse(ChannelCache.tryAcquireExclusive(closed), "已摘除的连接应返回 false");
        closed.close();
    }

    @Test
    @DisplayName("池空时 acquireExclusive 超时抛 HsmUnavailableException，不阻塞无限期")
    void acquireExclusiveTimeoutOnEmptyPool() {
        assertThrows(HsmUnavailableException.class, () -> ChannelCache.acquireExclusive(50));
    }
}
