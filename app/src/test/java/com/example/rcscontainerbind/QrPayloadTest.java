package com.example.rcscontainerbind;

import org.junit.Test;
import static org.junit.Assert.*;

public class QrPayloadTest {
    private AppConfig config() {
        // Parser tests use a configuration fixture without Android storage.
        return AppConfig.testConfig();
    }
    @Test public void structuredFieldsAreNotGuessed() throws Exception {
        AppConfig c = config(); c.ctnrTyp = "C1";
        QrPayload q = QrPayload.parse("{\"ctnrCode\":\"BOX001\",\"stgBinCode\":\"B01\"}", c);
        assertEquals("BOX001", q.ctnrCode); assertEquals("B01", q.stgBinCode);
        assertEquals("", q.positionCode); assertEquals("C1", q.ctnrTyp);
    }
    @Test public void plainRackCodeUsesConfiguredMode() throws Exception {
        AppConfig c = config(); c.ctnrTyp = "C1"; c.qrMode = "position";
        QrPayload q = QrPayload.parse("P001", c);
        assertEquals("P001", q.positionCode); assertEquals("", q.ctnrCode);
    }
    @Test public void customKeysAndUtf8AreSupported() throws Exception {
        AppConfig c = config(); c.ctnrTyp = "C1"; c.ctnrKey = "box"; c.binKey = "bin";
        QrPayload q = QrPayload.parse("box=%E6%96%99%E7%AE%B101&bin=B02", c);
        assertEquals("料箱01", q.ctnrCode); assertEquals("B02", q.stgBinCode);
    }
    @Test public void malformedOrIncompleteQrIsRejected() throws Exception {
        AppConfig c = config(); c.ctnrTyp = "C1";
        try { QrPayload.parse("{bad", c); fail(); } catch (Exception expected) { }
        QrPayload q = QrPayload.parse("BOX001", c);
        try { q.validate(true); fail(); } catch (Exception expected) { }
    }
    @Test public void endpointIsNormalizedAndRestricted() throws Exception {
        assertEquals("http://192.168.1.2:8182", AppConfig.endpointBase("http://192.168.1.2:8182/rcms/services/rest/hikRpcService/bindCtnrAndBin"));
        try { AppConfig.endpointBase("file:///tmp/test"); fail(); } catch (Exception expected) { }
        try { AppConfig.endpointBase("http://server:8182/other"); fail(); } catch (Exception expected) { }
    }
}
