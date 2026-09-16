-- 修复「切换 tab 后查询条件丢失」：回填 SYS_MENU.ROUTE_NAME + 清理不生效的重复菜单。
-- 成因：SysMenuServiceImpl.getRouteName(name, path) 在 ROUTE_NAME 为空时取 capitalize(PATH)，
--       得到的路由名（如 Alipay-user）与 views 下组件的 name（AlipayUserSearch）不一致；
--       tagsView.addCachedView 把路由名塞进 cachedViews，而 AppMain.vue 的
--       <keep-alive :include="cachedViews"> 按组件 name 匹配，对不上即不缓存。
-- 因此 ROUTE_NAME 必须逐条等于对应 .vue 的 <script setup name="..."> 值，且全表唯一（vue-router 路由名唯一）。
-- 执行后 getRouters 实时生效，前端重新登录即可，无需重建 web-admin 镜像。

UPDATE SYS_MENU SET ROUTE_NAME = 'DailyTicketRefundRecord'    WHERE MENU_ID = 2006;
UPDATE SYS_MENU SET ROUTE_NAME = 'FacePayOrder'               WHERE MENU_ID = 2007;
UPDATE SYS_MENU SET ROUTE_NAME = 'BaseFare'                   WHERE MENU_ID = 2008;
UPDATE SYS_MENU SET ROUTE_NAME = 'OrderRefundCycle'           WHERE MENU_ID = 2009;
UPDATE SYS_MENU SET ROUTE_NAME = 'BlacklistManagement'        WHERE MENU_ID = 2013;
UPDATE SYS_MENU SET ROUTE_NAME = 'RiskGroup'                  WHERE MENU_ID = 2016;
UPDATE SYS_MENU SET ROUTE_NAME = 'RiskRule'                   WHERE MENU_ID = 2020;
UPDATE SYS_MENU SET ROUTE_NAME = 'RiskControlLog'             WHERE MENU_ID = 2024;
UPDATE SYS_MENU SET ROUTE_NAME = 'LineInfo'                   WHERE MENU_ID = 2026;
UPDATE SYS_MENU SET ROUTE_NAME = 'StationInfo'                WHERE MENU_ID = 2027;
UPDATE SYS_MENU SET ROUTE_NAME = 'LineStationVersion'         WHERE MENU_ID = 2028;
UPDATE SYS_MENU SET ROUTE_NAME = 'DailyTicketRefund'          WHERE MENU_ID = 2029;
UPDATE SYS_MENU SET ROUTE_NAME = 'AlipayUserSearch'           WHERE MENU_ID = 2072;
UPDATE SYS_MENU SET ROUTE_NAME = 'ItpUserSearch'              WHERE MENU_ID = 2073;
UPDATE SYS_MENU SET ROUTE_NAME = 'SingleTicketPurchaseLimit'  WHERE MENU_ID = 2075;
UPDATE SYS_MENU SET ROUTE_NAME = 'CardPoolManagement'         WHERE MENU_ID = 2078;

-- 删除三条不生效的重复菜单（判据见文件末尾）。
DELETE FROM SYS_ROLE_MENU WHERE MENU_ID = 2030;
DELETE FROM SYS_MENU      WHERE MENU_ID = 2045;
DELETE FROM SYS_MENU      WHERE MENU_ID = 2030;
DELETE FROM SYS_MENU      WHERE MENU_ID = 2005;
COMMIT;

-- 删除判据（buildMenus 只在 menu_type='M' 时递归 children，SysMenuServiceImpl.java:180）：
--   2005「日票订单查询」C / refund / 无 perms / SYS_ROLE_MENU 0 行 —— 未授权任何角色，前台不可见，
--        且与 2029「日票退款」component 完全相同（trans/daily-ticket/refund/index）。
--   2030「日票退款记录」C / parent=2029（C 类型）—— 父级不是目录，buildMenus 直接丢弃整棵子树，
--        该路由从不下发给前端，页面永远打不开；与 2006 component 相同。
--   2045「日票退款记录查询」F / parent=2030 —— 随 2030 一起成为孤儿；其 perms
--        trans:daily-ticket:refund:list 在前端无 v-hasPermi 消费点（daily-ticket 页面全无权限指令）。
-- 保留：2006（挂在目录 2077 下、已授权，是真正在用的「日票退款记录」）、2029（页面本身）。
--   注意 2029 的按钮 2044「日票退款查询」不在保留范围：它落在下面第 50 行起的「重复按钮权限」
--   第一组里被删（perms 与父 C 菜单相同且前端无 v-hasPermi 消费点）。本行早前写「保留 2044」是错的。

-- 还原（按需逐条执行）：
-- UPDATE SYS_MENU SET ROUTE_NAME = NULL WHERE MENU_ID IN
--   (2006,2007,2008,2009,2013,2016,2020,2024,2026,2027,2028,2029,2072,2073,2075,2078);
-- INSERT INTO SYS_MENU VALUES (2005,'日票订单查询',2077,1,'refund','trans/daily-ticket/refund/index',NULL,NULL,1,0,'C','0','0',NULL,'edit','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2030,'日票退款记录',2029,1,'refund-record','trans/daily-ticket/refund-record/index',NULL,NULL,1,0,'C','0','0','trans:daily-ticket:refund:list','document','admin',SYSDATE,NULL,NULL,'查询日票退款处理记录');
-- INSERT INTO SYS_MENU VALUES (2045,'日票退款记录查询',2030,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','trans:daily-ticket:refund:list','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_ROLE_MENU VALUES (1,2030);

-- 清理重复的按钮权限菜单（21 条，全部 MENU_TYPE='F' 且 SYS_ROLE_MENU 0 行，删后每个 perms 仍至少保留 1 条）。
-- 成因：早期给 C 类页面菜单直接写了 perms，后来又批量补了一整套 F 类按钮菜单，两批重叠。
-- 第一组：perms 与其父 C 菜单完全相同，且这些 :query perms 在前端无 v-hasPermi 消费点。
DELETE FROM SYS_MENU WHERE MENU_ID IN (2041,2043,2044,2048,2049,2053,2057,2061,2062,2066,2070)
  AND MENU_TYPE = 'F' AND NOT EXISTS (SELECT 1 FROM SYS_ROLE_MENU rm WHERE rm.MENU_ID = SYS_MENU.MENU_ID);
-- 第二组：同一父菜单下同 perms 的两条 F，保留先建的（2010~2012 / 2017~2019 / 2021~2023 / 2025），删后建的。
DELETE FROM SYS_MENU WHERE MENU_ID IN (2042,2050,2051,2052,2054,2055,2056,2058,2059,2060)
  AND MENU_TYPE = 'F' AND NOT EXISTS (SELECT 1 FROM SYS_ROLE_MENU rm WHERE rm.MENU_ID = SYS_MENU.MENU_ID);
COMMIT;

-- 还原上面 21 条（列序 MENU_ID, MENU_NAME, PARENT_ID, ORDER_NUM, PATH, COMPONENT, QUERY, ROUTE_NAME,
--   IS_FRAME, IS_CACHE, MENU_TYPE, VISIBLE, STATUS, PERMS, ICON, CREATE_BY, CREATE_TIME, UPDATE_BY, UPDATE_TIME, REMARK）：
-- INSERT INTO SYS_MENU VALUES (2041,'当面付订单查询',2007,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','trans:face-pay:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2042,'当面付订单退款',2007,2,NULL,NULL,NULL,NULL,1,0,'F','0','0','trans:face-pay:refund','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2043,'黑名单查询',2013,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','trans:blacklist:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2044,'日票退款查询',2029,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','trans:daily-ticket:refund:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2048,'基础票价查询',2008,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:base-fare:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2049,'订单退款周期查询',2009,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:order-refund-cycle:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2050,'订单退款周期新增',2009,2,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:order-refund-cycle:add','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2051,'订单退款周期修改',2009,3,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:order-refund-cycle:edit','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2052,'订单退款周期删除',2009,4,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:order-refund-cycle:remove','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2053,'风险组查询',2016,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-group:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2054,'风险组新增',2016,2,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-group:add','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2055,'风险组修改',2016,3,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-group:edit','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2056,'风险组删除',2016,4,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-group:remove','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2057,'风险规则查询',2020,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-rule:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2058,'风险规则新增',2020,2,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-rule:add','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2059,'风险规则修改',2020,3,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-rule:edit','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2060,'风险规则删除',2020,4,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-rule:remove','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2061,'风险控制操作记录查询',2024,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:risk-control-log:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2062,'线路查询',2026,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:line:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2066,'站点查询',2027,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:station:query','#','admin',SYSDATE,NULL,NULL,NULL);
-- INSERT INTO SYS_MENU VALUES (2070,'线路站点版本查询',2028,1,NULL,NULL,NULL,NULL,1,0,'F','0','0','para:lineStationVersion:query','#','admin',SYSDATE,NULL,NULL,NULL);

-- 未处理的既有缺口（不是本次清理造成）：前端 para/operator/index.vue 用了
--   para:operator:add / edit / remove / export 四个 perms，但 SYS_MENU 里既没有该页面菜单、也没有这四条权限，
--   非 admin 角色打开该页时四个按钮恒不可见。需要业务确认是否补菜单。

-- 回查（2026-09-14 执行结果，全部实测）：
--   ROUTE_NAME 与组件 name 逐条一致，SYS_MENU 全表无重复 ROUTE_NAME（0 行）；
--   2077 子树剩 2006 / 2007(+2025) / 2029，2005 / 2030 / 2044 / 2045 已删除；
--   重复 perms 只剩 monitor:cache:list(113,114)，是 RuoYi 自带的「缓存监控 / 缓存列表」两个 C 菜单，未动；
--   前端 v-hasPermi 用到的 16 个业务 perms 在库内均有对应记录；孤儿菜单（父不存在）0 条。
-- SELECT MENU_ID, MENU_NAME, PATH, COMPONENT, ROUTE_NAME FROM SYS_MENU
--  WHERE MENU_TYPE = 'C' AND (COMPONENT LIKE 'trans/%' OR COMPONENT LIKE 'para/%') ORDER BY MENU_ID;
-- SELECT ROUTE_NAME, COUNT(*) FROM SYS_MENU WHERE ROUTE_NAME IS NOT NULL GROUP BY ROUTE_NAME HAVING COUNT(*) > 1;
