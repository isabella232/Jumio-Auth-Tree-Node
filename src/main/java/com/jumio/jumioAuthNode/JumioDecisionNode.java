/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2017-2018 ForgeRock AS.
 */


package com.jumio.jumioAuthNode;

import static com.jumio.jumioAuthNode.JumioConstants.ATTRIBUTES;
import static com.jumio.jumioAuthNode.JumioConstants.DONE;
import static com.jumio.jumioAuthNode.JumioConstants.FAILED;
import static com.jumio.jumioAuthNode.JumioConstants.FRAUD;
import static com.jumio.jumioAuthNode.JumioConstants.IDENTITY_VERIFICATION;
import static com.jumio.jumioAuthNode.JumioConstants.MATCH;
import static com.jumio.jumioAuthNode.JumioConstants.NOT_POSSIBLE;
import static com.jumio.jumioAuthNode.JumioConstants.NOT_READABLE;
import static com.jumio.jumioAuthNode.JumioConstants.NO_MATCH;
import static com.jumio.jumioAuthNode.JumioConstants.OUTCOME;
import static com.jumio.jumioAuthNode.JumioConstants.PENDING;
import static com.jumio.jumioAuthNode.JumioConstants.SCAN_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.SIMILARITY;
import static com.jumio.jumioAuthNode.JumioConstants.STATUS;
import static com.jumio.jumioAuthNode.JumioConstants.SUCCESS;
import static com.jumio.jumioAuthNode.JumioConstants.TRANSACTION_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.UID;
import static com.jumio.jumioAuthNode.JumioConstants.UNREADABLE;
import static com.jumio.jumioAuthNode.JumioConstants.UNSUPPORTED;
import static com.jumio.jumioAuthNode.JumioConstants.UNSUPPORTED_ID_COUNTRY;
import static com.jumio.jumioAuthNode.JumioConstants.UNSUPPORTED_ID_TYPE;
import static com.jumio.jumioAuthNode.JumioConstants.USERNAME;
import static com.jumio.jumioAuthNode.JumioConstants.USER_INFO;
import static com.jumio.jumioAuthNode.JumioConstants.USER_NAMES;
import static com.jumio.jumioAuthNode.JumioConstants.VERIFICATION;
import static org.forgerock.json.JsonValue.array;
import static org.forgerock.json.JsonValue.json;
import static org.forgerock.json.JsonValue.object;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.annotations.sm.Attribute;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.forgerock.util.i18n.PreferredLocales;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.sm.SMSException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;


@Node.Metadata(outcomeProvider = JumioDecisionNode.JumioDecisionOutcomeProvider.class,
        configClass = JumioDecisionNode.Config.class)
public class JumioDecisionNode extends AbstractDecisionNode {


    private final Logger logger = LoggerFactory.getLogger(JumioDecisionNode.class);
    private final JumioService serviceConfig;
    private final Config config;


    /**
     * Configuration for the node.
     */
    public interface Config {

        @Attribute(order = 100)
        Map<String, String> cfgAccountMapperConfiguration();

    }

    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of other classes
     * from the plugin.
     **/
    @Inject
    public JumioDecisionNode(@Assisted Config config, @Assisted Realm realm, AnnotatedServiceRegistry serviceRegistry)
            throws NodeProcessException {
        this.config = config;
        try {
            this.serviceConfig = serviceRegistry.getRealmSingleton(JumioService.class, realm).get();
        } catch (SSOException | SMSException e) {
            throw new NodeProcessException(e);
        }
    }

    private String checkStatus(String scanRef) throws NodeProcessException {

        try {
            String status;
            JSONObject jsonObj = getRetrievalMessage(
                    serviceConfig.serverUrl().toString() + "/api/netverify/v2/scans/" + scanRef);

            status = jsonObj.get(STATUS).toString();
            status = status.replaceAll("\"", "");

            return status;
        } catch (IOException e) {
            throw new NodeProcessException(e);
        }


    }

    /**
     * Retrieves the transaction results with the extracted data in a JSON object.
     */
    private JSONObject getVerificationResults(String scanRef) throws NodeProcessException {
        JSONObject result;

        try {
            JSONObject jsonObj = getRetrievalMessage(
                    serviceConfig.serverUrl().toString() + "/api/netverify/v2/scans/" + scanRef + "/data");

            result = jsonObj.getJSONObject("document");
            // Add the customerid, merchantReportingCriteria, merchantScanReference
            result.put(SCAN_REFERENCE, scanRef);
            JSONObject t = jsonObj.getJSONObject("transaction");
            result.put("customerId", t.getString("customerId"));

            // Check the ID verification and return if not APPROVED_VERIFIED with right outcome.
            String status = result.getString("status");
            if (status.equalsIgnoreCase("DENIED_FRAUD")) {
                result.put(OUTCOME, FRAUD);
                return result;
            } else if (status.equalsIgnoreCase(UNSUPPORTED_ID_TYPE) || status.equalsIgnoreCase(
                    UNSUPPORTED_ID_COUNTRY)) {
                result.put(OUTCOME, UNSUPPORTED);
                return result;

            } else if (status.equalsIgnoreCase(NOT_READABLE)) {
                result.put(OUTCOME, UNREADABLE);
                return result;
            }

            // Now check if selfie matches image on the ID.
            JSONObject v = (JSONObject) jsonObj.get(VERIFICATION);
            JSONObject iv = (JSONObject) v.get(IDENTITY_VERIFICATION);
            String similarity = iv.get(SIMILARITY).toString();
            similarity = similarity.replaceAll("\"", "");
            if (similarity.equals(MATCH)) {
                result.put(OUTCOME, SUCCESS);
            }
            if (similarity.equals(NO_MATCH)) {
                result.put(OUTCOME, FRAUD);
            }
            if (similarity.equals(NOT_POSSIBLE)) {
                result.put(OUTCOME, UNREADABLE);
            }
        } catch (IOException | NullPointerException muexc) {
            throw new NodeProcessException(muexc);
        }
        return result;
    }

    private JSONObject getRetrievalMessage(String serverURL)
            throws IOException, NullPointerException, NodeProcessException {

        String auth = serviceConfig.token() + ":" + String.valueOf(serviceConfig.secret());
        auth = Base64.getEncoder().encodeToString(auth.getBytes());

        URL url = new URL(serverURL);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Jumio ForgeRock/1.0.0");
        return new JSONObject(JumioUtils.convertStreamToString(conn.getInputStream()));
    }


    @Override
    public Action process(TreeContext context) throws NodeProcessException {
        JsonValue sharedState = context.sharedState;
        String scanReference = sharedState.get(TRANSACTION_REFERENCE).asString();

        String jumioStatus = checkStatus(scanReference);
        if (StringUtils.equalsIgnoreCase(jumioStatus, PENDING)) {
            return Action.goTo(PENDING).build();
        } else if (StringUtils.equalsIgnoreCase(jumioStatus, DONE)) {
            JSONObject results = getVerificationResults(scanReference);

            if (logger.isInfoEnabled()) {
                logger.info("Scan ID: " + scanReference + " Status: " + jumioStatus + " outcome: " +
                                    results.getString(OUTCOME));
            }

            //TODO: Call cfgAccountMapperConfiguration to get which Jumio attributes the customer wants to map to FR
            // attributes
            String outcome = results.getString(OUTCOME);
            switch (outcome) {
                case SUCCESS:
                    Map<String, String> map = config.cfgAccountMapperConfiguration();
                    try {
                        JsonValue attributes = json(object(map.size() + 1));
                        String username = sharedState.get(USERNAME).asString();
                        List<Object> uidArray = array();
                        uidArray.add(username);
                        attributes.put(UID, uidArray);

                        for (Map.Entry<String, String> entry : map.entrySet()) {
                            attributes.put(entry.getValue(), array(results.getString(entry.getKey())));
                        }
                        JsonValue userInfo = json(object());
                        userInfo.put(ATTRIBUTES, attributes);
                        JsonValue userNames = json(object(1));
                        List<Object> usernameArray = array();
                        usernameArray.add(username);

                        userNames.put(USERNAME, usernameArray);
                        userInfo.put(USER_NAMES, userNames);

                        sharedState.put(USER_INFO, userInfo);

                    } catch (JSONException je) {
                        if (logger.isInfoEnabled()) {
                            logger.info(je.getMessage());
                        }
                        throw new NodeProcessException(je);
                    }

                    return Action.goTo(SUCCESS).build();
                case FRAUD:
                    return Action.goTo(FRAUD).build();
                case UNREADABLE:
                    return Action.goTo(UNREADABLE).replaceSharedState(sharedState).build();
                case UNSUPPORTED:
                    return Action.goTo(UNSUPPORTED).replaceSharedState(sharedState).build();
                default:
                    return Action.goTo(FAILED).replaceSharedState(sharedState).build();
            }
        } else if (StringUtils.equalsIgnoreCase(jumioStatus, FAILED)) {
            return Action.goTo(FAILED).build();
        }

        throw new NodeProcessException("No outcome returned");

    }

    /**
     * Defines the possible outcomes from this node.
     */
    public static class JumioDecisionOutcomeProvider implements org.forgerock.openam.auth.node.api.OutcomeProvider {
        @Override
        public List<Outcome> getOutcomes(PreferredLocales locales, JsonValue nodeAttributes) {
            return new ArrayList<Outcome>() {{
                add(new Outcome(SUCCESS, SUCCESS));
                add(new Outcome(FAILED, FAILED));
                add(new Outcome(FRAUD, FRAUD));
                add(new Outcome(UNREADABLE, UNREADABLE));
                add(new Outcome(UNSUPPORTED, UNSUPPORTED));
                add(new Outcome(PENDING, PENDING));
            }};
        }
    }

}
