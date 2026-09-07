# RCS 容器绑定解绑 Android App

这是 Android Studio 项目，功能：一个页面、扫码、绑定、解绑，调用 RCS-2000 `bindCtnrAndBin` 接口。

二维码推荐内容：
```json
{"ctnrCode":"BOX001","ctnrTyp":"C1","stgBinCode":"p05","positionCode":"p05"}
```
也支持：
```text
ctnrCode=BOX001&ctnrTyp=C1&stgBinCode=p05&positionCode=p05
```

接口地址填写：
```text
http://192.168.67.2:8182
```
App 会自动拼接：
```text
/rcms/services/rest/hikRpcService/bindCtnrAndBin
```

## 生成可安装 APK
Android Studio 打开本目录后：Build > Build Bundle(s) / APK(s) > Build APK(s)。生成的 `app-debug.apk` 可复制到安卓手机安装。
