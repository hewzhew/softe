# 智能充电桩调度计费系统

第 2 组软件工程课程本机演示系统。系统实现站点时钟、事件流、车主充电申请、充电桩队列调度、分时计费、账单详单、管理员监控和单桩故障重调度。

## 技术栈

- 后端：Spring Boot 3、Java 17、Spring Data JPA、H2
- 前端：Vue 3、Vite、Element Plus、Axios
- 数据库：H2 文件模式，默认数据文件位于 `backend/data/`

## 与概要设计的对应关系

- Controller 层：`AccountController`、`ChargingController`、`BillingController`、`PileController`、`QueueController`、`FaultController`
- Service 层：`AccountService`、`ChargingService`、`SchedulerService`、`BillingService`、`PileService`、`QueueService`、`FaultService`
- Domain 层：`Vehicle`、`ChargingRequest`、`ChargingPile`、`ChargingSession`、`Bill`、`DetailedList`、`FaultRecord`
- Strategy 层：普通最短完成时间调度、优先级故障调度、时间顺序故障调度

## 后端运行

```powershell
cd D:\softe\backend
mvn test
mvn spring-boot:run
```

后端地址：

```text
http://localhost:8080
```

H2 控制台：

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:file:./data/charging-demo
User: sa
Password: 空
```

## 前端运行

```powershell
cd D:\softe\frontend
npm install
npm run dev
```

前端地址：

```text
http://127.0.0.1:5173
```

## 演示流程

推荐按 [docs/demo-script.md](docs/demo-script.md) 操作。

核心路径：

1. 在“站点运行”同步预置事件流，查看统一站点时钟、事件流和充电站沙盘。
2. 使用倍速或手动推进站点时间，观察车辆申请、调度、充电、完成和出账。
3. 在“车主自助”进入车辆门户，提交申请、查看当前状态和账单。
4. 在“运营管理”查看队列和充电桩状态，执行调度或处理故障。
5. 返回“站点运行”确认各工作区看到的是同一套本机业务数据。

## 说明

- 密码处理为课堂演示用的简化编码，不是生产级密码安全方案。
- 物理充电桩通信由本地状态模拟。
- Redis、真实设备网关和完整权限体系可作为后续工程扩展点。
