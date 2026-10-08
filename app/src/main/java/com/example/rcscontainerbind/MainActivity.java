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

public class MainActivity extends Activity {
    private AppConfig config;
    private EditText taskEdit;
    private TextView setupInfo, resultInfo;
    private Button cancelButton, retryButton;
    private RcsClient.Request pending;
    private String pendingTask = "";
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
        if (saved != null) taskEdit.setText(saved.getString("taskCode", ""));
        refresh();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("taskCode", taskEdit.getText().toString());
        super.onSaveInstanceState(out);
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private TextView label(String text, int size) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(42, 48, 57));
        return v;
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View wrap(View v) {
        LinearLayout outer = column();
        outer.setPadding(dp(20), dp(8), dp(20), dp(8));
        outer.addView(v);
        return outer;
    }

    private EditText field(String hint, String value, boolean password) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(16);
        e.setInputType(password
            ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return e;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = column();
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = label("RCS 任务取消", 23);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(52)));

        setupInfo = label("", 14);
        setupInfo.setGravity(Gravity.CENTER);
        root.addView(setupInfo);

        TextView instruction = label("请输入任务单号", 15);
        instruction.setPadding(0, dp(24), 0, dp(8));
        root.addView(instruction);

        taskEdit = field("任务单号 taskCode", "", false);
        taskEdit.setTextSize(20);
        taskEdit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(64)});
        root.addView(taskEdit, new LinearLayout.LayoutParams(-1, dp(64)));

        TextView hint = label("每次只需填写任务单号；服务器地址和取消方式由管理员设置。", 13);
        hint.setPadding(0, dp(6), 0, dp(14));
        root.addView(hint);

        cancelButton = new Button(this);
        cancelButton.setText("取消任务");
        cancelButton.setTextSize(18);
        root.addView(cancelButton, new LinearLayout.LayoutParams(-1, dp(60)));

        resultInfo = label("", 15);
        resultInfo.setTextIsSelectable(true);
        resultInfo.setPadding(0, dp(18), 0, dp(8));
        root.addView(resultInfo);

        retryButton = new Button(this);
        retryButton.setText("核实后重试上一次请求");
        root.addView(retryButton, new LinearLayout.LayoutParams(-1, -2));

        Space space = new Space(this);
        root.addView(space, new LinearLayout.LayoutParams(1, 0, 1));

        TextView admin = label("管理员设置", 14);
        admin.setGravity(Gravity.CENTER);
        admin.setTextColor(blue);
        admin.setPadding(0, dp(24), 0, dp(12));
        root.addView(admin);

        taskEdit.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            public void afterTextChanged(Editable s) { }
        });

        cancelButton.setOnClickListener(v -> confirmCancel());
        retryButton.setOnClickListener(v -> showRetryConfirm());
        admin.setOnClickListener(v -> openAdmin());
    }

    private String modeName() {
        return "1".equals(config.forceCancel) ? "软取消（1）" : "普通取消（0）";
    }

    private void refresh() {
        if (taskEdit == null) return;
        boolean configured = !config.baseUrl.isEmpty()
            && ("0".equals(config.forceCancel) || "1".equals(config.forceCancel));
        setupInfo.setText(configured
            ? "已配置 · " + modeName()
            : "请先进入管理员设置，填写服务器和取消方式");
        setupInfo.setTextColor(configured ? green : red);

        boolean ready = !busy && pending == null && configured
            && !taskEdit.getText().toString().trim().isEmpty();
        taskEdit.setEnabled(!busy && pending == null);
        cancelButton.setEnabled(ready);
        retryButton.setVisibility(pending == null ? View.GONE : View.VISIBLE);
        retryButton.setEnabled(!busy && pending != null);
    }

    private void showResult(String text, boolean success) {
        resultInfo.setText(text);
        resultInfo.setTextColor(success ? green : red);
    }

    private void confirmCancel() {
        if (busy || pending != null) return;
        final String taskCode = taskEdit.getText().toString().trim();
        try {
            final RcsClient.Request request = RcsClient.prepareCancel(config, taskCode);
            String warning;
            if ("1".equals(config.forceCancel)) {
                warning = "当前为软取消：RCS会尝试执行回库。文档说明叉车、辊筒车不支持软取消。";
            } else {
                warning = "当前为普通取消：如果AGV正背着货架，货架可能直接放在当前位置；CTU上的料箱可能需要人工处理。";
            }
            new AlertDialog.Builder(this)
                .setTitle("确认取消任务")
                .setMessage("任务单号：" + taskCode + "\n取消方式：" + modeName()
                    + "\n\n" + warning + "\n\n请确认任务单号和现场状态后再操作。")
                .setNegativeButton("返回", null)
                .setPositiveButton("确认取消", (d, w) -> send(request, taskCode))
                .show();
        } catch (Exception ex) {
            showResult(ex.getMessage(), false);
        }
    }

    private void send(RcsClient.Request request, String taskCode) {
        if (busy) return;
        busy = true;
        pending = request;
        pendingTask = taskCode;
        refresh();
        resultInfo.setTextColor(blue);
        resultInfo.setText("正在提交取消请求，请勿重复点击…");

        new Thread(() -> {
            try {
                RcsClient.Result result = RcsClient.execute(request);
                runOnUiThread(() -> {
                    busy = false;
                    String detail = "\n任务单号：" + taskCode
                        + "\n返回码：" + result.code
                        + "\n" + result.message
                        + "\n请求编号：" + result.reqCode;
                    if (result.success) {
                        pending = null;
                        pendingTask = "";
                        taskEdit.setText("");
                        showResult("取消成功" + detail + "\n请输入下一个任务单号。", true);
                    } else {
                        pending = null;
                        pendingTask = "";
                        showResult("取消未成功" + detail
                            + "\n任务单号已保留，请处理返回原因后再决定是否重试。", false);
                    }
                    refresh();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    busy = false;
                    showResult("请求结果未知：" + ex.getMessage()
                        + "\n任务单号：" + taskCode
                        + "\n请求编号：" + request.reqCode
                        + "\n请先在RCS核实任务状态，不要直接重复取消。", false);
                    refresh();
                });
            }
        }, "rcs-cancel-task").start();
    }

    private void showRetryConfirm() {
        if (busy || pending == null) return;
        new AlertDialog.Builder(this)
            .setTitle("上次请求结果未确认")
            .setMessage("任务单号：" + pendingTask
                + "\n请先在RCS核实任务是否已取消。\n\n重试会使用原请求编号，不会生成新的请求编号。")
            .setNegativeButton("返回", null)
            .setNeutralButton("已核实，放弃重试", (d, w) -> {
                pending = null;
                pendingTask = "";
                refresh();
            })
            .setPositiveButton("使用原编号重试", (d, w) -> send(pending, pendingTask))
            .show();
    }

    private void openAdmin() {
        if (busy) { toast("请等待当前请求结束"); return; }
        if (pending != null) { toast("请先核实并处理上一次请求"); return; }
        if (!config.hasPin()) { showPinSetup(); return; }
        if (System.currentTimeMillis() < pinLockedUntil) {
            toast("尝试过多，请稍后再试");
            return;
        }

        EditText pin = field("管理员密码", "", true);
        new AlertDialog.Builder(this)
            .setTitle("管理员验证")
            .setView(wrap(pin))
            .setNegativeButton("取消", null)
            .setPositiveButton("进入设置", (d, w) -> {
                if (config.verifyPin(pin.getText().toString())) {
                    pinFailures = 0;
                    showSettings();
                } else {
                    pinFailures++;
                    if (pinFailures >= 5) {
                        pinLockedUntil = System.currentTimeMillis() + 60000;
                        pinFailures = 0;
                    }
                    toast("管理员密码错误");
                }
            }).show();
    }

    private void showPinSetup() {
        EditText p1 = field("设置6至12位数字密码", "", true);
        EditText p2 = field("再次输入密码", "", true);
        LinearLayout form = column();
        form.addView(p1);
        form.addView(p2);

        new AlertDialog.Builder(this)
            .setTitle("首次设置管理员密码")
            .setMessage("此密码只用于保护本机设置。")
            .setView(wrap(form))
            .setNegativeButton("取消", null)
            .setPositiveButton("保存并进入", (d, w) -> {
                if (!p1.getText().toString().equals(p2.getText().toString())) {
                    toast("两次密码不一致");
                    return;
                }
                try {
                    config.setPin(p1.getText().toString());
                    showSettings();
                } catch (Exception ex) {
                    toast(ex.getMessage());
                }
            }).show();
    }

    private void showSettings() {
        LinearLayout form = column();
        form.addView(label("管理员填写一次，保存后长期保留。员工首页不能修改。", 14));

        TextView addressLabel = label("RCS 服务器地址", 14);
        addressLabel.setPadding(0, dp(16), 0, 0);
        form.addView(addressLabel);
        EditText address = field("http://IP:8182", config.baseUrl, false);
        form.addView(address);

        TextView modeLabel = label("取消方式", 14);
        modeLabel.setPadding(0, dp(16), 0, dp(4));
        form.addView(modeLabel);

        String[] labels = {
            "普通取消（0）",
            "软取消（1）"
        };
        Spinner mode = new Spinner(this);
        mode.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        mode.setSelection("1".equals(config.forceCancel) ? 1 : 0);
        form.addView(mode);

        TextView note = label(
            "普通取消可能需要现场人工处理货架/料箱；软取消会尝试回库，且文档说明叉车、辊筒车不支持。"
            + " matterArea、clientCode、tokenCode、agvCode等可选字段不在员工页面显示，本版本也不发送。",
            13);
        note.setPadding(0, dp(16), 0, 0);
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("管理员设置")
            .setView(wrap(form))
            .setNegativeButton("取消", null)
            .setNeutralButton("修改管理员密码", null)
            .setPositiveButton("保存设置", null)
            .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    AppConfig next = new AppConfig(this);
                    next.baseUrl = address.getText().toString().trim();
                    next.forceCancel = mode.getSelectedItemPosition() == 1 ? "1" : "0";
                    next.save();
                    config = next;
                    resultInfo.setText("");
                    refresh();
                    dialog.dismiss();
                    toast("设置已保存");
                } catch (Exception ex) {
                    new AlertDialog.Builder(this)
                        .setMessage(ex.getMessage())
                        .setPositiveButton("知道了", null)
                        .show();
                }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> showChangePin());
        });

        dialog.show();
    }

    private void showChangePin() {
        EditText p1 = field("新密码（6至12位数字）", "", true);
        EditText p2 = field("再次输入新密码", "", true);
        LinearLayout form = column();
        form.addView(p1);
        form.addView(p2);

        new AlertDialog.Builder(this)
            .setTitle("修改管理员密码")
            .setView(wrap(form))
            .setNegativeButton("取消", null)
            .setPositiveButton("保存", (d, w) -> {
                if (!p1.getText().toString().equals(p2.getText().toString())) {
                    toast("两次密码不一致");
                    return;
                }
                try {
                    config.setPin(p1.getText().toString());
                    toast("管理员密码已更新");
                } catch (Exception ex) {
                    toast(ex.getMessage());
                }
            }).show();
    }
}
