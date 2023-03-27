import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.theia.cloud.operator.util.JavaResourceUtil;

public class JavaResourceUtilTest {
    final static String resourcesPath = Paths.get("./", "src", "test", "resources").toString();

    static String testYaml;
    static String expectedYaml;

    @BeforeAll
    static void loadYamls() throws IOException {
        String testYamlPath = Paths.get(resourcesPath, "test_templates", "templateDeployment_bare_envvar.yaml").toString();
        String expectedYamlPath = Paths.get(resourcesPath, "test_templates", "templateDeployment_bare_envvar_ground_truth.yaml").toString();

        testYaml = new String(Files.readAllBytes(Paths.get(testYamlPath)));
        expectedYaml = new String(Files.readAllBytes(Paths.get(expectedYamlPath)));
    }

    // TODO: Add tests for malformed yaml, non existing container
    // TODO: ownerReference and namespace fields added to test yaml?!!! WTF? Where does it add.
    @Test
    void testHappyPath_addEnvVarMapToDeploymentYaml() throws JsonProcessingException{
    
        Map<String, String> newEnvVars = Map.of(
            "UNICORN_COLOR", "orange",
            "HIPPO_WEIGHT", "89kg"
        );

        String producedYaml = JavaResourceUtil.addEnvVarMapToDeploymentYaml(testYaml, newEnvVars, "placeholder-definitionname");
        // for more robust checking, compare their json trees instead of strings directly
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        JsonNode expectedTree = mapper.readTree(expectedYaml);
        JsonNode producedTree = mapper.readTree(producedYaml);

        assertEquals(expectedTree, producedTree);
    }

    @Test
    void testNonExistingContainer_addEnvVarMapToDeploymentYaml() {
        Map<String, String> newEnvVars = Map.of(
            "UNICORN_COLOR", "orange",
            "HIPPO_WEIGHT", "89kg"
        );

        assertThrows(NoSuchElementException.class, () -> {
            JavaResourceUtil.addEnvVarMapToDeploymentYaml(testYaml, newEnvVars, "not-existing-container");
        });
    }


    
}
