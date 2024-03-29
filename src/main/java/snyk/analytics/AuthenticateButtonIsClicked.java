//
//  AuthenticateButtonIsClicked.java
//  This file is auto-generated by Amplitude. Run `ampli pull jetbrains` to update.
//
//  Works with versions 1.2+ of ly.iterative.itly:sdk and plugins
//  https://search.maven.org/search?q=itly
//

package snyk.analytics;

import ly.iterative.itly.Event;

import java.util.HashMap;

public class AuthenticateButtonIsClicked extends Event {
  private static final String NAME = "Authenticate Button Is Clicked";
  private static final String ID = "2220c25f-ba76-4d5b-92f7-6d0e1c6165be";
  private static final String VERSION = "1.0.2";

  public enum EventSource {
    ADVISOR("Advisor"), APP("App"), LEARN("Learn"), IDE("IDE");

    private final String eventSource;

    public String getEventSource() {
      return this.eventSource;
    }

    EventSource(String eventSource) {
      this.eventSource = eventSource;
    }
  }

  public enum Ide {
    VISUAL_STUDIO_CODE("Visual Studio Code"), VISUAL_STUDIO("Visual Studio"), ECLIPSE("Eclipse"), JETBRAINS("JetBrains");

    private final String ide;

    public String getIde() {
      return this.ide;
    }

    Ide(String ide) {
      this.ide = ide;
    }
  }

  private AuthenticateButtonIsClicked(Builder builder) {
    super(NAME, builder.properties, ID, VERSION);
  }

  private AuthenticateButtonIsClicked(AuthenticateButtonIsClicked clone) {
    super(NAME, new HashMap<>(clone.getProperties()), ID, VERSION);
  }

  public AuthenticateButtonIsClicked clone() {
    return new AuthenticateButtonIsClicked(this);
  }

  public static IIde builder() {
    return new Builder();
  }

  // Inner Builder class with required properties
  public static class Builder implements IIde, IBuild {
    private final HashMap<String, Object> properties = new HashMap<>();

    private Builder() {
      this.properties.put("itly", true);
    }

    /**
     * Ide family.
     * <p>
     * Must be followed by by additional optional properties or build() method
     */
    public Builder ide(Ide ide) {
      this.properties.put("ide", ide.getIde());
      return this;
    }

    /**
     * Used to identify the source for multi-source events.
     * <p>
     * For example, if a given event is shared between Snyk Advisor and Snyk Learn, this property helps to differentiate between the two.
     */
    public Builder eventSource(EventSource eventSource) {
      this.properties.put("eventSource", eventSource.getEventSource());
      return this;
    }

    public AuthenticateButtonIsClicked build() {
      return new AuthenticateButtonIsClicked(this);
    }
  }

  // Required property interfaces
  public interface IIde {
    Builder ide(Ide ide);
  }

  /**
   * Build interface with optional properties
   */
  public interface IBuild {
    IBuild eventSource(EventSource eventSource);

    AuthenticateButtonIsClicked build();
  }
}
