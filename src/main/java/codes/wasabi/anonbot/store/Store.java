package codes.wasabi.anonbot.store;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class Store<K, V> {

    private final Map<K, Long> keyUpdates = new HashMap<>();
    private final Map<K, V> map;
    private final int maxSize;
    private final long expiry;
    private final File file;

    public Store(@Nullable File file, int maxSize, long expiry) {
        this.map = new HashMap<>();
        if (maxSize <= 0) maxSize = 1;
        this.maxSize = maxSize;
        this.file = file;
        this.expiry = expiry;
        Stores.addStore(this);
        load();
    }

    public Store(@Nullable File file, int maxSize) {
        this(file, maxSize, -1);
    }

    public Store(@Nullable File file, long expiry) {
        this(file, Integer.MAX_VALUE, expiry);
    }

    public Store(@Nullable File file) {
        this(file, Integer.MAX_VALUE, -1);
    }

    public Store(int maxSize, long expiry) {
        this(null, maxSize, expiry);
    }

    public Store(int maxSize) {
        this(null, maxSize, -1);
    }

    public Store(long expiry) {
        this(null, Integer.MAX_VALUE, expiry);
    }

    public Store() {
        this(null, Integer.MAX_VALUE, -1);
    }

    private void checkExpiry() {
        if (expiry > 0L) {
            final long expireTime = System.currentTimeMillis() - expiry;
            keyUpdates.entrySet().stream().filter((Map.Entry<K, Long> entry) -> entry.getValue() <= expireTime).map(Map.Entry::getKey).forEach(this::remove);
        }
    }

    public @Nullable V get(K key) {
        checkExpiry();
        return map.get(key);
    }

    public void set(K key, V value) {
        checkExpiry();
        long now = System.currentTimeMillis();
        if (!map.containsKey(key)) {
            int size = map.size();
            while (size > (maxSize - 1)) {
                remove(keyUpdates.entrySet().stream().min(Comparator.comparingLong(Map.Entry::getValue)).orElseThrow().getKey());
                size--;
            }
        }
        map.put(key, value);
        keyUpdates.put(key, now);
    }

    public boolean contains(K key) {
        checkExpiry();
        return map.containsKey(key);
    }

    public boolean remove(K key) {
        if (map.containsKey(key)) {
            map.remove(key);
            keyUpdates.remove(key);
            return true;
        }
        return false;
    }

    public int size() {
        checkExpiry();
        return map.size();
    }

    public void clear() {
        map.clear();
        keyUpdates.clear();
    }

    @SuppressWarnings("unchecked")
    public void load() {
        clear();
        if (file != null) {
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    try (ObjectInputStream ois = new ObjectInputStream(fis)) {
                        int size = ois.readInt();
                        for (int i = 0; i < size; i++) {
                            Object k = ois.readObject();
                            Object v = ois.readObject();
                            set((K) k, (V) v);
                        }
                    }
                } catch (IOException | ClassNotFoundException | ClassCastException ignored) {
                }
            }
        }
    }

    public void save() {
        if (file != null) {
            if (!file.exists()) {
                try {
                    if (!file.createNewFile()) throw new IOException();
                } catch (IOException ignored) {}
            }
            try (FileOutputStream fos = new FileOutputStream(file, false)) {
                try (ObjectOutputStream oos = new ObjectOutputStream(fos)) {
                    oos.writeInt(size());
                    for (Map.Entry<K, V> entry : map.entrySet()) {
                        oos.writeObject(entry.getKey());
                        oos.writeObject(entry.getValue());
                    }
                    oos.flush();
                }
            } catch (IOException ignored) { }
        }
    }

}
