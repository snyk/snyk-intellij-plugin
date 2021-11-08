//
//  DestinationsOptions.java
//  This file is auto-generated by Amplitude. Run `ampli pull jetbrains` to update.
//
//  Works with versions 1.2+ of ly.iterative.itly:sdk and plugins
//  https://search.maven.org/search?q=itly
//

package snyk.analytics;

public class DestinationsOptions {

  private DestinationsOptions(Builder builder) {
  }

  public static IBuild builder() {
    return new Builder();
  }

  public static class Builder implements IBuild {
        private Builder() {}


        public DestinationsOptions build() {
            return new DestinationsOptions(this);
        }
    }

    public interface IBuild {
        DestinationsOptions build();
    }
}
