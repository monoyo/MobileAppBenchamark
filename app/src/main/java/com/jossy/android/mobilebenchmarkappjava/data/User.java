package com.jossy.android.mobilebenchmarkappjava.data;

public class User {
    public String name;
    String surname;
    public int age;
    public boolean active;

    public User(User user) {
        this.name = user.name;
        this.surname = user.surname;
        this.age = user.age;
        this.active = user.active;
    }
}
