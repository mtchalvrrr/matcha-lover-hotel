package com.hotel.system.account;

import com.hotel.system.audit.AuditService;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPermissionServiceTest {
    private final AdminPermissionRepository repository = mock(AdminPermissionRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final AdminPermissionService service = new AdminPermissionService(repository, audit);

    @Test
    void permissionsAreEnabledByDefaultUntilSuperAdminChangesThem() {
        when(repository.findById("ROOM_MANAGE")).thenReturn(Optional.empty());
        assertTrue(service.isEnabled("ROOM_MANAGE"));
    }

    @Test
    void disablingPermissionPersistsSharedAdminSettingAndAuditEvent() {
        when(repository.findById("REPORT_VIEW")).thenReturn(Optional.empty());
        service.update("REPORT_VIEW", false, "superadmin");
        verify(repository).save(argThat(setting -> setting.getPermissionKey().equals("REPORT_VIEW")
                && !setting.isEnabled() && setting.getUpdatedBy().equals("superadmin")));
        verify(audit).record("superadmin", "ADMIN_PERMISSION_CHANGED", "ADMIN_PERMISSION",
                "REPORT_VIEW", "View reports changed from true to false. This applies equally to both Admin accounts.");
    }

    @Test
    void rejectsUnknownPermissionKeys() {
        assertThrows(IllegalArgumentException.class, () -> service.isEnabled("SUPER_ADMIN_ACCESS"));
    }
}