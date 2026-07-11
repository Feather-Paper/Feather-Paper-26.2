package net.feathermc.feather.util;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.List;
import java.util.RandomAccess;

@NullMarked
public final class FeatherFastCollections {

    private static boolean fastCollectionsEnabled = true;

    private FeatherFastCollections() {}

    public static void setFastCollectionsEnabled(boolean enabled) {
        fastCollectionsEnabled = enabled;
    }

    public static boolean isFastCollectionsEnabled() {
        return fastCollectionsEnabled;
    }

    // # fast contains lookup for random access lists
    public static <T> boolean fastContains(List<T> list, T element) {
        if (!fastCollectionsEnabled || !(list instanceof RandomAccess)) {
            return list.contains(element);
        }
        int size = list.size();
        for (int i = 0; i < size; i++) {
            T item = list.get(i);
            if (element == item || (element != null && element.equals(item))) {
                return true;
            }
        }
        return false;
    }

    // # fast clear for collections with threshold check
    public static void fastClear(Collection<?> collection) {
        if (collection.isEmpty()) {
            return;
        }
        collection.clear();
    }
}
