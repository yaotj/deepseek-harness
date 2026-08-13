-- 当面付订单查询菜单。父菜单 ID 可按现场“交易运营”菜单调整。
insert into sys_menu values ('2007', '当面付订单查询', '0', '12', 'face-pay-order', 'trans/face-pay/order/index', '', '', 1, 0, 'C', '0', '0', 'trans:face-pay:query', 'money', 'admin', sysdate, '', null, '查询 TVM 当面付订单 TBL_TVM_ORDER_PAY');
insert into sys_menu values ('2025', '当面付订单退款', '2007', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'trans:face-pay:refund', '#', 'admin', sysdate, '', null, '对支付成功的当面付订单发起全额退款');
