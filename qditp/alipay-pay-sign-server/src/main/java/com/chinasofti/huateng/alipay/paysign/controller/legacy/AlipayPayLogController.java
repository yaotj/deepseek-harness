package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.model.response.AlipayPayLogVO;
import com.chinasofti.huateng.alipay.paysign.model.response.PageResult;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayLogQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付宝出行订单查询 Controller：只读，只依赖 {@link AlipayPayLogQueryService}。
 *
 * <p><b>2026-09-20 迁入 {@code controller.legacy} 子包</b>（迁移第 12 条，**按用户裁决**）：
 * 本类读的是 <b>{@code ALIPAY_PAY_LOG} —— 一张已无写入方、计划废弃的旧表</b>，
 * 8 个 handler（6 个不同 URL，{@code list} / {@code travelList} 各有 GET + POST 两个入口）
 * <b>当前全部零调用方</b>。<b>URL 与 Bean 名逐字未动</b>，Spring 映射只看注解、与包路径无关。
 * 组件扫描根是启动类所在的 {@code ...alipay.paysign}，子包天然被扫到，
 * <b>NEVER 因为迁了包就去加 {@code @ComponentScan}</b>。
 *
 * <p><b>NEVER 因为「零调用方 + 在 legacy 包里」就删掉这 8 个 handler</b>：
 * {@code ALIPAY_PAY_LOG} 的**存量数据只能从这里读**。这与同包另两类的判据都不同，
 * {@code controller.legacy} 现有三类、**MUST 分清**：
 * <ul>
 *   <li>{@code AlipayPaySignController.selectSignInfo} —— 零调用方、**等待删除**（删时连 rpc 包装方法一起删）；</li>
 *   <li>{@code AlipayNotifyController} —— **两条端点都有真实在跑的调用方**，只因形态是历史遗留才迁入；</li>
 *   <li>本类 —— 零调用方、**但不能删**，因为它是旧表存量数据的唯一读出口。</li>
 * </ul>
 *
 * <p>原调用方 {@code POST payLog/travelList} 曾被旧的 {@code findTravelList} 编排调用，
 * 而 <b>2026-09-18 起乘车记录列表已改由 {@code trans-query-server} 的
 * {@code AlipayTravelQueryHandler} 用 {@code GATE_TXN_PAY} 分页 + 一次 {@code payTxnBrief} 批查组装</b>，
 * {@code rpc/AlipayPaySignClient.alipayTripPayLogTravelList} 随之无调用方。
 *
 * <p>每条查询都有 GET / POST 两个入口（POST 是给只能发 JSON 的客户端用的），两者共用同一个私有查询方法，
 * URL 与入参语义逐字保持原状 —— 包括 <b>两组端点的页码基数并不一致</b>：
 * {@code payLog/list} 的 {@code pageNum} 从 1 起（offset = (pageNum - 1) * pageSize），
 * {@code payLog/travelList} 从 0 起（offset = pageNum * pageSize）。
 * 这是历史现状、不是本次改造引入的；统一基数属于行为变更，且 {@code ALIPAY_PAY_LOG} 表已计划废弃，故此处不动。
 */
@RestController
@RequestMapping("/api/payment")
public class AlipayPayLogController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayLogController.class);
    private final AlipayPayLogQueryService alipayPayLogQueryService;

    public AlipayPayLogController(AlipayPayLogQueryService alipayPayLogQueryService) {
        this.alipayPayLogQueryService = alipayPayLogQueryService;
    }

    /**
     * 查询支付宝出行订单列表。
     */
    @GetMapping("/payLog/list")
    public PageResult<AlipayPayLogVO> list(@RequestParam(required = false) String thirdUserId,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return queryList("查询订单列表", thirdUserId, startTime, endTime,
                (pageNum - 1) * pageSize, pageSize, null, null);
    }

    /**
     * 查询支付宝出行订单列表（POST，兼容客户端 JSON 请求）。
     */
    @PostMapping("/payLog/list")
    public PageResult<AlipayPayLogVO> listPost(@RequestBody Map<String, Object> params) {
        int pageNum = intValue(params, "pageNum", 1);
        int pageSize = intValue(params, "pageSize", 10);
        return queryList("查询订单列表(POST)", stringValue(params, "thirdUserId"),
                stringValue(params, "startTime"), stringValue(params, "endTime"),
                (pageNum - 1) * pageSize, pageSize, null, null);
    }

    /**
     * 支付宝出行-查询乘车记录支付流水列表。
     */
    @GetMapping("/payLog/travelList")
    public PageResult<AlipayPayLogVO> travelList(@RequestParam String thirdUserId,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String debitRequestResult,
                                                 @RequestParam(required = false) String invoice,
                                                 @RequestParam(defaultValue = "0") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return queryList("查询乘车记录列表", thirdUserId, startDate, endDate,
                pageNum * pageSize, pageSize, debitRequestResult, invoice);
    }

    /**
     * 支付宝出行-查询乘车记录支付流水列表（POST）。
     */
    @PostMapping("/payLog/travelList")
    public PageResult<AlipayPayLogVO> travelListPost(@RequestBody Map<String, Object> params) {
        int pageNum = intValue(params, "pageNum", 0);
        int pageSize = intValue(params, "pageSize", 10);
        return queryList("查询乘车记录列表(POST)", stringValue(params, "thirdUserId"),
                stringValue(params, "startDate"), stringValue(params, "endDate"),
                pageNum * pageSize, pageSize,
                stringValue(params, "debitRequestResult"), stringValue(params, "invoice"));
    }

    /**
     * 按订单号查询支付日志详情。
     */
    @GetMapping("/payLog/detail")
    public AlipayPayLogVO detail(@RequestParam String orderNo) {
        log.info("查询订单详情: orderNo={}", orderNo);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByOrderNo(orderNo);
        log.info("查询订单详情响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }

    /**
     * 按进站交易ID查询支付流水。
     */
    @GetMapping("/payLog/entryId")
    public AlipayPayLogVO entryId(@RequestParam String entryId) {
        log.info("按进站交易ID查询: entryId={}", entryId);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByEntryId(entryId);
        log.info("按进站交易ID查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }

    /**
     * 按出站交易ID查询支付流水。
     */
    @GetMapping("/payLog/exitId")
    public AlipayPayLogVO exitId(@RequestParam String exitId) {
        log.info("按出站交易ID查询: exitId={}", exitId);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByExitId(exitId);
        log.info("按出站交易ID查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }

    /**
     * 按乘车记录查询支付流水。
     */
    @PostMapping("/payLog/queryByTravelRecord")
    public AlipayPayLogVO queryByTravelRecord(@RequestBody Map<String, String> params) {
        String thirdUserId = params.get("thirdUserId");
        String entryDate = params.get("entryDate");
        String cardNum = params.get("cardNum");
        log.info("按乘车记录查询: thirdUserId={}, entryDate={}, cardNum={}", thirdUserId, entryDate, cardNum);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByTravelRecord(thirdUserId, entryDate, cardNum);
        log.info("按乘车记录查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }

    private PageResult<AlipayPayLogVO> queryList(String action, String thirdUserId, String startTime, String endTime,
                                                 int offset, int limit, String debitRequestResult, String invoice) {
        log.info("{}: thirdUserId={}, startTime={}, endTime={}, offset={}, limit={}",
                action, thirdUserId, startTime, endTime, offset, limit);
        PageResult<AlipayPayLogVO> response = alipayPayLogQueryService.selectAlipayPayLogList(
                thirdUserId, startTime, endTime, offset, limit, debitRequestResult, invoice);
        log.info("{}响应结果: total={}, listSize={}", action,
                response != null ? response.getTotal() : "null",
                response != null && response.getList() != null ? response.getList().size() : "null");
        return response;
    }

    private static String stringValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    private static int intValue(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        return value != null ? Integer.parseInt(value.toString()) : defaultValue;
    }
}
