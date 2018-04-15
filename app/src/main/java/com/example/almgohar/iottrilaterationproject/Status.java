package com.example.almgohar.iottrilaterationproject;

public class Status {
    public long userID;
    public boolean attended;
    public double insideDuration;

    public Status(){

    }

    public Status(long userID, boolean attended, double insideDuration){
        this.userID = userID;
        this.attended = attended;
        this.insideDuration = insideDuration;
    }
}
