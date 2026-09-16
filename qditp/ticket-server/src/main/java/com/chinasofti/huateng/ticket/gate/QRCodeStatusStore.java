package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import org.springframework.stereotype.Component;

/**
 * {@code QRCODE_STATUS} 的唯一访问出口（owner = gate）。
 *
 * <p><b>为什么要有这个类。</b>改造前 {@link QRCodeStatusMapper} 被 4 个包直接持有
 * （{@code gate} / {@code ridestatus} / {@code supplement} / {@code controller.page}），
 * 其中**三个包在写**：{@code gate} 走 CAS upsert、{@code ridestatus} 走无条件 upsert、
 * {@code controller.page} 走 {@code updateCodeStatus}。于是「谁能改乘车状态」这个问题
 * 在代码里没有答案，CAS 那套并发保证也随时可能被另一个包的无条件 upsert 绕过。
 * 按 {@code docs/domain} 的「热路径写入定 owner」判据，过闸是热路径、owner 就是 {@code gate}。
 *
 * <p><b>约束（改动本类或其调用方前 MUST 先读）：</b>
 * <ul>
 *   <li><b>全模块只有本类引用 {@link QRCodeStatusMapper}，迁移已于 2026-09-14 完成</b>：
 *       {@code gate/GateTicketWriter} / {@code gate/GateTicketHandler} /
 *       {@code gate/AgmRideStatusServiceImpl} / {@code ridestatus/TicketRideStatusServiceImpl} /
 *       {@code supplement} 三个 handler / {@code query/OperationRideStatusService} 全部经本类访问。
 *       **NEVER 新增任何直接持有 mapper 的地方**。判据 **NEVER 用裸 `grep QRCodeStatusMapper`**
 *       —— 那样会命中另外 7 个文件里「NEVER 改回直接注 mapper」的注释本身，
 *       以及 {@code arch/TicketArchitectureTest} 里的规则字面量，全是假阳性。
 *       真判据只有两条：`^import ...mapper.QRCodeStatusMapper;` 与字段声明，
 *       机器判据则是 {@code arch/TicketArchitectureTest} 的
 *       {@code QRCodeStatusMapper只允许gate包引用}（它读的是字节码，不受注释影响）；</li>
 *   <li><b>包外只准读，写只有两个例外</b>：{@code ridestatus} 的开卡复位走 {@link #upsert}，
 *       运营端人工改状态走 {@link #updateCodeStatus}。**NEVER 再开第三个包外写入方**
 *       —— 每多一个，{@link #upsertWithCas} 的并发保证就多一条被绕过的路径；</li>
 *   <li>本类只做「一次调用 = 一条 SQL」的透传，**NEVER 在这里塞业务判断**
 *       （状态机白名单在 {@code QRCodeStatusEnum}，事务性组合写在 {@link GateTicketWriter}）；</li>
 *   <li>本类**不加 {@code @Transactional}**。事务边界由调用方决定 —— {@link GateTicketWriter}
 *       需要「明细 + 状态」同事务，而运营端改状态是单条自动提交，两者不能共用一个注解。</li>
 * </ul>
 */
@Component
public class QRCodeStatusStore {

    private final QRCodeStatusMapper qrCodeStatusMapper;

    public QRCodeStatusStore(QRCodeStatusMapper qrCodeStatusMapper) {
        this.qrCodeStatusMapper = qrCodeStatusMapper;
    }

    /**
     * 按卡号读当前票卡状态，不存在返回 {@code null}。
     */
    public QRCodeStatus findByCardId(String cardId) {
        return qrCodeStatusMapper.selectByCardId(cardId);
    }

    /**
     * 无条件 upsert（无并发保护）。
     *
     * <p><b>过闸链路 NEVER 用这个</b>，MUST 用 {@link #upsertWithCas}。本方法只服务
     * 「不在过闸热路径上、且明确要覆盖式落状态」的场景（当前是 APP 侧乘车状态回写）。
     */
    public int upsert(QRCodeStatus record) {
        return qrCodeStatusMapper.upsert(record);
    }

    /**
     * CAS upsert：仅当库里 {@code TXN_SEQ} 仍等于 {@code expectedTxnSeq} 时才写入。
     *
     * @return 1 = 写入成功；0 = 序号已被其他请求推进（CAS 失败，调用方 MUST 自己决定降级路径）
     */
    public int upsertWithCas(QRCodeStatus record, String expectedTxnSeq) {
        return qrCodeStatusMapper.upsertWithCas(record, expectedTxnSeq);
    }

    /**
     * 运营端只改 {@code CODE_STATUS}，保留进出站与末次交易等行程字段。
     *
     * @return 影响行数，0 表示该卡号在表里不存在
     */
    public int updateCodeStatus(String cardId, String codeStatus) {
        return qrCodeStatusMapper.updateCodeStatus(cardId, codeStatus);
    }
}
