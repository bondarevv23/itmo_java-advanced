package info.kgeorgiy.ja.bondarev.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.ToolProvider;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

/**
 * Class that implements the {@link Impler} and {@link JarImpler} interfaces.
 * Supports calling the {@link JarImpler#implementJar(Class, Path)} method by
 * the main method.
 */
public class Implementor implements JarImpler {
    /**
     * Format constant equal to {@link System#lineSeparator()}.
     */
    private static final String NEW_LINE = System.lineSeparator();

    /**
     * Format constant equal to four spaces. Used to make the
     * generated class support the style of the Kernighan code.
     */
    private static final String SPACES = "    ";

    /**
     * The main function is used to generate a jar file with the
     * implementation of the passed descriptor.It takes the string
     * name of the interface descriptor and the path of the jar file
     * and calls the {@link #implementJar(Class, Path)} method.
     * If the passed data is incorrect or the {@link #implementJar(Class, Path)}
     * method will fail with an exception, the function will display a
     * corresponding message in {@link System#err}.
     *
     * @param args command line arguments. <code>-jar</code> is expected as the first
     *             argument, the second and third arguments are the
     *             interface descriptor and the path to the jar file,
     *             respectively.
     */
    public static void main(String[] args) {
        if (args == null || args.length != 3) {
            System.err.println("3 arguments expected");
            return;
        }
        if (!Arrays.stream(args).filter(Objects::isNull).toList().isEmpty()) {
            System.err.println("some arguments are null");
            return;
        }
        if (!args[0].equals("-jar")) {
            System.err.printf("unknown first argument: \"%s\"%n", args[0]);
            return;
        }

        final Path zipPath;
        final Class<?> token;
        try {
            token = Class.forName(args[1]);
            zipPath = Path.of(args[2]);
        } catch (ClassNotFoundException | LinkageError e) {
            System.err.printf("second argument is invalid name of class: %s%n", args[1]);
            return;
        } catch (InvalidPathException exception) {
            System.err.printf("third argument is invalid path: %s%n", args[2]);
            return;
        }

        try {
            new Implementor().implementJar(token, zipPath);
        } catch (ImplerException exception) {
            System.err.printf("can't implement passed interface: %s%n", exception.getMessage());
        }
    }

    /**
     * returns the unicode string representation of the given character.
     * @param ch char
     * @return unicode {@link String}
     */
    private static String charToUnicode(char ch) {
        return String.format("\\u%04x", (int) ch);
    }

    /**
     * Escapes all characters in a string to unicode
     * @param string to be converted to unicode
     * @return unicode {@link String}
     */
    private static String stringToUnicode(String string) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            builder.append(charToUnicode(string.charAt(i)));
        }
        return builder.toString();
    }

    /**
     * Create jar file of empty implementation of the passed interface and save it to <var>.jar</var> file.
     *
     * @param token type token to create implementation for.
     * @param jarFile target <var>.jar</var> file.
     * @throws ImplerException if
     * <ul>
     *      <li>{@link #tryCreateDirectories(Path)} from jarFile throw it</li>
     *      <li>jarFile has a parent and {@link Files#createTempDirectory(Path, String, FileAttribute[])}
     *      from jarFile throw {@link IOException} or {@link SecurityException}</li>
     *      <li>{@link #tryDeleteDirectory(Path)} throws {@link IOException} from temporary directory</li>
     *      <li>{@link #implement(Class, Path)} throws {@link ImplerException}</li>
     *      <li>{@link #compileClass(Path, Class)} return code of generated
     *      class not equal to 0</li>
     *      <li>{@link #writeJar(Class, Path, Path)} from token temporary path and jarFile throws it</li>
     * </ul>
     * @see #tryCreateDirectories(Path)
     * @see Files#createTempDirectory(Path, String, FileAttribute[])
     * @see #tryDeleteDirectory(Path)
     * @see #implement(Class, Path)
     * @see #compileClass(Path, Class)
     * @see #writeJar(Class, Path, Path)
     */
    @Override
    public void implementJar(Class<?> token, Path jarFile) throws ImplerException {
        final Path temporaryPath;
        try {
            tryCreateDirectories(jarFile);
            temporaryPath = (jarFile.getParent() != null ?
                    Files.createTempDirectory(jarFile.getParent(), "temporary") :
                    Path.of("temporary"));
            try {
                new Implementor().implement(token, temporaryPath);
                final int returnCode = compileClass(temporaryPath, token);
                if (returnCode != 0) {
                    throw new ImplerException(
                            String.format("can't compile generated class, return code: %d", returnCode)
                    );
                }
                writeJar(token, temporaryPath, jarFile);
            } finally {
                tryDeleteDirectory(temporaryPath);
            }
        } catch (IOException | SecurityException exception) {
            throw new ImplerException("can't create temporary directory", exception);
        }
    }

    /**
     * Compiles the class located at the passed path and implementing the interface
     * of the passed descriptor.
     *
     * @param path path of compiling class
     * @param clazz descriptor of compiling class
     * @return return code of compilation
     * @throws ImplerException if URL of location of source code of passed clazz is not formatted or
     * {@link Class#getProtectionDomain()} from clazz throw {@link SecurityException}
     */
    private static int compileClass(Path path, Class<?> clazz) throws ImplerException {
        try {
            return ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-encoding", "UTF-8",
                    "-classpath",
                    Path.of(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(),
                    path.resolve(getLocalPathToClass(clazz, "Impl.java", File.separatorChar)).toString()
            );
        } catch (URISyntaxException | SecurityException | InvalidPathException exception) {
            throw new ImplerException("can't compile passed class", exception);
        }
    }

    /**
     * Creates a jar file on the path <var>to</var> and saves to it
     * the file located on the path <var>from</var> and on the local path
     * of the passed descriptor <var>clazz</var>.
     *
     * @param clazz descriptor of class that located in <var>from</var> path
     * @param from path where located class generated by descriptor
     * @param to path where it will be created <var>jar</var> archive
     * @throws ImplerException if an {@link IOException} or {@link SecurityException}
     * will be thrown during the creation of an OutputStream, the creation
     * of a ZipEntry, or during a copy operation
     * @see Files#newOutputStream(Path, OpenOption...)
     * @see JarOutputStream#putNextEntry(ZipEntry)
     * @see Files#copy(Path, OutputStream)
     */
    private static void writeJar(final Class<?> clazz, final Path from, final Path to) throws ImplerException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        final String fileName = getLocalPathToClass(clazz, "Impl.class", '/');
        try (final JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(to), manifest)) {
            jarOutputStream.putNextEntry(new ZipEntry(fileName));
            Files.copy(from.resolve(fileName), jarOutputStream);
        } catch (final IOException | SecurityException | InvalidPathException exception) {
            throw new ImplerException("can't write to jar file", exception);
        }
    }

    /**
     * Deletes the directory and all internal files at the given path. For this the
     * class {@link Files#walkFileTree(Path, FileVisitor)} is used
     *
     * @param path directory path to delete
     * @throws ImplerException if {@link Files#walkFileTree(Path, FileVisitor)} throws {@link IOException}
     * or {@link SecurityException} while traversing the tree
     * @see Files#walkFileTree(Path, FileVisitor)
     */
    private static void tryDeleteDirectory(Path path) throws ImplerException {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    return deletePath(file);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path file, IOException e) throws IOException {
                    return deletePath(file);
                }

                private FileVisitResult deletePath(Path file) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException exception) {
            throw new ImplerException("can't delete directory", exception);
        }
    }

    /**
     * Checks if the passed descriptor is an interface descriptor and not private.
     * If the handle is not an interface or private throws {@link ImplerException}
     *
     * @param token interface descriptor
     * @throws ImplerException if the passed descriptor is invalid
     */
    private void validateToken(Class<?> token) throws ImplerException {
        final int modifiers = token.getModifiers();
        if (!token.isInterface()) {
            throw new ImplerException("the passed token is not interface");
        }
        if (Modifier.isPrivate(modifiers)) {
            throw new ImplerException("the passed interface is private");
        }
    }

    /**
     * Creates the parent directories of the given path. if the passed path is a root
     * function does nothing else it calls the {@link Files#createDirectory(Path, FileAttribute[])}
     * method on the passed path's parent.
     *
     * @param path any path
     * @throws ImplerException if an I/O or Security error occurs
     */
    private static void tryCreateDirectories(Path path) throws ImplerException {
        if (path.getParent() == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException | SecurityException e) {
            throw new ImplerException("can't create package directory", e);
        }
    }

    /**
     * Gets the path string to the class implementation by descriptor
     * relative to the beginning of the package and add fileEnd to the end.
     *
     * @param clazz interface descriptor
     * @param fileEnd string to be added to the result
     * @return class path string relative to the beginning of the package plus fileEnd
     */
    private static String getLocalPathToClass(Class<?> clazz, String fileEnd, char separator) {
        return clazz.getPackageName().replace('.', separator) +
                separator + clazz.getSimpleName() + fileEnd;
    }

    /**
     * Generate class that implements interface in passed token escape it to unicode
     * and write it to root.
     *
     * @param token type token to create implementation for.
     * @param root root directory.
     * @throws ImplerException if
     * <ul>
     *     <li>the passed token did not pass validation</li>
     *     <li>{@link #tryCreateDirectories(Path)} throw it</li>
     *     <li>{@link Files#newBufferedWriter(Path, Charset, OpenOption...)} throw {@link IOException}
     *     or {@link SecurityException}</li>
     *     <li>If an I/O error occurs</li>
     * </ul>
     * @see #validateToken(Class)
     * @see #tryCreateDirectories(Path)
     * @see Files#newBufferedWriter(Path, Charset, OpenOption...)
     * @see #stringToUnicode(String) 
     */
    public void implement(Class<?> token, Path root) throws ImplerException {
        validateToken(token);
        final Path path;
        try {
            path = root.resolve(getLocalPathToClass(token, "Impl.java", File.separatorChar));
        } catch (InvalidPathException exception) {
            throw new ImplerException("can't resolve passed root", exception);
        }
        tryCreateDirectories(path);
        final StringBuilder code = new StringBuilder();
        generateCode(token, code);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(stringToUnicode(code.toString()));
        } catch (IOException | SecurityException exception) {
            throw new ImplerException("can't write to file", exception);
        }
    }

    /**
     * Generates class code that implements the passed interface and writes it to StringBuilder.
     * Generation includes: package generation,
     * class header generation and class body generation. Code is generated
     * in Kernighan style.
     *
     * @param token descriptor of interface
     * @param code StringBuilder to generating
     * @see #generatePackage(Class, StringBuilder)
     * @see #generateClassHeader(Class, StringBuilder) 
     * @see #generateMethods(Class, StringBuilder) 
     */
    private void generateCode(Class<?> token, StringBuilder code) {
        generatePackage(token, code);
        generateClassHeader(token, code);
        code.append("{");
        code.append(NEW_LINE);
        generateMethods(token, code);
        code.append("}");
        code.append(NEW_LINE);
    }

    /**
     * Generates a package class from the given interface.
     * This function does not generate anything if the packet of the passed descriptor
     * is null or empty. Generated code writes to passed StringBuilder. The
     * generated packet is equal to the packets of the passed descriptor. An
     * empty line is generated after the packet.
     *
     * @param token descriptor of interface
     * @param code StringBuilder to generating
     */
    private void generatePackage(Class<?> token, StringBuilder code) {
        if (token.getPackage() != null && !token.getPackage().getName().isEmpty()) {
            code.append("package");
            code.append(" ");
            code.append(token.getPackage().getName());
            code.append(";");
            code.append(NEW_LINE);
            code.append(NEW_LINE);
        }
    }

    /**
     * Generates a public class header from the given interface.
     * Generated code writes to passed StringBuilder. The name of
     * generated class header is <var>InterfaceName + Impl</var>.
     *
     * @param token descriptor of interface
     * @param code StringBuilder to generating
     */
    private void generateClassHeader(Class<?> token, StringBuilder code) {
        code.append("public");
        code.append(" ");
        code.append("class");
        code.append(" ");
        code.append(token.getSimpleName());
        code.append("Impl");
        code.append(" ");
        code.append("implements");
        code.append(" ");
        code.append(token.getCanonicalName());
        code.append(" ");
    }

    /**
     * Generates code for all abstract methods from the descriptor in turn.
     * The code of all methods writes to StringBuilder <var>code</var>. To
     * generate each individual function is used {@link #generateMethod(Method, StringBuilder)}
     *
     * @param token descriptor whose methods will be generated
     * @param code StringBuilder to generating
     * @see #generateMethod(Method, StringBuilder) 
     */
    private void generateMethods(Class<?> token, StringBuilder code) {
        for (Method method : getAbstractMethods(token)) {
            generateMethod(method, code);
        }
    }

    /**
     * Get list of all abstract methods from the token.
     *
     * @param token of class descriptor
     * @return {@link List} of all not abstract methods from descriptor
     */
    private List<Method> getAbstractMethods(Class<?> token) {
        return Arrays.stream(token.getMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .collect(Collectors.toList());
    }

    /**
     * Generate code of passed method to StringBuilder.
     * Code is generated in Kernighan style, method modifiers are generated completely
     * excluding the ABSTRACT and TRANSIENT modifiers. The return value
     * and parameter types are generated in the canonical name. The argument
     * names are taken from the descriptor. The {@link #generateReturnValue(Class)}
     * method is used to generate the default implementation.
     *
     * @param method descriptor of method that will be generated.
     * @param code StringBuilder to generating
     * @see #generateReturnValue(Class)
     */
    private void generateMethod(Method method, StringBuilder code) {
        // modifier
        code.append(SPACES);
        code.append(Modifier.toString(method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.TRANSIENT));
        code.append(" ");

        // return value
        code.append(method.getReturnType().getCanonicalName());
        code.append(" ");

        // method name
        code.append(method.getName());
        code.append(" ");

        // parameters
        code.append(Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getType().getCanonicalName() + " " + parameter.getName())
                .collect(Collectors.joining(", ", "(", ")")));
        code.append(" ");

        // throws
        if (method.getExceptionTypes().length != 0) {
            code.append("throws");
            code.append(" ");
            code.append(Arrays.stream(method.getExceptionTypes()).map(Class::getName)
                    .collect(Collectors.joining(", ")));
            code.append(" ");
        }

        // body
        code.append("{");
        code.append(NEW_LINE);
        code.append(SPACES);
        code.append(SPACES);
        code.append("return");
        code.append(" ");
        code.append(generateReturnValue(method.getReturnType()));
        code.append(";");
        code.append(NEW_LINE);
        code.append(SPACES);
        code.append("}");
        code.append(NEW_LINE);
    }

    /**
     * Return string of default return value by descriptor.
     *
     * @param clazz descriptor of return value
     * @return a string displaying the default value for the passed descriptor
     */
    private String generateReturnValue(Class<?> clazz) {
        if (clazz.equals(boolean.class)) {
            return "false";
        }
        if (clazz.equals(void.class)) {
            return "";
        }
        if (clazz.isPrimitive()) {
            return "0";
        }
        return "null";
    }

    /**
     * creates instance of implementor
     */
    public Implementor() { }
}
