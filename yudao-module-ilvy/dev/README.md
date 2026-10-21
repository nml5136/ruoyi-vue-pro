# 完整宿主的隔离开发验收

本目录服务于 `ilvy-dev-host` Maven profile 和 `ilvy-host` Spring profile。它们不修改默认启动配置，也不用于发布。

## 实际运行结构

- 启动类：`cn.iocoder.yudao.server.YudaoServerApplication`，而非独立 `IlvyLocalApplication`。
- 宿主：`127.0.0.1:48082`，包含 system、infra、member、ilvy。
- MySQL：官方 `mysql:8.0.43` 容器 `ilvy-dev-mysql-20261021`，仅映射 `127.0.0.1:43306`；全新 `ilvy_dev` 库，业务表 InnoDB。
- Redis：官方 `redis:7.4.6` 容器 `ilvy-dev-redis-20261021`，仅映射 `127.0.0.1:46379`。
- 身份：现有 `/app-api/member/auth/login` 密码校验、会员记录、OAuth2 令牌及安全上下文；`mock-enable=false`。没有启用开发访客会话，也没有伪造微信身份。

本任务新建的数据库口令与两个会员测试身份仅存于 `/workspace/tools` 的 0600 私有运行文件，未提交、未打印、未上传 Library。不要把已有环境的凭据拷入此工程。

## 构建与安全配置

```sh
mvn -Pilvy-dev-host -pl yudao-server -am package -DskipTests
mvn -Pilvy-dev-host -pl yudao-server -am test -Dtest=IlvyHostConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false
```

`host.properties.example` 是不含口令的完整本地配置示例。实际运行使用独立配置路径：

```sh
java -jar yudao-server/target/yudao-server.jar --spring.config.location=file:/workspace/tools/ilvy-host.properties
```

不加载仓库 application-local.yaml 中的数据库和外部凭据。`IlvyHostDatabaseGuard` 在数据库初始化前拒绝非指定的本地 MySQL/Redis、非回环监听与 mock 登录。微信 SDK 用仅此 profile 生效的不可用适配器；调用会失败，不返回假授权。隔离库内默认社交和短信渠道已禁用，没有发送消息或联系外部服务。

## 数据初始化边界

在新容器的新库中先确认表数量为 0，然后导入仓库 `sql/mysql/ruoyi-vue-pro.sql`。该主 SQL 不含会员表；`host-member-fixture.sql` 根据当前 member DO 字段补充 11 个**本地测试用表结构**，不能作为生产会员模块迁移使用。

旅游业务表执行 `src/main/resources/db/migration/V1__ilvy.sql` 和 `V3__ilvy_owner_scope.sql`。本地宿主关闭自动 Flyway，避免把非空的系统库误作为全新 Flyway schema；独立 H2 启动器仍自动验收迁移。后续正式宿主迁移策略还需确定，不能原样在生产执行 fixture。

仅加载 `local-examples.json` 的 4 个虚构地点，并新建独立会员测试记录。没有导入外部景点或 Grok 资料。两个隔离测试账号用于真实 BCrypt 密码登录和用户隔离测试；它们不是线上用户或微信账号。

## 接口与浏览器验收

```sh
python yudao-module-ilvy/dev/verify-host.py --credentials /workspace/tools/ilvy-host-members.json
```

脚本限制回环地址和本地端口，读取外置测试凭据，不打印口令和令牌；通过真实宿主检验登录、匿名拒绝、MySQL 状态读写、用户隔离、版本冲突、伪造租户拒绝、反馈隔离、开发会话入口不可用、登出及令牌失效。可重复执行，不依赖清空个人表。

前端在 yegx 的 `ilvy/taro-app`：

```sh
ILVY_API=http://127.0.0.1:48082/app-api/ilvy ILVY_TENANT_ID=1 ILVY_AUTH_MODE=member npm run build:h5
ILVY_E2E_MODE=member ILVY_E2E_RESET=1 ILVY_E2E_CREDENTIALS=/workspace/tools/ilvy-host-members.json npm run test:e2e
```

RESET 只允许已声明的隔离测试账号 900002，通过真实 API 初始化该账号的收藏/计划/偏好，保留反馈历史。不可对真实用户运行。微信构建使用相同三个构建变量；localhost 产物尚未经过开发者工具/真机验证。

验收证据见 `../docs/完整宿主接口验收.json`、前端 `ilvy/docs/宿主浏览器验收.json` 和第二阶段报告。真实景点、微信/地图/天气/媒体/支付等外部能力、完整会员业务覆盖及生产迁移仍未完成。

### Private correction evidence (V4)

Implements product section 12.4 optional evidence attachments, not a crawler/importer.
Apply `V4__ilvy_private_evidence.sql` to the isolated development database before running
this version of the full host. Configure `ilvy.evidence-directory` to a dedicated
absolute directory named `ilvy-evidence*`; absent configuration reports disabled.
For local integration use `/workspace/tools/ilvy-evidence`, outside source/web roots.
Set multipart max-file-size to 3MB and max-request-size to 4MB; the service enforces
2 MiB PNG/JPEG with magic/header/dimension checks. At most three image IDs per feedback.
Files and metadata are scoped by tenant + member ID, never publicly served.
Only unsubmitted images can be deleted; submitted images remain in private feedback
history. This does not send feedback to a destination/operator, approve source facts,
or establish media publication rights. Browser/process termination before submission
may leave private unsubmitted files; lifecycle cleanup/production storage is still pending.
Never copy this directory, generated credentials or database dumps into reports.
