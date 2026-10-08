package com.example.rcscontainerbind;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class AppConfig {
    private static final String STORE = "rcs_cancel_config";
    private final SharedPreferences sp;

    public String baseUrl = "";
    public String forceCancel = "";

    AppConfig() { sp = null; }
    static AppConfig testConfig() { return new AppConfig(); }

    public AppConfig(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
        baseUrl = sp.getString("baseUrl", "");
        forceCancel = sp.getString("forceCancel", "");
    }

    public void save() throws Exception {
        validate();
        if (sp == null) return;
        boolean ok = sp.edit()
            .putString("baseUrl", baseUrl)
            .putString("forceCancel", forceCancel)
            .commit();
        if (!ok) throw new Exception("设置保存失败");
    }

    public void validate() throws Exception {
        baseUrl = endpointBase(baseUrl);
        if (baseUrl.isEmpty()) throw new Exception("请填写 RCS 服务器地址");
        forceCancel = clean(forceCancel);
        if (!"0".equals(forceCancel) && !"1".equals(forceCancel))
            throw new Exception("请选择取消方式");
    }

    public static String endpointBase(String value) throws Exception {
        String s = clean(value).replaceAll("/+$", "");
        if (s.isEmpty()) return "";

        URI uri = new URI(s);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
            || uri.getHost() == null || uri.getUserInfo() != null
            || uri.getQuery() != null || uri.getFragment() != null) {
            throw new Exception("请输入 http:// 或 https:// 开头的有效 RCS 地址");
        }

        String service = "/rcms/services/rest/hikRpcService";
        String path = uri.getPath();
        if (path != null && path.endsWith(service + "/cancelTask"))
            s = s.substring(0, s.length() - "/cancelTask".length());
        if (s.endsWith(service))
            s = s.substring(0, s.length() - service.length());

        String remaining = new URI(s).getPath();
        if (remaining != null && !remaining.isEmpty() && !"/".equals(remaining))
            throw new Exception("RCS 地址只需填写协议、IP 和端口");

        return s.replaceAll("/+$", "");
    }

    public static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    public boolean hasPin() {
        return sp != null && sp.contains("adminPinHash");
    }

    public void setPin(String pin) throws Exception {
        if (sp == null) throw new Exception("当前环境不能保存密码");
        if (!pin.matches("[0-9]{6,12}"))
            throw new Exception("管理员密码需为6至12位数字");

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] hash = pinHash(pin, salt);

        boolean ok = sp.edit()
            .putString("adminPinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("adminPinHash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .commit();
        if (!ok) throw new Exception("密码保存失败");
    }

    public boolean verifyPin(String pin) {
        if (sp == null) return false;
        try {
            byte[] salt = Base64.decode(sp.getString("adminPinSalt", ""), Base64.DEFAULT);
            byte[] expected = Base64.decode(sp.getString("adminPinHash", ""), Base64.DEFAULT);
            return MessageDigest.isEqual(expected, pinHash(pin, salt));
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] pinHash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
