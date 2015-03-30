package de.thetaphi.forbiddenapis;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DeprecatedGen5 extends DeprecatedGen<File> {

  protected DeprecatedGen5(String javaVersion, File source, File output) {
    super(javaVersion, source, output);
  }

  @Override
  protected void collectClasses(File source) throws IOException {
    final InputStream in = new FileInputStream(source);
    try { 
      final ZipInputStream zip = new ZipInputStream(in);
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        try {
          if (entry.isDirectory()) continue;
          if (entry.getName().endsWith(".class")) {
            parseClass(zip);
          }
        } finally {
          zip.closeEntry();
        }
      }
    } finally {
      in.close();
    }
  }
  
  @SuppressForbidden
  public static void main(String... args) throws Exception {
    if (args.length != 2) {
      System.err.println("Invalid parameters; must be: java_version /path/to/outputfile.txt");
      System.exit(1);
    }
    new DeprecatedGen5(args[0], new File(System.getProperty("java.home"), "lib/rt.jar"), new File(args[1])).run();
  }
}
