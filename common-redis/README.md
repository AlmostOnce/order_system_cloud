# common-redis Redis 基础模块

> Redis 能力封装：同步/响应式模板、原子计数 Lua、Redisson 分布式锁与布隆过滤器。Redisson 依赖声明为 optional，避免传递给网关（其 Lettuce 响应式 Redis 与 Redisson 自动装配冲突），需要 Redisson 的服务自行显式引入。

## 关键类

| 类 | 职责 |
|---|---|
| `RedisOperationsService` | 同步 StringRedisTemplate 封装（字符串/Hash/过期/删除），内置「INCR+PEXPIRE 原子计数」Lua；禁止在 WebFlux 事件循环调用 |
| `ReactiveRedisOperationsService` | 响应式封装（如 `hasKey` 返回 Mono），面向网关等 WebFlux 链路，不调 block() |
| `DistributedLockService` | Redisson RLock 封装，默认等 3 秒 / 租约 10 秒；取锁失败不抛异常，由调用方降级 |
| `ScheduledTaskLock` | 定时任务互斥租约（`SET NX EX` + 比较持有者后删除），不依赖 Redisson，供多实例 `@Scheduled` 只跑一次；Redis 故障时跳过本轮 |
| `RedisBloomFilterService` | Redisson 共享布隆过滤器，拦截「一定不存在」的 ID 防缓存穿透；不支持删除 |
| `RedissonInfrastructureConfig` | 仅在类路径存在 Redisson 时条件装配锁与布隆 Bean |
| `AuthzCacheKeys` | 授权缓存键常量 `authz:user-scope:{userId}`（TTL 300 秒，撤销由用户服务主动删除） |

## 使用约定

- 全部 16 个模块（含网关）依赖本模块；JWT 注销黑名单、限流、验证码、票据等键由各服务自定义前缀。
- Redis 作为黑名单/限流载体时各服务统一 fail-closed：无法确认即拒绝。
