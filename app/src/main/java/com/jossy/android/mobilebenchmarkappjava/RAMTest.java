package com.jossy.android.mobilebenchmarkappjava;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jossy.android.mobilebenchmarkappjava.data.User;

import java.io.File;
import java.lang.reflect.Type;
import java.util.*;

public class RAMTest {
    private static final int RUNS = 95000;
    private static final Map<String, Integer> nameCounter = new HashMap<>();
    private static final Map<String, Integer> surnameCounter = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final Type USER_LIST_TYPE = new TypeToken<List<User>>() {}.getType();
    private static String JSON_DATA = new Users().list;

    public static void runBenchmark() {
        List<User> bigList = new ArrayList<>();
        nameCounter.clear();
        surnameCounter.clear();
        List<User> users = GSON.fromJson(JSON_DATA, USER_LIST_TYPE);
        if (users == null) users = Collections.emptyList();
        for (int i = 0; i < RUNS; i++) {
            List<User> shuffled = new ArrayList<>(users);
            Collections.shuffle(shuffled, new Random(i));
            bigList.addAll(shuffled);
            List<User> sorted = new ArrayList<>(shuffled);
            sorted.sort(Comparator.comparing(u -> u.name));
            List<User> filtered = new ArrayList<>();
            for (User u : sorted) {
                if (u.active && u.age > 18) {
                    User copy = new User(u);
                    if (copy.name != null) {
                        copy.name = copy.name.toUpperCase(Locale.ROOT);
                    }
                    filtered.add(copy);
                }
            }
            String serialized = GSON.toJson(filtered);
            List<User> deserialized = GSON.fromJson(serialized, USER_LIST_TYPE);
            if (deserialized == null) deserialized = Collections.emptyList();
            if (!deserialized.isEmpty()) {
                User randomUser = deserialized.get(new Random().nextInt(deserialized.size()));
                String randomUserName = randomUser.name;
            }
            for (User u : users) {
                if (u.name == null) continue;
                String[] parts = u.name.split(" ");
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    nameCounter.put(parts[0], nameCounter.getOrDefault(parts[0], 0) + 1);
                }
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    surnameCounter.put(parts[1], surnameCounter.getOrDefault(parts[1], 0) + 1);
                }
            }
        }
        bigList.clear();
    }
}
