import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.theia.cloud.operator.util.JavaResourceUtil;

public class JavaResourceUtilTest {
    final static String resourcesPath = Paths.get("./", "src", "test", "resources").toString();

    final static String DEPLOYMENT_YAML = "templateDeployment.yaml";
    final static String ENVVARMAP_EXPECTED_DEPLOYMENT_YAML = "templateDeployment_expectedEnvVars.yaml";
    final static String CONFIGMAP_ENVVAR_EXPECTED_DEPLOYMENT_YAML = "templateDeployment_expectedConfigMapEnv.yaml";
    final static String CONFIGMAP_ENVVAR_EXPECTED_DEPLOYMENT_YAML_MORE = "templateDeployment_expectedConfigMapEnv_more.yaml";


    final static String DEPLOYMENT_YAML_CONTAINER_NAME = "placeholder-definitionname";

    private static String loadTemplate(String templateFileName) throws IOException {
        String path = Paths.get(resourcesPath, "test_templates", templateFileName).toString();
        return new String(Files.readAllBytes(Paths.get(path)));
    }
    private void assertEqualsYamls(String expected, String produced) throws JsonProcessingException {
        // for more robust checking, compare their json trees instead of strings directly
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode expectedTree = mapper.readTree(expected);
        JsonNode producedTree = mapper.readTree(produced);

        assertEquals(expectedTree, producedTree);
    }

    // TODO: Add tests for malformed yaml, non existing container
    // TODO: ownerReference and namespace fields added to test yaml?!!! WTF? Where does it add.
    @Test
    void testHappyPath_addEnvVarMapToDeploymentYaml() throws IOException{
        String testYaml = loadTemplate(DEPLOYMENT_YAML);
        String expectedYaml = loadTemplate(ENVVARMAP_EXPECTED_DEPLOYMENT_YAML);

        Map<String, String> newEnvVars = Map.of(
            "UNICORN_COLOR", "orange",
            "HIPPO_WEIGHT", "89kg"
        );

        String producedYaml = JavaResourceUtil.addEnvVarMapToDeploymentYaml(
            testYaml, newEnvVars, DEPLOYMENT_YAML_CONTAINER_NAME
        );

        assertEqualsYamls(expectedYaml, producedYaml);
    }

    @Test
    void testNonExistingContainer_addEnvVarMapToDeploymentYaml() throws IOException {
        String testYaml = loadTemplate(DEPLOYMENT_YAML);

        Map<String, String> newEnvVars = Map.of(
            "UNICORN_COLOR", "orange",
            "HIPPO_WEIGHT", "89kg"
        );

        assertThrows(NoSuchElementException.class, () -> {
            JavaResourceUtil.addEnvVarMapToDeploymentYaml(testYaml, newEnvVars, "not-existing-container");
        });
    }

    @Test
    void testHappyPath_addConfigMapRefToEnvFromDeploymentYaml() throws IOException {
        String testYaml = loadTemplate(DEPLOYMENT_YAML);
        String expectedYaml = loadTemplate(CONFIGMAP_ENVVAR_EXPECTED_DEPLOYMENT_YAML);

        List<String> newCmRefs = List.of("my-cm", "my-cm2");
        List<String> newSecretRefs = List.of("my-secret", "my-secret2");

        // Add configMapRefs
        String producedYaml = JavaResourceUtil.addConfigMapRefToEnvFromDeploymentYaml(
            testYaml, newCmRefs, DEPLOYMENT_YAML_CONTAINER_NAME, false
        );
        
        // Add secretRefs
        producedYaml = JavaResourceUtil.addConfigMapRefToEnvFromDeploymentYaml(
            producedYaml, newSecretRefs, DEPLOYMENT_YAML_CONTAINER_NAME, 
        true);

        assertEqualsYamls(expectedYaml, producedYaml);
    }   

    @Test
    void testWithExistingFromEnv_addConfigMapRefToEnvFromDeploymentYaml() throws IOException {
        String testYaml = loadTemplate(CONFIGMAP_ENVVAR_EXPECTED_DEPLOYMENT_YAML);
        String expectedYaml = loadTemplate(CONFIGMAP_ENVVAR_EXPECTED_DEPLOYMENT_YAML_MORE);
        List<String> newCmRefs = List.of("this-cm-is-added-later");
        String producedYaml = JavaResourceUtil.addConfigMapRefToEnvFromDeploymentYaml(
            testYaml, newCmRefs, DEPLOYMENT_YAML_CONTAINER_NAME, false
        );

        assertEqualsYamls(expectedYaml, producedYaml);

    }


    
}
