package de.samply.directory_sync;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

public class Util {

  public static <K, V> Map<K, V> mapOf() {
    return new HashMap<>();
  }

  public static <K, V> Map<K, V> mapOf(K key, V value) {
    HashMap<K, V> map = new HashMap<>();
    map.put(key, value);
    return map;
  }

  private static String camelize(String string) {
    if (string == null)
      return null;
    if (string.isEmpty())
      return string;
    if (string.length() == 1)
      return string.toUpperCase();
    return string.substring(0, 1).toUpperCase() + string.substring(1).toLowerCase();
  }

  public static String deriveCountryCodeFromCountry(String country) {
    if (country == null || country.isEmpty())
      return country;
    return mapCountryNamesToCountryCodes.get(camelize(country));
  }

  private static Map<String, String> mapCountryNamesToCountryCodes = new HashMap<String, String> () {{
    put("Austria", "AT");
    put("Australia", "AU");
    put("Belgium", "BE");
    put("Bulgaria", "BG");
    put("Canada", "CA");
    put("Switzerland", "CH");
    put("Cyprus", "CY");
    put("Czech Republic", "CZ");
    put("Germany", "DE");
    put("Estonia", "EE");
    put("Spain", "ES");
    put("Europe", "EU");
    put("Finland", "FI");
    put("France", "FR");
    put("Greece", "GR");
    put("Hungary", "HU");
    put("Italy", "IT");
    put("Lithuania", "LT");
    put("Latvia", "LV");
    put("Malta", "MT");
    put("Netherlands", "NL");
    put("Norway", "NO");
    put("Poland", "PL");
    put("Portugal", "PT");
    put("Qatar", "QA");
    put("Russia", "RU");
    put("Sweden", "SE");
    put("Turkey", "TR");
    put("Uganda", "UG");
    put("United Kingdom", "UK");
    put("United States of America", "US");
    put("Vietnam", "VN");
  }};

  public static String jsonify(Object value) {
    return new Gson().toJson(value);
  }

  // public static String extractBiobankIdFromCollectionId(String collectionId) {
  //   String[] idParts = collectionId.split(":");
  //   String biobankID = idParts[0] + ":" + idParts[1] + ":" + idParts[2];
  //   return biobankID;
  // }

    
  /**
  * Get a printable stack trace from an Exception object.
  * @param e
  * @return
 */
  public static String traceFromException(Exception e) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
       return sw.toString();
   }
}
