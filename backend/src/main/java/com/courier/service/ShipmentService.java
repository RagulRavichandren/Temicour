package com.courier.service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.courier.entity.Shipment;
import com.courier.entity.Region;
import com.courier.repository.RegionRepository;
import com.courier.repository.ShipmentRepository;
@Service
public class ShipmentService {
 private static final Set<String> SHIPMENT_CATEGORIES=Set.of("Carton Boxes","Gunny Bags","Oil Tins","Trays Or Cases","Bundles");
 private final ShipmentRepository repo;
 private final RegionRepository regionRepository;
 public ShipmentService(ShipmentRepository repo, RegionRepository regionRepository){this.repo=repo;this.regionRepository=regionRepository;}
 public List<Shipment> all(String operatingRegion){
   if (operatingRegion == null || operatingRegion.isBlank() || "ALL".equalsIgnoreCase(operatingRegion.trim())) {
     return repo.findAll();
   }
   return repo.findBySourceRegionIgnoreCaseOrDestinationRegionIgnoreCase(
       operatingRegion.trim(), operatingRegion.trim());
 }
 public List<Shipment> customerShipments(String username) {
   return repo.findByCustomerUsernameIgnoreCase(username);
 }
 @Transactional
 public Shipment create(Shipment s, boolean manual, String operatingRegion){
   validateShipmentContactDetails(s);
   validateShipmentCategory(s);
   if (s.getShipmentDate() == null) s.setShipmentDate(LocalDateTime.now());
   s.setCreationType(manual ? "MANUAL" : "CUSTOMER");
   String paymentType = s.getPaymentType() == null ? "PAID" : s.getPaymentType().trim().toUpperCase();
   if (!Set.of("PAID", "TO_PAY").contains(paymentType)) {
     paymentType = "PAID";
   }
   s.setPaymentType(paymentType);

   String source = s.getSourceRegion() == null ? "" : s.getSourceRegion().trim();
   String current = operatingRegion == null ? "ALL" : operatingRegion.trim();
   if (manual && ("ALL".equalsIgnoreCase(current) || !source.equalsIgnoreCase(current))) {
     throw new IllegalStateException("Manual shipments must be entered from their regional Export page");
   }
   boolean export = "ALL".equalsIgnoreCase(current)
       ? !source.isBlank()
           && (s.getCustomerUsername() != null
               || source.equalsIgnoreCase(s.getDestinationRegion() == null ? "" : s.getDestinationRegion().trim()))
       : source.equalsIgnoreCase(current);
   if (manual && !export) {
     throw new IllegalStateException("Manual shipments must be Export shipments");
   }
   if (manual) {
     if (s.getWeight() == null || s.getWeight() <= 0
         || s.getShipmentAmount() == null || s.getShipmentAmount() < 0) {
       throw new IllegalArgumentException("Weight and shipment amount are required for manual shipments");
     }
     s.setStatus("VERIFIED");
   } else if ("TO_PAY".equals(paymentType)) {
     s.setStatus("VERIFIED");
   } else {
     s.setStatus("WAITING_FOR_VERIFICATION");
   }
   s.setShipmentType(export ? "EXPORT" : "IMPORT");
   String sourceDistrictCode=districtCodeFor(s.getSourceRegion(),"Source");
   String destinationDistrictCode=districtCodeFor(s.getDestinationRegion(),"Destination");
   s.setTrackingNumber("PENDING-"+UUID.randomUUID());
   Shipment saved=repo.saveAndFlush(s);
   if(saved.getId()==null||saved.getId()<0||saved.getId()>9_999_999L){
     throw new IllegalStateException("Shipment ID exceeds the supported seven-digit tracking number range");
   }
   saved.setTrackingNumber(sourceDistrictCode+"-"+String.format(Locale.ROOT,"%07d",saved.getId())+"-"+destinationDistrictCode);
   return repo.save(saved);
 }
 private String districtCodeFor(String regionName,String routeEnd){
   Region region=regionName==null?null:regionRepository.findByNameIgnoreCase(regionName.trim()).orElse(null);
   if(region==null){
     throw new IllegalArgumentException(routeEnd+" region is not registered; configure its district code before creating shipments");
   }
   Region district=region;
   if("REGION".equalsIgnoreCase(region.getAccountType())){
     String parentDistrict=region.getParentDistrict()==null?"":region.getParentDistrict().trim();
     district=parentDistrict.isBlank()?null:regionRepository.findByNameIgnoreCase(parentDistrict).orElse(null);
   }
   String code=district==null||district.getCode()==null?"":district.getCode().trim().toUpperCase(Locale.ROOT);
   if(!code.matches("[A-Z0-9]{1,8}")){
     throw new IllegalArgumentException(routeEnd+" district code is missing or invalid; configure a 1-8 character alphanumeric code");
   }
   return code;
 }
 public Shipment createReturn(String originalTrackingNumber, String operatingRegion, String reason){
   String region=operatingRegion==null?"":operatingRegion.trim();
   String tracking=originalTrackingNumber==null?"":originalTrackingNumber.trim();
   if(region.isBlank() || "ALL".equalsIgnoreCase(region)){
     throw new IllegalStateException("Only a regional administrator can create a return shipment");
   }
   if(reason==null || reason.isBlank()){
     throw new IllegalArgumentException("A return reason is required");
   }
   Shipment original=repo.findByTrackingNumber(tracking).orElseThrow();
   String source=original.getSourceRegion()==null?"":original.getSourceRegion().trim();
     if(!region.equalsIgnoreCase(original.getDestinationRegion()==null?"":original.getDestinationRegion().trim())
       || !"RECEIVED".equalsIgnoreCase(original.getStatus()) || source.isBlank()
       || region.equalsIgnoreCase(source)){
     throw new IllegalStateException("Only dispatched shipments addressed to this region can be returned");
   }
   if(repo.existsByReturnOfTrackingNumberIgnoreCase(tracking)){
     throw new IllegalStateException("A return shipment already exists for this shipment");
   }
   original.setReturnReason(reason.trim());
   original.setStatus("RETURNED");
   return repo.save(original);
 }
 public Shipment status(String tracking,String newStatus,String operatingRegion){
   return status(tracking, newStatus, operatingRegion, null, null);
 }
 public Shipment status(String tracking,String newStatus,String operatingRegion, Double weight, Double amount){
   Shipment s=repo.findByTrackingNumber(tracking).orElseThrow();
   if (!Set.of("WAITING_FOR_VERIFICATION", "VERIFIED", "IN_TRANSIT", "RECEIVED", "DELIVERED", "TO_PAY_WAITING_FOR_VERIFICATION", "RETURN_DISPATCHED", "RETURN_RECEIVED").contains(newStatus)) {
     throw new IllegalArgumentException("Unsupported shipment status");
   }
   String region = operatingRegion == null ? "ALL" : operatingRegion.trim();
   if ("ALL".equalsIgnoreCase(region)) {
     throw new IllegalStateException("Global administrators cannot update shipment tracking");
   }
   boolean returnJourney=s.getReturnReason()!=null&&!s.getReturnReason().isBlank();
   String transitRegion=returnJourney?s.getDestinationRegion():s.getSourceRegion();
   String receivingRegion=returnJourney?s.getSourceRegion():s.getDestinationRegion();
   if("RETURN_DISPATCHED".equals(newStatus)&&!returnJourney){
     throw new IllegalStateException("Only a returned shipment can be marked return dispatched");
   }
   if (Set.of("WAITING_FOR_VERIFICATION", "VERIFIED", "IN_TRANSIT").contains(newStatus)
       && !region.equalsIgnoreCase(transitRegion)) {
     throw new IllegalStateException(returnJourney
         ? "Only the region that marked the shipment returned can dispatch it into transit"
         : "Only the source region can update verification and transit tracking");
   }
   if ("RECEIVED".equals(newStatus)
       && !region.equalsIgnoreCase(receivingRegion)) {
     throw new IllegalStateException(returnJourney
         ? "Only the original source region can receive the returned shipment"
         : "Only the destination region can receive this shipment");
   }
   if("RETURN_DISPATCHED".equals(newStatus)&&!region.equalsIgnoreCase(receivingRegion)){
     throw new IllegalStateException("Only the original source region can mark the returned shipment dispatched");
   }
   if("RETURN_RECEIVED".equals(newStatus)&&(!returnJourney||!region.equalsIgnoreCase(receivingRegion))){
     throw new IllegalStateException("Only the original source region can receive a returned shipment");
   }
   if(returnJourney){
     boolean validReturnTransition=("RETURNED".equals(s.getStatus())&&"IN_TRANSIT".equals(newStatus))
         ||("IN_TRANSIT".equals(s.getStatus())&&Set.of("RETURN_RECEIVED", "RETURN_DISPATCHED").contains(newStatus));
     if(!validReturnTransition){
       throw new IllegalStateException("Returned shipments must move from returned to transit, then be received by the original source region");
     }
   }
   if ("DELIVERED".equals(newStatus)
     && !region.equalsIgnoreCase(receivingRegion)) {
     throw new IllegalStateException(returnJourney
         ? "Only the original source region can deliver the returned shipment"
         : "Only the destination region can deliver this shipment");
   }
   if ("RECEIVED".equals(newStatus)&&!returnJourney) {
     boolean verifyingToPay = "RECEIVED".equals(s.getStatus())
         && "TO_PAY".equalsIgnoreCase(s.getPaymentType())
         && !Boolean.TRUE.equals(s.getDestinationPaymentVerified());
     boolean canReceive = "IN_TRANSIT".equals(s.getStatus())
         || ("VERIFIED".equals(s.getStatus()) && "TO_PAY".equalsIgnoreCase(s.getPaymentType()))
         || "TO_PAY_WAITING_FOR_VERIFICATION".equals(s.getStatus())
         || verifyingToPay;
     if (!canReceive) {
       throw new IllegalStateException("Shipment must be in transit before it can be received");
     }
     if ("TO_PAY".equalsIgnoreCase(s.getPaymentType())) {
       if (verifyingToPay || "TO_PAY_WAITING_FOR_VERIFICATION".equals(s.getStatus())) {
         if (weight == null || weight <= 0 || amount == null || amount < 0) {
           throw new IllegalArgumentException("Weight and shipment amount are required for destination verification");
         }
         s.setWeight(weight);
         s.setShipmentAmount(amount);
         s.setDestinationPaymentVerified(true);
       } else {
         s.setDestinationPaymentVerified(false);
       }
     }
   }
   if ("TO_PAY_WAITING_FOR_VERIFICATION".equals(newStatus)
       && !region.equalsIgnoreCase(s.getDestinationRegion())) {
     throw new IllegalStateException("Only the destination region can verify a to-pay shipment");
   }
   if ("RECEIVED".equals(newStatus) && !returnJourney && "TO_PAY_WAITING_FOR_VERIFICATION".equals(s.getStatus())) {
     if (weight == null || weight <= 0 || amount == null || amount < 0) {
       throw new IllegalArgumentException("Weight and shipment amount are required for destination verification");
     }
     s.setWeight(weight);
     s.setShipmentAmount(amount);
     s.setDestinationPaymentVerified(true);
   }
   if ("DELIVERED".equals(newStatus) && !"RECEIVED".equals(s.getStatus())) {
     throw new IllegalStateException("Shipment must be received before it can be delivered");
   }
   if ("VERIFIED".equals(newStatus)) {
     if (weight == null || weight <= 0 || amount == null || amount < 0) {
       throw new IllegalArgumentException("Weight and shipment amount are required for verification");
     }
     s.setWeight(weight);
     s.setShipmentAmount(amount);
   }
   s.setStatus(newStatus); return repo.save(s);
 }
 public Shipment verifyDestinationPayment(String tracking, String operatingRegion, Double weight, Double amount){
   Shipment s=repo.findByTrackingNumber(tracking).orElseThrow();
   String region=operatingRegion==null?"":operatingRegion.trim();
   String paymentType=s.getPaymentType()==null?"":s.getPaymentType().trim();
   String status=s.getStatus()==null?"":s.getStatus().trim().toUpperCase(Locale.ROOT);
   if(region.isBlank() || "ALL".equalsIgnoreCase(region)
       || !region.equalsIgnoreCase(s.getDestinationRegion()==null?"":s.getDestinationRegion().trim())){
     throw new IllegalStateException("Only the destination region can verify a to-pay shipment");
   }
   if(!"TO_PAY".equalsIgnoreCase(paymentType)
       || !Set.of("RECEIVED","DELIVERED").contains(status)
       || Boolean.TRUE.equals(s.getDestinationPaymentVerified())){
     throw new IllegalStateException("Only an unverified received to-pay shipment can be verified");
   }
   if(weight==null || !Double.isFinite(weight) || weight<=0
       || amount==null || !Double.isFinite(amount) || amount<0){
     throw new IllegalArgumentException("Weight and shipment amount are required for destination verification");
   }
   s.setWeight(weight);
   s.setShipmentAmount(amount);
   s.setDestinationPaymentVerified(true);
   return repo.save(s);
 }
 public void deleteUnverified(String tracking, String operatingRegion){
   Shipment s=repo.findByTrackingNumber(tracking).orElseThrow();
   String region=operatingRegion==null?"ALL":operatingRegion.trim();
   boolean allowed="ALL".equalsIgnoreCase(region)
       || region.equalsIgnoreCase(s.getSourceRegion())
       || region.equalsIgnoreCase(s.getDestinationRegion());
   if(!allowed || !"WAITING_FOR_VERIFICATION".equals(s.getStatus())){
     throw new IllegalStateException("Only an administrator of a shipment region can delete an unverified shipment");
   }
   repo.delete(s);
 }
 public Shipment updateUnverified(String tracking, Shipment input, String operatingRegion){
   Shipment s=repo.findByTrackingNumber(tracking).orElseThrow();
   String region=operatingRegion==null?"":operatingRegion.trim();
   boolean unverified="WAITING_FOR_VERIFICATION".equals(s.getStatus());
  boolean editableStatus="VERIFIED".equals(s.getStatus());
   if(region.isBlank() || !region.equalsIgnoreCase(s.getSourceRegion())
       || (!unverified && !editableStatus)){
     throw new IllegalStateException("Only the source region can edit this shipment");
   }
   validateShipmentContactDetails(input);
  validateShipmentCategory(input);
   s.setSenderName(input.getSenderName()); s.setSenderPhone(input.getSenderPhone());
   s.setSenderAddress(input.getSenderAddress()); s.setReceiverName(input.getReceiverName());
   s.setReceiverPhone(input.getReceiverPhone()); s.setReceiverAddress(input.getReceiverAddress());
   s.setDestination(input.getDestination()); s.setPackageDescription(input.getPackageDescription());
  s.setShipmentCategory(input.getShipmentCategory());
   if(unverified){
     s.setSourceRegion(input.getSourceRegion());
     s.setDestinationRegion(input.getDestinationRegion());
     s.setShipmentDate(input.getShipmentDate());
   }
   s.setExportDate(input.getExportDate());
   return repo.save(s);
 }
 private void validateShipmentContactDetails(Shipment shipment){
   if(shipment==null || !validPhone(shipment.getSenderPhone()) || !validPhone(shipment.getReceiverPhone())){
     throw new IllegalArgumentException("Sender and receiver phone numbers must contain exactly 10 digits");
   }
   if(shipment.getSenderAddress()==null || shipment.getSenderAddress().isBlank()
       || shipment.getReceiverAddress()==null || shipment.getReceiverAddress().isBlank()){
     throw new IllegalArgumentException("Sender and receiver addresses are required");
   }
   shipment.setSenderPhone(shipment.getSenderPhone().trim());
   shipment.setReceiverPhone(shipment.getReceiverPhone().trim());
   shipment.setSenderAddress(shipment.getSenderAddress().trim());
   shipment.setReceiverAddress(shipment.getReceiverAddress().trim());
 }
 private void validateShipmentCategory(Shipment shipment){
   String category=shipment==null||shipment.getShipmentCategory()==null?"":shipment.getShipmentCategory().trim();
   if(!SHIPMENT_CATEGORIES.contains(category)){
     throw new IllegalArgumentException("Select a valid shipment category");
   }
   shipment.setShipmentCategory(category);
 }
 private boolean validPhone(String phone){return phone!=null && phone.trim().matches("[0-9]{10}");}
 public void resetAll(){ repo.deleteAll(); }
 public List<Shipment> seedDemoData(){
   List<Shipment> demo = new ArrayList<>();
   demo.add(makeDemo("DEMO-TPK-PAID-001", "Karthik", "9000100001", "TPK Main Street", "Raja", "9000100101", "Srirangam Market", "Temi-Thiruparenkundram", "Temi-Srirangam", "TPK Office", "Srirangam Office", "PAID", "WAITING_FOR_VERIFICATION", 2.5, 420.0, "Demo paid waiting shipment"));
   demo.add(makeDemo("DEMO-TPK-PAID-002", "Anand", "9000100002", "TPK West Street", "Priya", "9000100102", "Srirangam Town", "Temi-Thiruparenkundram", "Temi-Srirangam", "TPK Office", "Srirangam Office", "PAID", "IN_TRANSIT", 3.0, 520.0, "Demo paid in transit shipment"));
   demo.add(makeDemo("DEMO-TPK-TOPAY-003", "Muthu", "9000100003", "TPK East Street", "Kavin", "9000100103", "Srirangam Hub", "Temi-Thiruparenkundram", "Temi-Srirangam", "TPK Office", "Srirangam Office", "TO_PAY", "VERIFIED", 2.0, 310.0, "Demo to-pay verified shipment"));
   demo.add(makeDemo("DEMO-TPK-TOPAY-004", "Ramesh", "9000100004", "TPK Hospital Road", "Soundar", "9000100104", "Srirangam Center", "Temi-Thiruparenkundram", "Temi-Srirangam", "TPK Office", "Srirangam Office", "TO_PAY", "RECEIVED", 2.8, 440.0, "Demo to-pay received shipment"));
   demo.add(makeDemo("DEMO-SRI-PAID-005", "Nisha", "9000100005", "Srirangam Main Road", "Venkatesh", "9000100105", "TPK City", "Temi-Srirangam", "Temi-Thiruparenkundram", "Srirangam Office", "TPK Office", "PAID", "VERIFIED", 1.8, 280.0, "Demo return paid verified shipment"));
   demo.add(makeDemo("DEMO-SRI-TOPAY-006", "Deepa", "9000100006", "Srirangam Old Road", "Arun", "9000100106", "TPK Warehouse", "Temi-Srirangam", "Temi-Thiruparenkundram", "Srirangam Office", "TPK Office", "TO_PAY", "DELIVERED", 2.2, 360.0, "Demo return to-pay delivered shipment"));
   demo.add(makeDemo("DEMO-TPK-PAID-007", "Balu", "9000100007", "TPK Market", "Suganya", "9000100107", "Madurai Hub", "Temi-Thiruparenkundram", "Temi-Madurai", "TPK Office", "Madurai Office", "PAID", "VERIFIED", 3.2, 550.0, "Demo district transfer verified shipment"));
   return repo.saveAll(demo);
 }
 private Shipment makeDemo(String tracking, String senderName, String senderPhone, String senderAddress,
     String receiverName, String receiverPhone, String receiverAddress, String sourceRegion,
     String destinationRegion, String sourceOffice, String destination, String paymentType,
     String status, double weight, double amount, String description){
   Shipment s = new Shipment();
   s.setTrackingNumber(tracking);
   s.setSenderName(senderName); s.setSenderPhone(senderPhone); s.setSenderAddress(senderAddress);
   s.setReceiverName(receiverName); s.setReceiverPhone(receiverPhone); s.setReceiverAddress(receiverAddress);
   s.setSourceRegion(sourceRegion); s.setDestinationRegion(destinationRegion);
   s.setSourceOffice(sourceOffice); s.setDestination(destination); s.setPaymentType(paymentType);
   s.setPackageDescription(description); s.setWeight(weight); s.setShipmentAmount(amount);
   s.setDestinationPaymentVerified("TO_PAY".equals(paymentType) && "DELIVERED".equals(status));
   s.setStatus(status); s.setShipmentType("EXPORT"); s.setCreationType("DEMO");
   s.setDimensions("30 x 20 x 15 cm"); s.setShipmentDate(LocalDateTime.now()); s.setExportDate(LocalDateTime.now());
   return s;
 }
}