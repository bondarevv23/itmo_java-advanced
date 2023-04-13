package info.kgeorgiy.ja.bondarev.walk;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class Walk {
    private static final String HASH_ALGORITHM = "SHA-256";
    private static String hexFileHashSum(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            try (DigestInputStream digestInputStream = new DigestInputStream(
                    new BufferedInputStream(new FileInputStream(file)), digest
            )) {
                byte[] buffer = new byte[1024 * 8];
                while (digestInputStream.read(buffer) != -1) {
                    // no operations.
                }
                return HexFormat.of().formatHex(digest.digest());
            } catch (IOException | SecurityException e) {
                System.out.println("Can't read file: " + e.getMessage());
            }
            return HexFormat.of().formatHex(new byte[digest.getDigestLength()]);
        } catch (NoSuchAlgorithmException | SecurityException e) {
            throw new RuntimeException("Wrong algorithm name");
        }
    }

    public static void main(String[] args) {
        if (args == null) {
            System.out.println("Array of arguments is null");
            return;
        }
        if (args.length != 2) {
            System.out.println("Expected 2 arguments, but get " + args.length);
            return;
        }
        if (args[0] == null) {
            System.out.println("First argument must be not null");
            return;
        }
        if (args[1] == null) {
            System.out.println("Second argument must be not null");
            return;
        }
        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);
        try {
            File parent = outputFile.getParentFile();
            if (!inputFile.exists()) {
                System.out.println("Input file not found");
                return;
            }
            if (!inputFile.isFile()) {
                System.out.println("Input is not file");
                return;
            }
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                System.out.println("Couldn't create directory: " + parent);
                return;
            }
        } catch (SecurityException e) {
            System.out.println("Can't get access to file " + e.getMessage());
            return;
        }

        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(inputFile, StandardCharsets.UTF_8));
             BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))) {
            String fileName;
            while ((fileName = bufferedReader.readLine()) != null) {
                bufferedWriter.write(hexFileHashSum(new File(fileName)) + " " + fileName);
                bufferedWriter.newLine();
            }
        } catch (IOException | SecurityException e) {
            System.out.println("Can't read or write file " + e.getMessage());
        }
    }
}
