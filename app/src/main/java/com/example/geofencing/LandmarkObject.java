package com.example.geofencing;

import com.google.android.gms.maps.model.LatLng;

public class LandmarkObject {
    String name;
    LatLng latlng;
    String date;
    String description;

    public LandmarkObject(String name, double lat, double lng, String date, String description) {
        setName(name);
        setLatLng(lat, lng);
        setDate(date);
        setDesc(description);
    }

    // Set/Get variable name
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDate(String date) {
        this.date = date;
    }


    // Set/Get variable latlng
    public void setLatLng(double lat, double lng) {
        this.latlng = new LatLng(lat, lng);
    }


    // Set/Get variable desc
    public String getDesc() {
        return description;
    }

    public void setDesc(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "LandMark{" +
                "name='" + name + '\'' +
                ", latlng=" + latlng +
                ", date=" + date +
                ", description=" + description +
                '}';
    }
}