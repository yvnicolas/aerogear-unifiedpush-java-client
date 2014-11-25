/**
 * JBoss, Home of Professional Open Source
 * Copyright Red Hat, Inc., and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.aerogear.unifiedpush;

import net.iharder.Base64;
import org.jboss.aerogear.unifiedpush.http.HttpClient;
import org.jboss.aerogear.unifiedpush.message.MessageResponseCallback;
import org.jboss.aerogear.unifiedpush.message.UnifiedMessage;
import org.jboss.aerogear.unifiedpush.model.ProxyConfig;
import org.jboss.aerogear.unifiedpush.model.TrustStoreConfig;
import org.jboss.aerogear.unifiedpush.utils.PushConfiguration;

import java.net.HttpURLConnection;
import java.net.Proxy;
import java.nio.charset.Charset;
import java.util.logging.Logger;

import static org.jboss.aerogear.unifiedpush.utils.ValidationUtils.isEmpty;


public class DefaultPushSender implements PushSender {

    private static final Logger logger = Logger.getLogger(DefaultPushSender.class.getName());

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final String serverURL;
    private final ProxyConfig proxy;
    private final TrustStoreConfig customTrustStore;
    private final String pushApplicationId;
    private final String masterSecret;


    /**
     * Only called by builder.
     */
    private DefaultPushSender(Builder builder) {
        serverURL = builder.rootServerURL;
        pushApplicationId = builder.pushApplicationId;
        masterSecret = builder.masterSecret;
        proxy = builder.proxy;
        customTrustStore = builder.customTrustStore;
    }

    public static Builder withRootServerURL(String rootServerURL) {
        return new Builder(rootServerURL);
    }

    public static Builder withConfig(String location) {
        return new Builder(PushConfiguration.read(location));
    }

    /**
     * Builder to build Client with more configuration.
     */
    public static class Builder {

        private final String rootServerURL;
        private String pushApplicationId;
        private String masterSecret;
        private ProxyConfig proxy;
        private TrustStoreConfig customTrustStore;

        public Builder(String rootServerURL) {
            if (isEmpty(rootServerURL)) {
                throw new IllegalStateException("server can not be null");
            }
            this.rootServerURL = !rootServerURL.endsWith("/") ? rootServerURL + '/' : rootServerURL;
        }

        public Builder(PushConfiguration pushConfiguration) {
            this.rootServerURL = pushConfiguration.getServerUrl();
            this.masterSecret = pushConfiguration.getMasterSecret();
            this.pushApplicationId = pushConfiguration.getPushApplicationId();
        }


        /**
         * Specifies which Push Application the sender will be using.
         *
         * @param pushApplicationId The pushApplicationID
         * @return the current {@link Builder} instance
         */
        public Builder pushApplicationId(String pushApplicationId) {
            this.pushApplicationId = pushApplicationId;
            return this;
        }

        /**
         * Set the masterSecret used to authenticate against the Push Server.
         *
         * @param masterSecret The masterSecret
         * @return the current {@link Builder} instance
         */
        public Builder masterSecret(String masterSecret){
            this.masterSecret = masterSecret;
            return this;
        }

        /**
         * Set a custom trustStore.
         * 
         * @param trustStorePath The trustStore file path.
         * @param trustStoreType The trustStore type. If null the default type iss used.
         * @param trustStorePassword The trustStore password.
         * @return the current {@link Builder} instance
         */
        public Builder customTrustStore(String trustStorePath, String trustStoreType, String trustStorePassword) {
            customTrustStore = new TrustStoreConfig(trustStorePath, trustStoreType, trustStorePassword);
            return this;
        }

        /**
         * Specify proxy that should be used to connect.
         * 
         * @param proxyHost Hostname of proxy.
         * @param proxyPort Port of proxy.
         * @return the current {@link Builder} instance
         */
        public Builder proxy(String proxyHost, int proxyPort) {
            if (proxy == null) {
                proxy = new ProxyConfig(Proxy.Type.HTTP);
            }
            proxy.setProxyHost(proxyHost);
            proxy.setProxyPort(proxyPort);
            return this;
        }

        /**
         * If proxy needs authentication, specify User.
         * 
         * @param proxyUser Username for authentication.
         * @return the current {@link Builder} instance
         */
        public Builder proxyUser(String proxyUser) {
            if (proxy == null) {
                proxy = new ProxyConfig(Proxy.Type.HTTP);
            }
            proxy.setProxyUser(proxyUser);
            return this;
        }

        /**
         * Sets password used with specified user.
         * 
         * @param proxyPassword Password for user authentication.
         * @return the current {@link Builder} instance
         */
        public Builder proxyPassword(String proxyPassword) {
            if (proxy == null) {
                proxy = new ProxyConfig(Proxy.Type.HTTP);
            }
            proxy.setProxyPassword(proxyPassword);
            return this;
        }

        /**
         * Configure type of proxy.
         * 
         * @param proxyType Type of proxy as
         * @return the current {@link Builder} instance
         */
        public Builder proxyType(Proxy.Type proxyType) {
            if (proxy == null) {
                proxy = new ProxyConfig(Proxy.Type.HTTP);
            }
            proxy.setProxyType(proxyType);
            return this;
        }

        /**
         * Build the {@link DefaultPushSender}.
         * 
         * @return the built up {@link DefaultPushSender}
         */
        public DefaultPushSender build() {
            return new DefaultPushSender(this);
        }
    }

    /**
     * Construct the URL fired against the Unified Push Server
     * 
     * @return a StringBuilder containing the constructed URL
     */
    protected String buildUrl() {
        if (isEmpty(getServerURL())) {
            throw new IllegalStateException("server can not be null");
        }

        return getServerURL() + "rest/sender/";
    }

    @Override
    public void send(UnifiedMessage unifiedMessage, MessageResponseCallback callback) {
        String jsonString = unifiedMessage.getObject().toJsonString();
        // fire!
        submitPayload(buildUrl(), jsonString, pushApplicationId, masterSecret, callback);
    }

    @Override
    public void send(UnifiedMessage unifiedMessage) {
        send(unifiedMessage, null);
    }

    /**
     * The actual method that does the real send and connection handling
     * 
     * @param url the URL to use for the HTTP POST request.
     * @param jsonPayloadObject the JSON payload of the POST request
     * @param pushApplicationId the registered applications identifier.
     * @param masterSecret the master secret for the push server.
     * @param callback the {@link MessageResponseCallback} that will be called once the POST request completes.
     */
    private void submitPayload(String url, String jsonPayloadObject, String pushApplicationId, String masterSecret,
            MessageResponseCallback callback) {
        String credentials = pushApplicationId + ':' + masterSecret;
        int statusCode;

        HttpURLConnection httpURLConnection = null;
        try {
            String encoded = Base64.encodeBytes(credentials.getBytes(UTF_8));

            // POST the payload to the UnifiedPush Server
            httpURLConnection = (HttpURLConnection) HttpClient.post(url, encoded, jsonPayloadObject, UTF_8, proxy,
                    customTrustStore);

            statusCode = httpURLConnection.getResponseCode();
            logger.info(String.format("HTTP Response code from UnifiedPush Server: %s", statusCode));

            // if we got a redirect, let's extract the 'Location' header from the response
            // and submit the payload again
            if (isRedirect(statusCode)) {
                String redirectURL = httpURLConnection.getHeaderField("Location");
                logger.info(String.format("Performing redirect to '%s'", redirectURL));
                // execute the 'redirect'
                submitPayload(redirectURL, jsonPayloadObject, pushApplicationId, masterSecret, callback);
            } else {
                if (callback != null) {
                    callback.onComplete(statusCode);
                }
            }

        } catch (Exception e) {
            logger.severe("Send did not succeed: " + e.getMessage());
            if (callback != null) {
                callback.onError(e);
            }
        } finally {
            // tear down
            if (httpURLConnection != null) {
                httpURLConnection.disconnect();
            }

        }
    }

    /**
     * checks if the given status code is a redirect (301, 302 or 303 response status code)
     */
    private static boolean isRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM ||
                statusCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                statusCode == HttpURLConnection.HTTP_SEE_OTHER;
    }

    /**
     * Get the used server URL.
     * 
     * @return The Server that is used
     */
    @Override
    public String getServerURL() {
        return serverURL;
    }

    @Override
    public ProxyConfig getProxy() {
        return proxy;
    }

    @Override
    public TrustStoreConfig getCustomTrustStore() {
        return customTrustStore;
    }

    @Override
    public String getPushApplicationId() {
        return pushApplicationId;
    }

    @Override
    public String getMasterSecret() {
        return masterSecret;
    }

}
