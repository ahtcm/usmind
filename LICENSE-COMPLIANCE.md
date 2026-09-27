# USMIND 依赖许可证合规分析报告

> 生成日期：2026-09-28 ｜ 分析对象：`code/backend`（Maven）、`code/web-user`（npm）、`code/web-admin`（npm）、便携版内置基础设施
> 方法说明：后端以实际打包进 fat jar 的 120 个依赖 jar 为准（`BOOT-INF/lib` 枚举）；前端以 `package-lock.json`（lockfileVersion 3，官方 license 字段）为准；许可证来源为 Maven Central / npm registry 官方 POM/Package 元数据及组件官方仓库。

---

## 一、总览

| 范围 | 组件数 | 宽松许可（MIT/ISC/BSD/Apache/0BSD/BlueOak/Zlib/BC） | 需注意（弱 copyleft / 双许可 / FOSS Exception） | 风险等级 |
| --- | --- | --- | --- | --- |
| 后端 Java 依赖（fat jar） | 120 | ~112 | 8（mysql-connector-j、jakarta.annotation-api、javax.activation-api、jaxb-api、logback×2、jsqlparser、jacoco） | 🟢 合规（含 8 项注意点） |
| 用户端前端（web-user） | 221 | 218 | 3（jszip、pako、enhanced-resolve 未标注） | 🟢 合规（含 3 项注意点） |
| 管理后台前端（web-admin） | 136 | 136 | 0 | 🟢 完全合规 |
| 基础设施（便携版内置） | 4 | 2（nginx、OpenJDK runtime） | 2（MySQL Server GPL-2.0、Redis RSALv2/SSPLv1） | 🟡 特殊许可需知悉 |

**结论：本项目依赖整体合规，无 AGPL 等强传染性开源许可的直接或主要依赖；不存在🔴致命冲突。** 有 4 类许可需按下列说明履行义务（保留声明 / 内部使用边界 / 双许可选型）。

---

## 二、许可证分布

### 后端（120 个 jar）

| 许可证 | 数量 | 典型组件 |
| --- | --- | --- |
| Apache-2.0 | ~102 | Spring Boot/Security/Data、jackson、tomcat-embed、HikariCP、lettuce、guava、netty、okhttp、okio、mybatis(-plus)、jjwt、aliyun-sdk-oss、minio、tencentcloud-sdk、snakeyaml、hibernate-validator、micrometer、reactor 等 |
| MIT | 5 | java-jwt、qiniu-java-sdk、cos_api、checker-qual、lombok（未打包，编译期） |
| EPL-1.0 OR LGPL-2.1（双许可） | 2 | logback-classic、logback-core |
| GPL-2.0 + Universal FOSS Exception | 1 | **mysql-connector-j** |
| EPL-2.0 OR GPL-2.0 w/CPE | 1 | jakarta.annotation-api |
| CDDL-1.1 OR GPL-2.0 w/CPE | 2 | javax.activation-api、jaxb-api |
| LGPL-2.1 OR Apache-2.0（双许可） | 1 | jsqlparser（可选用 Apache-2.0） |
| EPL-2.0 | 1 | org.jacoco.agent（runtime） |
| Bouncy Castle Licence（宽松） | 2 | bcprov-jdk15on、bcprov-jdk18on |
| BSD-3-Clause | 2 | xstream、xmlpull（近似） |
| Apache 类似（去 ack 条款） | 1 | jdom2 |
| 其它（无主依赖风险） | 0 | — |

### 用户端前端（web-user，221 包）

| 许可证 | 数量 | 代表性包 |
| --- | --- | --- |
| MIT | 199 | vue、vue-router、pinia、axios、katex、codemirror、simple-mind-map、enhanced-resolve 等 |
| ISC | 9 | graceful-fs、glob、minimatch、picocolors 等 |
| BSD-3-Clause | 5 | quill、parchment、diff、ieee754、source-map-js |
| Apache-2.0 | 2 | mathjax、fast-diff |
| BSD-2-Clause | 1 | entities |
| (MIT OR GPL-3.0-or-later) | 1 | **jszip**（双许可，选用 MIT） |
| (MIT AND Zlib) | 1 | pako（双许可并存，均宽松） |
| 0BSD | 1 | tslib |
| BlueOak-1.0.0 | 1 | sax |
| 未标注（官方为 MIT） | 1 | enhanced-resolve@2.3.0 |

### 管理后台前端（web-admin，136 包）

全部为宽松许可：MIT 132、BSD-3-Clause 2（normalize-wheel-es、source-map-js）、BSD-2-Clause 1（entities）、ISC 1（picocolors）。**零注意项。**

---

## 三、需要重点关注的组件（8+3+2 项）

### 🟡 后端（8 项）

| 组件 | 版本 | 许可证 | 兼容情况 | 冲突/义务描述 |
| --- | --- | --- | --- | --- |
| mysql-connector-j | 8.3.0 | GPL-2.0 + Universal FOSS Exception | ✅ 内部使用合规 | 应用作为独立进程通过 JDBC 通信，不构成衍生作品；**商用闭源再分发**需 Oracle 商业许可（付费）。免费开源项目分发合规 |
| jakarta.annotation-api | 2.1.1 | EPL-2.0 OR GPL-2.0 w/CPE | ✅ 合规 | 弱 copyleft；以 EPL-2.0 条款使用需保留版权声明，修改需开源修改部分（本项目未修改） |
| javax.activation-api | 1.2.0 | CDDL-1.1 OR GPL-2.0 w/CPE | ✅ 合规 | 同上；CDDL 为文件级弱 copyleft，未修改即无额外义务 |
| jaxb-api | 2.3.1 | CDDL-1.1 OR GPL-2.0 w/CPE | ✅ 合规 | 同上 |
| logback-classic / core | 1.4.14 | EPL-1.0 OR LGPL-2.1 | ✅ 合规 | 动态链接使用满足 LGPL 可替换性义务（Java 平台普遍做法）；或按 EPL-1.0 条款（未修改则仅需保留声明） |
| jsqlparser | 4.9 | LGPL-2.1 OR Apache-2.0 | ✅ 合规 | 双许可，明确选用 **Apache-2.0** 条款即可规避 LGPL |
| org.jacoco.agent（runtime） | 0.8.5 | EPL-2.0 | ✅ 合规（建议移除） | 测试覆盖率探针被打进生产 fat jar，属多余依赖；建议从打包排除 |
| jdom2 | 2.0.6.1 | Apache 类似（去 ack 条款） | ✅ 合规 | 宽松，仅保留版权声明 |

### 🟡 用户端前端（3 项）

| 组件 | 版本 | 许可证 | 兼容情况 | 冲突/义务描述 |
| --- | --- | --- | --- | --- |
| jszip | 3.10.2 | MIT OR GPL-3.0-or-later | ✅ 合规 | 双许可；**必须在分发物中声明选用 MIT 条款**（本项目导出 `.usmind` 包使用 jszip，建议在 THIRD-PARTY 声明中注明） |
| pako | 1.0.11 | MIT AND Zlib | ✅ 合规 | 双许可并存，两者均为宽松许可，无冲突 |
| enhanced-resolve | 2.3.0 | 未标注（官方 MIT） | ✅ 合规 | npm registry 老版本缺 license 字段；官方为 webpack 项目 MIT。建议 lock 升级或声明 |

### 🟠 基础设施（2 项，非代码依赖）

| 组件 | 版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| MySQL Community Server（便携版内置） | 8.x | GPL-2.0 | 独立服务器进程，与应用通过网络协议通信，**不传染应用代码**；但若将"含 MySQL 的便携包"再分发，需一并提供 MySQL 对应开源义务（源码可达性）。内部使用无碍 |
| Redis（便携版内置） | 7.x | RSALv2 + SSPLv1（**非 OSI 开源许可**） | 允许内部使用、修改、再分发；**限制：禁止对外提供"托管 Redis 服务"**（如售卖 DBaaS）。本项目随应用自托管使用合规，不提供 Redis 托管服务即可 |

---

## 四、完全合规（无需动作）的主要组件

- **Spring Boot 全家**（web/security/validation/data-redis/test、spring-framework 6.1.6）：Apache-2.0
- **MyBatis-Plus 全家**（3.5.7 + mybatis-spring）：Apache-2.0
- **JJWT**（0.12.5）：Apache-2.0
- **OSS SDK 四件套**：aliyun-sdk-oss（Apache-2.0）、minio（Apache-2.0）、cos_api（MIT）、qiniu-java-sdk（MIT）
- **Web 容器**：tomcat-embed-core 10.1.20（Apache-2.0）
- **序列化/网络**：jackson 2.15.4、guava、gson、netty、okhttp/okio（均 Apache-2.0）
- **前端核心**：vue 3、vue-router、pinia、axios、element-plus、@element-plus/icons-vue（均 MIT）
- **公式/富文本**：katex（MIT）、mathjax（Apache-2.0）、codemirror（MIT）、quill/parchment（BSD-3）
- **构建链**：vite、@vitejs/plugin-vue（MIT/ISC，仅构建期，不进产物）
- **基础设施**：nginx（BSD-2-Clause）、OpenJDK 17 runtime（GPL-2.0 + Classpath Exception，可自由商用再分发）

---

## 五、合规建议（按优先级）

1. **生成 THIRD-PARTY-LICENSES 清单**：在后端 `BOOT-INF/lib` 与前端 `node_modules` 基础上生成 `THIRD-PARTY-LICENSES.txt`（各组件名称、版本、许可证、版权方），随发布物一起分发，满足 Apache/BSD/MIT/EPL 的"保留声明"义务。
2. **明确 jszip 双许可选型**：在 `package.json` 或 README 中注明 jszip 按 **MIT** 条款使用。
3. **移除 jacoco agent runtime**：从生产依赖排除（若它非必要，检查 `org.jacoco.agent` 来源——疑似传递误入，从 pom 或依赖仲裁中排除）。
4. **Redis 许可边界**：文档化"内置 Redis 仅供自托管，不得对外提供托管服务"（RSALv2 限制）。
5. **MySQL 商用再分发评审**：若计划向第三方分发含 MySQL 的便携版/安装包，评估 Oracle 商业许可或替换为 MariaDB（LGPL）/ 引导用户自装。
6. **前端 lock 补全**：`enhanced-resolve@2.3.0` 无 license 字段，建议 `npm update` 到标注 MIT 的版本或在声明中补注。
7. **logback**：未修改使用，保留 EPL/LGPL 声明即可；无需动作。

---

## 六、数据口径与局限

- 后端清单为 `code/backend/target/usmind-backend.jar`（fat jar，scope compile+runtime）实际打包的 **120 个 jar**；test 依赖（spring-boot-starter-test、lombok 编译期）未计入运行面，但在 README 提示：spring-boot-starter-test 本身为 Apache-2.0。
- 前端清单来自 lock 文件 **官方 license 字段**（npm registry 写入），web-user 221 包 / web-admin 136 包（含传递依赖、可选依赖、构建链）。
- `cos_api` 的 POM 许可证名为 "cos-java-sdk"（腾讯云官方仓库 LICENSE 为 MIT），已按官方仓库判定。
- Redis/MySQL 的许可判定基于便携版内置版本（Redis 7.x、MySQL 8.x Community）。
- 本报告为合规**提示性**分析，不构成法律意见；商业发行前建议由法务复核。
