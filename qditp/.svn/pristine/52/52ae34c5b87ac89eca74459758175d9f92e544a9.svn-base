package com.chinasofti.huateng.online.service;

import com.chinasofti.huateng.online.model.BaseRespDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos.NotiVerifyResultReqDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos.RequestSynKeyListReqDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos.RequestSynKeyListRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.BomBusinessResultReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.BomTopupResultReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.HceUpdateResultReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestGenNoCashOrderRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestGetPayResultReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestGetPayResultRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestPaymentReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestPaymentRespDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestUpdateCardDataReqDTO;
import com.chinasofti.huateng.online.model.bom.BomDtos.RequestUpdateCardDataRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestGenSjtOrderRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestPayResultReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestPayResultRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestTakeTicketAuthRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestTopupReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestTopupRespDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.online.model.tvm.TvmDtos.TopupCardResultNotiReqDTO;

public interface OnlineService {
    /**
     * IF5A-01 请求票卡分析。
     * 供 BOM 在二维码更新、乘客事务处理前调用，用于判断当前票卡状态以及本次建议操作类型。
     */
    RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    /**
     * IF5A-03 请求票卡更新。
     * BOM 根据票卡分析结果选择补进站、补出站或 20 分钟更新后，调用本接口完成平台侧票卡状态更新，
     * 并返回最新行业数据给 BOM 回写。
     */
    RequestUpdateCardDataRespDTO requestUpdateCardData(RequestUpdateCardDataReqDTO request);

    /**
     * IF8A-04 请求非现金收款下单。
     * 对应 BOM 非现金业务，平台生成一笔待支付订单，后续通过扫码支付与支付结果查询推进状态。
     */
    RequestGenNoCashOrderRespDTO requestGenNoCashOrder(RequestGenNoCashOrderReqDTO request, String deviceId);

    /**
     * IF8A-05 扫码支付。
     * BOM 扫描乘客付款码后调用，平台向支付渠道发起支付并同步返回直接结果。
     */
    RequestPaymentRespDTO requestBomPayment(RequestPaymentReqDTO request);

    /**
     * IF2A-09 BOM 上报充值结果通知。
     * 主要处理 TVM 充值异常后由 BOM 辅助确认的最终充值结果。
     */
    BaseRespDTO notiBomTopupResult(BomTopupResultReqDTO request);

    /**
     * IF8A-06 查询支付结果。
     * 当 BOM 的支付请求超时或设备侧需要轮询支付状态时调用。
     */
    RequestGetPayResultRespDTO requestBomPayResult(RequestGetPayResultReqDTO request);

    /**
     * IF2A-08 业务操作结果通知。
     * BOM 业务完成后回告 ITP，平台据此将订单闭环为成功或失败。
     */
    BaseRespDTO notiBomBusinessResult(BomBusinessResultReqDTO request);

    /**
     * IF5A-09 HCE 票卡更新结果通知。
     * 对 HCE 票卡更新后的行业数据、状态及交易计数器进行平台侧落库。
     */
    BaseRespDTO notiUpdateHceData(HceUpdateResultReqDTO request);

    /**
     * IF1A-01 闸机检票通知。
     * AGM 完成二维码验票后，将交易结果准实时回传给 ITP，平台据此更新票卡最新状态。
     */
    BaseRespDTO notiVerifyResult(NotiVerifyResultReqDTO request, String deviceId);

    /**
     * IF1A-02 密钥同步。
     * AGM 在开机或运营开始前调用，用于获取 ITP CA 公钥等版本信息。
     */
    RequestSynKeyListRespDTO requestSynKeyList(RequestSynKeyListReqDTO request);

    /**
     * IF1A-04 查询票卡状态。
     * AGM 进站/出站前可调用，用于查询平台端票卡最后状态以及是否锁定。
     */
    RequestQrCodeStatusRespDTO requestQrCodeStatus(RequestQrCodeStatusReqDTO request);

    /**
     * IF2A-01 提交单程票订单。
     * TVM 现场购票发起下单，平台生成订单号与支付二维码地址。
     */
    RequestGenSjtOrderRespDTO requestGenSjtOrder(RequestGenSjtOrderReqDTO request, String deviceId);

    /**
     * IF2A-03 查询支付结果。
     * TVM 在二维码展示期间持续轮询本接口，直至支付成功、失败或超时结束。
     */
    RequestPayResultRespDTO requestTvmPayResult(RequestPayResultReqDTO request);

    /**
     * IF2A-04 出票结果通知。
     * TVM 出票张数与订单张数一致时调用，平台落库出票结果并保存已写卡明细。
     */
    BaseRespDTO notiTakeTicketResult(NotiTakeTicketResultReqDTO request);

    /**
     * IF2A-05 出票故障通知。
     * TVM 出票张数与订单张数不一致时调用，平台记录故障信息并进入退款/解锁处理状态。
     */
    BaseRespDTO notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request);

    /**
     * IF2A-06 充值结果通知。
     * TVM 实体卡充值成功后通知 ITP 更新订单与卡片余额结果。
     */
    BaseRespDTO topupCardResultNoti(TopupCardResultNotiReqDTO request);

    /**
     * IF2A-07 充值失败通知。
     * TVM 充值失败、取消或存疑时调用，平台据此更新订单状态并预留退款处理入口。
     */
    BaseRespDTO topupCardFailNoti(TopupCardFailNotiReqDTO request);

    /**
     * IF2A-08 扫码取票订单查询。
     * TVM 根据自身展示的取票二维码反查已激活订单，查到后才允许开始出票。
     */
    RequestTakeTicketAuthRespDTO requestTakeTicketAuth(RequestTakeTicketAuthReqDTO request);

    /**
     * IF2A-09 请求充值下单。
     * TVM 实体卡充值前生成待支付充值订单，并返回扫码支付二维码地址。
     */
    RequestTopupRespDTO requestTopup(RequestTopupReqDTO request, String deviceId);

    /**
     * IF2A-11 扫码支付。
     * TVM 扫描乘客付款码后调用，平台返回同步支付结果。
     */
    com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestPaymentRespDTO requestTvmPayment(
            com.chinasofti.huateng.online.model.tvm.TvmDtos.RequestPaymentReqDTO request);

    /**
     * 设备心跳接口。
     * 统一承接 BOM/AGM/TVM 的设备心跳请求，用于记录设备最后在线时间。
     */
    BaseRespDTO deviceHeartbeat(String providerId, String deviceId);
}
