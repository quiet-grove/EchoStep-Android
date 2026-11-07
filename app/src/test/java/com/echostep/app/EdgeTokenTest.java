package com.echostep.app;

import static org.junit.Assert.assertEquals;

import com.echostep.app.net.EdgeToken;

import org.junit.Test;

public class EdgeTokenTest {
    @Test
    public void matchesEdgeTtsReferenceTokens() {
        assertEquals("42EE6B1BB94E01AD-AE42E3ED8BC0BE6D74B75320DBA69F5B28A9508CD9D90E5EE951A0F389ED5E76",
                EdgeToken.generate(1761900000L));
        assertEquals("42EE6B1BB94E01AD-AE42E3ED8BC0BE6D74B75320DBA69F5B28A9508CD9D90E5EE951A0F389ED5E76",
                EdgeToken.generate(1761900123L));
        assertEquals("2D95CD894775C088-4376760ED3C180AC9C614C7874A5D10A8483FACBDF50EA0AEA8FBC05AE162F48",
                EdgeToken.generate(1761900300L));
    }
}
