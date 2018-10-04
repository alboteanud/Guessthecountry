package com.craiovadata.guessthecountry;


import com.google.firebase.firestore.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Item {

    private String country;
    private String img_title;
    private String img_description;
    private String music_title;
    private String music_description;
    private String country_code;
    private String id;

    public Item()  { }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getImg_title() {
        return img_title;
    }

    public void setImg_title(String img_title) {
        this.img_title = img_title;
    }

    public String getImg_description() {
        return img_description;
    }

    public void setImg_description(String img_description) {
        this.img_description = img_description;
    }

    public String getMusic_title() {
        return music_title;
    }

    public void setMusic_title(String music_title) {
        this.music_title = music_title;
    }

    public String getMusic_description() {
        return music_description;
    }

    public void setMusic_description(String music_description) {
        this.music_description = music_description;
    }

    public String getCountry_code() {
        return country_code;
    }

    public void setCountry_code(String country_code) {
        this.country_code = country_code;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getImageLocation() {
        return  "images/" + id + ".jpg";
    }
}
