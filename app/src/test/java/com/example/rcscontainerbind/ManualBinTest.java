package com.example.rcscontainerbind;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ManualBinTest {
    private AppConfig config() {
        AppConfig c = AppConfig.testConfig();
        c.baseUrl = "http://192.0.2.10:8182";
        c.ctnrTyp = "2";
        c.defaultBin = "SHOULD_NOT_USE";
        c.defaultPosition = "SHOULD_NOT_USE";
        c.clientCode = "OLD_CLIENT";
        c.tokenCode = "OLD_TOKEN";
        return c;
    }
    @Test public void onlyManualBinAndRequiredTypeAreSent() throws Exception {
        RcsClient.Request r = RcsClient.prepareBin(config(), " 900005Tp501013 ", "1");
        JSONObject j = new JSONObject(r.body);
        assertEquals("2", j.getString("ctnrTyp"));
        assertEquals("900005Tp501013", j.getString("stgBinCode"));
        assertEquals("1", j.getString("indBind"));
        assertEquals(r.reqCode, j.getString("reqCode"));
        assertEquals(32, r.reqCode.length());
        assertFalse(j.has("ctnrCode"));
        assertFalse(j.has("positionCode"));
        assertFalse(j.has("clientCode"));
        assertFalse(j.has("tokenCode"));
        assertEquals("http://192.0.2.10:8182" + RcsClient.PATH, r.endpoint);
    }
    @Test public void unbindUsesSameConfiguredTypeButFreshRequest() throws Exception {
        AppConfig c = config();
        RcsClient.Request a = RcsClient.prepareBin(c, "B01", "1");
        RcsClient.Request b = RcsClient.prepareBin(c, "B02", "0");
        assertEquals("2", new JSONObject(b.body).getString("ctnrTyp"));
        assertEquals("B02", new JSONObject(b.body).getString("stgBinCode"));
        assertEquals("0", new JSONObject(b.body).getString("indBind"));
        assertNotEquals(a.reqCode, b.reqCode);
    }
    @Test public void missingTypeBinAndInvalidActionAreRejected() throws Exception {
        AppConfig c = config();
        try { RcsClient.prepareBin(c, "  ", "1"); fail(); } catch (Exception expected) { }
        try { RcsClient.prepareBin(c, "B01", "2"); fail(); } catch (Exception expected) { }
        c.ctnrTyp = "";
        try { RcsClient.prepareBin(c, "B01", "0"); fail(); } catch (Exception expected) { }
        c.ctnrTyp = "2";
        try { RcsClient.prepareBin(c, "123456789012345678901234567890123", "1"); fail(); } catch (Exception expected) { }
    }
}
