//
//  HealthScoreIsClicked.java
//  This file is auto-generated by Iteratively. Run `itly pull jetbrains` to update.
//

package snyk.analytics;

import java.util.HashMap;

import ly.iterative.itly.Event;

public class HealthScoreIsClicked extends Event {
    private static final String NAME = "Health Score Is Clicked";
    private static final String ID = "47cf2487-2066-4f12-9846-cba17d1fa257";
    private static final String VERSION = "1.0.0";

    public enum Ide {
        VISUAL_STUDIO_CODE("Visual Studio Code"), VISUAL_STUDIO("Visual Studio"), ECLIPSE("Eclipse"), JETBRAINS("JetBrains");

        private String ide;

        public String getIde()
        {
            return this.ide;
        }

        Ide(String ide)
        {
            this.ide = ide;
        }
    }

    private HealthScoreIsClicked(Builder builder) {
        super(NAME, builder.properties, ID, VERSION);
    }

    private HealthScoreIsClicked(HealthScoreIsClicked clone) {
        super(NAME, new HashMap<>(clone.getProperties()), ID, VERSION);
    }

    public HealthScoreIsClicked clone() {
        return new HealthScoreIsClicked(this);
    }

    public static IIde builder() { return new Builder(); }

    // Inner Builder class with required properties
    public static class Builder implements IIde, IBuild {
        private final HashMap<String, Object> properties = new HashMap<String, Object>();

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

        public HealthScoreIsClicked build() {
            return new HealthScoreIsClicked(this);
        }
    }

    // Required property interfaces
    public interface IIde {
        Builder ide(Ide ide);
    }

    /** Build interface with optional properties */
    public interface IBuild {
        HealthScoreIsClicked build();
    }
}
