package com.hotel.system.audit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditControllerTest {
    @Test
    void exportsOnlyFilteredEventsAndNeutralizesSpreadsheetFormulas() {
        AuditService service = mock(AuditService.class);
        AuditLog event = new AuditLog("=formula-user", "+LOGIN_FAILED", "AUTHENTICATION",
                "guest", "@malicious detail, with comma");
        when(service.searchEvents("guest", "LOGIN", LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 8))).thenReturn(List.of(event));
        AuditController controller = new AuditController(service);

        var response = controller.exportAuditCsv("guest", "LOGIN",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);

        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("'=formula-user"));
        assertTrue(csv.contains("'+LOGIN_FAILED"));
        assertTrue(csv.contains("'@malicious detail, with comma"));
        verify(service).searchEvents("guest", "LOGIN",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8));
    }
}
