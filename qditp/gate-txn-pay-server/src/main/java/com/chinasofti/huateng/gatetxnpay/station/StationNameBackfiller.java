package com.chinasofti.huateng.gatetxnpay.station;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 按落库前的最终 {@code IN_STATION} / {@code OUT_STATION} 回填两个中文站名列。
 *
 * <p><b>为什么站名的 owner 必须是本模块</b>：站名的唯一写入源原本是 ticket-server 的
 * {@code GateTxnPayRequestAssembler.fillStationNames}，它按 {@code lastHandleStationCode} 解析，
 * 而那个值在 {@code GateTicketHandler} 里被无条件覆盖成 {@code QRCODE_STATUS.LAST_TXN_STATION} ——
 * 离线码进站报文没上传时它还是开卡占位值 {@code FFFF}（查不到站名），或者是**上一趟行程**的出站码
 * （能查到，但是错的名）。随后 {@code FareCalculator} 才按 {@code cardId + ticketTransSeq}
 * 重查首笔进站交易、覆盖 {@code IN_STATION}。**于是编码与站名不同源、时序还相反**：编码事后被改对了，
 * 站名没人回填。列表侧（IF8A-05）站名为空即回落显示编码，这就是「站名显示成 0622」的成因。</p>
 *
 * <p>因此规则是<b>谁改编码谁就负责改名</b>：本类在每个「编码已定型、即将写库」的位置各调一次。
 * ticket-server 那份 {@code fillStationNames} 保留作为兜底（本类查不到时不动它的结果），
 * <b>NEVER 因为本类存在就把它删掉</b> —— 它覆盖的是本模块拿不到 para 应答时的降级路径。</p>
 *
 * <p><b>NEVER 把查不到的站名写成空串或 null</b>：查不到就保留入参里已有的值。覆盖成空
 * 等于把上游已填对的站名擦掉，比不回填更糟。</p>
 */
@Component
public class StationNameBackfiller {

    private static final Logger log = LoggerFactory.getLogger(StationNameBackfiller.class);

    private final FareDataGateway fareDataGateway;

    public StationNameBackfiller(FareDataGateway fareDataGateway) {
        this.fareDataGateway = fareDataGateway;
    }

    /**
     * 按订单当前的进出站编码回填站名，查不到的那一列保持原值。
     *
     * <p>调用点 <b>MUST</b> 在「编码不会再变」之后：正常链路是算价结束、写库之前；
     * 离线码补偿链路是 {@code calculateOfflineFare} 重算完、{@code applyOfflineFareRecalculated}
     * 之前。放在算价之前等于又拿旧编码去查一遍，白做。</p>
     *
     * <p>本方法**不抛异常**（取数已在 {@link FareDataGateway#resolveStationNamesQuietly} 内吞掉）：
     * 站名只影响展示，<b>NEVER 因为它让出站扣费失败</b>。</p>
     */
    public void backfill(GateTxnPay order) {
        if (order == null) {
            return;
        }
        Set<String> stationCodes = new LinkedHashSet<>();
        if (StringUtils.hasText(order.getInStation())) {
            stationCodes.add(order.getInStation());
        }
        if (StringUtils.hasText(order.getOutStation())) {
            stationCodes.add(order.getOutStation());
        }
        if (stationCodes.isEmpty()) {
            return;
        }
        Map<String, String> stationNames = fareDataGateway.resolveStationNamesQuietly(stationCodes);
        if (stationNames.isEmpty()) {
            log.warn("站名回填未命中任何站码，保留上游站名, orderNo={}, inStation={}, outStation={}",
                    order.getOrderNo(), order.getInStation(), order.getOutStation());
            return;
        }
        String entryStationName = stationNames.get(order.getInStation());
        if (StringUtils.hasText(entryStationName)) {
            order.setEntryStationName(entryStationName);
        }
        String exitStationName = stationNames.get(order.getOutStation());
        if (StringUtils.hasText(exitStationName)) {
            order.setExitStationName(exitStationName);
        }
        log.info("站名回填完成, orderNo={}, inStation={}, entryStationName={}, outStation={}, exitStationName={}",
                order.getOrderNo(), order.getInStation(), order.getEntryStationName(),
                order.getOutStation(), order.getExitStationName());
    }
}
