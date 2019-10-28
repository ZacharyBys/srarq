package fm;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface FileManager {
    List<File> listDir(String dir) throws IOException;
}
