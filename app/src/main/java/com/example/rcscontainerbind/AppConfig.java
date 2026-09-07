package com.example.rcscontainerbind;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/** Local configuration. No factory PIN or server credentials are embedded. */
public final class AppConfig {
    private static final String STORE = "rcs_config";
    private static final String KEY_ALIAS = "rcs_local_token_v1";
    private final SharedPreferences sp;
    public String baseUrl = "", clientCode = "", tokenCode = "";
    public String ctnrTyp = "", defaultBin = "", defaultPosition = "";
    public String qrMode = "auto";
    public String ctnrKey = "ctnrCode", typeKey = "ctnrTyp", binKey = "stgBinCode", positionKey = "positionCode";

    public AppConfig(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(STORE, Context.MODE_PRIVATE);
        baseUrl = sp.getString("baseUrl", "");
        clientCode = sp.getString("clientCode", "");
        ctnrTyp = sp.getString("ctnrTyp", sp.getString("ctnrTyp", ""));
        defaultBin = sp.getString("defaultBin", "");
        defaultPosition = sp.getString("defaultPosition", "");
        qrMode = sp.getString("qrMode", "auto");
        ctnrKey = sp.getString("ctnrKey", "ctnrCode");
        typeKey = sp.getString("typeKey", "ctnrTyp");
        binKey = sp.getString("binKey", "stgBinCode");
        positionKey = sp.getString("positionKey", "positionCode");
        String encrypted = sp.getString("tokenEncrypted", "");
        if (!encrypted.isEmpty()) {
            try { tokenCode = decrypt(encrypted); }
            catch (Exception e) { tokenCode = ""; }
        } else if (sp.contains("tokenCode")) {
            tokenCode = sp.getString("tokenCode", "");
        }
    }

    public void save() throws Exception {
        validate();
        String encrypted = tokenCode.isEmpty() ? "" : encrypt(tokenCode);
        boolean ok = sp.edit().putString("baseUrl", baseUrl).putString("clientCode", clientCode)
            .putString("ctnrTyp", ctnrTyp).putString("defaultBin", defaultBin)
            .putString("defaultPosition", defaultPosition).putString("qrMode", qrMode)
            .putString("ctnrKey", ctnrKey).putString("typeKey", typeKey)
            .putString("binKey", binKey).putString("positionKey", positionKey)
            .putString("tokenEncrypted", encrypted).remove("tokenCode").commit();
        if (!ok) throw new Exception("设置保存失败");
    }

    public void validate() throws Exception {
        baseUrl = endpointBase(baseUrl);
        clientCode = clean(clientCode); tokenCode = clean(tokenCode);
        ctnrTyp = clean(ctnrTyp); defaultBin = clean(defaultBin); defaultPosition = clean(defaultPosition);
        if (clientCode.length() > 16 || tokenCode.length() > 64 || ctnrTyp.length() > 16 || defaultBin.length() > 32 || defaultPosition.length() > 32)
            throw new Exception("配置字段超过接口长度限制");
        if (!Arrays.asList("auto", "container", "bin", "position").contains(qrMode)) throw new Exception("二维码模式无效");
        if (ctnrKey.trim().isEmpty() || typeKey.trim().isEmpty() || binKey.trim().isEmpty() || positionKey.trim().isEmpty())
            throw new Exception("二维码字段名不能为空");
    }

    public static String endpointBase(String value) throws Exception {
        String s = clean(value).replaceAll("/+$", "");
        if (s.isEmpty()) return "";
        URI uri = new URI(s);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
            throw new Exception("请输入 http:// 或 https:// 开头的有效 RCS 地址");
        String path = uri.getPath();
        String service = "/rcms/services/rest/hikRpcService";
        if (path != null && path.endsWith(service + "/bindCtnrAndBin")) s = s.substring(0, s.length() - "/bindCtnrAndBin".length());
        if (s.endsWith(service)) s = s.substring(0, s.length() - service.length());
        if (!new URI(s).getPath().isEmpty() && !"/".equals(new URI(s).getPath())) throw new Exception("RCS 地址只需填写协议、IP 和端口");
        return s.replaceAll("/+$", "");
    }

    public static String clean(String s) { return s == null ? "" : s.trim(); }

    public boolean hasPin() { return sp.contains("adminPinHash"); }
    public void setPin(String pin) throws Exception {
        if (!pin.matches("[0-9]{6,12}")) throw new Exception("管理员密码需为6至12位数字");
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        byte[] hash = pinHash(pin, salt);
        if (!sp.edit().putString("adminPinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("adminPinHash", Base64.encodeToString(hash, Base64.NO_WRAP)).commit()) throw new Exception("密码保存失败");
    }
    public boolean verifyPin(String pin) {
        try {
            byte[] salt = Base64.decode(sp.getString("adminPinSalt", ""), Base64.DEFAULT);
            byte[] expected = Base64.decode(sp.getString("adminPinHash", ""), Base64.DEFAULT);
            return MessageDigest.isEqual(expected, pinHash(pin, salt));
        } catch (Exception e) { return false; }
    }
    private static byte[] pinHash(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, 120000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry)ks.getEntry(KEY_ALIAS, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    private String encrypt(String s) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key());
        return Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(s.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
    }
    private String decrypt(String s) throws Exception {
        String[] parts = s.split(":", 2);
        if (parts.length != 2) throw new Exception("密文损坏");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT)));
        return new String(c.doFinal(Base64.decode(parts[1], Base64.DEFAULT)), StandardCharsets.UTF_8);
    }
}
