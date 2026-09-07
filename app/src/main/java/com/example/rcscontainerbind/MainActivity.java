package com.example.rcscontainerbind;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** One operator screen. Server and container type are configured only by an administrator. */
public class MainActivity extends Activity {
    private AppConfig config;
    private EditText binEdit;
    private TextView setupInfo, resultInfo;
    private Button bindButton, unbindButton, retryButton;
    private RcsClient.Request pending;
    private String pendingBin = "";
    private boolean busy;
    private int pinFailures;
    private long pinLockedUntil;
    private final int blue = Color.rgb(25, 118, 210);
    private final int green = Color.rgb(35, 116, 71);
    private final int red = Color.rgb(175, 42, 42);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        config = new AppConfig(this);
        buildUi();
        if (saved != null) binEdit.setText(saved.getString("bin", ""));
        refresh();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("bin", binEdit.getText().toString());
        super.onSaveInstanceState(out);
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private TextView label(String text, int size) {
        TextView v = new TextView(this); v.setText(text); v.setTextSize(size);
        v.setTextColor(Color.rgb(42, 48, 57)); return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(1); return l; }
    private View wrap(View v) {
        LinearLayout outer = column(); outer.setPadding(dp(20), dp(8), dp(20), dp(8)); outer.addView(v); return outer;
    }
    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true);
        e.setTextSize(16);
        e.setInputType(password ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        return e;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout root = column(); root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252)); scroll.addView(root); setContentView(scroll);
        TextView title = label("容器绑定 / 解绑", 23); title.setGravity(Gravity.CENTER); title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));
        setupInfo = label("", 14); setupInfo.setGravity(Gravity.CENTER);
        root.addView(setupInfo);
        TextView instruction = label("请输入仓位编号", 15);
        instruction.setPadding(0, dp(24), 0, dp(8)); root.addView(instruction);
        binEdit = field("仓位编号 stgBinCode", "", false);
        binEdit.setTextSize(20); binEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        binEdit.setSingleLine(true);
        binEdit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        root.addView(binEdit, new LinearLayout.LayoutParams(-1, dp(64)));
        TextView hint = label("每次操作只需填写此编号，服务器和容器类型由管理员设置。", 13);
        hint.setPadding(0, dp(6), 0, dp(12)); root.addView(hint);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(0);
        bindButton = new Button(this); bindButton.setText("绑定"); bindButton.setTextSize(18);
        unbindButton = new Button(this); unbindButton.setText("解绑"); unbindButton.setTextSize(18);
        actions.addView(bindButton, new LinearLayout.LayoutParams(0, dp(60), 1));
        actions.addView(unbindButton, new LinearLayout.LayoutParams(0, dp(60), 1));
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        resultInfo = label("", 15); resultInfo.setTextIsSelectable(true);
        resultInfo.setPadding(0, dp(18), 0, dp(8)); root.addView(resultInfo);
        retryButton = new Button(this); retryButton.setText("核实后重试上一次请求");
        root.addView(retryButton, new LinearLayout.LayoutParams(-1, -2));
        Space space = new Space(this); root.addView(space, new LinearLayout.LayoutParams(1, 0, 1));
        TextView admin = label("管理员设置", 14); admin.setGravity(Gravity.CENTER); admin.setTextColor(blue);
        admin.setPadding(0, dp(24), 0, dp(12)); root.addView(admin);
        binEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });
        bindButton.setOnClickListener(v -> confirm("1"));
        unbindButton.setOnClickListener(v -> confirm("0"));
        retryButton.setOnClickListener(v -> showRetryConfirm());
        admin.setOnClickListener(v -> openAdmin());
    }
    private void refresh() {
        if (binEdit == null) return;
        boolean configured = !config.baseUrl.isEmpty() && !config.ctnrTyp.isEmpty();
        setupInfo.setText(configured ? "已配置 · 容器类型：" + config.ctnrTyp : "请先进入管理员设置，填写服务器和容器类型");
        setupInfo.setTextColor(configured ? green : red);
        boolean ready = !busy && pending == null && configured && !binEdit.getText().toString().trim().isEmpty();
        binEdit.setEnabled(!busy && pending == null);
        bindButton.setEnabled(ready); unbindButton.setEnabled(ready);
        retryButton.setVisibility(pending == null ? View.GONE : View.VISIBLE);
        retryButton.setEnabled(!busy && pending != null);
    }
    private void showResult(String text, boolean success) {
        resultInfo.setText(text); resultInfo.setTextColor(success ? green : red);
    }
    private void confirm(String action) {
        if (busy || pending != null) return;
        final String bin = binEdit.getText().toString().trim();
        try {
            final RcsClient.Request request = RcsClient.prepareBin(config, bin, action);
            String name = "1".equals(action) ? "绑定" : "解绑";
            new AlertDialog.Builder(this).setTitle("确认" + name)
                .setMessage("容器类型：" + config.ctnrTyp + "\n仓位编号：" + bin + "\n\n请核对现场仓位后确认。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认" + name, (d, w) -> send(request, bin)).show();
        } catch (Exception ex) { showResult(ex.getMessage(), false); }
    }
    private void send(RcsClient.Request request, String bin) {
        if (busy) return;
        busy = true; pending = request; pendingBin = bin; refresh();
        String name = "1".equals(request.action) ? "绑定" : "解绑";
        resultInfo.setTextColor(blue); resultInfo.setText("正在提交" + name + "，请勿重复点击…");
        new Thread(() -> {
            try {
                RcsClient.Result result = RcsClient.execute(request);
                runOnUiThread(() -> {
                    busy = false;
                    String detail = "\n仓位编号：" + bin + "\n返回码：" + result.code + "\n" + result.message + "\n请求编号：" + result.reqCode;
                    if (result.success) {
                        pending = null; pendingBin = "";
                        binEdit.setText("");
                        showResult(name + "成功" + detail + "\n请填写下一个仓位编号。", true);
                    } else {
                        if ("1".equals(result.code) || "100".equals(result.code)) { pending = null; pendingBin = ""; }
                        showResult(name + "未确认成功" + detail + "\n请核实 RCS 状态后处理。", false);
                    }
                    refresh();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    busy = false;
                    showResult("请求结果未知：" + ex.getMessage() + "\n仓位编号：" + bin + "\n请求编号：" + request.reqCode + "\n请先核实 RCS 状态，不要盲目重复操作。", false);
                    refresh();
                });
            }
        }, "rcs-binding-request").start();
    }
    private void showRetryConfirm() {
        if (busy || pending == null) return;
        new AlertDialog.Builder(this).setTitle("上次操作结果未确认")
            .setMessage("仓位：" + pendingBin + "\n请先在 RCS 核实该操作是否已生效。\n\n重试会使用原请求编号，不会创建新请求。")
            .setNegativeButton("取消", null)
            .setNeutralButton("已核实，重新输入", (d, w) -> {
                pending = null; pendingBin = ""; binEdit.setText(""); refresh();
            })
            .setPositiveButton("使用原编号重试", (d, w) -> send(pending, pendingBin)).show();
    }
    private void openAdmin() {
        if (busy) { toast("请等待当前请求结束"); return; }
        if (pending != null) { toast("请先核实并处理上一次请求"); return; }
        if (!config.hasPin()) { showPinSetup(); return; }
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
    private void showSettings() {
        LinearLayout form = column();
        form.addView(label("管理员填写一次，保存后长期保留。员工无法在首页修改。", 14));
        TextView addressLabel = label("RCS 服务器地址", 14); addressLabel.setPadding(0, dp(16), 0, 0); form.addView(addressLabel);
        EditText address = field("http://IP:8182", config.baseUrl, false); form.addView(address);
        TextView typeLabel = label("固定容器类型 ctnrTyp", 14); typeLabel.setPadding(0, dp(16), 0, 0); form.addView(typeLabel);
        EditText type = field("例如 2（以现场配置为准）", config.ctnrTyp, false); form.addView(type);
        TextView note = label("仓位编号由员工每次输入，不在这里设置默认值。保存不会清除服务器或容器类型。", 13);
        note.setPadding(0, dp(16), 0, 0); form.addView(note);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("管理员设置").setView(wrap(form))
            .setNegativeButton("取消", null).setNeutralButton("修改管理员密码", null)
            .setPositiveButton("保存设置", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    AppConfig next = new AppConfig(this);
                    next.baseUrl = address.getText().toString().trim(); next.ctnrTyp = type.getText().toString().trim();
                    next.defaultBin = ""; next.defaultPosition = "";
                    next.save(); config = next;
                    pending = null; pendingBin = ""; binEdit.setText(""); resultInfo.setText(""); refresh();
                    dialog.dismiss(); toast("设置已保存");
                } catch (Exception ex) { new AlertDialog.Builder(this).setMessage(ex.getMessage()).setPositiveButton("知道了", null).show(); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> showChangePin());
        });
        dialog.show();
    }
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
