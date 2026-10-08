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

public final class RcsClient {
    public static final String PATH = "/rcms/services/rest/hikRpcService/cancelTask";

    private RcsClient() { }

    public static final class Request {
        public final String endpoint, body, reqCode, taskCode, forceCancel;

        private Request(String endpoint, String body, String reqCode,
                        String taskCode, String forceCancel) {
            this.endpoint = endpoint;
            this.body = body;
            this.reqCode = reqCode;
            this.taskCode = taskCode;
            this.forceCancel = forceCancel;
        }
    }

    public static Request prepareCancel(AppConfig config, String taskCode) throws Exception {
        String base = AppConfig.endpointBase(config.baseUrl);
        if (base.isEmpty()) throw new Exception("请先由管理员配置 RCS 地址");

        String mode = AppConfig.clean(config.forceCancel);
        if (!"0".equals(mode) && !"1".equals(mode))
            throw new Exception("请先由管理员配置取消方式");

        String task = AppConfig.clean(taskCode);
        if (task.isEmpty()) throw new Exception("请输入任务单号");
        if (task.length() > 64) throw new Exception("任务单号不能超过64个字符");
        if (task.contains("\n") || task.contains("\r"))
            throw new Exception("任务单号不能包含换行");

        String reqCode = UUID.randomUUID().toString().replace("-", "");
        JSONObject body = new JSONObject();
        body.put("reqCode", reqCode);
        body.put("reqTime",
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
        body.put("forceCancel", mode);
        body.put("taskCode", task);

        return new Request(base + PATH, body.toString(), reqCode, task, mode);
    }

    public static final class Result {
        public final int httpStatus;
        public final String code, message, reqCode;
        public final boolean success;

        private Result(int status, String code, String message,
                       String reqCode, boolean success) {
            this.httpStatus = status;
            this.code = code;
            this.message = message;
            this.reqCode = reqCode;
            this.success = success;
        }
    }

    public static Result execute(Request request) throws Exception {
        HttpURLConnection conn =
            (HttpURLConnection) new URL(request.endpoint).openConnection();
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

            try (OutputStream out = conn.getOutputStream()) {
                out.write(bytes);
            }

            int status = conn.getResponseCode();
            InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String text = read(in);

            JSONObject json;
            try {
                json = new JSONObject(text);
            } catch (Exception ex) {
                throw new Exception("服务器返回非 JSON（HTTP " + status + "），请检查地址和 RCS 服务");
            }

            if (!json.has("code") || !json.has("reqCode"))
                throw new Exception("RCS 响应缺少 code 或 reqCode");

            String code = json.optString("code", "");
            String reqCode = json.optString("reqCode", "");
            String message = json.optString("message", "");

            if (!request.reqCode.equals(reqCode))
                throw new Exception("请求编号不匹配，请人工核实任务状态");

            boolean success = status >= 200 && status < 300 && "0".equals(code);
            return new Result(status, code, message, reqCode, success);
        } finally {
            conn.disconnect();
        }
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream source = in;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            int total = 0;
            while ((n = source.read(buf)) != -1) {
                total += n;
                if (total > 65536) throw new Exception("服务器响应过大");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }
}
