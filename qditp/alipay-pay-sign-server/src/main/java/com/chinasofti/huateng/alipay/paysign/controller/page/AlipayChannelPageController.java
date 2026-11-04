package com.chinasofti.huateng.alipay.paysign.controller.page;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.service.AlipayChannelPageService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运营端支付宝渠道只读查询（对标旧 ITP「支付宝出行管理」的签约用户 / 渠道退款记录，走 web 侧 nginx 前缀）。
 *
 * <p>「支付统计」不落本模块：扣费权威在 gate-txn-pay 的 {@code GATE_TXN_PAY}，渠道口径
 * {@code ISSUE_CHANNEL_CODE='07'}（支付宝出行），见 gate-txn-pay {@code /page/gate-txn-pay} 的
 * {@code issueChannelCode} 参数（前端预置 07 即渠道统计页）；{@code ALIPAY_PAY_TXN_DETAIL}
 * 按本模块设计不是查询入口，NEVER 给它加页面查询。
 */
@RestController
@RequestMapping("/page/alipay")
public class AlipayChannelPageController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final AlipayChannelPageService channelPageService;

    public AlipayChannelPageController(AlipayChannelPageService channelPageService) {
        this.channelPageService = channelPageService;
    }

    /** 签约用户分页：要求用户标识 / 卡号 / 签约时间窗至少一项，避免无条件全表扫。 */
    @GetMapping("/signed-users")
    public ResultVO<Map<String, Object>> signedUsers(@RequestParam(required = false) String thirdUserId,
                                                     @RequestParam(required = false) String cardId,
                                                     @RequestParam(required = false) String signStatus,
                                                     @RequestParam(required = false) String signTimeBegin,
                                                     @RequestParam(required = false) String signTimeEnd,
                                                     @RequestParam(required = false) Integer pageNum,
                                                     @RequestParam(required = false) Integer pageSize) {
        if (!hasSearchScope(thirdUserId, cardId, signTimeBegin, signTimeEnd)) {
            return ResultMapper.illegalParams("请填写用户ID、卡号，或完整的签约时间范围");
        }
        int[] pageArgs = pageArgs(pageNum, pageSize);
        List<AlipaySignInfo> list = channelPageService.selectSignedUsersPage(thirdUserId, cardId, signStatus,
                signTimeBegin, signTimeEnd, pageArgs[0], pageArgs[1]);
        int total = channelPageService.countSignedUsersPage(thirdUserId, cardId, signStatus, signTimeBegin, signTimeEnd);
        return ResultMapper.ok(pageOf(list, total, pageArgs));
    }

    /** 渠道退款记录分页：要求退款单号 / 原订单号 / 交易日期窗（yyyy-MM-dd）至少一项。 */
    @GetMapping("/refund-records")
    public ResultVO<Map<String, Object>> refundRecords(@RequestParam(required = false) String refundOrderNo,
                                                       @RequestParam(required = false) String orderNo,
                                                       @RequestParam(required = false) String refundStatus,
                                                       @RequestParam(required = false) String txnDateBegin,
                                                       @RequestParam(required = false) String txnDateEnd,
                                                       @RequestParam(required = false) Integer pageNum,
                                                       @RequestParam(required = false) Integer pageSize) {
        String begin = normalizeTxnDate(txnDateBegin);
        String end = normalizeTxnDate(txnDateEnd);
        if (refundOrderNo == null && orderNo == null && (begin == null || end == null)) {
            return ResultMapper.illegalParams("请填写退款单号、原订单号，或完整的交易日期范围");
        }
        int[] pageArgs = pageArgs(pageNum, pageSize);
        List<AlipayRefundTxnDetail> list = channelPageService.selectRefundRecordsPage(refundOrderNo, orderNo,
                refundStatus, begin, end, pageArgs[0], pageArgs[1]);
        int total = channelPageService.countRefundRecordsPage(refundOrderNo, orderNo, refundStatus, begin, end);
        return ResultMapper.ok(pageOf(list, total, pageArgs));
    }

    /** TXN_DATE 存储格式 yyyyMMdd（月分区键），页面 yyyy-MM-dd 输入归一化。 */
    private String normalizeTxnDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String digits = value.trim().replace("-", "");
        return digits.matches("\\d{8}") ? digits : null;
    }

    private boolean hasSearchScope(String thirdUserId, String cardId, String signTimeBegin, String signTimeEnd) {
        return StringUtils.hasText(thirdUserId) || StringUtils.hasText(cardId)
                || (StringUtils.hasText(signTimeBegin) && StringUtils.hasText(signTimeEnd));
    }

    private int[] pageArgs(Integer pageNum, Integer pageSize) {
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new int[]{(currentPage - 1) * currentPageSize, currentPageSize};
    }

    private Map<String, Object> pageOf(List<?> list, int total, int[] pageArgs) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", list);
        page.put("total", total);
        return page;
    }
}
