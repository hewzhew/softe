# 智能充电桩调度计费系统实现设计

日期：2026-06-01

## 目标

本设计用于把第 2 组已有的《智能充电桩调度管理系统概要设计》和《智能充电桩调度计费系统详细需求》落成一个可本机运行、可课堂验收演示的系统。系统重点证明充电请求、排队调度、分时计费、账单详单、充电桩监控和故障重调度能够形成完整业务闭环。

第一版实现以可演示、可测试、易部署为优先级，不追求真实生产系统的全部工程能力。概要设计中提到的 Redis、物理充电桩网关和复杂权限体系在演示版中以本地数据库、模拟设备状态和简化角色入口替代。

## 技术路线

采用前后端分离架构：

- 后端：Spring Boot 3、Java 17、Spring Data JPA、H2 数据库。
- 前端：Vue 3、Vite、Element Plus、Axios。
- 数据库：H2 文件模式，默认随项目本机启动，可保留演示数据。
- 接口：REST API。
- 项目结构：根目录下创建 `backend/` 和 `frontend/` 两个子项目。

选择 H2 而不是 MySQL，是为了减少课堂验收环境依赖。选择 Spring Boot + Vue，是为了和概要设计中的 Controller-Service-Domain-Repository 分层、车主端/管理员端界面设计保持一致。

## 功能范围

第一版必须实现：

1. 车主注册车辆账号，对应 `createNewAccount(car_Id, userName, car_Capacity)` 和 `set_pwd(******)`。
2. 提交充电申请，对应 `E_chargingRequest(car_Id, Request_Amount, Request_Mode)`。
3. 修改充电量和充电模式，对应 `Modify_Amount(car_Id, Amount)` 和 `Modify_Mode(car_Id, Mode)`。
4. 查询车辆状态和队列状态，对应 `Query_Car_State(car_id)` 和 `Query_QueueState(queuelist)`。
5. 开始、查询、结束充电，对应 `Start_Charging`、`Query_Charging_State`、`End_Charging`。
6. 生成并查询账单和详单，对应 `Request_Bill` 和 `Request_DetailedList`。
7. 管理员启动、运行、关闭充电桩，并查看充电桩状态，对应 `powerOn`、`Start_ChargingPile`、`powerOff`、`Query_PileState`。
8. 管理员维护计费参数，对应 `setParameters`。
9. 模拟单个充电桩故障，支持优先级调度、时间顺序调度和充电中故障恢复。
10. 系统参数可配置：快充桩数量、慢充桩数量、等候区容量、每个充电桩队列长度、快充功率、慢充功率、电价和服务费。

第一版暂不实现：

- 真实支付。
- 真实物理充电桩通信。
- 完整用户登录鉴权。
- Redis 缓存和 WebSocket 推送。
- 多站点、多管理员权限管理。

可选加分项预留接口：

- 单次调度总充电时长最短。
- 批量调度总充电时长最短。

如果核心闭环完成且时间允许，再实现这两个策略。

## 后端架构

后端采用四层结构：

```text
controller  ->  service  ->  domain  ->  repository
```

主要包结构：

```text
backend/src/main/java/.../charging/
  controller/
    AccountController
    ChargingController
    BillingController
    PileController
    QueueController
    FaultController
    ConfigController

  service/
    AccountService
    ChargingService
    SchedulerService
    BillingService
    PileService
    QueueService
    FaultService
    ConfigService

  domain/
    UserAccount
    Vehicle
    ChargingRequest
    ChargingPile
    ChargingSession
    Bill
    DetailedList
    FaultRecord
    TariffRule
    StationConfig

  repository/
    UserAccountRepository
    VehicleRepository
    ChargingRequestRepository
    ChargingPileRepository
    ChargingSessionRepository
    BillRepository
    DetailedListRepository
    FaultRecordRepository
    TariffRuleRepository
    StationConfigRepository

  strategy/
    SchedulingStrategy
    ShortestFinishTimeStrategy
    PriorityFaultStrategy
    TimeOrderFaultStrategy
    ShortestSingleStrategy
    ShortestBatchStrategy
```

Controller 只负责请求参数接收、基础校验和 DTO 返回；Service 负责业务规则、事务边界和跨对象协作；Domain 保存业务状态；Repository 负责 H2 持久化。

## 核心领域对象

`UserAccount` 表示车主账号，保存用户名、密码摘要和账号状态。

`Vehicle` 表示车辆，保存 `carId`、电池容量和所属账号。

`ChargingRequest` 表示一次充电申请，保存车辆、请求电量、充电模式、请求时间、排队号、当前位置和状态。状态包括 `WAITING_AREA`、`PILE_QUEUE`、`CHARGING`、`FINISHED`、`CANCELLED`、`INTERRUPTED`。

`ChargingPile` 表示充电桩，保存桩编号、模式、功率、运行状态、累计充电次数、累计充电时长、累计充电量。状态包括 `OFFLINE`、`IDLE`、`WORKING`、`FAULT`。

`ChargingSession` 表示实际充电会话，保存车辆、申请、充电桩、开始时间、结束时间、实际充电量和状态。

`Bill` 表示账单汇总，`DetailedList` 表示一次充电详单。故障中断产生的已充部分也生成详单。

`FaultRecord` 表示故障记录，保存故障桩、故障时间、恢复时间、调度策略和处理结果。

`StationConfig` 保存验收可变参数，例如快充桩数、慢充桩数、等候区容量和队列长度。

## 调度规则

普通调度遵循详细需求中的规则：

1. 快充请求只能进入快充桩队列，慢充请求只能进入慢充桩队列。
2. 当任意匹配模式充电桩队列存在空位时，从等候区选择该模式排队号最靠前的车辆进入充电区。
3. 为该车辆选择预计完成时间最短的充电桩队列。
4. 预计完成时间 = 目标充电量 / 充电桩功率 + 所选充电桩队列中所有未完成车辆的剩余充电时间。

排队号规则：

- 快充为 `F1`、`F2`、`F3`。
- 慢充为 `T1`、`T2`、`T3`。
- 修改充电模式时重新生成目标模式的新排队号，并排到目标模式等候区末尾。
- 修改充电量时排队号不变，但预计等待时间重新计算。

故障调度规则：

1. 只处理单个充电桩故障。
2. 故障桩状态变为 `FAULT`，不再接受新调度。
3. 如果故障桩当前有正在充电车辆，停止本次计费并为已充部分生成详单。
4. 暂停等候区叫号。
5. 根据管理员选择的策略重调度故障队列和受影响车辆。

优先级调度：

- 其它同类型充电桩出现空位时，优先为故障队列车辆服务。
- 故障队列全部调度完毕后，再恢复等候区叫号。
- 第一版优先级按“故障受影响车辆优先、等待时间长者优先、请求时间早者优先”排序。

时间顺序调度：

- 暂停等候区叫号。
- 将故障队列中尚未完成的车辆和其它同类型充电桩中尚未开始充电的车辆合并。
- 按排队号生成时间或 `requestTime` 从早到晚重新分配。
- 调度完毕后恢复等候区叫号。

故障恢复：

- 故障桩恢复为 `IDLE` 后，如果其它同类型充电桩中仍有未开始充电车辆，则暂停等候区叫号。
- 将这些车辆按时间顺序重新调度到包含恢复桩在内的同类型可用桩。
- 调度完毕后恢复等候区叫号。

## 计费规则

总费用 = 充电费 + 服务费。

服务费 = 服务费单价 * 实际充电度数。

充电费按分时电价计算：

- 峰时：10:00-15:00、18:00-21:00，默认 1.0 元/度。
- 平时：7:00-10:00、15:00-18:00、21:00-23:00，默认 0.7 元/度。
- 谷时：23:00-次日 7:00，默认 0.4 元/度。

实际充电时长 = 实际充电度数 / 充电功率。

如果一次充电跨越多个时段，按每个时段覆盖的充电时长折算电量并分别计费。第一版用线性充电模型，即单位时间内充电功率恒定。

## REST API 草案

账号：

- `POST /api/accounts`：创建账号和车辆。
- `POST /api/accounts/{carId}/password`：设置密码。

充电申请：

- `POST /api/charging/requests`：提交充电申请。
- `PATCH /api/charging/requests/{carId}/amount`：修改充电量。
- `PATCH /api/charging/requests/{carId}/mode`：修改充电模式。
- `GET /api/charging/cars/{carId}/state`：查询车辆状态。
- `POST /api/charging/{carId}/start`：开始充电。
- `GET /api/charging/{carId}/state`：查询充电状态。
- `POST /api/charging/{carId}/end`：结束充电。

账单：

- `GET /api/bills?carId=&date=`：查询账单。
- `GET /api/bills/{billId}/details`：查询详单。

充电桩：

- `POST /api/piles/{pileId}/power-on`：启动充电桩。
- `POST /api/piles/{pileId}/start`：运行充电桩。
- `POST /api/piles/{pileId}/power-off`：关闭充电桩。
- `GET /api/piles`：查询全部充电桩状态。
- `GET /api/piles/{pileId}`：查询单个充电桩状态。

队列：

- `GET /api/queues`：查询全部队列状态。
- `POST /api/scheduler/dispatch`：手动触发普通调度。

故障：

- `POST /api/faults`：模拟故障，参数为 `pileId` 和 `strategy`。
- `POST /api/faults/{pileId}/recover`：恢复充电桩。

配置：

- `GET /api/config`：读取系统配置。
- `PUT /api/config`：更新系统配置并初始化充电桩。
- `POST /api/demo/reset`：重置演示数据。
- `POST /api/demo/seed`：生成一组演示车辆和请求。

## 前端设计

前端采用一个单页应用，左侧导航分为三个视图。

车主端：

- 车辆账号表单。
- 充电申请表单。
- 当前排队状态。
- 当前充电状态。
- 账单和详单列表。

管理员端：

- 充电桩状态表。
- 充电桩队列表。
- 启动、运行、关闭、故障、恢复按钮。
- 计费参数表单。
- 故障调度策略选择。

演示控制台：

- 初始化参数表单。
- 批量生成车辆请求。
- 手动触发调度。
- 重置数据。
- 当前系统快照，包括等候区、各充电桩队列、进行中会话和最新账单。

界面以稳定、清晰、便于课堂演示为原则，不做营销式首页。第一屏直接展示演示控制台和当前系统状态。

## 数据流

提交充电申请：

1. 车主端提交车辆、充电量和模式。
2. `ChargingController` 接收请求。
3. `ChargingService` 校验车辆、容量和当前状态。
4. 创建 `ChargingRequest`，生成排队号。
5. `SchedulerService` 尝试调度到匹配充电桩队列。
6. 返回车辆位置、车辆状态、队列号和请求时间。

结束充电：

1. 车主端或管理员端触发结束充电。
2. `ChargingService` 结束 `ChargingSession`。
3. `BillingService` 按实际充电量和分时电价生成 `Bill` 与 `DetailedList`。
4. `PileService` 释放充电桩。
5. `SchedulerService` 调度下一辆车。
6. 返回账单和操作结果。

故障处理：

1. 管理员端选择故障桩和调度策略。
2. `FaultService` 标记故障桩并创建 `FaultRecord`。
3. 如果存在进行中会话，`BillingService` 结算已充部分。
4. `SchedulerService` 根据策略重排受影响车辆。
5. 返回故障处理结果和最新队列状态。

## 测试与验收

后端测试优先覆盖：

- 分时电价计费。
- 普通最短完成时间调度。
- 修改充电模式后重新排队。
- 修改充电量后等待时间刷新。
- 优先级故障调度。
- 时间顺序故障调度。
- 充电中故障后已充部分生成详单。

前端验收重点：

- 页面可以创建和查询数据。
- 调度结果能清晰展示。
- 故障策略切换后结果不同且可解释。
- 重置和初始化功能稳定。

本机验收命令目标：

```text
backend Windows: .\mvnw.cmd test
backend Windows: .\mvnw.cmd spring-boot:run
backend macOS/Linux: ./mvnw test
backend macOS/Linux: ./mvnw spring-boot:run
frontend: npm install
frontend: npm run dev
```

最终交付应包括：

- 后端源码。
- 前端源码。
- README 运行说明。
- 演示脚本。
- 测试用例说明。

## 风险与处理

如果老师要求使用 MySQL，则保留 JPA Repository 层，只需替换 datasource 配置和驱动依赖。

如果老师要求严格按照“服务器端、用户客户端、管理员客户端”拆分，可以在当前 Vue 单页应用中保持三类入口；演示上等价，工程上仍是前后端分离。

如果现场不能联网安装前端依赖，需要提前准备 `node_modules` 或打包后的 `dist/`。后端 H2 不依赖外部数据库，风险较低。

如果时间不足，优先保证普通调度、计费、账单、故障优先级调度和时间顺序调度。可选加分策略只保留接口和说明。

## 通过标准

系统达到以下标准即可进入实现验收阶段：

1. 一条车辆申请可以从注册、排队、调度、开始充电、结束充电走到账单生成。
2. 管理员可以查看所有充电桩状态和各桩队列。
3. 系统参数可在页面修改并影响调度结果。
4. 充电桩故障后可以按优先级或时间顺序完成重调度。
5. 后端核心业务逻辑有自动化测试覆盖。
6. 前端可以在本机浏览器完成完整演示流程。
