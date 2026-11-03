# IT-040: nginx proxy_buffering 导致响应截断

## 背景
E2E 测试发现所有 API 返回 `content-length: 0`，响应体为空，但后端日志显示正常返回数据。

## 根因
nginx 配置缺少 `proxy_buffering off;`，导致大响应被截断。

## 影响范围
- 测试环境: http://172.20.211.23:30029
- 涉及所有 `/xxx-server` 路径的后端服务

## 解决方案
在 `web/nginx.conf` 的所有 `location /xxx-server` 块中添加 `proxy_buffering off;`

## 验证步骤
1. 应用配置修改
2. 重启 web Deployment
3. 测试 API 响应：
   ```bash
   curl http://172.20.211.23:30029/web-server/system/user/list
   ```
4. 确认返回完整 JSON 数据

## 状态
- [ ] 配置已修改（本地）
- [ ] 已提交到仓库
- [ ] 已通过 CloudOS 部署
- [ ] 已验证修复效果

## 关联文档
- E2E 测试报告: `/tmp/e2e-completed-report.md`
- 修复方案: `/tmp/fix-summary.md`
