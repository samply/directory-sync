package de.samply.directory_sync.directory.model;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class represents a BBMRI-ERIC identifier which has the following form:
 * <p>
 * {@literal bbmri-eric:ID:<country-code>_<suffix>}
 */
public class BbmriEricId {

  private static final Pattern PATTERN = Pattern.compile("bbmri-eric:ID:([A-Z]{2})(_.+)");

  private final String countryCode;
  private final String suffix;

  private BbmriEricId(String countryCode, String suffix) {
    this.countryCode = Objects.requireNonNull(countryCode);
    this.suffix = Objects.requireNonNull(suffix);
  }

  /**
   * Returns the two-letter upper-case country code of this identifier.
   *
   * @return the two-letter upper-case country code of this identifier.
   */
  public String getCountryCode() {
    return countryCode;
  }

  /**
   * Tries to create a BBMRI-ERIC identifier from string.
   *
   * @param s the string to parse.
   * @return a BBMRI-ERIC identifier or {@link Optional#empty empty} if {@code s} doesn't represent
   * a valid BBMRI-ERIC identifier
   */
  public static Optional<BbmriEricId> valueOf(String s) {
    Matcher matcher = PATTERN.matcher(s);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(new BbmriEricId(matcher.group(1), matcher.group(2)));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BbmriEricId that = (BbmriEricId) o;
    return countryCode.equals(that.countryCode) && suffix.equals(that.suffix);
  }

  @Override
  public int hashCode() {
    return Objects.hash(countryCode, suffix);
  }

  @Override
  public String toString() {
    return "bbmri-eric:ID:" + countryCode + suffix;
  }
}
