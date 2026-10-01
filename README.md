# Netty WebSocket 电商在线客服 Demo

轻量 Java 17 Demo：Netty WebSocket 长连接、读写空闲心跳、浏览器指数退避重连、会话重绑定、人工客服接入和双向消息路由。

项目同时提供独立健康检查、Prometheus 指标、JSON 结构化日志、Docker Compose 和 GitHub Actions，便于展示长连接服务的运行与排障能力。

## 核心流程

```text
顾客连接 /ws?sessionId=...&userId=...&role=CUSTOMER
  → SessionRegistry 保存会话
  → 无客服时返回 HANDOFF_REQUIRED
  → 客服以 AGENT 身份连接并发送 CLAIM
  → 双方按 sessionId 路由消息
  → 同 sessionId 重连并替换旧 Channel
```

## 运行

```powershell
mvn test
mvn package
java -jar target/netty-websocket-customer-service-1.0.0.jar
```

也可以容器化启动：

```powershell
docker compose up -d --build
```

浏览器打开 `web/client.html`。开两个页面：一个选择 `CUSTOMER`，另一个选择 `AGENT`；客服端填写顾客 `sessionId` 后点击“客服接入”。

## 可复用代码

- `HandshakeQueryHandler`：握手参数解析与身份绑定。
- `SessionRegistry`：会话管理、重连替换和客服路由。
- `CustomerServiceWebSocketHandler`：心跳、超时关闭和消息分发。
- `web/client.html`：浏览器心跳与指数退避重连。
- `HealthMetricsHandler`：复用同一 Netty 端口暴露 `/health` 与 `/metrics`。

## 运维接口

- `GET /health`：返回服务存活状态。
- `GET /metrics`：Prometheus 文本格式，包含连接、消息、心跳超时和人工排队计数。
- 控制台日志采用 JSON 格式，便于日志平台按字段检索。

## 本人改造内容

- 从上游通用 Netty/Atmosphere 多模块服务器提取为单 Maven Demo。
- 新增电商在线客服会话、人工接入、双向消息路由和排队提示。
- 新增长连接心跳、超时剔除、断线重绑定与自动重连。
- 新增会话路由单元测试和本地双端测试页。
- 新增 Prometheus 指标、结构化日志、健康检查、容器化和 CI。

## 开源说明

本项目基于 `Atmosphere/nettosphere` 的公开代码与提交历史改造，保留 `LICENSE.txt`（Apache License 2.0）和版权信息。新增客服业务代码、测试页与中文文档由本仓库维护者实现。
