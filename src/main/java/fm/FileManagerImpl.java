package fm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FileManagerImpl implements FileManager {
    @Override
    public List<File> listDir(String dir) throws IOException {
        return Files
                .walk(Paths.get(dir))
                .filter(Files::isRegularFile)
                .map(path -> path.toFile())
                .collect(Collectors.toList());
    }
}
