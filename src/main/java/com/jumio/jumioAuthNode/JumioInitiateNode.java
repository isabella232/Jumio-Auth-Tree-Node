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

import static com.jumio.jumioAuthNode.JumioConstants.CUSTOMER_INTERNAL_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.ERROR_URL;
import static com.jumio.jumioAuthNode.JumioConstants.REDIRECT_URL;
import static com.jumio.jumioAuthNode.JumioConstants.SUCCESS_URL;
import static com.jumio.jumioAuthNode.JumioConstants.TRANSACTION_REFERENCE;
import static com.jumio.jumioAuthNode.JumioConstants.TRANSACTION_STATUS;
import static com.jumio.jumioAuthNode.JumioConstants.USER_REFERENCE;

import org.apache.commons.lang.StringUtils;
import org.forgerock.json.JsonValue;
import org.forgerock.openam.auth.node.api.AbstractDecisionNode;
import org.forgerock.openam.auth.node.api.Action;
import org.forgerock.openam.auth.node.api.Node;
import org.forgerock.openam.auth.node.api.NodeProcessException;
import org.forgerock.openam.auth.node.api.SharedStateConstants;
import org.forgerock.openam.auth.node.api.TreeContext;
import org.forgerock.openam.core.realms.Realm;
import org.forgerock.openam.sm.AnnotatedServiceRegistry;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.sso.SSOException;
import com.sun.identity.authentication.spi.RedirectCallback;
import com.sun.identity.sm.SMSException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;


@Node.Metadata(outcomeProvider = AbstractDecisionNode.OutcomeProvider.class,
        configClass = JumioInitiateNode.Config.class)
public class JumioInitiateNode extends AbstractDecisionNode {


    private final Logger logger = LoggerFactory.getLogger(JumioInitiateNode.class);
    private final JumioService serviceConfig;

    /**
     * Configuration for the node.
     */
    public interface Config {

    }


    /**
     * Create the node using Guice injection. Just-in-time bindings can be used to obtain instances of other classes
     * from the plugin.
     **/
    @Inject
    public JumioInitiateNode(@Assisted Realm realm, AnnotatedServiceRegistry serviceRegistry)
            throws NodeProcessException {
        try {
            this.serviceConfig = serviceRegistry.getRealmSingleton(JumioService.class, realm).get();
        } catch (SSOException | SMSException e) {
            throw new NodeProcessException(e);
        }
        if (StringUtils.isEmpty(serviceConfig.serverUrl().toString()) || StringUtils.isEmpty(serviceConfig.token()) ||
                StringUtils.isEmpty(String.valueOf(serviceConfig.secret())) || StringUtils.isEmpty(
                serviceConfig.customerInternalReference()) || StringUtils.isEmpty(
                serviceConfig.merchantReportingCriteria())) {
            throw new NodeProcessException("One or values in the Jumio Service is empty");
        }
    }

    @Override
    public Action process(TreeContext context) throws NodeProcessException {

        Map<String, List<String>> parameters = context.request.parameters;
        JsonValue sharedState = context.sharedState;
        String streamToString, redirectURL, userReference;
        userReference = sharedState.get(SharedStateConstants.USERNAME).asString();


        if (parameters.containsKey(TRANSACTION_STATUS) && parameters.containsKey(CUSTOMER_INTERNAL_REFERENCE) &&
                parameters.containsKey(TRANSACTION_REFERENCE)) {
            if (sharedState.isDefined(TRANSACTION_STATUS) && sharedState.isDefined(CUSTOMER_INTERNAL_REFERENCE) &&
                    sharedState.isDefined(TRANSACTION_REFERENCE)) {
                // We have looped back from a unsuccessful ID proofing attempt, remove sharedState and continue
                sharedState.remove(TRANSACTION_STATUS);
                sharedState.remove(CUSTOMER_INTERNAL_REFERENCE);
                sharedState.remove(TRANSACTION_REFERENCE);
            } else {
                // We have returned from redirect, store Jumio data in shared state and go to next node
                sharedState.put(TRANSACTION_STATUS, parameters.get(TRANSACTION_STATUS).get(0));
                sharedState.put(TRANSACTION_REFERENCE, parameters.get(TRANSACTION_REFERENCE).get(0));
                sharedState.put(CUSTOMER_INTERNAL_REFERENCE, userReference);

                if (logger.isInfoEnabled()) {
                    logger.info("Returned from redirect.  Scan ID: " + parameters.get(TRANSACTION_REFERENCE).get(0) +
                                        " Status: " + parameters.get(CUSTOMER_INTERNAL_REFERENCE).get(0) +
                                        " Cust Ref: " +
                                        parameters.get(TRANSACTION_REFERENCE).get(0));
                }
                return goTo(true).replaceSharedState(sharedState).build();
            }
        }

        URL url;
        try {
            url = new URL(serviceConfig.serverUrl().toString() + "/api/v4/initiate");
        } catch (MalformedURLException e) {
            throw new NodeProcessException(e);
        }

        String auth = serviceConfig.token() + ":" + String.valueOf(serviceConfig.secret());
        auth = Base64.getEncoder().encodeToString(auth.getBytes());
        HttpURLConnection conn;

        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "Jumio ForgeRock/1.0.0");
            conn.setRequestProperty("Authorization", "Basic " + auth);
        } catch (IOException e) {
            throw new NodeProcessException(e);
        }

        JSONObject bodyObject = new JSONObject();
        bodyObject.put(CUSTOMER_INTERNAL_REFERENCE, serviceConfig.customerInternalReference());
        bodyObject.put(USER_REFERENCE, userReference);

        bodyObject.put(SUCCESS_URL, serviceConfig.redirectURI());
        bodyObject.put(ERROR_URL, serviceConfig.redirectURI());

        OutputStreamWriter wr;
        int responseCode;
        try {
            wr = new OutputStreamWriter(conn.getOutputStream());
            wr.write(bodyObject.toString());
            wr.flush();
            streamToString = JumioUtils.convertStreamToString(conn.getInputStream());
            responseCode = conn.getResponseCode();
        } catch (IOException e) {
            throw new NodeProcessException(e);
        }


        if (responseCode == 200) {
            JSONObject jo = new JSONObject(streamToString);
            // if successfully submitted, move the files to completed.

            if (logger.isInfoEnabled()) {
                logger.info("Scan ID: " + jo.getString(TRANSACTION_REFERENCE) + " initiated. ");
            }

            redirectURL = (String) jo.get(REDIRECT_URL);
        } else if (responseCode == 403) {
            throw new NodeProcessException("403: Invalid Credentials");
        } else if (responseCode == 400) {
            throw new NodeProcessException("400: Bad request.");

        } else {
            throw new NodeProcessException("Unknown.");
        }


        RedirectCallback redirectCallback = new RedirectCallback(redirectURL, null, "GET");
        redirectCallback.setTrackingCookie(true);
        return Action.send(redirectCallback).build();
    }

}
