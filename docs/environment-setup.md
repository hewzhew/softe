# 开发环境准备说明

本项目计划采用 Spring Boot + Vue + H2。为了让本机演示稳定，建议使用下面的环境。

## 必需软件

1. Git
   - 已检测到本机可用：`git version 2.54.0.windows.1`。

2. JDK 17
   - Spring Boot 3 推荐使用 Java 17。
   - 本机已检测到 JDK 17 路径：`C:\Program Files\Amazon Corretto\jdk17.0.17_10`。
   - 当前默认 `JAVA_HOME` 指向 JDK 25。项目会把编译目标锁定为 Java 17；如遇兼容问题，可在当前 PowerShell 临时切换：

```powershell
$env:JAVA_HOME = "C:\Program Files\Amazon Corretto\jdk17.0.17_10"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

   - 安装后在 PowerShell 中检查：

```powershell
java -version
javac -version
```

期望能看到版本号包含 `17`。

3. Node.js LTS
   - 前端 Vue + Vite 使用 Node.js。
   - 建议安装当前 LTS 版本。
   - 安装后在 PowerShell 中检查：

```powershell
node -v
npm -v
```

4. IntelliJ IDEA 或 VS Code
   - 后端推荐 IntelliJ IDEA Community/Ultimate。
   - 前端可用 VS Code。

## 推荐但非必需

1. Maven
   - 如果后端生成 Maven Wrapper，则不需要单独安装 Maven。
   - 如果未生成 Wrapper，可安装 Maven 并检查：

```powershell
mvn -v
```

2. pnpm
   - 第一版默认用 npm，避免额外依赖。

## 仓库约定

当前仓库根目录为：

```text
D:\softe
```

计划目录：

```text
D:\softe
  backend\     Spring Boot 后端
  frontend\    Vue 前端
  docs\        设计、计划、环境和演示文档
```

构建产物、数据库本地文件和依赖目录不会提交到 Git：

- `backend/target/`
- `frontend/node_modules/`
- `frontend/dist/`
- `*.mv.db`
- `*.sqlite`

## 后续启动方式

后端：

```powershell
cd D:\softe\backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

前端：

```powershell
cd D:\softe\frontend
npm install
npm run dev
```

## 给 Codex 的工作环境建议

1. 不要手动删除 `D:\softe\.git`、`.gitignore`、`.gitattributes`。
2. 如果手动改了文件，可以直接告诉 Codex 改了哪里；Codex 会保留你的改动。
3. 大型生成目录不要提交，例如 `node_modules`、`target`、`dist`。
4. 如果要发给同学或老师，优先发源码压缩包，不包含 `node_modules` 和构建产物。
5. 如果老师要求 MySQL，可以在后期只改后端 datasource 配置，不需要推翻整体设计。
