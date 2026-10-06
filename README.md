# tooling-extension-host-java

插件化工具宿主的扩展贡献解析与生效状态检查能力。第三方扩展以声明式清单（JSON）提交贡献，
宿主在单进程内完成清单校验、版本/能力匹配、贡献合并裁决、生效状态解释与热更新，
配合本地文件持久化，结论可在本地复现与验证，不依赖任何外部服务与真实凭据。

## 扩展清单格式

```json
{
  "id": "ext.demo",                  // 必填，扩展唯一标识
  "version": "1.0.0",                // 必填，语义化版本
  "apiVersionRange": "[1.0,2.0)",    // 必填，依赖的宿主接口版本范围
  "requiredCapabilities": ["settings"], // 可选，所需能力标识，必须存在于宿主提供的能力中
  "priority": 10,                    // 可选，冲突裁决优先级，缺省取 host.default-priority
  "contributions": [                 // 可选，贡献声明列表
    { "type": "SETTING", "id": "editor.fontSize", "payload": { "value": 14 } }
  ]
}
```

合法贡献类型（`ContributionType`）：`COMMAND`、`SETTING`、`KEYBINDING`、`LANGUAGE`、`PANEL`。

## 清单校验规则

任一规则不满足，**整个提交集合被拒绝**（不允许只加载一半），错误定位到具体扩展与字段：

- `id` / `version` / `apiVersionRange` 为必填字段；
- `version` 必须是合法语义化版本，`apiVersionRange` 必须是合法范围表达式；
- `requiredCapabilities` 中引用的能力标识必须存在于宿主实际提供的能力集合中；
- 贡献的 `type` 必须是合法类型，`id` 必填且同一扩展同类型内不得重复；
- 同一提交集合中扩展 `id` 不得重复。

## 版本与能力匹配规则

- 扩展的 `apiVersionRange` 必须包含宿主当前 `host.api-version`。
  范围语法：`1.2.3`（精确）、`[1.0,2.0)`、`(1.0,2.0]`、`[1.0,]`、`(,2.0)`；
  版本号允许 1~3 段数字，可带预发布后缀。
- `requiredCapabilities` 必须是宿主 `host.capabilities` 的子集。
- 不满足时按 `host.mismatch-policy` 处理：
  - `REJECT`：整个更新被拒绝，宿主继续用上一个可用集合服务；
  - `DEGRADE`：不兼容的扩展被剔除出集合（结果中以 warning 说明原因），其余扩展正常生效。
- 所有拒绝/降级结论都携带可解释的原因文本（哪个扩展、缺哪个能力、期望与实际的版本）。

## 贡献合并与冲突裁决

同一 `类型:id` 被多个扩展声明时，按以下**稳定顺序**裁决出唯一生效者：

1. `priority` 高者胜（未声明按 `host.default-priority`）；
2. 优先级相同，扩展 `id` 字典序小者胜；
3. 仍相同，贡献在清单中的声明下标小者胜。

合并前扩展先按 id 排序，因此**合并结果与扩展读取顺序、并发度无关**。
每条生效贡献记录完整来源链（`overriddenBy`，按裁决顺序从高到低），可追溯被谁覆盖、
间接被哪些扩展影响。

## 检查接口（为什么是这个值）

`GET /api/explain/{type}/{id}` 返回：

- `effective`：最终生效的贡献内容（与实际生效状态一致）；
- `winnerExtensionId`：生效来源扩展；
- `overrideChain`：完整覆盖链（含胜出者与全部被覆盖者，不只解释链的一部分）；
- `indirectInfluencers`：被覆盖但参与了裁决的扩展；
- `reason`：人类可读的结论说明。

## 热更新与并发语义

- `PUT /api/extensions`（整体替换）、`POST /api/extensions`（启用/升级）、
  `DELETE /api/extensions/{id}`（停用）均为热更新：
  先在旧快照之外完整构建并校验候选集合，**全部通过后一次性原子替换**；
  校验失败则当前快照不变，旧集合继续对外服务（回退）。
- 每次成功提交代次（`generation`）单调递增。
- 查询走无锁快照读：任一时刻读到的都是某个已提交的完整集合，不会出现新旧混合；
  慢查询不阻塞更新；更新在内部串行化，更新之间不会互相覆盖造成状态漂移。

## 配置项（application.properties）

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `host.api-version` | `1.0.0` | 宿主当前接口版本 |
| `host.capabilities` | 空 | 宿主提供的能力标识，逗号分隔 |
| `host.mismatch-policy` | `REJECT` | 版本/能力不满足策略：`REJECT` 或 `DEGRADE` |
| `host.default-priority` | `0` | 清单未声明 `priority` 时的默认合并优先级 |
| `host.store-file` | 空 | 扩展集合本地持久化文件路径，留空不持久化；启动时自动重载 |

## REST 接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `PUT` | `/api/extensions` | 整体替换扩展集合（失败回退，返回 422 + 错误明细） |
| `POST` | `/api/extensions` | 启用/升级单个扩展（同 id 替换） |
| `DELETE` | `/api/extensions/{id}` | 停用扩展 |
| `GET` | `/api/extensions` | 当前生效集合与代次 |
| `GET` | `/api/contributions` | 当前生效贡献注册表（含来源链） |
| `GET` | `/api/explain/{type}/{id}` | 解释某条贡献为什么是该值 |

## 构建与测试

```bash
mvn test
```

测试覆盖：清单非法（缺字段/非法类型/未知能力）、版本与能力不满足（REJECT/DEGRADE）、
贡献冲突裁决与顺序无关性、覆盖链追溯、热更新回退、并发读写一致性、持久化往返。
