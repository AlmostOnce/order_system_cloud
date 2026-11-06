# Android 无线真机调试提示无网络：排查与配置

记录日期：2026-11-06。适用环境：Windows 10、WSL 中运行的 Nginx、HBuilderV 无线调试、Android 学生端。

## 现象与根因

手机能被 HBuilderV 识别并运行应用，但首页显示“网络连接失败，请检查网络后重试”。后端启动成功不等于手机能够访问后端。

本次排查发现两种连接问题：

1. 原 Android 请求地址为 `http://127.0.0.1:8088/api`。手机上的 `127.0.0.1` 指手机自身，需要 ADB 反向转发才能访问电脑。HBuilderV 的 `8000/8001` 调试转发不包含业务接口 `8088`；无线重连后不能依赖之前的转发仍然存在。临时执行 `adb reverse tcp:8088 tcp:8088` 曾恢复访问，但不是本次最终采用的方案。
2. 改为电脑局域网地址后仍失败：Windows 的 `8088` 由 `wslrelay.exe` 提供，当时仅监听 `127.0.0.1` 和 `::1`。电脑本机访问验证码接口返回 HTTP 200，但访问 `192.168.0.14:8088` 超时。WSL 内部已监听 `0.0.0.0:8088`，因此还需要补齐 Windows 局域网入口。

不需要修改或替换 `adb.exe`，也不应把 ADB 服务端口与后端 HTTP 端口混为一谈。

## 采用方案：局域网直连与持久端口转发

访问链路：

```text
手机 → 192.168.0.14:8088（Windows）
     → 127.0.0.1:8088（WSL localhost 转发）
     → WSL Nginx → 网关 → 业务服务
```

### 1. Android 统一请求地址

学生端仓库 `E:/order_system_cloud_user/common/config.uts` 的 `APP-ANDROID` 条件块已改为：

```typescript
apiBaseUrl = 'http://192.168.0.14:8088/api'
```

统一请求层、图片地址处理和通知 WebSocket 使用同一 `API_BASE_URL`，不应在各页面分别硬编码。配置变化需要重新运行到手机，旧安装产物不会自动读取电脑源码。

该地址仅供本地开发；发布前替换为正式 HTTPS 域名。手机与电脑必须处于可互访的局域网，访客网络或无线客户端隔离仍可能阻断访问。

### 2. 配置 Windows 局域网入口

以管理员身份打开 PowerShell，逐条执行。只复制代码块中的命令，不要复制终端提示符 `PS>`、`>` 或 `>>`。

先查看现有配置，已有相同映射时无需重复添加：

```powershell
netsh.exe interface portproxy show all
```

没有对应映射时添加：

```powershell
netsh.exe interface portproxy add v4tov4 listenaddress=192.168.0.14 listenport=8088 connectaddress=127.0.0.1 connectport=8088
```

检查是否已存在同名防火墙规则：

```powershell
netsh.exe advfirewall firewall show rule name="点餐系统局域网8088"
```

没有规则时添加，避免反复执行产生同名规则：

```powershell
netsh.exe advfirewall firewall add rule name="点餐系统局域网8088" dir=in action=allow protocol=TCP localip=192.168.0.14 localport=8088 remoteip=localsubnet
```

该规则允许本地子网访问电脑指定地址的 TCP 8088，不等于关闭防火墙。端口转发和防火墙规则持久保存，不依赖 ADB 会话；实际服务仍需运行，且端口代理依赖 Windows IP Helper 服务。

电脑当前地址 `192.168.0.14` 来自 DHCP。长期使用应在路由器为电脑保留该地址；地址或网络发生变化时，需同步调整前端地址、端口代理和防火墙规则。这里不承诺跨网络自动适配。

### 3. 验证

检查端口代理和防火墙规则与上述值一致，再检查监听地址：

```powershell
netstat.exe -ano | findstr.exe :8088
```

应包含 `192.168.0.14:8088` 的监听，仅有 `127.0.0.1:8088` 不足以供手机访问。

在电脑浏览器依次打开：

```text
http://127.0.0.1:8088/api/auth/captcha
http://192.168.0.14:8088/api/auth/captcha
```

两者都应返回验证码接口 JSON。再用手机浏览器打开第二个地址；电脑可访问而手机不可访问时，继续检查防火墙、手机所在网络及路由器隔离策略。

最后刷新或重新进入应用首页，核对档口、图片等实际资源，并断开重连无线调试后再验证一次。业务访问不应再依赖 `adb reverse`。

## 本次命令报错说明

- `New-NetFirewallRule` 提示无法识别：当前终端环境未提供该 cmdlet，不能仅凭该报错判定系统不支持防火墙。上述方案使用系统自带 `netsh.exe`，无需安装 PowerShell 模块。
- `>>` 提示无法识别：把提示符误当成了命令。按 `Ctrl+C` 退出未完成的续行，再逐条粘贴纯命令。
- 提示权限不足：使用管理员终端。普通权限不能写入这些系统配置。

## 撤销配置

不再需要该开发入口时，以管理员身份执行以下命令，仅删除本方案的映射和专用规则，不清空其他端口代理或防火墙配置：

```powershell
netsh.exe interface portproxy delete v4tov4 listenaddress=192.168.0.14 listenport=8088
netsh.exe advfirewall firewall delete rule name="点餐系统局域网8088" protocol=TCP localip=192.168.0.14 localport=8088
```

撤销后，前端仍指向局域网地址，需同步调整开发访问方案。

## 记录时的验证状态

- 已确认无线 ADB 设备在线；此前截屏确实出现首页网络失败。
- 已实测电脑回环地址验证码接口 HTTP 200、局域网地址超时，并确认 Windows 仅回环监听及 WSL 内部监听 8088。
- Android 统一配置已修改并通过静态检查。
- 写本文档前再次读取系统配置，已看到 `192.168.0.14:8088 → 127.0.0.1:8088` 的持久端口代理条目。
- 防火墙规则、代理实际监听、手机访问及断开重连后的最终结果尚未完成核验；存在配置条目不等于全链路已经恢复。
- 未运行 Maven、构建、服务启动或重启命令。
