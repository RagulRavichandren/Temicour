package com.courier.entity;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity @Table(name="shipments")
public class Shipment {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(unique=true, nullable=false) private String trackingNumber;
 private String senderName, senderPhone, senderAddress;
 private String receiverName, receiverPhone, receiverAddress;
 private String sourceOffice, destination;
 private String shipmentType;
 private String paymentType;
 private String sourceRegion, destinationRegion;
 private String packageDescription;
 private String shipmentCategory;
 private Double weight;
 private Double shipmentAmount;
 private Boolean destinationPaymentVerified;
 private String dimensions;
 private String status;
 private String creationType;
 private String customerUsername;
 private String returnOfTrackingNumber;
 private String returnReason;
 private LocalDateTime shipmentDate, exportDate;
 private LocalDateTime createdAt, updatedAt;

 @PrePersist void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; if(shipmentDate==null) shipmentDate=createdAt; if(status==null) status="WAITING_FOR_VERIFICATION"; }
 @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }

 public Long getId(){return id;} public String getTrackingNumber(){return trackingNumber;}
 public void setTrackingNumber(String v){trackingNumber=v;} public String getSenderName(){return senderName;}
 public void setSenderName(String v){senderName=v;} public String getSenderPhone(){return senderPhone;}
 public void setSenderPhone(String v){senderPhone=v;} public String getSenderAddress(){return senderAddress;}
 public void setSenderAddress(String v){senderAddress=v;} public String getReceiverName(){return receiverName;}
 public void setReceiverName(String v){receiverName=v;} public String getReceiverPhone(){return receiverPhone;}
 public void setReceiverPhone(String v){receiverPhone=v;} public String getReceiverAddress(){return receiverAddress;}
 public void setReceiverAddress(String v){receiverAddress=v;} public String getSourceOffice(){return sourceOffice;}
 public void setSourceOffice(String v){sourceOffice=v;} public String getDestination(){return destination;}
 public void setDestination(String v){destination=v;} public String getPackageDescription(){return packageDescription;}
 public String getShipmentType(){return shipmentType;} public void setShipmentType(String v){shipmentType=v;}
 public String getPaymentType(){return paymentType;} public void setPaymentType(String v){paymentType=v;}
 public String getSourceRegion(){return sourceRegion;} public void setSourceRegion(String v){sourceRegion=v;}
 public String getDestinationRegion(){return destinationRegion;} public void setDestinationRegion(String v){destinationRegion=v;}
 public void setPackageDescription(String v){packageDescription=v;} public Double getWeight(){return weight;}
 public String getShipmentCategory(){return shipmentCategory;} public void setShipmentCategory(String v){shipmentCategory=v;}
 public void setWeight(Double v){weight=v;} public String getDimensions(){return dimensions;}
 public Double getShipmentAmount(){return shipmentAmount;} public void setShipmentAmount(Double v){shipmentAmount=v;}
 public Boolean getDestinationPaymentVerified(){return destinationPaymentVerified;}
 public void setDestinationPaymentVerified(Boolean v){destinationPaymentVerified=v;}
 public void setDimensions(String v){dimensions=v;} public String getStatus(){return status;}
 public void setStatus(String v){status=v;} public String getCreationType(){return creationType;}
 public void setCreationType(String v){creationType=v;} public LocalDateTime getCreatedAt(){return createdAt;}
 public String getCustomerUsername(){return customerUsername;} public void setCustomerUsername(String v){customerUsername=v;}
 public String getReturnOfTrackingNumber(){return returnOfTrackingNumber;} public void setReturnOfTrackingNumber(String v){returnOfTrackingNumber=v;}
 public String getReturnReason(){return returnReason;} public void setReturnReason(String v){returnReason=v;}
 public LocalDateTime getUpdatedAt(){return updatedAt;}
 public LocalDateTime getShipmentDate(){return shipmentDate;} public void setShipmentDate(LocalDateTime v){shipmentDate=v;}
 public LocalDateTime getExportDate(){return exportDate;} public void setExportDate(LocalDateTime v){exportDate=v;}
}