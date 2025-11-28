package com.example.iresponderapp;

public class ReceiverModel {

    public String receiverId, fullName, contactNumber, email, agency, location;

    public ReceiverModel() {
        // Required empty constructor for Firebase
    }

    public ReceiverModel(String receiverId, String fullName, String contactNumber,
                         String email, String agency, String location) {

        this.receiverId = receiverId;
        this.fullName = fullName;
        this.contactNumber = contactNumber;
        this.email = email;
        this.agency = agency;
        this.location = location;
    }
}
