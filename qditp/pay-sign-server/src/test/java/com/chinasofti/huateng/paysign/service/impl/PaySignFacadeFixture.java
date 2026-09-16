package com.chinasofti.huateng.paysign.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.ContractGatewayAdapter;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.PaymentGatewayAdapter;
import com.chinasofti.huateng.paysign.port.PaymentGatewayPort;
import com.chinasofti.huateng.paysign.port.RefundGatewayAdapter;
import com.chinasofti.huateng.paysign.port.RefundGatewayPort;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.PaySignService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code PaySignWorkflow} 的测试装配夹具（批次 0 护栏，ADR-D46 记的「该模块没有 mock 脚手架」由本类补上）。
 *
 * <p><b>为什么需要它</b>：`PaySignWorkflow` 有 16 个 {@code @Autowired} 字段、无构造器注入，
 * 于是本模块 48 个既有测试没有一例装配过它 —— 主流程（签约 / 解约 / 支付 / 退款 / 三个回调）
 * 至今零测试覆盖。拆分 `PaySignWorkflow` 必须先有「行为等价」的判据，本类就是那个判据的地基。
 *
 * <p><b>刻意不起 Spring 上下文</b>：起上下文会拉起 Druid 数据源与 mybatis-adaptor，本机没有
 * Oracle 就跑不了，测试也就永远不会被人真的执行。这里只把 mock 从构造器传进去，纯 JVM 内跑。
 *
 * <p><b>装配 MUST 走构造器，NEVER 退回 {@code ReflectionTestUtils.setField}</b>（2026-09-16，ADR-D96）：
 * 四个领域服务的协作者已全部 {@code final} + 构造注入，于是「夹具与被测类的协作者集合不一致」
 * 由**编译器**拦住。原先那四组 {@code injectXxx} 方法是按**字符串字段名**注的，
 * 少注一个不会有任何提示、只留一个 {@code null} 字段等着某条用例踩到，已按本条全部删除。
 *
 * <p><b>本类与 `service.impl` 同包是有意的</b>：{@code writeLog} / {@code isGatewaySuccess} 等
 * 包级方法要能直接断言。<b>NEVER 把它挪到别的包再靠反射调私有方法</b> —— 那样重命名方法时测试
 * 不会编译失败，只会在运行期抛 `NoSuchMethodException`，等于把护栏做成了纸糊的。
 *
 * <p><b>网关的 {@code isSuccess} / {@code errorMessage} 用 {@code thenCallRealMethod}</b>：
 * 这两个方法只读入参、不碰 OkHttp 与私钥，跑真实现才能保证「成功码判定」与生产一致。
 * <b>NEVER 改成 {@code thenReturn(true)}</b>，否则成功码规则一改，测试仍然全绿。
 */
final class PaySignFacadeFixture {

    /** 网关地址只要「非空且可辨识」即可：真实调用已被 mock 掉，断言看的是传了哪个 URL。 */
    static final String GATEWAY_URL_PREFIX = "http://pay-gateway.test/api/v1/";

    final PaySignProperties properties = new PaySignProperties();
    final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);
    final PayTxnDetailMapper payTxnDetailMapper = mock(PayTxnDetailMapper.class);
    final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    final PayRefundDetailMapper payRefundDetailMapper = mock(PayRefundDetailMapper.class);
    final PayCallbackLogMapper payCallbackLogMapper = mock(PayCallbackLogMapper.class);
    final AppNotifyService appNotifyService = mock(AppNotifyService.class);
    final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    final AccountDomainPort accountDomainPort = mock(AccountDomainPort.class);
    final PayGatewayClient payGatewayClient = mock(PayGatewayClient.class);
    final BlacklistClient blacklistClient = mock(BlacklistClient.class);
    final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    final ChannelSyncDeliverer channelSyncDeliverer = mock(ChannelSyncDeliverer.class);

    /**
     * 审计流水写入点用**真实实现**，不 mock。
     *
     * <p>它在 2026-09-14 由 {@code PaySignWorkflow.writeLog} 抽出（批次 1），属**纯移动**：
     * 跑真实现并让它共用同一个 {@code paySignRequestMapper} mock，{@link #auditLogs()} 才能
     * 继续捕获到全部流水 —— 于是拆分前写的那些断言**一条都不用改就继续通过**，这本身就是等价性证明。
     * <b>NEVER 把它换成 mock</b>：换掉之后「归并成 SIGN / UNSIGN」这条口径就再也没有测试守着了。
     */
    final PaySignAuditLogger auditLogger;

    /**
     * 签约 + 解约组（{@code requestSignInfo} / {@code alipayTripRequestSignInfo} /
     * {@code requestContractAdvisory} / {@code requestContractResult} / {@code requestTermination}）的真实现。
     *
     * <p>2026-09-15 这五个入口连同其余私有辅助方法由 {@code PaySignWorkflow} **纯搬迁**到
     * {@link ContractDomainServiceImpl}，`PaySignWorkflow` 随之删除。{@code paySignProperties} /
     * {@code paySignRequestMapper} / {@code terminationRequestMapper} / {@code auditLogger} /
     * {@code accountDomainPort} 也随之改注到本对象上 —— <b>注的是同一批 mock 实例</b>，
     * 断言口径不变；原先注的 {@code payGatewayClient} 换成包着它的 {@link #paySignGateway}。</p>
     */
    final ContractDomainServiceImpl contractDomainService;

    /**
     * 支付组（{@code requestPay} / {@code requestRefund} / {@code receivePayResult}）的真实现。
     *
     * <p>2026-09-15 这三个入口连同 21 个私有辅助方法由 {@code PaySignWorkflow} **纯搬迁**到
     * {@link PaymentDomainServiceImpl}，`PAY_TXN_DETAIL` / `PAY_REFUND_DETAIL` / `PAY_CALLBACK_LOG`
     * 三个 mapper 与 {@code blacklistClient} / {@code gateTxnPayClient} 也随之从 workflow 移到这里。
     * 因此这几个 mock 现在注到本对象上 —— <b>注的是同一批 mock 实例</b>，断言口径不变。</p>
     */
    final PaymentDomainServiceImpl paymentDomainService;

    /**
     * 退款组（{@code requestRefund} / {@code compensateRefundQuery} / {@code compensateRefundSummary}）的真实现。
     *
     * <p>2026-09-15 由 {@link PaymentDomainServiceImpl} **纯搬迁**而来（那个类当时 1166 行、
     * 同时管支付与退款）。{@code PAY_TXN_DETAIL} / {@code PAY_REFUND_DETAIL} 两个 mapper
     * 与 {@code paySignProperties} / {@code paySignGateway} 在两边都注、<b>注的是同一批 mock 实例</b>，
     * 断言口径不变。</p>
     */
    final RefundDomainServiceImpl refundDomainService;

    /**
     * 回调组（{@code receiveSignResult} / {@code receiveTerminationResult}）的真实现。
     *
     * <p>2026-09-15 这两个入口连同 5 个私有辅助方法由 {@code PaySignWorkflow} **纯搬迁**到
     * {@link CallbackDomainServiceImpl}，{@code appNotifyService} / {@code eventPublisher} /
     * {@code transactionTemplate} / {@code channelSyncDeliverer} 也随之从 workflow 移到这里。
     * 三个 mapper 两边都注、<b>注的是同一批 mock 实例</b>，断言口径不变。</p>
     */
    final CallbackDomainServiceImpl callbackDomainService;

    /** 网关调用与应答判读的收口 Bean，内部包着同一个 {@link #payGatewayClient} mock。 */
    final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    /**
     * 签约/解约方向的出向端口，<b>用真实现</b>（2026-09-16，ADR-D112）。
     *
     * <p>内部包着同一批 {@link #properties} 与 {@link #paySignGateway}，因此
     * 「打哪个 URL、怎么判成功码」的口径与收口前**逐字相同** —— 那 205 个既有断言
     * 一条都不用改就继续通过，这本身即等价性证明。<b>NEVER 换成 mock</b>：
     * 换掉之后 URL 选取与成功码判定就再也没有测试守着了。
     */
    final ContractGatewayPort contractGatewayPort = new ContractGatewayAdapter(properties, paySignGateway);

    /** 扣款方向的出向端口，同样用真实现、同样包着那两个 mock（理由同上）。 */
    final PaymentGatewayPort paymentGatewayPort = new PaymentGatewayAdapter(properties, paySignGateway);

    /** 退款方向的出向端口，同样用真实现（理由同上）。 */
    final RefundGatewayPort refundGatewayPort = new RefundGatewayAdapter(properties, paySignGateway);

    /**
     * 与生产完全同构的调用链门面：{@code PaySignServiceImpl} → 三个领域服务
     * （{@link ContractDomainServiceImpl} / {@link PaymentDomainServiceImpl} / {@link CallbackDomainServiceImpl}）。
     *
     * <p><b>特征测试一律经它下钻，NEVER 直接调 {@link #contractDomainService}</b>。理由是拆分的等价性判据：
     * 断言挂在**对外入口**上，业务代码在三个领域服务之间怎么搬都不影响调用点，
     * 于是「测试全绿」才真的等于「对外行为没变」。若断言挂在被拆的那个类上，
     * 一旦把方法搬走就必须同时改测试 —— 那时「全绿」只证明**代码和测试被一致地改了**，
     * 证不了行为等价，而这里是支付链路，等价性是唯一的验收依据。
     *
     * <p>2026-09-15 建立本字段时**没有动一行业务代码**，19 个既有断言原样全绿，
     * 这就是「换调用路径本身是忠实的」的证明。当天晚些时候 {@code PaySignWorkflow} 被删除、
     * 五个签约入口与三个支付入口分别搬进上面两个领域服务，**这些断言一条都没改过**
     * —— 那才是这个门面字段的全部价值。<b>NEVER 把测试改回直调某个具体的领域服务实现。</b>
     */
    final PaySignService service;

    private PaySignFacadeFixture() {
        fillGatewayUrls();
        auditLogger = new PaySignAuditLogger(paySignRequestMapper);
        contractDomainService = new ContractDomainServiceImpl(
                paySignInfoMapper,
                paySignRequestMapper,
                terminationRequestMapper,
                auditLogger,
                accountDomainPort,
                contractGatewayPort);
        paymentDomainService = new PaymentDomainServiceImpl(
                properties,
                payTxnDetailMapper,
                payCallbackLogMapper,
                accountDomainPort,
                blacklistClient,
                gateTxnPayClient,
                paymentGatewayPort);
        refundDomainService = new RefundDomainServiceImpl(
                payTxnDetailMapper,
                payRefundDetailMapper,
                refundGatewayPort);
        callbackDomainService = new CallbackDomainServiceImpl(
                paySignInfoMapper,
                paySignRequestMapper,
                terminationRequestMapper,
                appNotifyService,
                auditLogger,
                eventPublisher,
                transactionTemplate,
                channelSyncDeliverer);
        stubTransactionTemplate();
        stubGatewayPureMethods();
        service = new PaySignServiceImpl(
                contractDomainService,
                paymentDomainService,
                refundDomainService,
                callbackDomainService,
                paySignInfoMapper,
                payTxnDetailMapper);
    }

    static PaySignFacadeFixture create() {
        return new PaySignFacadeFixture();
    }

    private void fillGatewayUrls() {
        properties.setContractUrl(GATEWAY_URL_PREFIX + "contract");
        properties.setContractAdvisoryUrl(GATEWAY_URL_PREFIX + "creditQuery");
        properties.setContractResultUrl(GATEWAY_URL_PREFIX + "contract/queryResult");
        properties.setTerminationUrl(GATEWAY_URL_PREFIX + "contract/dismissal");
        properties.setRequestPayUrl(GATEWAY_URL_PREFIX + "pay");
        properties.setPayQueryUrl(GATEWAY_URL_PREFIX + "pay/query");
        properties.setRequestRefundUrl(GATEWAY_URL_PREFIX + "refund");
        properties.setDefaultNotifyUrl("http://itp.test/notify");
        properties.setRequestPayNotifyUrl("http://itp.test/payNotify");
    }

    /** 让 {@code execute} 直接跑回调，等价于「本地事务成功提交」。 */
    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private void stubGatewayPureMethods() {
        when(payGatewayClient.isSuccess(any())).thenCallRealMethod();
        when(payGatewayClient.errorMessage(any(), anyString())).thenCallRealMethod();
    }

    /** 让网关对任意 URL 返回给定应答。 */
    void gatewayReturns(PaySignGatewayResponse response) {
        when(payGatewayClient.request(anyString(), anyMap())).thenReturn(response);
    }

    /** 网关成功应答（{@code code=0}），{@code data} 由调用方按需塞。 */
    static PaySignGatewayResponse gatewaySuccess() {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        return response;
    }

    /** 网关业务失败应答。 */
    static PaySignGatewayResponse gatewayFailure(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    /**
     * 取本次调用写入 {@code APP_PAY_SIGN_REQUEST} 的全部审计流水。
     *
     * <p>批次 1 要把 {@code writeLog} 抽成独立 Bean，届时**这些断言必须一字不改地继续通过**，
     * 那才叫「审计口径没动」。
     */
    List<PaySignRequest> auditLogs() {
        ArgumentCaptor<PaySignRequest> captor = ArgumentCaptor.forClass(PaySignRequest.class);
        org.mockito.Mockito.verify(paySignRequestMapper, org.mockito.Mockito.atLeast(0)).insert(captor.capture());
        return captor.getAllValues();
    }
}
