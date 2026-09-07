package com.example.rcscontainerbind;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

/** The only production network operation is the documented bindCtnrAndBin POST. */
public final class RcsClient {
    public static final String PATH = "/rcms/services/rest/hikRpcService/bindCtnrAndBin";
    private RcsClient() { }

    public static final class Request {
        public final String endpoint, body, reqCode, action;
        private Request(String endpoint, String body, String reqCode, String action) {
            this.endpoint = endpoint; this.body = body; this.reqCode = reqCode; this.action = action;
        }
    }

    /** Manual-bin workflow: type is administrator-configured; only the bin changes. */
    public static Request prepareBin(AppConfig config, String bin, String action) throws Exception {
        if (!"1".equals(action) && !"0".equals(action)) throw new Exception("操作类型无效");
        String base = AppConfig.endpointBase(config.baseUrl);
        if (base.isEmpty()) throw new Exception("请先由管理员配置 RCS 地址");
        String type = AppConfig.clean(config.ctnrTyp);
        String code = AppConfig.clean(bin);
        if (type.isEmpty() || type.length() > 16) throw new Exception("请先由管理员配置有效的容器类型");
        if (code.isEmpty()) throw new Exception("请输入仓位编号");
        if (code.length() > 32) throw new Exception("仓位编号不能超过32个字符");
        if (code.contains("\n") || code.contains("\r")) throw new Exception("仓位编号不能包含换行");
        String reqCode = UUID.randomUUID().toString().replace("-", "");
        JSONObject body = new JSONObject();
        body.put("reqCode", reqCode);
        body.put("reqTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        body.put("ctnrTyp", type);
        body.put("stgBinCode", code);
        body.put("indBind", action);
        return new Request(base + PATH, body.toString(), reqCode, action);
    }

    /** Retained for compatibility with earlier QR-parser tests; not used by the new UI. */
    public static Request prepare(AppConfig config, QrPayload qr, String action) throws Exception {
        if (!"1".equals(action) && !"0".equals(action)) throw new Exception("操作类型无效");
        qr.validate("1".equals(action));
        String base = AppConfig.endpointBase(config.baseUrl);
        if (base.isEmpty()) throw new Exception("请先配置 RCS 服务器地址");
        String code = UUID.randomUUID().toString().replace("-", "");
        JSONObject body = new JSONObject();
        body.put("reqCode", code);
        body.put("reqTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        body.put("clientCode", config.clientCode);
        body.put("tokenCode", config.tokenCode);
        body.put("ctnrCode", qr.ctnrCode);
        body.put("ctnrTyp", qr.ctnrTyp);
        body.put("stgBinCode", qr.stgBinCode);
        body.put("positionCode", qr.positionCode);
        body.put("indBind", action);
        return new Request(base + PATH, body.toString(), code, action);
    }

    public static final class Result {
        public final int httpStatus;
        public final String code, message, reqCode;
        public final boolean success;
        private Result(int status, String code, String message, String reqCode, boolean success) {
            this.httpStatus = status; this.code = code; this.message = message;
            this.reqCode = reqCode; this.success = success;
        }
    }
    public static Result execute(Request request) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(request.endpoint).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        try {
            byte[] bytes = request.body.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = read(in);
            JSONObject json;
            try { json = new JSONObject(text); }
            catch (Exception ex) { throw new Exception("服务器返回非 JSON（HTTP " + status + "），请检查地址和 RCS 服务"); }
            if (!json.has("code") || !json.has("reqCode")) throw new Exception("RCS 响应缺少 code 或 reqCode");
            String code = json.optString("code", "");
            String reqCode = json.optString("reqCode", "");
            if (!request.reqCode.equals(reqCode)) throw new Exception("请求编号不匹配，请人工核实操作结果");
            String message = json.optString("message", "");
            return new Result(status, code, message, reqCode, status >= 200 && status < 300 && "0".equals(code));
        } finally { conn.disconnect(); }
    }
    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream source = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096]; int n; int total = 0;
            while ((n = source.read(buf)) != -1) {
                total += n;
                if (total > 65536) throw new Exception("服务器响应过大");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}
