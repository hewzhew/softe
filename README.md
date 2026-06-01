# 智能充电桩调度计费系统

第 2 组软件工程课程验收演示版。系统实现车主充电申请、充电桩队列调度、分时计费、账单详单、管理员监控和单桩故障重调度。

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

1. 在演示控制台初始化系统参数。
2. 生成演示车辆和请求。
3. 触发调度，查看各充电桩队列。
4. 在车主端注册、申请、查询状态、开始充电、结束充电。
5. 查看账单和详单。
6. 在管理员端选择充电桩并触发故障。
7. 分别演示优先级调度和时间顺序调度。

## 说明

- 密码处理为课堂演示用的简化编码，不是生产级密码安全方案。
- 物理充电桩通信由本地状态模拟。
- Redis、真实设备网关和完整权限体系可作为后续工程扩展点。
