package fm;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FileManagerTest {
    @Test
    public void listDir() throws IOException {
        FileManager fileManager = new FileManagerImpl();
        Path resourceDirectory = Paths.get("src","test","resources", "fm");
        String dir = resourceDirectory.toFile().getPath();
        List<File> files = fileManager.listDir(dir);
        assertThat(files.size()).isEqualTo(1);
    }
}
