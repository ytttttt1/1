package com.example.rcscontainerbind;

import android.app.*;
import android.os.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.text.InputType;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private EditText baseUrl, clientCode, tokenCode, ctnrCode, ctnrTyp, stgBinCode, positionCode;
    private TextView status;


    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 42, 36, 24);
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("RCS 容器绑定 / 解绑");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(25, 118, 210));
        root.addView(title);

        baseUrl = addInput(root, "RCS地址，例如 http://192.168.1.10:8182", "http://192.168.1.10:8182");
        clientCode = addInput(root, "clientCode，可空", "");
        tokenCode = addInput(root, "tokenCode，可空", "");
        ctnrCode = addInput(root, "容器编号 ctnrCode，扫码填入", "");
        ctnrTyp = addInput(root, "容器类型 ctnrTyp", "C1");
        stgBinCode = addInput(root, "仓位编号 stgBinCode", "");
        positionCode = addInput(root, "位置编号 positionCode，可与仓位一致", "");

        Button scan = new Button(this);
        scan.setText("扫描货架/容器二维码");
        scan.setOnClickListener(v -> {
            IntentIntegrator integrator = new IntentIntegrator(this);
            integrator.setPrompt("请扫描二维码");
            integrator.setBeepEnabled(true);
            integrator.initiateScan();
        });
        root.addView(scan);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button bind = new Button(this);
        bind.setText("绑定");
        bind.setOnClickListener(v -> submit("1"));
        Button unbind = new Button(this);
        unbind.setText("解绑");
        unbind.setOnClickListener(v -> submit("0"));
        row.addView(bind, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(unbind, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(row);

        status = new TextView(this);
        status.setText("待扫码");
        status.setTextSize(16);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                parseQr(result.getContents());
                status.setText("扫码成功：" + result.getContents());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private EditText addInput(LinearLayout root, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void parseQr(String s) {
        try {
            if (s.trim().startsWith("{")) {
                JSONObject o = new JSONObject(s);
                setIf(o, "ctnrCode", ctnrCode); setIf(o, "ctnrTyp", ctnrTyp);
                setIf(o, "ctnrType", ctnrTyp); setIf(o, "stgBinCode", stgBinCode);
                setIf(o, "positionCode", positionCode);
            } else if (s.contains("=")) {
                for (String part : s.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length == 2) apply(kv[0], URLDecoder.decode(kv[1], "UTF-8"));
                }
            } else {
                ctnrCode.setText(s.trim());
            }
        } catch (Exception ex) { status.setText("二维码解析失败：" + ex.getMessage()); }
    }

    private void setIf(JSONObject o, String k, EditText e) throws Exception { if (o.has(k)) e.setText(o.getString(k)); }
    private void apply(String k, String v) {
        if ("ctnrCode".equalsIgnoreCase(k)) ctnrCode.setText(v);
        if ("ctnrTyp".equalsIgnoreCase(k) || "ctnrType".equalsIgnoreCase(k)) ctnrTyp.setText(v);
        if ("stgBinCode".equalsIgnoreCase(k)) stgBinCode.setText(v);
        if ("positionCode".equalsIgnoreCase(k)) positionCode.setText(v);
    }

    private void submit(String indBind) {
        if (baseUrl.getText().toString().trim().isEmpty() || ctnrTyp.getText().toString().trim().isEmpty()) {
            status.setText("请填写 RCS地址 和 容器类型"); return;
        }
        if (stgBinCode.getText().toString().trim().isEmpty() && positionCode.getText().toString().trim().isEmpty()) {
            status.setText("请填写 stgBinCode 或 positionCode"); return;
        }
        status.setText(("1".equals(indBind) ? "绑定" : "解绑") + "请求发送中...");
        new Thread(() -> {
            try {
                JSONObject req = new JSONObject();
                req.put("reqCode", UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                req.put("reqTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
                req.put("clientCode", clientCode.getText().toString().trim());
                req.put("tokenCode", tokenCode.getText().toString().trim());
                req.put("ctnrCode", ctnrCode.getText().toString().trim());
                req.put("ctnrTyp", ctnrTyp.getText().toString().trim());
                req.put("stgBinCode", stgBinCode.getText().toString().trim());
                req.put("positionCode", positionCode.getText().toString().trim());
                req.put("indBind", indBind);

                String u = baseUrl.getText().toString().replaceAll("/+$", "") + "/rcms/services/rest/hikRpcService/bindCtnrAndBin";
                HttpURLConnection c = (HttpURLConnection)new URL(u).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(20000);
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
                OutputStream os = c.getOutputStream(); os.write(req.toString().getBytes("UTF-8")); os.close();
                InputStream is = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
                String resp = readAll(is);
                runOnUiThread(() -> status.setText("返回：" + resp));
            } catch (Exception ex) { runOnUiThread(() -> status.setText("请求失败：" + ex.getMessage())); }
        }).start();
    }

    private String readAll(InputStream is) throws IOException {
        if (is == null) return "";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024]; int n;
        while ((n = is.read(buf)) >= 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }
}
