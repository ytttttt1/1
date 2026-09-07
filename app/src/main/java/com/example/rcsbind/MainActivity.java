package com.example.rcsbind;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private EditText baseUrlEdit, clientCodeEdit, tokenCodeEdit, defaultCtnrTypEdit;
    private TextView scanText, resultText;
    private String ctnrCode = "";
    private String ctnrTyp = "";
    private String stgBinCode = "";
    private String positionCode = "";
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = getSharedPreferences("rcs_config", MODE_PRIVATE);
        buildUi();
        loadConfig();
    }

    private void buildUi() {
        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("容器绑定 / 解绑");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        baseUrlEdit = edit("RCS地址，如 http://192.168.1.10:8182");
        clientCodeEdit = edit("clientCode，可空");
        tokenCodeEdit = edit("tokenCode，可空");
        defaultCtnrTypEdit = edit("默认容器类型，如 C1");
        root.addView(baseUrlEdit);
        root.addView(clientCodeEdit);
        root.addView(tokenCodeEdit);
        root.addView(defaultCtnrTypEdit);

        Button scanBtn = new Button(this);
        scanBtn.setText("扫描货架二维码");
        scanBtn.setTextSize(18);
        root.addView(scanBtn, new LinearLayout.LayoutParams(-1, dp(56)));

        scanText = new TextView(this);
        scanText.setText("未扫描");
        scanText.setTextSize(16);
        scanText.setPadding(0, pad, 0, pad);
        root.addView(scanText, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button bindBtn = new Button(this);
        bindBtn.setText("绑定");
        bindBtn.setTextSize(18);
        Button unbindBtn = new Button(this);
        unbindBtn.setText("解绑");
        unbindBtn.setTextSize(18);
        row.addView(bindBtn, new LinearLayout.LayoutParams(0, dp(56), 1));
        row.addView(unbindBtn, new LinearLayout.LayoutParams(0, dp(56), 1));
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        resultText = new TextView(this);
        resultText.setTextSize(15);
        resultText.setPadding(0, pad, 0, 0);
        root.addView(resultText, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        scanBtn.setOnClickListener(v -> startScan());
        bindBtn.setOnClickListener(v -> callBindApi("1"));
        unbindBtn.setOnClickListener(v -> callBindApi("0"));
    }

    private EditText edit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(15);
        e.setPadding(0, dp(8), 0, dp(8));
        return e;
    }

    private void loadConfig() {
        baseUrlEdit.setText(sp.getString("baseUrl", "http://IP:8182"));
        clientCodeEdit.setText(sp.getString("clientCode", ""));
        tokenCodeEdit.setText(sp.getString("tokenCode", ""));
        defaultCtnrTypEdit.setText(sp.getString("ctnrTyp", "C1"));
    }

    private void saveConfig() {
        sp.edit()
                .putString("baseUrl", baseUrlEdit.getText().toString().trim())
                .putString("clientCode", clientCodeEdit.getText().toString().trim())
                .putString("tokenCode", tokenCodeEdit.getText().toString().trim())
                .putString("ctnrTyp", defaultCtnrTypEdit.getText().toString().trim())
                .apply();
    }

    private void startScan() {
        saveConfig();
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("请扫描货架二维码");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) return;
        if (result.getContents() == null) {
            toast("已取消扫描");
            return;
        }
        parseQr(result.getContents().trim());
    }

    /**
     * 推荐二维码内容：
     * {"ctnrCode":"BOX001","ctnrTyp":"C1","stgBinCode":"p05","positionCode":"p05"}
     * 也支持：ctnrCode=BOX001&ctnrTyp=C1&stgBinCode=p05&positionCode=p05
     * 若二维码只有普通文本，则按 ctnrCode 处理，stgBinCode/positionCode 需包含在二维码中才可直接绑定。
     */
    private void parseQr(String raw) {
        try {
            if (raw.startsWith("{")) {
                JSONObject j = new JSONObject(raw);
                ctnrCode = j.optString("ctnrCode", "");
                ctnrTyp = j.optString("ctnrTyp", j.optString("ctnrType", defaultCtnrTypEdit.getText().toString().trim()));
                stgBinCode = j.optString("stgBinCode", "");
                positionCode = j.optString("positionCode", stgBinCode);
            } else if (raw.contains("=")) {
                ctnrCode = getParam(raw, "ctnrCode");
                ctnrTyp = firstNonEmpty(getParam(raw, "ctnrTyp"), getParam(raw, "ctnrType"), defaultCtnrTypEdit.getText().toString().trim());
                stgBinCode = getParam(raw, "stgBinCode");
                positionCode = firstNonEmpty(getParam(raw, "positionCode"), stgBinCode);
            } else {
                ctnrCode = raw;
                ctnrTyp = defaultCtnrTypEdit.getText().toString().trim();
                stgBinCode = "";
                positionCode = "";
            }
            scanText.setText("容器编号：" + ctnrCode + "\n容器类型：" + ctnrTyp + "\n仓位编号：" + stgBinCode + "\n位置编号：" + positionCode);
            resultText.setText("");
        } catch (Exception e) {
            toast("二维码格式错误：" + e.getMessage());
        }
    }

    private String getParam(String raw, String key) {
        String s = raw;
        int idx = s.indexOf('?');
        if (idx >= 0) s = s.substring(idx + 1);
        for (String pair : s.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(key)) return kv[1].trim();
        }
        return "";
    }

    private String firstNonEmpty(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    private void callBindApi(String indBind) {
        saveConfig();
        if (baseUrlEdit.getText().toString().trim().isEmpty()) { toast("请配置 RCS 地址"); return; }
        if (ctnrTyp.isEmpty()) { toast("缺少容器类型 ctnrTyp"); return; }
        if (stgBinCode.isEmpty() && positionCode.isEmpty()) { toast("二维码需包含 stgBinCode 或 positionCode"); return; }

        resultText.setText("提交中...");
        new Thread(() -> {
            try {
                String url = baseUrlEdit.getText().toString().trim();
                url = url.replaceAll("/+$", "") + "/rcms/services/rest/hikRpcService/bindCtnrAndBin";
                JSONObject body = new JSONObject();
                body.put("reqCode", UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                body.put("reqTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
                body.put("clientCode", clientCodeEdit.getText().toString().trim());
                body.put("tokenCode", tokenCodeEdit.getText().toString().trim());
                body.put("ctnrCode", ctnrCode);
                body.put("ctnrTyp", ctnrTyp);
                body.put("stgBinCode", stgBinCode);
                body.put("positionCode", positionCode);
                body.put("indBind", indBind);
                String resp = postJson(url, body.toString());
                runOnUiThread(() -> resultText.setText(("1".equals(indBind) ? "绑定" : "解绑") + "结果：\n" + resp));
            } catch (Exception e) {
                runOnUiThread(() -> resultText.setText("请求失败：" + e.getMessage()));
            }
        }).start();
    }

    private String postJson(String urlStr, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) { os.write(bytes); }
        int code = conn.getResponseCode();
        InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString().trim();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); }
}
