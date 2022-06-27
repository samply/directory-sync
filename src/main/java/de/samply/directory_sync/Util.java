package de.samply.directory_sync;

import java.util.HashMap;
import java.util.Map;

public class Util {

  public static <K, V> Map<K, V> mapOf() {
    return new HashMap<>();
  }

  public static <K, V> Map<K, V> mapOf(K key, V value) {
    HashMap<K, V> map = new HashMap<>();
    map.put(key, value);
    return map;
  }
}
