package com.courier.service;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.courier.entity.Region;
import com.courier.entity.Shipment;
import com.courier.repository.RegionRepository;
import com.courier.repository.ShipmentRepository;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {
    @Mock
    private ShipmentRepository repo;

    @Mock
    private RegionRepository regionRepository;

    @InjectMocks
    private ShipmentService service;

    @Test
    void verifiesDeliveredToPayShipmentOnlyAfterExplicitDestinationConfirmation() {
        Shipment shipment = shipment(" tO_pAy ", " delivered ", null);
        when(repo.findByTrackingNumber("DEMO-SRI-TOPAY-006")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        Shipment verified = service.verifyDestinationPayment(
            "DEMO-SRI-TOPAY-006", " TEMI-THIRUPARENKUNDRAM ", 2.4, 375.0);

        assertSame(shipment, verified);
        assertEquals(2.4, shipment.getWeight());
        assertEquals(375.0, shipment.getShipmentAmount());
        assertTrue(shipment.getDestinationPaymentVerified());
        assertEquals(" delivered ", shipment.getStatus());
        verify(repo).save(shipment);
    }

    @Test
    void verifiesReceivedToPayShipmentWithAnExplicitFalseFlag() {
        Shipment shipment = shipment("TO_PAY", "RECEIVED", false);
        when(repo.findByTrackingNumber("received")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        service.verifyDestinationPayment("received", "Temi-Thiruparenkundram", 2.4, 375.0);

        assertTrue(shipment.getDestinationPaymentVerified());
        verify(repo).save(shipment);
    }

    @Test
    void rejectsDestinationPaymentVerificationFromAnotherRegion() {
        Shipment shipment = shipment("TO_PAY", "DELIVERED", null);
        when(repo.findByTrackingNumber("DEMO-SRI-TOPAY-006")).thenReturn(Optional.of(shipment));

        assertThrows(IllegalStateException.class, () -> service.verifyDestinationPayment(
            "DEMO-SRI-TOPAY-006", "Temi-Srirangam", 2.4, 375.0));
        verify(repo, never()).save(any());
    }

    @Test
    void doesNotVerifyPaidOrUnreceivedShipments() {
        Shipment paid = shipment("PAID", "DELIVERED", null);
        Shipment unreceived = shipment("TO_PAY", "IN_TRANSIT", null);
        when(repo.findByTrackingNumber("paid")).thenReturn(Optional.of(paid));
        when(repo.findByTrackingNumber("unreceived")).thenReturn(Optional.of(unreceived));

        assertThrows(IllegalStateException.class,
            () -> service.verifyDestinationPayment("paid", "Temi-Thiruparenkundram", 2.4, 375.0));
        assertThrows(IllegalStateException.class,
            () -> service.verifyDestinationPayment("unreceived", "Temi-Thiruparenkundram", 2.4, 375.0));
        verify(repo, never()).save(any());
    }

    @Test
    void requiresValidWeightAndAmountAndRejectsAlreadyVerifiedShipments() {
        Shipment shipment = shipment("TO_PAY", "DELIVERED", null);
        when(repo.findByTrackingNumber("invalid")).thenReturn(Optional.of(shipment));
        when(repo.findByTrackingNumber("verified"))
            .thenReturn(Optional.of(shipment("TO_PAY", "DELIVERED", true)));

        assertThrows(IllegalArgumentException.class,
            () -> service.verifyDestinationPayment("invalid", "Temi-Thiruparenkundram", 0.0, 375.0));
        assertThrows(IllegalArgumentException.class,
            () -> service.verifyDestinationPayment("invalid", "Temi-Thiruparenkundram", 2.4, -1.0));
        assertThrows(IllegalArgumentException.class,
            () -> service.verifyDestinationPayment("invalid", "Temi-Thiruparenkundram", Double.NaN, 375.0));
        assertThrows(IllegalStateException.class,
            () -> service.verifyDestinationPayment("verified", "Temi-Thiruparenkundram", 2.4, 375.0));
        verify(repo, never()).save(any());
    }

    @Test
    void marksDispatchedShipmentReturnedWithoutChangingItsTrackingOrRoute() {
        Shipment original = shipment("PAID", "RECEIVED", null);
        original.setTrackingNumber("ORIGINAL-1");
        original.setSourceRegion("Origin Region");
        original.setDestinationRegion("Receiving Region");
        original.setSenderName("Original Sender");
        original.setReceiverName("Original Receiver");
        original.setPackageDescription("Original parcel");
        original.setWeight(2.5);
        original.setShipmentAmount(420.0);
        when(repo.findByTrackingNumber("ORIGINAL-1")).thenReturn(Optional.of(original));
        when(repo.save(any(Shipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Shipment returned = service.createReturn("ORIGINAL-1", "Receiving Region", "Recipient declined");

        assertEquals("ORIGINAL-1", returned.getTrackingNumber());
        assertEquals("Origin Region", returned.getSourceRegion());
        assertEquals("Receiving Region", returned.getDestinationRegion());
        assertEquals("Original Sender", returned.getSenderName());
        assertEquals("Original Receiver", returned.getReceiverName());
        assertEquals("RETURNED", returned.getStatus());
        assertEquals("Recipient declined", returned.getReturnReason());
        assertNull(returned.getReturnOfTrackingNumber());
        verify(repo).save(original);
        assertThrows(IllegalStateException.class,
            () -> service.createReturn("ORIGINAL-1", "Receiving Region", "Another attempt"));
    }

    @Test
    void dispatchesReturnedShipmentFromReturnRegionThenReceivesItAtOriginalSource() {
        Shipment returned = shipment("PAID", "RETURNED", null);
        returned.setTrackingNumber("RETURN-1");
        returned.setSourceRegion("Origin Region");
        returned.setDestinationRegion("Receiving Region");
        returned.setReturnReason("Recipient declined");
        when(repo.findByTrackingNumber("RETURN-1")).thenReturn(Optional.of(returned));
        when(repo.save(returned)).thenReturn(returned);

        assertThrows(IllegalStateException.class,
            () -> service.status("RETURN-1", "RECEIVED", "Origin Region"));
        assertEquals("RETURNED", returned.getStatus());
        assertThrows(IllegalStateException.class,
            () -> service.status("RETURN-1", "RETURN_RECEIVED", "Origin Region"));
        assertEquals("RETURNED", returned.getStatus());

        Shipment inTransit = service.status("RETURN-1", "IN_TRANSIT", "Receiving Region");
        assertSame(returned, inTransit);
        assertEquals("IN_TRANSIT", inTransit.getStatus());

        Shipment returnReceived = service.status("RETURN-1", "RETURN_RECEIVED", "Origin Region");
        assertSame(returned, returnReceived);
        assertEquals("RETURN_RECEIVED", returnReceived.getStatus());
        assertEquals("Origin Region", returnReceived.getSourceRegion());
        assertEquals("Receiving Region", returnReceived.getDestinationRegion());
        verify(repo, org.mockito.Mockito.times(2)).save(returned);
    }

    @Test
    void onlyOriginalSourceCanReceiveAnInTransitReturn() {
        Shipment returned = shipment("PAID", "RETURNED", null);
        returned.setTrackingNumber("RETURN-2");
        returned.setSourceRegion("Origin Region");
        returned.setDestinationRegion("Receiving Region");
        returned.setReturnReason("Recipient declined");
        when(repo.findByTrackingNumber("RETURN-2")).thenReturn(Optional.of(returned));

        assertThrows(IllegalStateException.class,
            () -> service.status("RETURN-2", "IN_TRANSIT", "Origin Region"));
        assertEquals("RETURNED", returned.getStatus());
        returned.setStatus("IN_TRANSIT");
        assertThrows(IllegalStateException.class,
            () -> service.status("RETURN-2", "RETURN_RECEIVED", "Receiving Region"));
        assertEquals("IN_TRANSIT", returned.getStatus());
        assertThrows(IllegalStateException.class,
            () -> service.status("RETURN-2", "IN_TRANSIT", "Origin Region"));
        assertEquals("IN_TRANSIT", returned.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void ordinaryShipmentCannotBeMarkedReturnDispatched() {
        Shipment ordinary = shipment("PAID", "IN_TRANSIT", null);
        ordinary.setSourceRegion("Origin Region");
        ordinary.setDestinationRegion("Receiving Region");
        when(repo.findByTrackingNumber("ORDINARY-1")).thenReturn(Optional.of(ordinary));

        assertThrows(IllegalStateException.class,
            () -> service.status("ORDINARY-1", "RETURN_DISPATCHED", "Receiving Region"));
        assertThrows(IllegalStateException.class,
            () -> service.status("ORDINARY-1", "RETURN_RECEIVED", "Receiving Region"));
        assertEquals("IN_TRANSIT", ordinary.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsReturnWhenShipmentIsNotDispatchedToCurrentRegionOrAlreadyReturned() {
        Shipment wrongRegion = shipment("PAID", "RECEIVED", null);
        wrongRegion.setSourceRegion("Origin Region");
        wrongRegion.setDestinationRegion("Another Region");
        Shipment notDispatched = shipment("PAID", "VERIFIED", null);
        notDispatched.setSourceRegion("Origin Region");
        notDispatched.setDestinationRegion("Receiving Region");
        Shipment inTransit = shipment("PAID", "IN_TRANSIT", null);
        inTransit.setSourceRegion("Origin Region");
        inTransit.setDestinationRegion("Receiving Region");
        when(repo.findByTrackingNumber("WRONG-REGION")).thenReturn(Optional.of(wrongRegion));
        when(repo.findByTrackingNumber("NOT-DISPATCHED")).thenReturn(Optional.of(notDispatched));
        when(repo.findByTrackingNumber("IN-TRANSIT")).thenReturn(Optional.of(inTransit));

        assertThrows(IllegalStateException.class,
            () -> service.createReturn("WRONG-REGION", "Receiving Region", "Reason"));
        assertThrows(IllegalStateException.class,
            () -> service.createReturn("NOT-DISPATCHED", "Receiving Region", "Reason"));
        assertThrows(IllegalStateException.class,
            () -> service.createReturn("IN-TRANSIT", "Receiving Region", "Reason"));
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsDuplicateReturnAndBlankReason() {
        Shipment original = shipment("PAID", "RECEIVED", null);
        original.setSourceRegion("Origin Region");
        original.setDestinationRegion("Receiving Region");
        when(repo.findByTrackingNumber("ORIGINAL-2")).thenReturn(Optional.of(original));
        when(repo.existsByReturnOfTrackingNumberIgnoreCase("ORIGINAL-2")).thenReturn(true);

        assertThrows(IllegalStateException.class,
            () -> service.createReturn("ORIGINAL-2", "Receiving Region", "Reason"));
        assertThrows(IllegalArgumentException.class,
            () -> service.createReturn("ORIGINAL-2", "Receiving Region", " "));
        verify(repo, never()).save(any());
    }

    @Test
    void allowsSourceRegionToEditVerifiedSendShipmentWithoutChangingRouteOrEntryDate() {
        Shipment shipment = shipment("PAID", "VERIFIED", null);
        shipment.setTrackingNumber("SEND-EDIT-1");
        shipment.setSourceRegion("Origin Region");
        shipment.setDestinationRegion("Receiving Region");
        LocalDateTime entryDate = LocalDateTime.of(2026, 9, 29, 10, 0);
        shipment.setShipmentDate(entryDate);
        Shipment input = validShipment();
        input.setSenderName("Updated Sender");
        input.setSourceRegion("Changed Region");
        input.setDestinationRegion("Changed Destination");
        input.setShipmentDate(entryDate.plusDays(1));
        input.setExportDate(entryDate.plusDays(2));
        when(repo.findByTrackingNumber("SEND-EDIT-1")).thenReturn(Optional.of(shipment));
        when(repo.save(shipment)).thenReturn(shipment);

        Shipment updated = service.updateUnverified("SEND-EDIT-1", input, "Origin Region");

        assertSame(shipment, updated);
        assertEquals("Updated Sender", shipment.getSenderName());
        assertEquals("Origin Region", shipment.getSourceRegion());
        assertEquals("Receiving Region", shipment.getDestinationRegion());
        assertEquals(entryDate, shipment.getShipmentDate());
        assertEquals(entryDate.plusDays(2), shipment.getExportDate());
        verify(repo).save(shipment);
    }

    @Test
    void rejectsSendShipmentEditFromAnotherRegion() {
        Shipment shipment = shipment("PAID", "IN_TRANSIT", null);
        shipment.setSourceRegion("Origin Region");
        when(repo.findByTrackingNumber("SEND-EDIT-2")).thenReturn(Optional.of(shipment));

        assertThrows(IllegalStateException.class,
            () -> service.updateUnverified("SEND-EDIT-2", new Shipment(), "Receiving Region"));
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsSendShipmentEditWhenStatusIsNotVerified() {
        Shipment shipment = shipment("PAID", "IN_TRANSIT", null);
        shipment.setSourceRegion("Origin Region");
        when(repo.findByTrackingNumber("SEND-EDIT-3")).thenReturn(Optional.of(shipment));

        assertThrows(IllegalStateException.class,
            () -> service.updateUnverified("SEND-EDIT-3", new Shipment(), "Origin Region"));
        verify(repo, never()).save(any());
    }

    @Test
    void rejectsShipmentCreationWithInvalidPhonesOrMissingAddresses() {
        Shipment invalidPhone = validShipment();
        invalidPhone.setSenderPhone("12345");
        Shipment missingSenderAddress = validShipment();
        missingSenderAddress.setSenderAddress(" ");
        Shipment missingReceiverAddress = validShipment();
        missingReceiverAddress.setReceiverAddress(null);
        Shipment invalidCategory = validShipment();
        invalidCategory.setShipmentCategory("Other");

        assertThrows(IllegalArgumentException.class,
            () -> service.create(invalidPhone, false, "Origin Region"));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(missingSenderAddress, false, "Origin Region"));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(missingReceiverAddress, false, "Origin Region"));
        assertThrows(IllegalArgumentException.class,
            () -> service.create(invalidCategory, false, "Origin Region"));
        verify(repo, never()).save(any());
    }

    @Test
    void persistsSelectedShipmentCategoryOnCreate() {
        Shipment shipment = validShipment();
        shipment.setShipmentCategory("Oil Tins");
        when(regionRepository.findByNameIgnoreCase("Origin Region"))
            .thenReturn(Optional.of(region("Origin Region", "TPK", "DISTRICT", null)));
        when(regionRepository.findByNameIgnoreCase("Receiving Region"))
            .thenReturn(Optional.of(region("Receiving Region", "SRI", "DISTRICT", null)));
        when(repo.saveAndFlush(shipment)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(shipment, "id", 42L);
            return shipment;
        });
        when(repo.save(shipment)).thenReturn(shipment);

        Shipment created = service.create(shipment, false, "Origin Region");

        assertEquals("Oil Tins", created.getShipmentCategory());
        assertEquals("TPK-0000042-SRI", created.getTrackingNumber());
        verify(repo).saveAndFlush(shipment);
        verify(repo).save(shipment);
    }

    @Test
    void usesParentDistrictCodesForRegionalOffices() {
        Shipment shipment = validShipment();
        Region sourceOffice = region("Origin Region", "OFFICE", "REGION", "Origin District");
        Region destinationOffice = region("Receiving Region", "OFFICE", "REGION", "Receiving District");
        when(regionRepository.findByNameIgnoreCase("Origin Region")).thenReturn(Optional.of(sourceOffice));
        when(regionRepository.findByNameIgnoreCase("Origin District"))
            .thenReturn(Optional.of(region("Origin District", "TPK", "DISTRICT", null)));
        when(regionRepository.findByNameIgnoreCase("Receiving Region")).thenReturn(Optional.of(destinationOffice));
        when(regionRepository.findByNameIgnoreCase("Receiving District"))
            .thenReturn(Optional.of(region("Receiving District", "SRI", "DISTRICT", null)));
        when(repo.saveAndFlush(shipment)).thenAnswer(invocation -> {
            ReflectionTestUtils.setField(shipment, "id", 43L);
            return shipment;
        });
        when(repo.save(shipment)).thenReturn(shipment);

        service.create(shipment, false, "Origin Region");

        assertEquals("TPK-0000043-SRI", shipment.getTrackingNumber());
    }

    @Test
    void rejectsShipmentCreationWhenDistrictCodeIsNotConfigured() {
        Shipment shipment = validShipment();
        when(regionRepository.findByNameIgnoreCase("Origin Region"))
            .thenReturn(Optional.of(region("Origin Region", null, "DISTRICT", null)));

        assertThrows(IllegalArgumentException.class,
            () -> service.create(shipment, false, "Origin Region"));
        verify(repo, never()).save(any());
    }

    private Region region(String name, String code, String accountType, String parentDistrict) {
        Region region = new Region();
        region.setName(name);
        region.setCode(code);
        region.setAccountType(accountType);
        region.setParentDistrict(parentDistrict);
        return region;
    }

    private Shipment validShipment() {
        Shipment shipment = new Shipment();
        shipment.setShipmentCategory("Carton Boxes");
        shipment.setSenderName("Sender");
        shipment.setSenderPhone("9876543210");
        shipment.setSenderAddress("Sender address");
        shipment.setReceiverName("Receiver");
        shipment.setReceiverPhone("9123456780");
        shipment.setReceiverAddress("Receiver address");
        shipment.setSourceRegion("Origin Region");
        shipment.setDestinationRegion("Receiving Region");
        return shipment;
    }

    private Shipment shipment(String paymentType, String status, Boolean destinationPaymentVerified) {
        Shipment shipment = new Shipment();
        shipment.setPaymentType(paymentType);
        shipment.setStatus(status);
        shipment.setDestinationRegion("Temi-Thiruparenkundram");
        shipment.setDestinationPaymentVerified(destinationPaymentVerified);
        return shipment;
    }
}
