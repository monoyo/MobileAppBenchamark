package com.jossy.android.mobilebenchmarkappjava;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class RAMTest {
    private static final File file = new File("users.json");
    private static final int RUNS = 95000;
    public static final Map<String, Integer> nameCounter = new HashMap<>();
    public static final Map<String, Integer> surnameCounter = new HashMap<>();

    public static void runBenchmark() {
        List<User> bigList = new ArrayList<>();
        nameCounter.clear();
        surnameCounter.clear();
        for (int i = 0; i < RUNS; i++) {
            if (file.exists()) {
                String json = file.toString();
                Type userListType = new TypeToken<List<User>>() {}.getType();
                List<User> users = new Gson().fromJson(json, userListType);
                List<User> shuffled = new ArrayList<>(users);
                java.util.Collections.shuffle(shuffled);
                bigList.addAll(shuffled);
                java.util.Collections.sort(shuffled, new java.util.Comparator<User>() {
                    @Override
                    public int compare(User u1, User u2) {
                        return u1.name.compareTo(u2.name);
                    }
                });
                List<User> filtered = new ArrayList<>();
                for (User user : shuffled) {
                    if (user.active && user.age > 18) {
                        User copy = new User(user);
                        copy.name = user.name.toUpperCase();
                        filtered.add(copy);
                    }
                }
                String serialized = new Gson().toJson(filtered);
                List<User> deserialized = new Gson().fromJson(serialized, userListType);
                if (!deserialized.isEmpty()) {
                    Random rand = new Random();
                    User randomUser = deserialized.get(rand.nextInt(deserialized.size()));
                    String randomUserName = randomUser.name;
                }
                for (User user : users) {
                    String[] parts = user.name.split(" ");
                    if (parts.length > 0) {
                        String firstName = parts[0];
                        Integer count = nameCounter.containsKey(firstName) ? nameCounter.get(firstName) : 0;
                        nameCounter.put(firstName, count + 1);
                    }
                    if (parts.length > 1) {
                        String surname = parts[1];
                        Integer count = surnameCounter.containsKey(surname) ? surnameCounter.get(surname) : 0;
                        surnameCounter.put(surname, count + 1);
                    }
                }
            }
        }
        bigList.clear();
    }
}

class User {
    String name;
    String surname;
    int age;
    boolean active;

    public User(User user) {
        this.name = user.name;
        this.surname = user.surname;
        this.age = user.age;
        this.active = user.active;
    }
}