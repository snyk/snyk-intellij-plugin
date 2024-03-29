//
//  PluginIsUninstalled.java
//  This file is auto-generated by Amplitude. Run `ampli pull jetbrains` to update.
//
//  Works with versions 1.2+ of ly.iterative.itly:sdk and plugins
//  https://search.maven.org/search?q=itly
//

package snyk.analytics;

import ly.iterative.itly.Event;

import java.util.HashMap;

public class PluginIsUninstalled extends Event {
    private static final String NAME = "Plugin Is Uninstalled";
    private static final String ID = "5936cb0e-2639-4b76-baea-f0c086b860b0";
  private static final String VERSION = "1.0.2";

    public enum Ide {
        VISUAL_STUDIO_CODE("Visual Studio Code"), VISUAL_STUDIO("Visual Studio"), ECLIPSE("Eclipse"), JETBRAINS("JetBrains");

      private final String ide;

        public String getIde()
        {
            return this.ide;
        }

        Ide(String ide)
        {
            this.ide = ide;
        }
    }

    private PluginIsUninstalled(Builder builder) {
        super(NAME, builder.properties, ID, VERSION);
    }

    private PluginIsUninstalled(PluginIsUninstalled clone) {
        super(NAME, new HashMap<>(clone.getProperties()), ID, VERSION);
    }

    public PluginIsUninstalled clone() {
        return new PluginIsUninstalled(this);
    }

    public static IIde builder() { return new Builder(); }

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

        public PluginIsUninstalled build() {
            return new PluginIsUninstalled(this);
        }
    }

    // Required property interfaces
    public interface IIde {
        Builder ide(Ide ide);
    }

    /** Build interface with optional properties */
    public interface IBuild {
        PluginIsUninstalled build();
    }
}
