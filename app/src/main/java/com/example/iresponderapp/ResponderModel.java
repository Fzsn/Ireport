package com.example.iresponderapp;

public class ResponderModel {

    public String userId;
    public String fullName;
    public String contactNumber;
    public String email;
    public String location; // ⭐ NEW FIELD ADDED
    public String agency;
    public String password;

    public ResponderModel() {
        // Required empty constructor for Firebase
    }

    // ⭐ UPDATED CONSTRUCTOR to include location
    public ResponderModel(String userId, String fullName, String contactNumber,
                          String email, String location, String agency, String password) {
        this.userId = userId;
        this.fullName = fullName;
        this.contactNumber = contactNumber;
        this.email = email;
        this.location = location; // ⭐ INITIALIZING NEW FIELD
        this.agency = agency;
        this.password = password;
    }

    // You should add getters and setters if you are following best practices,
    // but for Firebase public fields are often used for simplicity.
    // If you need getters/setters, let me know!
}