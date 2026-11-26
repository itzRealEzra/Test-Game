import brut.androlib.smali.SmaliBuilder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Assemble one decoded Apktool smali tree into a DEX without rebuilding resources. */
public final class AssembleSmali {
    private AssembleSmali() { }

    private static void collectSmali(File directory, List<File> output) {
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) collectSmali(child, output);
            else if (child.isFile() && child.getName().endsWith(".smali")) output.add(child);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "Usage: AssembleSmali <smali-root> <output.dex> <api-level>");
        }
        File source = new File(args[0]).getCanonicalFile();
        File output = new File(args[1]).getCanonicalFile();
        int apiLevel = Integer.parseInt(args[2]);
        if (!source.isDirectory()) throw new IOException("Smali source directory not found: " + source);

        List<File> files = new ArrayList<>();
        collectSmali(source, files);
        Collections.sort(files);
        if (files.isEmpty()) throw new IOException("No .smali files found under " + source);

        DexBuilder dex = new DexBuilder(new Opcodes(apiLevel));
        SmaliBuilder assembler = new SmaliBuilder(apiLevel);
        int assembled = 0;
        for (File file : files) {
            if (!assembler.buildFile(file, dex)) {
                throw new IOException("Could not assemble " + file);
            }
            assembled++;
        }

        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create output directory: " + parent);
        }
        FileDataStore store = new FileDataStore(output);
        try {
            dex.writeTo(store);
        } finally {
            store.raf.close();
        }
        System.out.println("Assembled " + assembled + " smali files into "
                + output + " (" + output.length() + " bytes)");
    }
}
