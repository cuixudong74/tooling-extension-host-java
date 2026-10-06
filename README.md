# tooling-extension-host-java

插件化工具宿主的扩展贡献解析与生效状态检查能力。第三方扩展以声明式清单（JSON）提交希望注入宿主的贡献（命令、设置项、快捷键、语言能力、面板入口），宿主负责清单校验、版本/能力匹配、多扩展贡献合并与冲突裁决、生效状态解释，以及扩展集合的热更新。整条链路只在本进程内完成，不依赖任何外部服务。

## 模块结构

```
extension/
├── model/       清单、贡献、宿主信息等不可变模型
├── io/          清单 JSON 解析（仅结构解析，不做合法性判断）
├── validation/  清单合法性校验（必填字段、贡献类型、能力标识引用）
├── version/     语义化版本范围解析与匹配
├── compat/      宿主与扩展的版本/能力兼容性检查（REJECT / DEGRADE 策略）
├── merge/       多扩展贡献合并与冲突裁决（确定性）
├── registry/    不可变快照、解释查询、核心服务（热更新、并发控制）
├── config/      Spring 配置项与装配
└── api/         REST 接口
```

## 清单格式

```json
{
  "id": "ext.example",
  "version": "1.0.0",
  "apiVersionRange": "[1.0,2.0)",
  "requiredCapabilities": ["git"],
  "contributions": [
    {"type": "SETTING", "id": "editor.tabSize", "priority": 5, "payload": {"default": "4"}}
  ]
}
```

- `id` / `version` / `apiVersionRange` / `contributions` 为必填；`requiredCapabilities` 可空。
- 贡献 `type` 合法值：`COMMAND`、`SETTING`、`KEYBINDING`、`LANGUAGE`、`PANEL`。
- 贡献全局唯一键为 `TYPE:id`（不同类型下相同 id 不冲突）。

## 校验规则（任一违反即整体拒绝，不会只加载一半）

1. 必填字段缺失或格式非法（扩展 id、版本号、接口版本范围）；
2. 声明了非法贡献类型、贡献 id 为空或含非法字符、同一扩展内重复声明同一贡献；
3. `requiredCapabilities` 引用了宿主不存在的能力标识；
4. 同一集合中扩展 id 重复。

所有错误以 `扩展id.字段: 原因` 的形式一次性返回，可定位到具体扩展与字段，例如
`ext.bad.contributions[0].type: 贡献类型为必填字段，合法值为 ...`。

## 版本与能力匹配规则

- 扩展的 `apiVersionRange` 必须覆盖宿主的 `apiVersion`。支持的表达式：
  - 精确版本 `1.2.3`；区间 `[1.0,2.0)`、`(1.0,2.0]`；
  - 脱字符 `^1.2`（等价 `[1.2.0,2.0.0)`）；波浪号 `~1.2`（等价 `[1.2.0,1.3.0)`）；
  - 比较组合 `>=1.0 <2.0`。
- 扩展声明的每个 `requiredCapabilities` 必须存在于宿主能力集合中。
- 不满足时按 `extension-host.compatibility-policy` 处理：
  - `REJECT`（默认）：整个更新被拒绝，当前服务状态不变，错误信息含扩展 id 与具体原因；
  - `DEGRADE`：不兼容的扩展被降级剔除（记录到快照的 `degraded` 列表），其余扩展正常生效。

## 贡献合并与冲突裁决

同一贡献键（`TYPE:id`）被多个扩展声明时，按以下顺序裁决，靠前者生效：

1. 贡献声明的 `priority` 高者优先；
2. `priority` 相同，扩展 id 字典序小者优先；
3. 仍相同，扩展内声明序号小者优先。

合并前输入先按扩展 id 排序，因此**合并结果不随扩展读取顺序或并发度改变**。
每个生效贡献都携带完整覆盖链（`winner` + `overridden`），可追溯到来源扩展与版本。

## 生效状态解释

`explain(TYPE:id)` 返回：

- `winner`：生效来源（扩展 id、版本、贡献内容、声明序号）；
- `overriddenBy`：完整覆盖链（按裁决顺序），即"被谁覆盖"的反向——谁被当前赢家压过；
- `affectedBy`：间接影响该键的全部扩展 id；
- `reason`：人类可读的裁决原因。

解释直接取自当前生效快照的裁决结果，保证与实际生效状态一致，覆盖链完整无截断。

## 热更新与并发语义

- 所有更新（`replaceAll` / `enable` / `disable` / `upgrade`）在内部锁上串行执行：
  先完整构建并校验新快照，**校验失败则丢弃新快照、继续以上一个可用集合服务（回退）**；
  成功才通过 `AtomicReference` 一次性原子切换，不会出现新旧混合。
- 查询走无锁读：任一时刻读到的都是某个已提交的完整不可变快照，
  慢查询不阻塞更新推进；快照代际（`generation`）单调递增，更新之间不会互相覆盖造成状态漂移。

## 配置项（application.properties）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `extension-host.api-version` | `1.0.0` | 宿主接口版本，用于与扩展 `apiVersionRange` 匹配 |
| `extension-host.capabilities` | 空 | 宿主可提供的能力标识列表（逗号分隔） |
| `extension-host.compatibility-policy` | `REJECT` | 兼容性策略：`REJECT` / `DEGRADE` |

## REST 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/extension-host/extensions` | 整体替换扩展集合（请求体为清单 JSON 数组），校验失败返回 422 且状态回退不变 |
| POST | `/api/extension-host/extensions/enable` | 启用 / 升级单个扩展（请求体为单个清单 JSON） |
| POST | `/api/extension-host/extensions/{id}/disable` | 停用单个扩展 |
| GET | `/api/extension-host/contributions` | 当前全部生效贡献及裁决结果 |
| GET | `/api/extension-host/explain/{type}/{id}` | 解释某贡献为何是当前值（覆盖链追溯） |
| GET | `/api/extension-host/status` | 当前快照代际、活跃扩展、降级记录 |

## 测试

```bash
mvn test
```

覆盖：清单非法（缺字段/非法类型/未知能力/重复 id）、版本与能力不满足（REJECT/DEGRADE）、
贡献冲突与输入顺序无关性、覆盖链追溯、热更新回退、启用/停用/升级原子性、
并发读写一致性与并发更新无漂移、REST 端到端。
