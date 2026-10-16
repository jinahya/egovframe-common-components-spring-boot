import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Rewrites a jar without the named entries, everything else byte-for-byte in order. Run by the
 * components module at package time as a single-file source program (JEP 330), no build of its own:
 * <pre>java tools/StripJarEntries.java target/x.jar egovframework/com/cmm/service/</pre>
 * Why: see the comment on the exec-maven-plugin execution in egovframe-common-components/pom.xml.
 */
public class StripJarEntries {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: StripJarEntries <jar> <entry>...");
            System.exit(2);
        }
        Path jar = Path.of(args[0]);
        List<String> entries = Arrays.asList(args).subList(1, args.length);
        Path tmp = jar.resolveSibling(jar.getFileName() + ".tmp");
        int removed = 0;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (entries.contains(entry.getName())) {
                    removed++;
                    continue;
                }
                ZipEntry copy = new ZipEntry(entry.getName());
                copy.setTime(entry.getTime());
                if (entry.getMethod() == ZipEntry.STORED) {
                    byte[] data = readAll(in);
                    copy.setMethod(ZipEntry.STORED);
                    copy.setSize(data.length);
                    copy.setCompressedSize(data.length);
                    copy.setCrc(entry.getCrc());
                    out.putNextEntry(copy);
                    out.write(data);
                } else {
                    out.putNextEntry(copy);
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
        Files.move(tmp, jar, StandardCopyOption.REPLACE_EXISTING);
        if (removed != entries.size()) {
            System.err.println("StripJarEntries: expected to remove " + entries + " but removed " + removed);
            System.exit(1);
        }
        System.out.println("StripJarEntries: removed " + entries + " from " + jar);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toByteArray();
    }

    @SuppressWarnings("unused")
    private static void drain(InputStream in, OutputStream out) throws IOException {
        in.transferTo(out);
    }
}
