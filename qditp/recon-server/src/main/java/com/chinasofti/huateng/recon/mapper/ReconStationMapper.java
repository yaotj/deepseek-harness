package com.chinasofti.huateng.recon.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 车站到线路的映射，唯一用途是在 PAY 汇总文件写出前补齐「线路」那一段。
 *
 * <p><b>为什么这张表在 recon-server 里</b>：{@code STATION_INFO} 属车站 / 参数域，owner 不是本模块。
 * 在本模块直连它是**有意破例**，理由是它把原先散在四个源服务里的四处同样破例收敛成一处 ——
 * gate-txn-pay / collect-pay / face-pay / ticket 的 recon mapper 曾各自写一遍
 * {@code LEFT JOIN STATION_INFO S ON S.STATION_CODE = ...}，于是「线路怎么取」这件事有四份副本、
 * 四份各自的 join 列名、四次维表结构变更的暴露面。线路是车站的函数（一个车站码只对应一条线路），
 * 因此它完全可以在聚合完成后由消费端一次补齐，不必让每个源都认识这张维表。</p>
 *
 * <p><b>NEVER 用车站码前 2 位推线路</b>：实测前 2 位恰好等于 {@code LINE_CODE}，但那是编码巧合、
 * 不是契约，甲方改编码规则时不会通知我方，猜错等于把汇总账挂到错误线路上。这条约束原文在
 * gate-txn-pay-server 与 collect-pay-server 的 recon mapper 注释里，收口到本模块后依然成立。</p>
 *
 * <p><b>查不到的车站 MUST 留空、NEVER 丢行</b>：维表缺记录时线路段为空、该组单独成行，账仍在。
 * 这与原先各源用 {@code LEFT JOIN}（而非 {@code INNER JOIN}）的取舍完全一致 ——
 * 用内连接会把该组的账整组从对账文件里抹掉，那是静默漏账，比线路段为空严重得多。</p>
 */
@Mapper
public interface ReconStationMapper {

    /**
     * 全量取车站码到线路码的映射。
     *
     * <p>返回 {@code List<Map>} 而不是实体：本模块只要两列，没有必要为一张他域维表建实体类。
     * 车站维表基数是几百行量级，一次全量拉回内存做 Map 比逐车站查库便宜得多，也避免了
     * 「聚合几千行、逐行发一条 SQL」那种把批处理退化成 N+1 的写法。</p>
     *
     * <p>本查询**每生成一个 PAY 文件调一次**，不做进程级缓存：日终对账一天只跑一轮，
     * 缓存省下的那一次查询毫无意义，却会引入「参数改了但 recon-server 没重启、线路仍按旧映射填」
     * 这种只在跨天时暴露的偏差。</p>
     */
    List<Map<String, Object>> selectStationLineCodes();
}
