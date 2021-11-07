// Dict.java
// Author: Stuart Clayman
// Email: s.clayman@ucl.ac.uk
// Date: August 2021

package cc.clayman.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * A better way to create a fixed map / dictionary.
 * Inspired by JDK9 Map.of()
 */
public interface Dict {
    //Returns an immutable map containing zero mappings.
    static <K,V> Map<K,V> of​() {
        Map<K,V> map = new HashMap<K,V>();
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing a single mapping.
    static <K,V> Map<K,V> of​(K k1, V v1) {
        Map<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing two mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2) {
        Map<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing three mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3) {
        Map<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        return Collections.unmodifiableMap(map);
    }

    //Returns an immutable map containing four mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }


    // Returns an immutable map containing five mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing six mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }
        
    // Returns an immutable map containing seven mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }
        
    // Returns an immutable map containing eight mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing nine mappings.        
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }

    // Returns an immutable map containing ten mappings.
    static <K,V> Map<K,V> of​(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5, K k6, V v6, K k7, V v7, K k8, V v8, K k9, V v9, K k10, V v10) {
        HashMap<K,V> map = new HashMap<K,V>();
        map.put(k1,v1);
        map.put(k2,v2);
        map.put(k3,v3);
        map.put(k4,v4);
        return Collections.unmodifiableMap(map);
    }

}

