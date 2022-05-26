
package gov.tak.platform.config;


/**
 * An aggregate of parameters and resolvers to use when creating configurable objects with
 * ConfigFactories and ConfigLoaders.
 * 
 * 
 */
public class ConfigEnvironment {

    private PhraseParser.Parameters _phraseParms;
    private FlagsParser.Parameters _flagsParms;

    private ConfigEnvironment(final ConfigEnvironment e) {
        if (e._phraseParms != null) {
            _phraseParms = new PhraseParser.Parameters(e._phraseParms);
        }
        if (e._flagsParms != null) {
            _flagsParms = new FlagsParser.Parameters(e._flagsParms);
        }
    }

    private ConfigEnvironment() {
    }

    public PhraseParser.Parameters getPhraseParserParameters() {
        return _phraseParms;
    }

    public FlagsParser.Parameters getFlagsParserParameters() {
        return _flagsParms;
    }

    public Builder buildUpon() {
        return new Builder(this);
    }

    public static class Builder {

        private final ConfigEnvironment _e;

        public Builder() {
            _e = new ConfigEnvironment();
        }

        private Builder(ConfigEnvironment e) {
            _e = new ConfigEnvironment(e);
        }

        public ConfigEnvironment build() {
            return new ConfigEnvironment(_e);
        }

        public Builder setPhraseParserParameters(
                PhraseParser.Parameters phraseParms) {
            _e._phraseParms = phraseParms;
            return this;
        }

        public Builder setFlagsParameters(FlagsParser.Parameters flagsParms) {
            _e._flagsParms = flagsParms;
            return this;
        }

    }

}
