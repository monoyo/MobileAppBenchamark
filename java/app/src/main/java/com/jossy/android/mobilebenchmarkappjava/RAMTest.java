package com.jossy.android.mobilebenchmarkappjava;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jossy.android.mobilebenchmarkappjava.data.User;

import java.lang.reflect.Type;
import java.util.*;

public class RAMTest {
    private static final int RUNS = 1800;
    private static final Map<String, Integer> nameCounter = new HashMap<>();
    private static final Map<String, Integer> surnameCounter = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static final Type USER_LIST_TYPE = new TypeToken<List<User>>() {
    }.getType();
    private static String JSON_DATA = new Users().list;

    public static void runBenchmark() {
        runBenchmark(RUNS);
    }

    public static void runBenchmark(int runs) {
        List<User> bigList = new ArrayList<>();
        List<User> users = parseUsers();
        
        for (int i = 0; i < runs; i++) {
            processSingleRun(users, bigList);
        }
        
        bigList.clear();
    }

    private static List<User> parseUsers() {
        List<User> users = GSON.fromJson(JSON_DATA, USER_LIST_TYPE);
        return users != null ? users : Collections.emptyList();
    }

    private static void processSingleRun(List<User> users, List<User> bigList) {
        List<User> shuffled = shuffleUsers(users);
        bigList.addAll(shuffled);
        
        List<User> filtered = filterAndProcessUsers(shuffled);
        
        processSerializationCycle(filtered);
        
        updateNameCounters(users);
    }

    private static List<User> shuffleUsers(List<User> users) {
        List<User> shuffled = new ArrayList<>(users);
        Collections.shuffle(shuffled, new Random(System.nanoTime()));
        return shuffled;
    }

    private static List<User> filterAndProcessUsers(List<User> shuffled) {
        shuffled.sort(Comparator.comparing(u -> u.name));
        
        List<User> filtered = new ArrayList<>();
        for (User u : shuffled) {
            if (isUserQualifying(u)) {
                User copy = new User(u);
                copy.name = copy.name != null ? copy.name.toUpperCase(Locale.ROOT) : null;
                filtered.add(copy);
            }
        }
        return filtered;
    }

    private static boolean isUserQualifying(User u) {
        return u.active && u.age > 18;
    }

    private static void processSerializationCycle(List<User> filtered) {
        String serialized = GSON.toJson(filtered);
        List<User> deserialized = GSON.fromJson(serialized, USER_LIST_TYPE);
        
        if (deserialized != null && !deserialized.isEmpty()) {
            User randomUser = deserialized.get(new Random().nextInt(deserialized.size()));
            String ignored = randomUser.name;
        }
    }

    private static void updateNameCounters(List<User> users) {
        nameCounter.clear();
        surnameCounter.clear();
        
        for (User u : users) {
            updateCountersForUser(u);
        }
    }

    private static void updateCountersForUser(User u) {
        if (u.name == null) return;
        
        String[] parts = u.name.split(" ");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            nameCounter.put(parts[0], nameCounter.getOrDefault(parts[0], 0) + 1);
        }
        if (parts.length > 1 && !parts[1].isEmpty()) {
            surnameCounter.put(parts[1], surnameCounter.getOrDefault(parts[1], 0) + 1);
        }
    }
}
