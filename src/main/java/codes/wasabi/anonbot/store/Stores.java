package codes.wasabi.anonbot.store;

import codes.wasabi.anonbot.data.DMState;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Stores {

    public static Store<Long, HashSet<Long>> CHANNELS;
    public static Store<Long, DMState> DM;
    public static Store<Long, Long> OWNERS;

    public static void init() {
        CHANNELS = new Store<>(new File("./channels.dat"));
        DM = new Store<>(60 * 60 * 8 * 1000L);
        OWNERS = new Store<>(1000);
    }

    private static final Set<Store<?, ?>> set = new HashSet<>();

    static void addStore(Store<?, ?> store) {
        set.add(store);
    }

    @Contract(pure = true)
    public static @NotNull @UnmodifiableView Set<Store<?, ?>> getAll() {
        return Collections.unmodifiableSet(set);
    }

    public static void saveAll() {
        for (Store<?, ?> s : set) {
            s.save();
        }
    }

    public static void loadAll() {
        for (Store<?, ?> s : set) {
            s.load();
        }
    }

}
