package com.example.rcscontainerbind;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class QrPayloadTest {
    @Test public void endpointIsNormalizedAndRestricted() throws Exception {
        assertEquals(
            "http://192.168.1.2:8182",
            AppConfig.endpointBase(
                "http://192.168.1.2:8182/rcms/services/rest/hikRpcService/cancelTask"));
        try {
            AppConfig.endpointBase("file:///tmp/test");
            fail();
        } catch (Exception expected) { }
        try {
            AppConfig.endpointBase("http://server:8182/other");
            fail();
        } catch (Exception expected) { }
    }

    @Test public void requestUsesTaskCodeAndConfiguredModeOnly() throws Exception {
        AppConfig c = AppConfig.testConfig();
        c.baseUrl = "http://192.168.1.2:8182";
        c.forceCancel = "0";

        RcsClient.Request request = RcsClient.prepareCancel(c, "TASK001");
        JSONObject body = new JSONObject(request.body);

        assertEquals("TASK001", body.getString("taskCode"));
        assertEquals("0", body.getString("forceCancel"));
        assertTrue(body.has("reqCode"));
        assertTrue(body.has("reqTime"));
        assertFalse(body.has("clientCode"));
        assertFalse(body.has("tokenCode"));
        assertFalse(body.has("agvCode"));
        assertFalse(body.has("matterArea"));
    }

    @Test public void invalidModeAndTaskAreRejected() throws Exception {
        AppConfig c = AppConfig.testConfig();
        c.baseUrl = "http://192.168.1.2:8182";
        c.forceCancel = "";
        try {
            RcsClient.prepareCancel(c, "TASK001");
            fail();
        } catch (Exception expected) { }

        c.forceCancel = "1";
        try {
            RcsClient.prepareCancel(c, "   ");
            fail();
        } catch (Exception expected) { }

        StringBuilder longCode = new StringBuilder();
        for (int i = 0; i < 65; i++) longCode.append('A');
        try {
            RcsClient.prepareCancel(c, longCode.toString());
            fail();
        } catch (Exception expected) { }
    }
}
