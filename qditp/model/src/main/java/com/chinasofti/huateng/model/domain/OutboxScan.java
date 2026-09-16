package com.chinasofti.huateng.model.domain;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 「落库状态 + 扫表补偿」（手写 transactional outbox）的**扫描循环骨架**。
 *
 * <p>本项目不使用消息队列（AGENTS.md §5.1），跨域投递统一是「四列 + 扫表重试」：
 * {@code *_SYNC_STATUS} / {@code *_SYNC_RETRY_COUNT} / {@code *_SYNC_TIME} / {@code *_SYNC_RESULT}。
 * 规范与 SQL 侧的坑见 {@code docs/domain/outbox.md}，本类只固化 <b>Java 侧的三条不变量</b>：</p>
 *
 * <ol>
 *   <li><b>单条失败 NEVER 中断整批</b> —— 一条恒失败的记录会永久挡住它后面的所有行；</li>
 *   <li><b>每行只计一次</b> success 或 failed，{@code onFailure} 自身抛异常也不会把同一行算两次；</li>
 *   <li><b>投递方法与失败处理都可能抛异常</b>（落状态的 UPDATE 遇到连接回收 / 锁等待超时就会抛），
 *       因此外层兜一层 {@code onUnexpected}。</li>
 * </ol>
 *
 * <p><b>为什么不抽一张公共 outbox 表</b>：那张表会被多个域共写，直接违反
 * {@code docs/domain/README.md} 判据 3「热路径写入定 owner」。<b>NEVER 建公共 outbox 表</b>，
 * 待投递的事实 MUST 留在**产生它的那张业务表**上（如 {@code USER_PHONE_CHANGE_LOG.SIGN_SYNC_*}）。
 * 能共用的只有本类这段循环。</p>
 *
 * <p><b>为什么放 {@code model} 而不是各模块自己抄一份</b>：它与同包的 {@link SyncStatus} 是同一个关注点
 * （跨域投递的共用词汇），且零依赖、不参与序列化。<b>NEVER 往本类塞 Spring / MyBatis 依赖</b> ——
 * 一旦它需要注入什么，就说明抽错了层。</p>
 *
 * <p><b>本类刻意不管这些</b>，因为它们各域不同、MUST 留在调用方：扫表 SQL 与白名单、重试上限的判据、
 * 达上限后开工单还是转人工、状态列的取值集合。</p>
 */
public final class OutboxScan {

    private OutboxScan() {
    }

    /**
     * 一轮扫描的结果。{@code scanned} 是本轮捞到的行数，恒等于 {@code success + failed}。
     *
     * <p>各域的补偿端点通常有自己的返回记录（如 {@code SignSyncCompensateResult}），
     * 那是**对外契约**、由 controller 序列化给 web-admin，<b>NEVER 用本记录替换它</b>，转换一下即可。</p>
     */
    public record Result(int scanned, int success, int failed) {
    }

    /**
     * 逐行投递并统计。
     *
     * @param rows          本轮扫到的待投递行；{@code null} 或空直接返回全 0
     * @param deliver       投递一行，返回 true 表示已确认成功。<b>它自己 MUST 负责把成败落库</b>
     *                      （本类不碰数据库），也可以抛异常，抛出时按失败计入并交给 {@code onUnexpected}
     * @param onFailure     {@code deliver} 返回 false 后的处置，如「重试次数达上限就开工单」。
     *                      它抛异常同样只影响本行
     * @param onUnexpected  {@code deliver} / {@code onFailure} 抛异常时的兜底，
     *                      <b>MUST 只记日志、NEVER 再抛</b>：从这里抛出去会中断整批
     */
    public static <T> Result run(List<T> rows,
                                 Predicate<T> deliver,
                                 Consumer<T> onFailure,
                                 BiConsumer<T, RuntimeException> onUnexpected) {
        if (rows == null || rows.isEmpty()) {
            return new Result(0, 0, 0);
        }
        int success = 0;
        int failed = 0;
        for (T row : rows) {
            boolean delivered = false;
            try {
                delivered = deliver.test(row);
                if (!delivered) {
                    onFailure.accept(row);
                }
            } catch (RuntimeException e) {
                onUnexpected.accept(row, e);
            }
            if (delivered) {
                success++;
            } else {
                failed++;
            }
        }
        return new Result(rows.size(), success, failed);
    }
}
