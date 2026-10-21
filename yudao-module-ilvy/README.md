# ilvy 旅游业务模块：本地闭环阶段

独立模块位于 ruoyi-vue-pro 的 `ilvy-dev` 分支，遵循 controller / service / dal 分层，复用芋道 `CommonResult`、`LoginUser`、`SecurityFrameworkUtils`，使用 Spring JDBC/事务与输入校验。

此阶段运行的是**独立本地启动器**，尚未把模块依赖加入完整 `yudao-server`。它不代表正式会员认证、租户平台、MySQL/Redis 或生产发布已经验收。主仓库原有 datasource 配置未读取、未修改。

## 编译与测试

在仓库根目录使用 JDK 21、Maven 3.9.11：

```sh
mvn -pl yudao-module-ilvy -am package -Dtest=IlvyApiTest,IlvyFilterTest,IlvyTenantTest -Dsurefire.failIfNoSpecifiedTests=false
```

本云任务 Maven 在 `/workspace/tools/apache-maven-3.9.11/bin/mvn`，依赖缓存使用 `-Dmaven.repo.local=/workspace/.m2`。原有华为镜像在本环境 DNS 失败；构建使用 `-s /workspace/tools/maven-central.xml`（官方 Central + 平台既有代理，不包含凭据），没有改仓库镜像配置。

## 启动隔离本地服务

```sh
cd yudao-module-ilvy
mkdir -p .local
java -jar target/yudao-module-ilvy-2026.06-jdk8-SNAPSHOT-local.jar --spring.profiles.active=ilvy-local
```

监听 `127.0.0.1:48081`，数据库是本模块 `.local/ilvy.mv.db`（H2 文件库，MySQL 兼容模式）。测试使用独立 `jdbc:h2:mem:ilvy-api` 与 `ilvy-owner`，是真实数据库和 SQL 迁移，不是 Mock。H2 模式不能替代 MySQL 实际兼容性验收。未启动 MySQL/Redis，未连接远程库。

本地 profile 在数据库/Flyway 初始化前拒绝非隔离 H2 地址与非回环监听。Flyway V1 建表、V2 刻意不载入生产目录、V3 增加租户/用户类型作用域。仅本地 profile 的 seeder 加载 4 个虚构样本，绝不标成真实陕西景点目录。

## API 与身份

- `GET /app-api/ilvy/places`：地区/类型与附类型/主题/等级/搜索、已知费用/驾车时长/设施/年龄/停留筛选。
- `GET /app-api/ilvy/places/{id}`：同一地点唯一 ID 的完整详情。
- `GET /app-api/ilvy/capabilities`：当前外部能力均为 false。
- `GET/PUT /app-api/ilvy/state`：收藏分组、计划名称日期顺序备注、最多三个对比位、偏好；version 乐观锁，冲突返回 409。
- `POST/GET /app-api/ilvy/feedback`：提交与读取本用户待核实反馈，不直接修改景点事实。
- `POST /app-api/ilvy/local-session`：**仅本地 profile** 提供的开发访客会话，非微信身份。opaque bearer 24 小时过期，只存在本地服务内存，不输出到日志。服务重启后旧会话失效，旧开发用户数据仍在本地库但不冒充可跨设备恢复的正式账号。

个人表以租户 + 会员用户类型 + 登录用户 ID 隔离，不接收客户端指定 owner；管理员类型不能读写会员数据。正式宿主接入时，控制器 `/ilvy` 由芋道 APP API 前缀映射，不重复写 `/app-api`。必须继续验证完整会员 OAuth2、租户传递、默认鉴权和生产数据库，而非启用本地会话代替。

`application-ilvy-local.properties` 的框架自动配置排除只作用于隔离启动器；没有关闭正式宿主鉴权。普通模块 jar 与 `-local.jar` 分离，避免把可执行包当作宿主依赖。

## 资料交接与后续

见 [资料交接契约](docs/资料交接契约.md) 与 `schema/place-evidence-v1.schema.json`。无采集、自动导入、发布或 Grok 联系动作。schema 暂为交接约定，正式导入校验及运营审核流程尚未实现。

下一阶段：接入完整 yudao-server/会员与租户链路，隔离 MySQL/Redis 验收；完善目录分页/索引与分字段事实有效期；引入已核验且获许可的真实景点资料；联调外部能力与微信开发者工具/真机。
