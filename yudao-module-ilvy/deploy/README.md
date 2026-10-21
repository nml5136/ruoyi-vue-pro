# ilvy 后端最小隔离测试部署交接

仅用于先跑通“Git 后端交付 → Grok 检查远程电脑 → 启动隔离测试服务”的路径，不是生产部署方案。前端不在本次推送范围。目标仓库 `https://github.com/nml5136/ruoyi-vue-pro`，目标分支 `ilvy`。部署必须使用父会话推送回执中的完整40位 SHA，不能只按浮动分支名发布；部署机用 `git rev-parse HEAD` 核对。后端业务基线为 `a01f3d8591a0064ba57001383a4d63cabfe3ee83`，本批仅增加可移植测试部署文件。

本目录不依赖云任务临时路径，不包含数据库口令、测试账号密码、令牌、数据库备份或依赖目录。不要复制云环境的 `/workspace/tools` 文件、数据库卷、会员记录或 Maven 凭据。所有秘密由远程电脑已有的授权配置机制提供；如果没有已批准的测试库凭据或会员，停下报告，不自行生成口令、创建访问身份或开放匿名写入。

## Grok 首先检查，不能假定目标已具备

- 独立、干净的发布目录；已有文件或改动时保留并报告，不覆盖、reset 或删除。
- 已授权的 Git 读取方式及精确发布 SHA；不能借用其他身份或向聊天发送令牌。
- 已验收工具链：Java 21、Maven 3.9.11；官方 MySQL 8.0.43、Redis 7.4.6；Docker Engine 与 Compose v2（本任务检查为2.40.3）。不声称其他版本已验收。
- 本机 `43306`、`46379`、`48082` 均空闲且不属于生产；磁盘、内存、私有持久目录及备份位置由 Grok 实查后报告，本任务没有远程容量基准。
- 仅创建独立 `ilvy-test` Compose 项目及其命名卷，不挂载生产卷、不连接生产库。密码必须已获批准，不在本任务中生成或默认启用。

## 构建和回归

在仓库根目录执行：

```sh
mvn -B -ntp -Pilvy-dev-host -pl yudao-server -am verify \
  -Dtest=IlvyApiTest,IlvyFilterTest,IlvyTenantTest,IlvyEvidenceApiTest,IlvyCatalogPageTest,IlvyHostConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

产物为 `yudao-server/target/yudao-server.jar`，启动类是完整的 `cn.iocoder.yudao.server.YudaoServerApplication`，包括 system/infra/member/ilvy，不使用独立 H2 启动器。必须完成测试再交付；不要把 `-DskipTests` 当验收。默认 Maven 镜像不可达时，可使用官方 Central 或组织已授权镜像；不复制云端私有代理和凭据。发布目录保留 JAR 的 SHA256、源码 SHA 和测试结果。

## 环境变量（仅名称，无真实值）

| 名称 | 用途 |
|---|---|
| `ILVY_DB_PASSWORD` | 已批准的隔离 MySQL `ilvy_dev` 用户口令，Compose与Java应一致 |
| `ILVY_MYSQL_ROOT_PASSWORD` | 仅隔离测试 MySQL 的已批准管理口令，用于空库初始化，不传给业务进程 |
| `ILVY_COMPOSE_ENV` | 本机私有 Compose 环境文件路径，权限0600，不进Git |
| `ILVY_TEST_DATA_DIR` | 绝对持久目录，末级必须为 `ilvy-test-data`；图片在其 `ilvy-evidence` 子目录 |
| `ILVY_ALLOW_EMPTY_TEST_INIT=YES` | 仅初次空库初始化的显式开关；日常启动不要设置 |
| `ILVY_SMOKE_MOBILE`、`ILVY_SMOKE_PASSWORD` | 可选，已有且已批准的测试会员，用于只读鉴权烟测；不创建账号 |
| `ILVY_DEPLOY_DIR` | 由启动脚本自动解析，不需手工写入秘密 |

`environment.example` 只给变量名和占位符，不能直接当有效秘密使用。Compose的私有环境文件与Java进程的环境变量由目标机已有安全机制加载，脚本不会擅自读取其他环境的身份文件。

## 服务与初始化：仅全新隔离测试库

目标机确认隔离、凭据授权、端口和卷名称后，在仓库根目录执行：

```sh
docker compose --env-file "$ILVY_COMPOSE_ENV" -p ilvy-test \
  -f yudao-module-ilvy/deploy/compose.yaml up -d

docker compose --env-file "$ILVY_COMPOSE_ENV" -p ilvy-test \
  -f yudao-module-ilvy/deploy/compose.yaml ps

ILVY_ALLOW_EMPTY_TEST_INIT=YES bash yudao-module-ilvy/deploy/init-empty-test-db.sh
```

这是后续远程操作交接命令，本云任务没有执行它们。服务只映射 `127.0.0.1:43306`、`127.0.0.1:46379`；数据库固定 `ilvy_dev`。初始化先核对目标库表数必须为0，否则退出，不导入、不删除已有表，不自动重试。

顺序：仓库基础 SQL → 禁用基础种子管理账号和短信/社交对外渠道 → 本地测试会员表结构 → V1/V2/V3/V4/V5/V6 旅游迁移，记录文件校验值。会员表结构来自 `dev/host-member-fixture.sql`，只适用于这个全新隔离测试库，不是完整生产会员迁移。默认不创建会员、测试登录账号或景点记录，不自动采集或导入真实资料。基础 SQL 自带示例配置，因此必须在应用首次启动前执行禁用步骤。

MySQL DDL可能已部分生效；任何步骤失败都保留现场并报告，不能在非空库强行重跑。已有测试库升级须先备份、核对迁移记录与实际表结构，只应用未执行的迁移，不能运行本初始化脚本；生产迁移需另行评审。启动配置关闭自动Flyway，不能把非空系统库当新库自动baseline。

## 启动与默认安全边界

Java进程已通过目标机安全机制取得必要环境变量后：

```sh
bash yudao-module-ilvy/deploy/run-test.sh --check
bash yudao-module-ilvy/deploy/run-test.sh
```

启动脚本自动定位仓库/JAR/配置，没有云路径依赖；前台运行，交由目标机已有服务管理机制保管PID和标准输出。不要把口令放命令参数、日志、交接回执或Git。

`ilvy-host` Spring profile 强制：MySQL `127.0.0.1:43306/ilvy_dev`、Redis `127.0.0.1:46379`、HTTP `127.0.0.1:48082`，`mock-enable=false`。故意不能把数据库指向远程/生产，也不能改成 `0.0.0.0`。本任务不打开防火墙、不部署公网、不安装穿透工具；任何后续访问方式由用户和Grok另行确认。

微信SDK明确未配置，调用失败而不伪造身份；短信/社交渠道禁用。没有匿名写会话。目录索引在启动时从本库内容构建；空目录是正常状态，不能宣称已拥有真实旅游数据。私有图片按会员/租户隔离，物理清理关闭，不配置自动清除计划；持久目录与数据库应一起备份。

## 健康与真实接口烟测

```sh
python3 yudao-module-ilvy/deploy/smoke.py
```

检查：能力接口、实际MySQL分页查询、匿名私有数据拒绝、开发会话入口不可用；有已批准会员变量时才额外验证真实密码登录、私有状态只读、登出和令牌撤销（涉及Redis）。不修改收藏/计划/反馈，不自动创建账号。没有会员时明确输出鉴权链路未验收，不能算完整远程验收。不要将云端 `dev/verify-host.py` 的 fixture/reset 流程用于真实用户。

数据库/缓存可另外使用 Compose 内的既有授权管理方式执行 `SELECT 1`、`redis-cli ping`；不得把健康检查连接到生产。Grok应返回：源码完整SHA、JAR SHA256、工具/服务版本、监听地址、数据库是否全新、迁移记录、烟测通过与未验收项、日志中的精简错误，不含秘密。

## 回滚

停止本测试服务的已登记PID/服务单元，恢复上一个已验收JAR与同版本外置配置，并重新烟测。不要仅凭分支名重建“旧版本”，使用已记录SHA/校验值。不删除数据库卷或图片，不运行 `down -v`。V1..V6 没有通用逆向脚本；涉及不兼容结构时，先保留失败现场，再在独立恢复目标中恢复部署前数据库与图片备份，经过确认后切换。不要 reset/改写远端历史或自动清理用户数据。

## 已有证据和未验收范围

业务基线 `a01f3d8591a0064ba57001383a4d63cabfe3ee83` 已在完整宿主、隔离MySQL/Redis和真实member身份下验收：34项后端测试、43项MySQL分页检查、15项宿主检查。H5仅用于消费者流程验收，不能替代微信验收。本批重新执行34项 Maven verify；启动脚本的 `--check`、Shell/Python语法、Compose渲染及空库/非空库保护采用不触库的模拟检查。尚未在Grok远程电脑运行这些部署命令。

未完成：目标机实际配置与资源核查、远程部署/启动/烟测、获批准会员的远程鉴权烟测；真实旅游/建筑资料和授权媒体；微信主体/AppID、地图/天气等配置、微信开发者工具/真机。当前分支不支持生产发布；原文运营后台不在首轮范围。前端本轮不推送，也不将微信未验收混称为本次后端测试部署阻塞。
