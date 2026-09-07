package com.example.rcscontainerbind;

import org.json.JSONObject;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Decode a QR without inventing a relationship between a rack, bin and container. */
public final class QrPayload {
    public final String raw, ctnrCode, ctnrTyp, stgBinCode, positionCode;
    private QrPayload(String raw, String code, String typ, String bin, String position) {
        this.raw = raw; this.ctnrCode = code; this.ctnrTyp = typ;
        this.stgBinCode = bin; this.positionCode = position;
    }
    public static QrPayload parse(String input, AppConfig cfg) throws Exception {
        String raw = AppConfig.clean(input);
        if (raw.isEmpty() || raw.length() > 4096) throw new Exception("二维码为空或内容过长");
        String code = "", typ = cfg.ctnrTyp, bin = cfg.defaultBin, pos = cfg.defaultPosition;
        Map<String, String> values = new HashMap<>();
        boolean structured = false;
        if (raw.startsWith("{")) {
            JSONObject json = new JSONObject(raw);
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!json.isNull(key)) values.put(key, json.optString(key, ""));
            }
            structured = true;
        } else if (raw.contains("=")) {
            String query = raw;
            int q = query.indexOf('?');
            if (q >= 0) query = query.substring(q + 1);
            for (String part : query.split("&")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) values.put(URLDecoder.decode(kv[0], "UTF-8"), URLDecoder.decode(kv[1], "UTF-8"));
            }
            structured = true;
        }
        if (structured) {
            code = get(values, cfg.ctnrKey, "ctnrCode");
            typ = first(get(values, cfg.typeKey, "ctnrTyp", "ctnrType"), typ);
            bin = first(get(values, cfg.binKey, "stgBinCode"), bin);
            pos = first(get(values, cfg.positionKey, "positionCode"), pos);
            if (code.isEmpty() && bin.isEmpty() && pos.isEmpty()) throw new Exception("二维码没有可识别的容器或仓位字段，请检查管理员字段映射");
        } else {
            if (raw.startsWith("http://") || raw.startsWith("https://")) throw new Exception("二维码是网址，请配置与实际标签匹配的解析规则");
            switch (cfg.qrMode) {
                case "container": case "auto": code = raw; break;
                case "bin": bin = raw; break;
                case "position": pos = raw; break;
                default: throw new Exception("二维码模式无效");
            }
        }
        code = AppConfig.clean(code); typ = AppConfig.clean(typ);
        bin = AppConfig.clean(bin); pos = AppConfig.clean(pos);
        if (code.length() > 30 || typ.length() > 16 || bin.length() > 32 || pos.length() > 32)
            throw new Exception("二维码字段超过 RCS 接口长度限制");
        return new QrPayload(raw, code, typ, bin, pos);
    }
    private static String first(String a, String b) { return a.isEmpty() ? b : a; }
    private static String get(Map<String, String> map, String... keys) {
        for (String k : keys) {
            String v = map.get(k);
            if (v != null) return AppConfig.clean(v);
        }
        return "";
    }
    public void validate(boolean binding) throws Exception {
        if (ctnrTyp.isEmpty()) throw new Exception("缺少容器类型 ctnrTyp，请让管理员配置");
        if (stgBinCode.isEmpty() && positionCode.isEmpty()) throw new Exception("缺少仓位或地图位置，不能仅凭容器号推断仓位");
        if (!binding && ctnrCode.isEmpty() && positionCode.isEmpty()) {
            // A specific bin and type are allowed by the documented interface.
            if (stgBinCode.isEmpty()) throw new Exception("解绑缺少仓位信息");
        }
    }
    public String summary() {
        return "容器编号：" + display(ctnrCode) + "\n容器类型：" + display(ctnrTyp)
            + "\n仓位编号：" + display(stgBinCode) + "\n地图位置：" + display(positionCode);
    }
    private static String display(String s) { return s.isEmpty() ? "（未提供）" : s; }
}
