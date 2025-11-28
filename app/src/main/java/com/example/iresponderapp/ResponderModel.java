package com.example.iresponderapp;
public class ResponderModel {

    public String userId;
    public String fullName;
    public String contactNumber;
    public String email;
    public String agency;
    public String password;

    public ResponderModel() {
        // Required empty constructor for Firebase
    }

    public ResponderModel(String userId, String fullName, String contactNumber,
                          String email, String agency, String password) {
        this.userId = userId;
        this.fullName = fullName;
        this.contactNumber = contactNumber;
        this.email = email;
        this.agency = agency;
        this.password = password;
    }
}
