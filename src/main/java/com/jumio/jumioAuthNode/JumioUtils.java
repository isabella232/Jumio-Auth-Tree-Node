package com.jumio.jumioAuthNode;

import org.forgerock.openam.auth.node.api.NodeProcessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

class JumioUtils {

    private JumioUtils() {}

    static String convertStreamToString(InputStream is) throws NodeProcessException {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            throw new NodeProcessException(e);
        }
        return sb.toString();
    }

}
