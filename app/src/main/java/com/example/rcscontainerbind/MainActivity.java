package com.example.rcscontainerbind;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private AppConfig config;
    private QrPayload current;
    private RcsClient.Request pending;
    private TextView scanInfo, resultInfo, setupInfo;
    private Button scanButton, bindButton, unbindButton, retryButton;
    private boolean busy = false;
    private int pinFailures = 0;
    private long pinLockedUntil = 0;
    private final int blue = Color.rgb(25, 118, 210);
    private final int green = Color.rgb(35, 116, 71);
    private final int red = Color.rgb(175, 42, 42);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        config = new AppConfig(this);
        buildUi();
        if (saved != null && saved.containsKey("scan")) {
            try { current = QrPayload.parse(saved.getString("scan"), config); } catch (Exception ignored) { }
        }
        refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        if (current != null) out.putString("scan", current.raw);
        super.onSaveInstanceState(out);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(Color.rgb(42, 48, 57)); return v;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(root);
        setContentView(scroll);
        TextView title = label("容器绑定 / 解绑", 23);
        title.setGravity(Gravity.CENTER); title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));
        setupInfo = label("", 14); setupInfo.setGravity(Gravity.CENTER);
        root.addView(setupInfo);
        TextView instruction = label("扫描二维码，核对信息后选择操作", 14);
        instruction.setGravity(Gravity.CENTER); instruction.setPadding(0, dp(12), 0, dp(16));
        root.addView(instruction);
        scanButton = new Button(this); scanButton.setText("扫描货架 / 容器二维码"); scanButton.setTextSize(17);
        root.addView(scanButton, new LinearLayout.LayoutParams(-1, dp(58)));
        scanInfo = label("尚未扫描", 16); scanInfo.setTextIsSelectable(true);
        scanInfo.setPadding(dp(12), dp(18), dp(12), dp(18));
        scanInfo.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(16); root.addView(scanInfo, infoParams);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        bindButton = new Button(this); bindButton.setText("绑定"); bindButton.setTextSize(18);
        unbindButton = new Button(this); unbindButton.setText("解绑"); unbindButton.setTextSize(18);
        actions.addView(bindButton, new LinearLayout.LayoutParams(0, dp(60), 1));
        actions.addView(unbindButton, new LinearLayout.LayoutParams(0, dp(60), 1));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, -2);
        actionParams.topMargin = dp(18); root.addView(actions, actionParams);
        resultInfo = label("", 15); resultInfo.setTextIsSelectable(true);
        resultInfo.setPadding(0, dp(16), 0, dp(8)); root.addView(resultInfo);
        retryButton = new Button(this); retryButton.setText("重试上一次请求（相同编号）");
        root.addView(retryButton, new LinearLayout.LayoutParams(-1, -2));
        TextView admin = label("管理员设置", 14); admin.setGravity(Gravity.CENTER);
        admin.setPadding(0, dp(22), 0, dp(12)); admin.setTextColor(blue);
        root.addView(admin, new LinearLayout.LayoutParams(-1, -2));
        scanButton.setOnClickListener(v -> startScan());
        bindButton.setOnClickListener(v -> confirm("1"));
        unbindButton.setOnClickListener(v -> confirm("0"));
        retryButton.setOnClickListener(v -> { if (pending != null && !busy) showRetryConfirm(); });
        admin.setOnClickListener(v -> openAdmin());
    }
    private void refresh() {
        boolean configured = !config.baseUrl.isEmpty();
        setupInfo.setText(configured ? "已配置服务器 · 员工操作" : "尚未配置服务器，请先进入管理员设置");
        setupInfo.setTextColor(configured ? green : red);
        scanInfo.setText(current == null ? "尚未扫描" : current.summary());
        scanButton.setEnabled(!busy && configured);
        bindButton.setEnabled(!busy && configured && current != null);
        unbindButton.setEnabled(!busy && configured && current != null);
        retryButton.setVisibility(pending == null ? View.GONE : View.VISIBLE);
        retryButton.setEnabled(!busy && pending != null);
    }
    private void showResult(String text, boolean success) {
        resultInfo.setText(text); resultInfo.setTextColor(success ? green : red);
    }
    private void startScan() {
        if (busy) return;
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("请扫描二维码"); integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true); integrator.initiateScan();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        IntentResult scan = IntentIntegrator.parseActivityResult(request, result, data);
        if (scan == null) return;
        if (scan.getContents() == null) { toast("已取消扫描"); return; }
        try {
            QrPayload parsed = QrPayload.parse(scan.getContents(), config);
            current = parsed; pending = null;
            resultInfo.setText(""); refresh();
        } catch (Exception ex) {
            current = null; pending = null; refresh();
            showResult("二维码解析失败：" + ex.getMessage(), false);
        }
    }
    private void confirm(String action) {
        if (busy || current == null) return;
        try {
            RcsClient.Request request = RcsClient.prepare(config, current, action);
            String name = "1".equals(action) ? "绑定" : "解绑";
            new AlertDialog.Builder(this).setTitle("确认" + name)
                .setMessage(current.summary() + "\n\n请确认现场容器与仓位一致。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认" + name, (d, w) -> send(request)).show();
        } catch (Exception ex) { showResult(ex.getMessage(), false); }
    }
    private void showRetryConfirm() {
        new AlertDialog.Builder(this).setTitle("确认重试")
            .setMessage("上一次操作结果未确认。请先核实 RCS 状态，避免重复操作。\n\n重试将使用原请求编号：" + pending.reqCode)
            .setNegativeButton("取消", null)
            .setPositiveButton("使用原编号重试", (d, w) -> send(pending)).show();
    }
    private void send(RcsClient.Request request) {
        if (busy) return;
        busy = true; pending = request; refresh();
        String name = "1".equals(request.action) ? "绑定" : "解绑";
        resultInfo.setTextColor(blue); resultInfo.setText("正在提交" + name + "，请勿重复点击…");
        new Thread(() -> {
            try {
                RcsClient.Result result = RcsClient.execute(request);
                runOnUiThread(() -> {
                    busy = false;
                    String detail = "\n返回码：" + result.code + "\n" + result.message + "\n请求编号：" + result.reqCode;
                    if (result.success) {
                        pending = null; current = null;
                        showResult(name + "成功" + detail + "\n请扫描下一个二维码。", true);
                    } else {
                        if ("1".equals(result.code) || "100".equals(result.code)) pending = null;
                        showResult(name + "未确认成功" + detail + "\n请核实 RCS 状态后处理。", false);
                    }
                    refresh();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    busy = false;
                    showResult("请求结果未知：" + ex.getMessage() + "\n请求编号：" + request.reqCode + "\n请先核实 RCS 状态，不要盲目重复操作。", false);
                    refresh();
                });
            }
        }, "rcs-binding-request").start();
    }

    private void openAdmin() {
        if (busy) { toast("请等待当前请求结束"); return; }
        if (!config.hasPin()) {
            showPinSetup(); return;
        }
        if (System.currentTimeMillis() < pinLockedUntil) { toast("尝试过多，请稍后再试"); return; }
        EditText pin = field("管理员密码", "", true);
        new AlertDialog.Builder(this).setTitle("管理员验证").setView(wrap(pin))
            .setNegativeButton("取消", null).setPositiveButton("进入设置", (d, w) -> {
                if (config.verifyPin(pin.getText().toString())) { pinFailures = 0; showSettings(); }
                else {
                    pinFailures++;
                    if (pinFailures >= 5) { pinLockedUntil = System.currentTimeMillis() + 60000; pinFailures = 0; }
                    toast("管理员密码错误");
                }
            }).show();
    }
    private void showPinSetup() {
        EditText p1 = field("设置6至12位数字密码", "", true);
        EditText p2 = field("再次输入密码", "", true);
        LinearLayout form = column(); form.addView(p1); form.addView(p2);
        new AlertDialog.Builder(this).setTitle("首次设置管理员密码")
            .setMessage("此密码仅保护本机设置，不替代 RCS 服务端权限。请妥善保管。")
            .setView(wrap(form)).setNegativeButton("取消", null)
            .setPositiveButton("保存并进入", (d, w) -> {
                if (!p1.getText().toString().equals(p2.getText().toString())) { toast("两次密码不一致"); return; }
                try { config.setPin(p1.getText().toString()); showSettings(); }
                catch (Exception ex) { toast(ex.getMessage()); }
            }).show();
    }
    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true);
        e.setTextSize(15);
        if (password) e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        else e.setInputType(InputType.TYPE_CLASS_TEXT);
        return e;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private View wrap(View view) {
        LinearLayout outer = column(); outer.setPadding(dp(20), dp(8), dp(20), dp(8)); outer.addView(view); return outer;
    }
    private void showSettings() {
        LinearLayout form = column();
        TextView info = label("修改后立即保存在本机，无需重新安装。请按现场接口配置填写。", 13);
        form.addView(info);
        LinkedHashMap<String, EditText> fields = new LinkedHashMap<>();
        addField(form, fields, "baseUrl", "RCS 地址（http://IP:8182）", config.baseUrl, false);
        addField(form, fields, "clientCode", "clientCode（可空，需先在RCS配置）", config.clientCode, false);
        addField(form, fields, "tokenCode", "tokenCode（可空，保留原值）", "", false);
        addField(form, fields, "ctnrTyp", "默认容器类型 ctnrTyp", config.ctnrTyp, false);
        addField(form, fields, "defaultBin", "默认仓位 stgBinCode（可空）", config.defaultBin, false);
        addField(form, fields, "defaultPosition", "默认地图位置 positionCode（可空）", config.defaultPosition, false);
        TextView modeLabel = label("纯文本二维码代表什么", 14); modeLabel.setPadding(0, dp(12), 0, 0); form.addView(modeLabel);
        String[] modes = {"自动识别（纯文本=容器编号）", "容器编号", "仓位编号", "地图位置编号"};
        String[] values = {"auto", "container", "bin", "position"};
        Spinner mode = new Spinner(this); mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes));
        int selected = java.util.Arrays.asList(values).indexOf(config.qrMode); mode.setSelection(Math.max(0, selected)); form.addView(mode);
        TextView mapLabel = label("二维码字段映射（JSON或key=value）", 14); mapLabel.setPadding(0, dp(12), 0, 0); form.addView(mapLabel);
        addField(form, fields, "ctnrKey", "容器编号字段名", config.ctnrKey, false);
        addField(form, fields, "typeKey", "容器类型字段名", config.typeKey, false);
        addField(form, fields, "binKey", "仓位字段名", config.binKey, false);
        addField(form, fields, "positionKey", "地图位置字段名", config.positionKey, false);
        TextView note = label("注意：纯文本二维码不能自动推断容器与仓位关系。默认仓位/位置只有在现场确实固定时才填写。", 13);
        note.setPadding(0, dp(12), 0, 0); form.addView(note);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(wrap(form));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("管理员设置")
            .setView(scroll).setNegativeButton("取消", null).setNeutralButton("修改管理员密码", null)
            .setPositiveButton("保存设置", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    AppConfig next = new AppConfig(this);
                    next.baseUrl = text(fields, "baseUrl"); next.clientCode = text(fields, "clientCode");
                    String token = text(fields, "tokenCode"); next.tokenCode = token.isEmpty() ? config.tokenCode : token;
                    next.ctnrTyp = text(fields, "ctnrTyp"); next.defaultBin = text(fields, "defaultBin"); next.defaultPosition = text(fields, "defaultPosition");
                    next.qrMode = values[mode.getSelectedItemPosition()];
                    next.ctnrKey = text(fields, "ctnrKey"); next.typeKey = text(fields, "typeKey"); next.binKey = text(fields, "binKey"); next.positionKey = text(fields, "positionKey");
                    next.save(); config = next;
                    current = null; pending = null; resultInfo.setText(""); refresh();
                    dialog.dismiss(); toast("设置已保存，请重新扫码");
                } catch (Exception ex) { new AlertDialog.Builder(this).setMessage(ex.getMessage()).setPositiveButton("知道了", null).show(); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showChangePin());
        });
        dialog.show();
    }
    private void addField(LinearLayout form, Map<String, EditText> fields, String key, String hint, String value, boolean password) {
        EditText e = field(hint, value, password); fields.put(key, e); form.addView(e);
    }
    private String text(Map<String, EditText> fields, String key) { return fields.get(key).getText().toString().trim(); }
    private void showChangePin() {
        EditText p1 = field("新密码（6至12位数字）", "", true);
        EditText p2 = field("再次输入新密码", "", true);
        LinearLayout form = column(); form.addView(p1); form.addView(p2);
        new AlertDialog.Builder(this).setTitle("修改管理员密码").setView(wrap(form))
            .setNegativeButton("取消", null).setPositiveButton("保存", (d, w) -> {
                if (!p1.getText().toString().equals(p2.getText().toString())) { toast("两次密码不一致"); return; }
                try { config.setPin(p1.getText().toString()); toast("管理员密码已更新"); }
                catch (Exception ex) { toast(ex.getMessage()); }
            }).show();
    }
}
