import org.junit.jupiter.api.Test;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.theia.cloud.operator.util.JavaResourceUtil;

public class JavaResourceUtilTest {
    static String resourcesPath = Paths.get("./", "src", "test", "resources").toString();

    // TODO: Add tests for malformed yaml, non existing container
    // TODO: ownerReference and namespace fields added to test yaml?!!! WTF? Where does it add.
    @Test
    void testHappyPath_addEnvVarMapToDeploymentYaml() throws FileNotFoundException, IOException {
    
        String testYamlPath = Paths.get(resourcesPath, "test_templates", "templateDeployment_bare_envvar.yaml").toString();
        String expectedYamlPath = Paths.get(resourcesPath, "test_templates", "templateDeployment_bare_envvar_ground_truth.yaml").toString();

        String testYaml = new String(Files.readAllBytes(Paths.get(testYamlPath)));
        String expectedYaml = new String(Files.readAllBytes(Paths.get(expectedYamlPath)));
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

    
}
