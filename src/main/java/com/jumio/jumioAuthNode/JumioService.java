package com.jumio.jumioAuthNode;

import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.annotations.sm.Config;
import org.forgerock.openam.sm.annotations.adapters.Password;

/**
 * Configuration for the node.
 */
@Config(scope = Config.Scope.REALM)
public interface JumioService {
    @Attribute(order = 100)
    default Server serverUrl() {
        return Server.US;
    };

    @Attribute(order = 150)
    String token();

    @Attribute(order = 200)
    @Password
    default char[] secret() { return new char[0]; };

    @Attribute(order = 250)
    String merchantReportingCriteria();

    @Attribute(order = 300)
    String customerInternalReference();

    @Attribute(order = 350)
    String redirectURI();

    /**
     * Units used by the heartbeat time interval setting.
     */
    public enum Server {
        /**
         * US Server.
         */
        US,
        /**
         * EU Server.
         */
        EU;

        @Override
        public String toString() {
            switch(this) {
                case US: return "https://netverify.com";
                case EU: return "https://lon.netverify.com";
                default: throw new IllegalArgumentException();
            }
        }
    }
}

