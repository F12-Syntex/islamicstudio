package com.syntex.islamicstudio.db.model;

public class Surah {

    private int id;
    private String nameArabic;
    private String nameEnglish;
    private int ayahCount;

    public Surah(int id, String nameArabic, String nameEnglish, int ayahCount) {
        this.id = id;
        this.nameArabic = nameArabic;
        this.nameEnglish = nameEnglish;
        this.ayahCount = ayahCount;
    }

    public int getId() {
        return id;
    }

    public String getNameArabic() {
        return nameArabic;
    }

    public String getNameEnglish() {
        return nameEnglish;
    }

    public int getAyahCount() {
        return ayahCount;
    }
}
