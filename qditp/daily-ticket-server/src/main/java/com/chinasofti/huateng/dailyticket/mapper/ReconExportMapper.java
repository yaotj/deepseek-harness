package com.chinasofti.huateng.dailyticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 日终对账抽取专用只读 Mapper（来源标识 {@code daily-ticket}，负责 DETAIL 与 PAY 两类文件）。
 *
 * <p>行格式与字段顺序来自甲方 {@code docs/接口规范文档/ACC与ITP之间的文件.docx} §一「对账文件」，
 * 与 {@code ReconFileTypeEnum} 的类注释一一对应。**NEVER 按本项目自己的口径重排字段** —— 分片是
 * 纯文本、无 schema，错位不报错，只会静默出错账。</p>
 *
 * <p>本 Mapper 只读三张表、不写任何表，因此不参与事务。**NEVER 在这里加写方法** —— 抽取链路一旦
 * 带上写操作，长循环期间就会持有行锁，与虚拟线程 pin 叠加会放大成全 JVM 停止调度的事故。</p>
 *
 * <h2>DETAIL —— 虚拟电子多日计次票文件（7 段）</h2>
 * <pre>
 * 运营日期 | 交易类型 | 逻辑卡号 | 交易日期时间 | 交易金额 | 当前车站名称 | 设备编码
 * </pre>
 * <p>本模块只产出其中的**「车票购买（发售）」**行：甲方原文「车票购买的交易在对账文件中交易类型为
 * 发售，当前车站类型和设备编码传空」。另一类行是**超时行程**（交易类型「出站」、费用取线网最高票价
 * 或 1），那部分数据在闸机出站扣费链路里，**由 gate-txn-pay-server 负责产出，本模块 NEVER 产出**
 * —— 本模块 5 张表既没有进出站信息也没有超时费用，硬凑只能凭空造数。</p>
 *
 * <h2>PAY —— 统计汇总文件（21 段 = 5 段键 + 16 段度量）</h2>
 * <p>本模块只填**旅游票张数(发售) / 旅游票金额**这一组（0 基下标 9 / 10），其余 14 段度量写字面
 * {@code 0}，由 recon-server 按前 5 段键与其他源累加。数据来自**旅游票子单**
 * {@code DAILY_TICKET_ORDER}（{@code PARENT_ORDER_NO IS NOT NULL}），**不是**主单
 * {@code TRAVEL_TICKET_ORDER}，原因见 {@link #selectTravelTicketPaySummary}。</p>
 *
 * <h2>口径（跨服务契约，改动 MUST 同步 recon-server 与另外三个源服务）</h2>
 * <ul>
 *   <li>DETAIL：只导已支付成功的日票订单，判定为 {@code PAY_STATUS = 'PAID'}，窗口按
 *       {@code PAY_DATE}（支付完成时点，TIMESTAMP）切，**不是** {@code CREATE_TIME}；</li>
 *   <li>PAY：同样取 {@code DAILY_TICKET_ORDER} 的 {@code PAY_STATUS = 'PAID'} 与
 *       {@code PAY_DATE} 窗口，只是额外加上 {@code PARENT_ORDER_NO IS NOT NULL} 收窄到旅游票
 *       子单，并按 {@code PAY_CHANNEL_CODE} 拆到支付方式维度；</li>
 *   <li>窗口一律左闭右开，跨两个自然日，故两类文件的日期段都由时间列现算，
 *       NEVER 用指令里的 {@code businessDate} 顶替。</li>
 * </ul>
 *
 * <p>本模块 5 张表**没有任何线路号 / 车站号 / 设备号列**（已全量核对 schema.sql），因此 DETAIL 的
 * 「当前车站名称 / 设备编码」两段与 PAY 的「线路 / 车站 / 设备编号」三段统一由 service 层填空串。
 * 前者是甲方明确要求传空，后者是本模块确实没有该维度，两者含义不同但落地表现一致。
 * PAY 的「支付方式」段**不再是空串**，取子单的 {@code PAY_CHANNEL_CODE}。</p>
 */
@Mapper
public interface ReconExportMapper {

    /**
     * DETAIL：按 Keyset 游标翻页取「车票购买（发售）」明细，游标为 {@code (PAY_DATE, ORDER_NO)}。
     *
     * <p>**NEVER 改成大页码 OFFSET** —— Oracle 的 {@code OFFSET n ROWS} 仍要扫掉前 n 行，
     * 数据量上来后单页耗时随页码线性上涨，而抽取跑在阻塞 DB 调用上，慢 SQL 会 pin 住虚拟线程的
     * 载体线程。Keyset 每页代价恒定。</p>
     *
     * <p>返回列：{@code OPERATE_DATE}（运营日期 YYYYMMDD）、{@code TXN_DATE_TIME}
     * （交易日期时间 YYYYMMDDHH24MISS）、{@code PAY_DATE} 与 {@code ORDER_NO}（仅供游标回填，
     * 不进文件行）、{@code PAY_AMOUNT}（交易金额，单位分）、{@code CARD_NUM}（逻辑卡号）。</p>
     *
     * @param windowStart 窗口起点（含）
     * @param windowEnd   窗口终点（不含）
     * @param lastPayDate 上一页最后一行的 {@code PAY_DATE}，首页传 null
     * @param lastOrderNo 上一页最后一行的 {@code ORDER_NO}，首页传 null
     * @param limit       本页最大行数
     * @return 明细行，列名见 XML 的 select 列表
     */
    List<Map<String, Object>> selectDetailPage(@Param("windowStart") Timestamp windowStart,
                                               @Param("windowEnd") Timestamp windowEnd,
                                               @Param("lastPayDate") Timestamp lastPayDate,
                                               @Param("lastOrderNo") String lastOrderNo,
                                               @Param("limit") int limit);

    /**
     * PAY：取旅游票发售汇总，按「日期 + 支付方式」在库内 GROUP BY 后返回。
     *
     * <p>汇总 **MUST 在库内做**，NEVER 拉全量明细回 JVM 再 group：后者等于把整窗口的行搬进堆内存。
     * 结果行数是「窗口内日期数 × 支付渠道数」量级（左闭右开跨两个自然日），一次查完即可。</p>
     *
     * <p><b>取数对象是旅游票子单 {@code DAILY_TICKET_ORDER}，不是主单
     * {@code TRAVEL_TICKET_ORDER}</b>（2026-09-11 改）。主单只是聚合壳：
     * {@code requestTravelOrder} 插入时写死 {@code ORDER_STATUS='CREATED'} /
     * {@code PAY_STATUS='INIT'}，而全仓库对 {@code travelTicketOrderMapper} 只有一次
     * {@code insert}、**没有任何 UPDATE**，主单状态永不回写。支付实际是**每张子单各走一次**：
     * {@code DailyTicketServiceImpl.requestPay} 第一步 {@code orderMapper.selectByOrderNo}
     * 查的是 {@code DAILY_TICKET_ORDER}，上送网关的商户单号是子单号、金额是单张
     * {@code TICKET_PRICE}；回写走 {@code updatePayResultIfPaying}，把
     * {@code PAY_STATUS='PAID'} / {@code PAY_DATE} / {@code PAY_AMOUNT} 落在子单上。
     * 因此原来「查主单 + {@code PAY_STATUS='PAID'} + {@code UPDATE_TIME} 切窗口」的前提不成立，
     * 该口径下本查询恒返回 0 行。</p>
     *
     * <p><b>张数用 {@code COUNT(*)}，NEVER 换回 {@code SUM(TICKET_COUNT)}</b>：
     * {@code TICKET_COUNT} 只存在于主单表，子单表没有该列；且下单时按 {@code ticketCount}
     * 循环拆单（{@code buildTravelSubOrder} 每次生成一条子单），**一条子单恰好一张票**，
     * 所以笔数即甲方要的「旅游票张数(发售)」。</p>
     *
     * <p>金额取 {@code NVL(SUM(NVL(PAY_AMOUNT, TICKET_PRICE)), 0)}，两列都在子单表上、单位分。
     * 支付回写时 {@code PAY_AMOUNT} 为空即回落 {@code TICKET_PRICE}，正常已付单不会为空，
     * 内层 {@code NVL} 只兜历史脏数据；外层 {@code NVL} 兜「无行时 {@code SUM} 返回 NULL」。</p>
     *
     * <p>窗口列是子单 {@code PAY_DATE}，**不再是主单 {@code UPDATE_TIME}**。这同时消掉了原实现
     * 「主单无 {@code PAY_DATE}、只能用 {@code UPDATE_TIME}，日后任何非支付类更新都会让该单在新
     * 窗口重复计入」的已知偏差。</p>
     *
     * <p>{@code PARENT_ORDER_NO IS NOT NULL} 是识别旅游票子单的**唯一判据**：独立日票该列为空
     * （见 {@code daily-ticket-server-schema.sql} 的列注释）。NEVER 改用 {@code ORDER_TYPE}
     * （旅游票子单与独立日票同为 {@code '1'}）或 {@code CARD_TYPE}（原样落 APP 入参、混着三套
     * 编码空间）。</p>
     *
     * <p>「支付方式」段（PAY 第 5 段键）现在填子单 {@code PAY_CHANNEL_CODE}
     * （{@code requestPay} 经 {@code updatePayRequest} 落库），因此分组键含该列，一天可能多行。
     * 线路 / 车站 / 设备编号三段仍由 service 填空串，本模块 5 张表确实没有这三列。</p>
     *
     * <p>补充事实（**不是缺陷，是当前设计**）：旅游票**没有聚合支付**。APP 只能拿子单号逐张付，
     * 拿主单号 {@code 0T...} 调 {@code requestPay} 会在 {@code orderMapper.selectByOrderNo}
     * 命中 0 行、返回「订单不存在」（{@code validateOrderNo} 只校验 {@code orderType='1'} 与非空，
     * 不认单号前缀）。因此 NEVER 再据「主单 {@code PAY_STATUS} 恒为 {@code INIT}」判 P0。</p>
     *
     * <p><b>索引：不新增，现有两条够用</b>（{@code sql/daily-ticket-recon-export-index.sql} 与
     * {@code sql/daily-ticket-server-schema.sql}）。
     * ①{@code IDX_DAILY_TICKET_ORDER_RECON (PAY_STATUS, PAY_DATE, ORDER_NO)}：前导列
     * {@code PAY_STATUS} 是等值、{@code PAY_DATE} 是范围，正是 B-tree 能用的「等值 + 范围」组合，
     * 一次 range scan 即定位到窗口内的已付单；
     * ②{@code IDX_DAILY_TICKET_ORDER_PARENT (PARENT_ORDER_NO)}：Oracle 的 B-tree **不存储全 NULL
     * 键**，所以这条单列索引里只有旅游票子单，对 {@code IS NOT NULL} 反而是可用的访问路径
     * （反过来说：{@code IS NOT NULL} 无法在 ①里被评估 —— {@code PARENT_ORDER_NO} 不在 ①的键里，
     * 该谓词只能在回表后过滤）。
     * 两条路径都要回表取 {@code PAY_AMOUNT} / {@code TICKET_PRICE} / {@code PAY_CHANNEL_CODE}，
     * 都不是覆盖索引，但窗口只有两个自然日、行数是「日票日销量」量级，回表代价可以接受。
     * 想做成覆盖索引就得把 4 列都塞进键（如
     * {@code (PAY_STATUS, PAY_DATE, PARENT_ORDER_NO, PAY_AMOUNT, TICKET_PRICE, PAY_CHANNEL_CODE)}），
     * 那是为一天跑一次的对账 SQL 给高频写入的订单表加一条宽索引，**不划算，故不加**。
     * 顺带说明：原 {@code IDX_TRAVEL_TICKET_ORDER_RECON ON TRAVEL_TICKET_ORDER
     * (PAY_STATUS, UPDATE_TIME)} 已随本次口径变更从脚本中**删除** —— 不再查主单，那条索引无用。</p>
     *
     * @param windowStart 窗口起点（含）
     * @param windowEnd   窗口终点（不含）
     * @return 汇总行，含 {@code TXN_DATE} / {@code PAY_CHANNEL_CODE} / {@code TICKET_COUNT}
     *         / {@code TICKET_AMOUNT}
     */
    List<Map<String, Object>> selectTravelTicketPaySummary(@Param("windowStart") Timestamp windowStart,
                                                           @Param("windowEnd") Timestamp windowEnd);
}
