# RCS 容器绑定 / 解绑 Android App

内部试用版 1.1。员工首页只保留扫码、绑定、解绑、结果显示和管理员设置入口。管理员设置保存在本机，修改服务器地址、容器类型或二维码规则不需要重新安装 APK。

## 首次使用

1. 安装 APK，打开后点击底部“管理员设置”。首次设置 6–12 位数字密码，随后进入配置。
2. 填写 RCS 地址（例如 `http://192.0.2.10:8182`；这是示例地址，不能直接连接生产系统）、容器类型以及现场需要的 clientCode/tokenCode。可选字段应以 RCS 的实际配置为准。
3. 选择纯文本二维码代表容器编号、仓位编号还是地图位置；JSON 和 key=value 二维码可自定义字段映射。保存后重新扫码。
4. 员工扫码，核对容器类型、容器号、仓位和地图位置，再点击绑定或解绑并确认。成功后清空当前扫码，避免重复操作。

管理员密码仅是本机设置保护，不能替代 RCS 服务端的账号、权限和网络访问控制。没有预置万能密码；丢失密码需要由设备管理员处理本机应用数据，清除数据会丢失配置。tokenCode 使用 Android Keystore 加密后保存在本机，不应写入源码或 GitHub。

## 二维码规则

推荐完整 JSON：

```json
{"ctnrCode":"BOX001","ctnrTyp":"C1","stgBinCode":"p05","positionCode":"p05"}
```

也支持 `ctnrCode=BOX001&ctnrTyp=C1&stgBinCode=p05&positionCode=p05`。纯文本二维码按管理员选择的模式解释。默认容器类型及固定仓位/位置可在设置中填写，但只有现场业务确实固定时才应设置。

**不能仅凭货架二维码自动推断容器与仓位的关系。** 如果现有标签只有货架号，而 RCS 需要容器类型、容器号和仓位，必须先明确标签内容及现场映射规则。此版本不调用未知查询接口，也不猜测容器号或仓位号。虚拟货架位置绑定/解绑的参数应按现场 RCS 配置核实。

## 接口

依据《厂内物流机器人控制系统 RCS-2000 V3.3 对外任务接口文档》3.1.8（第37–39页）：

`POST /rcms/services/rest/hikRpcService/bindCtnrAndBin`

请求参数包括 reqCode、reqTime、clientCode、tokenCode、ctnrCode、ctnrTyp、stgBinCode、positionCode、indBind。所有参数按字符串发送，1为绑定、0为解绑。程序仅在 HTTP 成功且响应 code 为 0、reqCode 与请求匹配时显示成功。超时或返回码异常不代表操作一定失败；重试使用原请求编号，并要求先核实 RCS 状态。软件不会自动重试。

生产使用前必须使用实际二维码和测试仓位联调，确认绑定、解绑及异常处理符合现场配置。本项目尚未通过真实 RCS 生产环境验收。

## 编译和安装

GitHub Actions → Build Android APK → Run workflow。成功后下载 Artifacts 中的 `RcsContainerBindApp-debug-apk`，解压得到 app-debug.apk。源码使用 Java 17、Android Gradle Plugin 8.6.1、Gradle 8.7、SDK 35，最低 Android 6.0（API 23）。

当前自动构建是调试签名 APK。不同 GitHub runner 生成的调试签名可能不同，**不能保证直接覆盖已安装版本**。安装提示签名不一致时，不能强行覆盖；卸载会清除本机配置。长期部署应使用由公司安全保管的固定发布签名，将签名文件及密码放在受保护的 CI 密钥管理中，不要提交到公开仓库。保持相同 applicationId、签名和递增 versionCode 才能可靠覆盖升级。

日常修改 App 内配置不需要重新编译。修改程序代码仍然需要重新构建和安装更新。固定签名、版本发布、设备管理和自动更新属于后续正式部署工作，本试用版没有自动更新服务。

## 安全注意

仓库应设置为 Private。不要上传真实服务器凭据、Token、签名私钥、员工个人信息或未经授权公开的公司接口文档。HTTP 明文通信仅适用于受控内网；跨公网访问应使用 HTTPS 或受管 VPN。管理员密码不是服务端鉴权，员工操作仍需由 RCS 侧实施权限及审计控制。
