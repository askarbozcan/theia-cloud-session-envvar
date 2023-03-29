/********************************************************************************
 * Copyright (C) 2022 EclipseSource, Lockular, Ericsson, STMicroelectronics and 
 * others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 ********************************************************************************/
package org.eclipse.theia.cloud.operator.util;

import static org.eclipse.theia.cloud.common.util.LogMessageUtil.formatLogMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public final class JavaResourceUtil {

    private static final String TEMPLATES = "/templates";
    private static final Logger LOGGER = LogManager.getLogger(JavaResourceUtil.class);

    private JavaResourceUtil() {
    }

    public static String readResourceAndReplacePlaceholders(String resourceName, Map<String, String> replacements,
	    String correlationId) throws IOException, URISyntaxException {
	try (InputStream inputStream = getInputStream(resourceName, correlationId)) {
	    String template = new BufferedReader(new InputStreamReader(inputStream)).lines().parallel()
		    .collect(Collectors.joining("\n"));
	    for (Entry<String, String> replacement : replacements.entrySet()) {
		template = template.replace(replacement.getKey(), replacement.getValue());
		LOGGER.trace(formatLogMessage(correlationId,
			"Replaced " + replacement.getKey() + " with " + replacement.getValue() + " :\n" + template));
	    }
	    return template;
	}
    }


    /* ------ Utils for adding enviornment variables to deployment specs ----- */

    public static class MalformedDeploymentYamlException extends RuntimeException {
        MalformedDeploymentYamlException(String msg) {
            super(msg);
        }
    }

    public static String addEnvVarMapToDeploymentYaml(
        String yaml, Map<String, String> envVarMap, String containerName) throws NoSuchElementException, JsonProcessingException {

        if (envVarMap.size() == 0) {
            return yaml;
        }

        // Read the yaml
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode tree = mapper.readTree(yaml);

        JsonNode containersNode = getContainersNode(tree);

        // Add to env map of container (or create 'env' if it does not exist)
        JsonNode containerSpec = getContainerSpecNode(containersNode, containerName);

        JsonNode existingEnv = containerSpec.get("env");
        Map<String, String> newEnvMap = new HashMap<>();
        if (existingEnv != null) {
            for (JsonNode envKeyValue : existingEnv) {
                newEnvMap.put(
                    envKeyValue.get("name").asText(), 
                    envKeyValue.get("value").asText()
                );
            }
        }

        newEnvMap.putAll(envVarMap); // add new values to existing parsed env values

        // convert it into a json node we can plug into the tree
        ArrayNode newEnvMapNode = mapper.createArrayNode();
        for (Entry<String, String> kv : newEnvMap.entrySet()) {
            ObjectNode entry = mapper.createObjectNode();
            entry.put("name", kv.getKey());
            entry.put("value", kv.getValue());
            newEnvMapNode.add(entry);
        }

        // update "env" map in spec.spec.containers[containerIdx]
        ObjectNode containerNodeObj = (ObjectNode) containerSpec;
        containerNodeObj.set("env", newEnvMapNode);
        
        // Finally return serialized updated tree
        byte[] serialized = mapper.writeValueAsBytes(tree);
        return new String(serialized, StandardCharsets.UTF_8);
    }

    public static String addConfigMapRefToEnvFromDeploymentYaml(String yaml, List<String> cmNames, String containerName, boolean fromSecretRef) throws JsonProcessingException {
        if (cmNames.size() == 0) {
            return yaml;
        }

        // Read the yaml
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode tree = mapper.readTree(yaml);

        JsonNode containersNode = getContainersNode(tree);
        JsonNode containerSpec = getContainerSpecNode(containersNode, containerName);
        
        // get envFrom map (or create if doesn't exist)
        JsonNode envFromNode = containerSpec.get("envFrom");
        ObjectNode objContainerNode = (ObjectNode) containerSpec;
        if (envFromNode == null) {
            objContainerNode.set("envFrom", mapper.createArrayNode());
            envFromNode = containerSpec.get("envFrom");
        }

        String refMetaName = fromSecretRef ? "secretRef" : "configMapRef";
        ArrayNode arrEnvFromNode = (ArrayNode) envFromNode;
        for (String cmName : cmNames) {
            // name: cmName
            ObjectNode refNameNode = mapper.createObjectNode();
            refNameNode.put("name", cmName);

            // configMapRef:
            //  name: cmName
            ObjectNode newRefNode = mapper.createObjectNode();
            newRefNode.set(refMetaName, refNameNode);

            // add to the envFrom array
            arrEnvFromNode.add(newRefNode);
        }

        byte[] serialized = mapper.writeValueAsBytes(tree);
        return new String(serialized, StandardCharsets.UTF_8);
    }


    /* -------------- Util to find JSON node of the container spec -------------- */
    private static JsonNode getContainersNode(JsonNode tree) {

        // check if it contains the required fields
        JsonNode containersNode;
        try {
            containersNode = tree
            .get("spec")
            .get("template")
            .get("spec")
            .get("containers");

        } catch (NullPointerException npe) {
            MalformedDeploymentYamlException e = new MalformedDeploymentYamlException("Deployment yaml is malformed? spec.template.spec.containers field does not exist?");
            LOGGER.error("Malformed deployment yaml", e);
            throw e;
        }

        return containersNode;
    }
    private static JsonNode getContainerSpecNode(JsonNode containersNode, String containerName) {
        JsonNode containerSpec = null;
        for (JsonNode contJsonNode : containersNode) {
            if (contJsonNode.get("name").asText().equals(containerName)) {
                containerSpec = contJsonNode;
                break;
            }
        }
        
        if (containerSpec == null) {
            throw new NoSuchElementException("Container with name '" + containerName + "' does not exist in spec.");
        } else {
            return containerSpec;
        }   
    }


    protected static InputStream getInputStream(String resourceName, String correlationId)
	    throws FileNotFoundException {
	/* check if template is overridden */
	File file = Paths.get(TEMPLATES, resourceName).toFile();
	if (file.exists()) {
	    LOGGER.info(
		    formatLogMessage(correlationId, "Updating custom template read from " + file.getAbsolutePath()));
	    return new FileInputStream(file);
	}

	LOGGER.trace(formatLogMessage(correlationId, "Updating template read with classloader from " + resourceName));
	return JavaResourceUtil.class.getResourceAsStream(resourceName);
    }

}
