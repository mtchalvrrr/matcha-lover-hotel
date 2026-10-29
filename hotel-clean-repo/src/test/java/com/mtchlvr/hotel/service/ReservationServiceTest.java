package com.mtchlvr.hotel.service;

import com.mtchlvr.hotel.model.AppUser;
import com.mtchlvr.hotel.model.Reservation;
import com.mtchlvr.hotel.model.Room;
import com.mtchlvr.hotel.repo.ReservationRepository;
import com.mtchlvr.hotel.repo.RoomRepository;
import com.mtchlvr.hotel.repo.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReservationServiceTest {
    private ReservationRepository reservations;
    private RoomRepository rooms;
    private UserRepository users;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        reservations = mock(ReservationRepository.class);
        rooms = mock(RoomRepository.class);
        users = mock(UserRepository.class);
        service = new ReservationService(reservations, rooms, users);
    }

    @Test
    void rejectsCheckoutOnOrBeforeCheckin() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.reserve("guest@example.com", 1L, LocalDate.now().plusDays(1), LocalDate.now().plusDays(1)));
        assertTrue(error.getMessage().contains("later than check-in"));
        verifyNoInteractions(users, rooms, reservations);
    }

    @Test
    void rejectsCheckInTimeInThePast() {
        LocalDate yesterday = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).minusDays(1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.reserve("guest@example.com", 1L, yesterday, LocalTime.of(10, 0),
                yesterday, LocalTime.of(12, 0)));
        assertTrue(error.getMessage().contains("future check-in"));
        verifyNoInteractions(users, rooms, reservations);
    }

    @Test
    void acceptsSameDayReservationAndLeavesItPendingForManagerApproval() {
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).plusDays(1);
        AppUser customer = mock(AppUser.class);
        Room room = mock(Room.class);

        when(users.findByEmailIgnoreCase("guest@example.com"))
            .thenReturn(Optional.of(customer));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(room.getId()).thenReturn(1L);
        when(room.isAvailable()).thenReturn(true);
        when(room.isActive()).thenReturn(true);
        when(reservations.findAll()).thenReturn(List.of());
        when(reservations.save(any(Reservation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = service.reserve(
            "guest@example.com", 1L, today, LocalTime.of(10, 0),
            today, LocalTime.of(18, 0));

        assertEquals(today, result.getCheckIn());
        assertEquals(today, result.getCheckOut());
        assertEquals(LocalTime.of(10, 0), result.getCheckInTime());
        assertEquals(LocalTime.of(18, 0), result.getCheckOutTime());
        assertEquals("PENDING", result.getStatus());
        verify(reservations).save(any(Reservation.class));
    }

    @Test
    void rejectsApprovalWhenAnotherActiveReservationOverlaps() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7L);
        Reservation target = mock(Reservation.class);
        when(target.getId()).thenReturn(1L);
        when(target.getRoom()).thenReturn(room);
        when(target.getCheckIn()).thenReturn(LocalDate.now().plusDays(4));
        when(target.getCheckOut()).thenReturn(LocalDate.now().plusDays(8));
        Reservation overlap = mock(Reservation.class);
        when(overlap.getId()).thenReturn(2L);
        when(overlap.getRoom()).thenReturn(room);
        when(overlap.getStatus()).thenReturn("PENDING");
        when(overlap.getCheckIn()).thenReturn(LocalDate.now().plusDays(6));
        when(overlap.getCheckOut()).thenReturn(LocalDate.now().plusDays(10));
        when(reservations.findById(1L)).thenReturn(Optional.of(target));
        when(reservations.findAll()).thenReturn(List.of(target, overlap));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(1L, "APPROVED"));
        assertTrue(error.getMessage().contains("overlaps"));
        verify(reservations, never()).save(any());
    }

    @Test
    void acceptsValidStatusAndPersistsIt() {
        Reservation reservation = mock(Reservation.class);
        when(reservations.findById(9L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);
        assertSame(reservation, service.updateStatus(9L, "REJECTED"));
        verify(reservation).setStatus("REJECTED");
        verify(reservations).save(reservation);
    }


    @Test
    void keepingPendingCancellationRequestRestoresPendingStatus() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservation.getStatusBeforeCancellation()).thenReturn("PENDING");
        when(reservations.findById(11L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(11L, "APPROVED"));

        verify(reservation).setStatus("PENDING");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void keepingApprovedCancellationRequestRestoresApprovedStatus() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(7L);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(12L);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservation.getStatusBeforeCancellation()).thenReturn("APPROVED");
        when(reservation.getRoom()).thenReturn(room);
        when(reservation.getCheckIn()).thenReturn(LocalDate.now().plusDays(3));
        when(reservation.getCheckOut()).thenReturn(LocalDate.now().plusDays(4));
        when(reservations.findById(12L)).thenReturn(Optional.of(reservation));
        when(reservations.findAll()).thenReturn(List.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(12L, "APPROVED"));

        verify(reservation).setStatus("APPROVED");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void confirmingCancellationMarksRequestCancelledAndClearsPriorStatus() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getStatus()).thenReturn("CANCELLATION_REQUESTED");
        when(reservations.findById(13L)).thenReturn(Optional.of(reservation));
        when(reservations.save(reservation)).thenReturn(reservation);

        assertSame(reservation, service.updateStatus(13L, "CANCELLED"));

        verify(reservation).setStatus("CANCELLED");
        verify(reservation).setStatusBeforeCancellation(null);
        verify(reservations).save(reservation);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(9L, "PENDING"));
        verifyNoInteractions(reservations);
    }

    @Test
    void rejectsApprovalWhenReservationHasNoAssignedRoom() {
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(21L);
        when(reservation.getStatus()).thenReturn("PENDING");
        when(reservation.getRoom()).thenReturn(null);
        when(reservation.getCheckIn()).thenReturn(LocalDate.now().plusDays(2));
        when(reservation.getCheckOut()).thenReturn(LocalDate.now().plusDays(3));
        when(reservations.findById(21L)).thenReturn(Optional.of(reservation));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(21L, "APPROVED"));

        assertTrue(error.getMessage().contains("missing its room or stay dates"));
        verify(reservations, never()).save(any());
    }

    @Test
    void rejectsApprovalWhenCheckoutDoesNotFollowCheckin() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(8L);
        Reservation reservation = mock(Reservation.class);
        when(reservation.getId()).thenReturn(22L);
        when(reservation.getStatus()).thenReturn("PENDING");
        when(reservation.getRoom()).thenReturn(room);
        when(reservation.getCheckIn()).thenReturn(LocalDate.now().plusDays(2));
        when(reservation.getCheckOut()).thenReturn(LocalDate.now().plusDays(2));
        when(reservation.getCheckInTime()).thenReturn(LocalTime.of(14, 0));
        when(reservation.getCheckOutTime()).thenReturn(LocalTime.of(12, 0));
        when(reservations.findById(22L)).thenReturn(Optional.of(reservation));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> service.updateStatus(22L, "APPROVED"));

        assertTrue(error.getMessage().contains("check-out must be after check-in"));
        verify(reservations, never()).save(any());
    }

}
