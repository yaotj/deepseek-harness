#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import json
import os

output_dir = "/Users/tuanjie/workspace/chinasofti/qd/qditp/docs/openapi"
os.makedirs(output_dir, exist_ok=True)

fep_alipay = {
    "openapi": "3.0.3",
    "info": {
        "title": "fep-alipay-server OpenAPI",
        "description": "支付宝出行 fep-alipay-server 入口网关接口文档，供测试使用。",
        "version": "2.0.4"
    },
    "servers": [
        {
            "url": "http://127.0.0.1:8080",
            "description": "本地开发环境"
        }
    ],
    "paths": {
        "/channel/addContract": {
            "post": {
                "tags": ["支付宝出行-签约管理"],
                "summary": "添加签约信息",
                "description": "接收支付宝出行侧签约申请，转发至 alipay-pay-sign-server 完成签约。",
                "operationId": "addContract",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripAddContractReqDTO"},
                            "example": {
                                "channel": "ALIPAY",
                                "thirdUserId": "ALIPAY_8823456789012345",
                                "agreementCode": "AGT20260001",
                                "channelAgreementCode": "ALIPAY_AGT_001",
                                "channelUserAccount": "2088123456789012",
                                "cardIssueCode": "0007"
                            }
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "签约成功响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripAddContractRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功"}
                            }
                        }
                    }
                }
            }
        },
        "/channel/terminateContract": {
            "post": {
                "tags": ["支付宝出行-签约管理"],
                "summary": "解约登记",
                "description": "接收支付宝出行侧解约申请，转发至 alipay-pay-sign-server 登记解约。",
                "operationId": "terminateContract",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripTerminateContractReqDTO"},
                            "example": {"agreementCode": "AGT20260001", "merchantNo": "2088123456789012"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "解约登记成功响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripTerminateContractRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功"}
                            }
                        }
                    }
                }
            }
        },
        "/channel/requestApplication": {
            "post": {
                "tags": ["支付宝出行-开卡申请"],
                "summary": "开卡申请",
                "description": "接收支付宝出行侧开卡申请，转发至 alipay-account-server 分配卡号并注册乘车状态。",
                "operationId": "requestApplication",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripRequestApplicationReqDTO"},
                            "example": {
                                "thirdUserId": "ALIPAY_8823456789012345",
                                "cardType": "02",
                                "msisdn": "74955953457",
                                "extend1": "",
                                "extend2": "",
                                "cardIssueCode": "0007"
                            }
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "开卡申请响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripRequestApplicationRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功", "cardId": "9900000000000001", "cardType": "02", "status": "ACTIVE"}
                            }
                        }
                    }
                }
            }
        },
        "/memberContract/channel/requestIndustryData": {
            "post": {
                "tags": ["支付宝出行-行业数据"],
                "summary": "获取行业数据",
                "description": "接收支付宝出行侧行业数据请求，查询用户信息并调用 industry-data-server 生成卡数据。",
                "operationId": "requestIndustryData",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripRequestIndustryDataReqDTO"},
                            "example": {"thirdUserId": "ALIPAY_8823456789012345", "cardId": "9900000000000001", "cardType": "02"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "行业数据响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripRequestIndustryDataRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功"}
                            }
                        }
                    }
                }
            }
        },
        "/channel/findTravelList": {
            "post": {
                "tags": ["支付宝出行-乘车记录"],
                "summary": "查询乘车记录列表",
                "description": "接收支付宝出行侧乘车记录查询请求，转发至 ticket-server 查询 ALIPAY_TRAVEL_RECORD。",
                "operationId": "findTravelList",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripFindTravelListReqDTO"},
                            "example": {"thirdUserId": "ALIPAY_8823456789012345", "page": "0", "size": "10", "startDate": "20260101", "endDate": "20261231"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "乘车记录列表响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripFindTravelListRespDTO"},
                                "example": {
                                    "retCode": "0000", "retMsg": "成功", "pageNumber": 0, "pageSize": 10, "totalPage": 1, "totalCount": 2,
                                    "ticketTransRecord": [{"entryStationName": "青岛站", "entryDate": "20260101103000", "exitStationName": "五四广场", "exitDate": "20260101103500", "payAmount": "200", "totalAmount": "200", "companionFlag": "N", "cardNum": "9900000000000001"}]
                                }
                            }
                        }
                    }
                }
            }
        },
        "/channel/findTravelDetail": {
            "post": {
                "tags": ["支付宝出行-乘车记录"],
                "summary": "查询乘车记录详情",
                "description": "接收支付宝出行侧乘车记录详情查询请求，转发至 ticket-server 查询 ALIPAY_TRAVEL_RECORD。",
                "operationId": "findTravelDetail",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripFindTravelDetailReqDTO"},
                            "example": {"thirdUserId": "ALIPAY_8823456789012345", "orderNo": "ORDER202601010001"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "乘车记录详情响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripFindTravelDetailRespDTO"},
                                "example": {
                                    "retCode": "0000", "retMsg": "成功", "entryStationName": "青岛站", "entryDate": "20260101103000",
                                    "exitStationName": "五四广场", "exitDate": "20260101103500", "payAmount": "200", "totalAmount": "200",
                                    "orderExpType": "01", "tradeOrderNo": "TRADE202601010001", "payTradeOrderNo": "PAY202601010001",
                                    "payOrderNoDate": "20260101", "payChannelCode": "0007", "debitRequestResult": "0000",
                                    "discountFee": "0", "discountInfo": "", "companionFlag": "N", "cardNum": "9900000000000001",
                                    "countingTimes": "", "countingFlag": ""
                                }
                            }
                        }
                    }
                }
            }
        },
        "/api/payment/requestPay": {
            "post": {
                "tags": ["支付宝出行-支付管理"],
                "summary": "支付申请",
                "description": "接收支付宝出行侧支付申请，创建支付订单。",
                "operationId": "requestPay",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripRequestPayReqDTO"},
                            "example": {
                                "orderNo": "ORDER202601010001", "scene": "barservice", "paymentVendor": "ALIPAY", "amount": 200,
                                "industryType": "1", "subject": "青岛地铁乘车费", "body": "青岛地铁五四广场站-青岛站",
                                "requestSignSeq": "SIGN202601010001", "thirdUserId": "ALIPAY_8823456789012345",
                                "orderTimeOut": 60, "authCode": "", "notifyUrl": "https://example.com/notify",
                                "returnUrl": "https://example.com/return", "ipAddress": "127.0.0.1", "remark": "乘车扣款",
                                "industryDetail": "{\"stationIn\":\"五四广场\",\"stationOut\":\"青岛站\"}"
                            }
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "支付申请响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripRequestPayRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功", "orderNo": "ORDER202601010001"}
                            }
                        }
                    }
                }
            }
        },
        "/api/payment/payQuery": {
            "post": {
                "tags": ["支付宝出行-支付管理"],
                "summary": "支付结果查询",
                "description": "接收支付宝出行侧支付结果查询请求。",
                "operationId": "payQuery",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripPayQueryReqDTO"},
                            "example": {"orderNo": "ORDER202601010001", "cardIssueCode": "0007", "cardNum": "9900000000000001", "channelAgreementNo": "ALIPAY_AGT_001"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "支付结果查询响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripPayQueryRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功", "outTradeNo": "ORDER202601010001", "paymentTime": "20260101103000", "tradeStatus": "TRADE_SUCCESS", "totalAmount": "200", "tradeNo": "ALIPAY202601010001", "tradeDesc": "支付成功"}
                            }
                        }
                    }
                }
            }
        },
        "/api/payment/requestRefund": {
            "post": {
                "tags": ["支付宝出行-支付管理"],
                "summary": "退款申请",
                "description": "接收支付宝出行侧退款申请。",
                "operationId": "requestRefund",
                "requestBody": {
                    "content": {
                        "application/json": {
                            "schema": {"$ref": "#/components/schemas/AlipayTripRequestRefundReqDTO"},
                            "example": {"orderNo": "ORDER202601010001", "cardIssueCode": "0007", "cardNum": "9900000000000001", "channelAgreementNo": "ALIPAY_AGT_001", "refundAmount": "200", "refundOrderNo": "REFUND202601010001"}
                        }
                    },
                    "required": True
                },
                "responses": {
                    "200": {
                        "description": "退款申请响应",
                        "content": {
                            "application/json": {
                                "schema": {"$ref": "#/components/schemas/AlipayTripRequestRefundRespDTO"},
                                "example": {"retCode": "0000", "retMsg": "成功"}
                            }
                        }
                    }
                }
            }
        }
    },
    "components": {
        "schemas": {
            "AlipayTripAddContractReqDTO": {
                "type": "object",
                "required": ["channel", "thirdUserId", "agreementCode", "cardIssueCode"],
                "properties": {
                    "channel": {"type": "string", "description": "支付渠道，固定为 ALIPAY"},
                    "thirdUserId": {"type": "string", "description": "支付宝用户ID，格式化后的用户标识"},
                    "agreementCode": {"type": "string", "description": "签约协议号，系统生成的唯一协议编号"},
                    "channelAgreementCode": {"type": "string", "description": "渠道协议号，支付宝侧的协议编号"},
                    "channelUserAccount": {"type": "string", "description": "渠道用户账户，用户在支付宝的账户标识"},
                    "cardIssueCode": {"type": "string", "description": "发卡类型代码，固定 0007"}
                }
            },
            "AlipayTripAddContractRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"}
                }
            },
            "AlipayTripTerminateContractReqDTO": {
                "type": "object",
                "required": ["agreementCode", "merchantNo"],
                "properties": {
                    "agreementCode": {"type": "string", "description": "协议号，签约时生成的协议编号"},
                    "merchantNo": {"type": "string", "description": "合作方机构编号/商户号"}
                }
            },
            "AlipayTripTerminateContractRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"}
                }
            },
            "AlipayTripRequestApplicationReqDTO": {
                "type": "object",
                "required": ["thirdUserId", "cardType", "cardIssueCode"],
                "properties": {
                    "thirdUserId": {"type": "string", "description": "第三方用户ID，格式化后的用户标识"},
                    "cardType": {"type": "string", "description": "卡片类型，如：02"},
                    "msisdn": {"type": "string", "description": "用户手机号码"},
                    "extend1": {"type": "string", "description": "扩展字段1"},
                    "extend2": {"type": "string", "description": "扩展字段2（可选）"},
                    "cardIssueCode": {"type": "string", "description": "发卡渠道代码 0007支付宝出行"}
                }
            },
            "AlipayTripRequestApplicationRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"},
                    "cardId": {"type": "string", "description": "卡片ID/逻辑卡号，开卡成功后返回"},
                    "cardType": {"type": "string", "description": "卡片类型"},
                    "status": {"type": "string", "description": "用户状态，如 ACTIVE"}
                }
            },
            "AlipayTripRequestIndustryDataReqDTO": {
                "type": "object",
                "required": ["thirdUserId", "cardId", "cardType"],
                "properties": {
                    "thirdUserId": {"type": "string", "description": "第三方用户ID"},
                    "cardId": {"type": "string", "description": "卡片ID/逻辑卡号"},
                    "cardType": {"type": "string", "description": "卡片类型"}
                }
            },
            "AlipayTripRequestIndustryDataRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"}
                }
            },
            "AlipayTripFindTravelListReqDTO": {
                "type": "object",
                "required": ["thirdUserId", "page", "size"],
                "properties": {
                    "thirdUserId": {"type": "string", "description": "第三方用户ID，格式化后的用户标识"},
                    "page": {"type": "string", "description": "页码，从0开始"},
                    "size": {"type": "string", "description": "每页大小"},
                    "debitRequestResult": {"type": "string", "description": "扣款请求结果筛选（可选）"},
                    "invoice": {"type": "string", "description": "发票状态筛选（可选）"},
                    "startDate": {"type": "string", "description": "开始日期（可选）"},
                    "endDate": {"type": "string", "description": "结束日期（可选）"}
                }
            },
            "AlipayTripFindTravelListRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"},
                    "pageNumber": {"type": "integer", "description": "当前页码"},
                    "pageSize": {"type": "integer", "description": "每页大小"},
                    "totalPage": {"type": "integer", "description": "总页数"},
                    "totalCount": {"type": "integer", "description": "总记录数"},
                    "ticketTransRecord": {"type": "array", "items": {"$ref": "#/components/schemas/AlipayTripTravelRecordDTO"}, "description": "乘车记录列表"}
                }
            },
            "AlipayTripFindTravelDetailReqDTO": {
                "type": "object",
                "required": ["thirdUserId", "orderNo"],
                "properties": {
                    "thirdUserId": {"type": "string", "description": "第三方用户ID，格式化后的用户标识"},
                    "orderNo": {"type": "string", "description": "订单号"}
                }
            },
            "AlipayTripFindTravelDetailRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码，0000 表示成功"},
                    "retMsg": {"type": "string", "description": "返回消息"},
                    "entryStationName": {"type": "string", "description": "进站站点名称"},
                    "entryDate": {"type": "string", "description": "进站时间"},
                    "exitStationName": {"type": "string", "description": "出站站点名称"},
                    "exitDate": {"type": "string", "description": "出站时间"},
                    "payAmount": {"type": "string", "description": "实付金额（单位：分）"},
                    "totalAmount": {"type": "string", "description": "总金额（单位：分）"},
                    "orderExpType": {"type": "string", "description": "订单扩展类型"},
                    "tradeOrderNo": {"type": "string", "description": "交易订单号"},
                    "payTradeOrderNo": {"type": "string", "description": "支付交易订单号"},
                    "payOrderNoDate": {"type": "string", "description": "支付订单日期"},
                    "payChannelCode": {"type": "string", "description": "支付渠道代码"},
                    "debitRequestResult": {"type": "string", "description": "扣款请求结果"},
                    "discountFee": {"type": "string", "description": "优惠金额"},
                    "discountInfo": {"type": "string", "description": "优惠信息"},
                    "companionFlag": {"type": "string", "description": "同行票标识"},
                    "cardNum": {"type": "string", "description": "卡号"},
                    "ticketCode": {"type": "string", "description": "日票票号"},
                    "countingTimes": {"type": "string", "description": "计次次数（预留）"},
                    "countingFlag": {"type": "string", "description": "计次标识（预留）"}
                }
            },
            "AlipayTripTravelRecordDTO": {
                "type": "object",
                "properties": {
                    "entryStationName": {"type": "string", "description": "进站站点名称"},
                    "entryDate": {"type": "string", "description": "进站时间"},
                    "exitStationName": {"type": "string", "description": "出站站点名称"},
                    "exitDate": {"type": "string", "description": "出站时间"},
                    "payAmount": {"type": "string", "description": "实付金额（单位：分）"},
                    "totalAmount": {"type": "string", "description": "总金额（单位：分）"},
                    "orderExpType": {"type": "string", "description": "订单扩展类型"},
                    "tradeOrderNo": {"type": "string", "description": "交易订单号"},
                    "payTradeOrderNo": {"type": "string", "description": "支付交易订单号"},
                    "payOrderNoDate": {"type": "string", "description": "支付订单日期"},
                    "payChannelCode": {"type": "string", "description": "支付渠道代码"},
                    "debitRequestResult": {"type": "string", "description": "扣款请求结果"},
                    "discountFee": {"type": "string", "description": "优惠金额"},
                    "discountInfo": {"type": "string", "description": "优惠信息"},
                    "companionFlag": {"type": "string", "description": "同行票标识"},
                    "cardNum": {"type": "string", "description": "卡号"},
                    "ticketCode": {"type": "string", "description": "日票票号"},
                    "countingTimes": {"type": "string", "description": "计次次数（预留）"},
                    "countingFlag": {"type": "string", "description": "计次标识（预留）"}
                }
            },
            "AlipayTripRequestPayReqDTO": {
                "type": "object",
                "required": ["orderNo", "scene", "paymentVendor", "amount", "industryType", "subject", "thirdUserId"],
                "properties": {
                    "orderNo": {"type": "string", "description": "订单号"},
                    "scene": {"type": "string", "description": "支付类型/场景"},
                    "paymentVendor": {"type": "string", "description": "支付方式"},
                    "amount": {"type": "integer", "description": "支付金额（单位：分）"},
                    "industryType": {"type": "string", "description": "行业类型：1-地铁 2-公交 3-打车 4-购物"},
                    "subject": {"type": "string", "description": "订单标题"},
                    "body": {"type": "string", "description": "订单描述"},
                    "requestSignSeq": {"type": "string", "description": "签约流水号（免密场景必填）"},
                    "thirdUserId": {"type": "string", "description": "用户ID"},
                    "orderTimeOut": {"type": "integer", "description": "订单超时时间（秒），默认60秒"},
                    "authCode": {"type": "string", "description": "授权码（部分渠道主动支付需要）"},
                    "notifyUrl": {"type": "string", "description": "回调地址"},
                    "returnUrl": {"type": "string", "description": "返回前端页面地址（可提前配置）"},
                    "ipAddress": {"type": "string", "description": "用户IP地址"},
                    "remark": {"type": "string", "description": "备注"},
                    "industryDetail": {"type": "string", "description": "行业详情，json格式"}
                }
            },
            "AlipayTripRequestPayRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码"},
                    "retMsg": {"type": "string", "description": "返回消息"},
                    "orderNo": {"type": "string", "description": "订单号"}
                }
            },
            "AlipayTripRequestRefundReqDTO": {
                "type": "object",
                "required": ["orderNo", "cardIssueCode", "cardNum", "channelAgreementNo", "refundAmount", "refundOrderNo"],
                "properties": {
                    "orderNo": {"type": "string", "description": "原订单号"},
                    "cardIssueCode": {"type": "string", "description": "卡机构编号，支付宝0007"},
                    "cardNum": {"type": "string", "description": "逻辑卡号"},
                    "channelAgreementNo": {"type": "string", "description": "渠道协议号"},
                    "refundAmount": {"type": "string", "description": "退款金额，单位分"},
                    "refundOrderNo": {"type": "string", "description": "退款订单号"}
                }
            },
            "AlipayTripRequestRefundRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码"},
                    "retMsg": {"type": "string", "description": "返回消息"}
                }
            },
            "AlipayTripPayQueryReqDTO": {
                "type": "object",
                "required": ["orderNo", "cardIssueCode", "cardNum", "channelAgreementNo"],
                "properties": {
                    "orderNo": {"type": "string", "description": "订单号"},
                    "cardIssueCode": {"type": "string", "description": "卡机构编号，支付宝0007"},
                    "cardNum": {"type": "string", "description": "逻辑卡号"},
                    "channelAgreementNo": {"type": "string", "description": "渠道协议号"}
                }
            },
            "AlipayTripPayQueryRespDTO": {
                "type": "object",
                "properties": {
                    "retCode": {"type": "string", "description": "返回码"},
                    "retMsg": {"type": "string", "description": "返回消息"},
                    "outTradeNo": {"type": "string", "description": "订单号"},
                    "paymentTime": {"type": "string", "description": "支付时间"},
                    "tradeStatus": {"type": "string", "description": "支付状态"},
                    "totalAmount": {"type": "string", "description": "支付金额"},
                    "tradeNo": {"type": "string", "description": "支付渠道订单号"},
                    "tradeDesc": {"type": "string", "description": "支付结果"}
                }
            }
        }
    }
}

with open(os.path.join(output_dir, "fep-alipay-server-openapi.json"), "w", encoding="utf-8") as f:
    json.dump(fep_alipay, f, ensure_ascii=False, indent=2)

print("fep-alipay-server-openapi.json generated")
