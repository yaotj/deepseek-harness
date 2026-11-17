package com.chinasofti.huateng.account.service;


public interface CardPoolAllocationService {

    CardAllocation reserveFromPool(String cardType, String businessType, String businessId, String ownerId);


    boolean confirmReservation(CardAllocation allocation, String scene);


    void releaseReservation(CardAllocation allocation, String scene);


    boolean isHceCard(String cardType);


    HceCardAllocation requestHceCardData(String thirdUserId, String ticketCard);


    record HceCardAllocation(String cardId, String hceData) {
    }


    record CardAllocation(String cardId, String hceData, String reservationId, String businessId) {
    }
}
