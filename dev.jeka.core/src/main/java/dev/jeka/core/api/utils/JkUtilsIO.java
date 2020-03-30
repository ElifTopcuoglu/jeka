package dev.jeka.core.api.utils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipFile;

/**
 * Utility class for dealing with Inputs/Outputs.
 *
 * @author Jerome Angibaud
 */
public final class JkUtilsIO {

    private JkUtilsIO() {
    }

    /**
     * Creates a no-op print getOutputStream.
     */
    public static PrintStream nopPrintStream() {
        return new PrintStream(nopOuputStream());
    }

    /**
     * Creates a no-op outputStream.
     */
    public static OutputStream nopOuputStream() {
        return new OutputStream() {

            @Override
            public void write(int paramInt) throws IOException {
                // Do nothing
            }
        };
    }

    /**
     * Closes the specified closeable object, ignoring any exceptions.
     */
    public static void closeQuietly(Closeable... closeables) {
        for (final Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (final Exception e) {
                // Ignored
            }
        }
    }

    /**
     * Closes the specified closeable object, ignoring any exceptions.
     */
    public static void closeifClosable(Object closeable) {
        if (closeable != null && closeable instanceof  Closeable) {
            try {
                ((Closeable)closeable).close();
            } catch (final Exception e) {
                // Ignored
            }
        }
    }

    /**
     * Closes the specified closeable object, ignoring any exceptions.
     */
    public static void closeOrFail(Closeable... closeables) {
        for (final Closeable closeable : closeables) {
            try {
                if (closeable != null) {
                    closeable.close();
                }
            } catch (final IOException e) {
                throw new UncheckedIOException("Cannot close " + closeable, e);
            }
        }
    }

    /**
     * Same as {@link FileInputStream} constructor but throwing unchecked
     * exceptions.
     */
    public static FileInputStream inputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (final FileNotFoundException e) {
            throw new UncheckedIOException("File " + file + " not found.", e);
        }
    }

    /**
     * Same as {@link FileOutputStream} constructor but throwing unchecked
     * exceptions.
     */
    public static FileOutputStream outputStream(File file, boolean append) {
        try {
            return new FileOutputStream(file, append);
        } catch (final FileNotFoundException e) {
            throw new IllegalArgumentException("File " + file + " not found.", e);
        }
    }

    /**
     * Same as {@link URL#openStream()} but throwing only unchecked exceptions.
     */
    public static InputStream inputStream(URL file) {
        try {
            return file.openStream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Equivalent to {@link InputStream#read()} but throwing only unchecked
     * exceptions.
     */
    public static int read(InputStream inputStream) {
        try {
            return inputStream.read();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the content of the specified input getOutputStream, line by line.
     */
    public static List<String> readAsLines(InputStream in) {
        final List<String> result = new LinkedList<>();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                result.add(line);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    /**
     * Returns the content of the given url as a string.
     */
    public static String read(URL url) {
        try (InputStream is =  url.openStream()){
            return readAsString(is);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the content of the given input getOutputStream as a single string.
     */
    public static String readAsString(InputStream in) {
        final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        final StringBuilder out = new StringBuilder();
        final String newLine = System.getProperty("line.separator");
        String line;
        boolean firstTime = true;
        try {
            while ((line = reader.readLine()) != null) {
                if (!firstTime) {
                    out.append(newLine);
                }
                out.append(line);
                firstTime = false;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toString();
    }

    /**
     * Same as {@link ZipFile#close()} but throwing only unchecked exceptions.
     */
    public static void closeQuietly(ZipFile... zipFiles) {
        for (final ZipFile zipFile : zipFiles) {
            try {
                zipFile.close();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Copies the content of an url in a cache file. The cached file path will
     * be [jeka user dir]/cache/url-contents/[last segment of the url (after
     * last '/')]. If the file already exist than the content of the url is not
     * copied and the file is directly returned.
     */
    public static Path copyUrlContentToCacheFile(URL url, PrintStream report, Path cacheDir) {
        final String name = JkUtilsString.substringAfterLast(url.getPath(), "/");
        final Path result = cacheDir.resolve(name);
        if (Files.exists(result)) {
            if (report != null) {
                report.println("Url " + url.toExternalForm() + " transformed to file by reading existing cached file "
                        + result);
            }
            return result;
        }
        JkUtilsPath.createFileSafely(result);
        if (report != null) {
            report.println("Url " + url.toExternalForm() + " transformed to file by creating file "
                    + result);
        }
        copyUrlToFile(url, result);
        return result;
    }

    /**
     * Copies the content of the given url to the specified file.
     */
    public static void copyUrlToFile(URL url, Path file) {
        try (OutputStream fileOutputStream = Files.newOutputStream(file);
                final InputStream inputStream = url.openStream()){
            copy(inputStream, fileOutputStream);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Copies the content of the given input getOutputStream to a specified output
     * getOutputStream.
     */
    public static void copy(InputStream in, OutputStream out) {
        final byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Serializes a given Java object to the specified file.
     */
    public static void serialize(Object object, Path file) {
        try (OutputStream out = Files.newOutputStream(file)){
            serialize(object, out);
        } catch (final IOException e) {
            throw new UncheckedIOException("File must exist.", e);
        }
    }

    /**
     * Serializes a given Java object to the specified output getOutputStream.
     */
    public static void serialize(Object object, OutputStream outputStream) {
        try {
            final OutputStream buffer = new BufferedOutputStream(outputStream);
            try (ObjectOutput output = new ObjectOutputStream(buffer)) {
                output.writeObject(object);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Error while serializing " + object, e);
        }
    }

    /**
     * Deserializes the content of the specified file to a Java object.
     */
    public static <T> T deserialize(Path file) {
        try {
            return (T) deserialize(Files.newInputStream(file));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Deserializes the content of the specified input getOutputStream to a Java object.
     */
    public static <T> T deserialize(InputStream inputStream) {
        return deserialize(inputStream, JkUtilsIO.class.getClassLoader());
    }

    /**
     * Deserialises the content of a given input file to a Java object loaded in
     * the specified classloader.
     */
    public static <T> T deserialize(InputStream inputStream, final ClassLoader classLoader) {
        try (final InputStream buffer = new BufferedInputStream(inputStream)) {
            final ObjectInput input = new ObjectInputStream(buffer) {

                @Override
                protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {

                    final String name = desc.getName();
                    try {
                        return Class.forName(name, false, classLoader);
                    } catch (final ClassNotFoundException ex) {
                        final Class<?> cl = primClasses.get(name);
                        if (cl != null) {
                            return cl;
                        } else {
                            throw ex;
                        }
                    }

                }

            };
            return (T) input.readObject();
        } catch (final IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Serializes an object to the current classloader and unserializes it in
     * the specified classloader.
     */
    @SuppressWarnings("unchecked")
    public static <T> T cloneBySerialization(Object objectToClone, ClassLoader targetClassLoader) {
        final ByteArrayOutputStream arrayOutputStream = new ByteArrayOutputStream();
        serialize(objectToClone, arrayOutputStream);
        final byte[] bytes = arrayOutputStream.toByteArray();
        final ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        return (T) deserialize(bin, targetClassLoader);
    }

    /**
     * Returns a thread that write each data read to the specified input
     * getOutputStream to the specified output getOutputStream.
     */
    public static JkStreamGobbler newStreamGobbler(InputStream is, OutputStream ... outputStreams) {
        return new JkStreamGobbler(is, outputStreams);
    }

    /**
     * Runs a thread copying all data from the specified input stream to sepecified  output streams. The
     * thread is started when the instance is created. You have to call
     * {@link #stop()} to stop the thread.
     */
    public static final class JkStreamGobbler {

        private final InnerRunnable innerRunnable;

        private final Thread thread;

        private JkStreamGobbler(InputStream is, OutputStream... outputStreams) {
            this.innerRunnable = new InnerRunnable(is, outputStreams);
            thread = new Thread(innerRunnable);
            thread.start();
        }

        /**
         * Stop the gobbling, meaning stop the thread.
         */
        public void stop() {
            this.innerRunnable.stop.set(true);
        }

        public void join() {
            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }

        private static class InnerRunnable implements Runnable {

            private final InputStream in;

            private final OutputStream[] outs;

            private final AtomicBoolean stop = new AtomicBoolean(false);

            private InnerRunnable(InputStream is, OutputStream[] outputStreams) {
                this.in = is;
                this.outs = outputStreams;
            }

            @Override
            public void run() {
                try (InputStreamReader isr = new InputStreamReader(in); BufferedReader br = new BufferedReader(isr)) {
                    String line;
                    while (!stop.get() && (line = br.readLine()) != null) {
                        final byte[] bytes = line.getBytes();
                        for (OutputStream out : outs) {
                            out.write(bytes, 0, bytes.length);
                            out.write('\n');
                            out.flush();
                        }
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /* table mapping primitive type names to corresponding class objects */
    private static final HashMap<String, Class<?>> primClasses = new HashMap<>(8, 1.0F);

    static {
        primClasses.put("boolean", boolean.class);
        primClasses.put("byte", byte.class);
        primClasses.put("char", char.class);
        primClasses.put("short", short.class);
        primClasses.put("int", int.class);
        primClasses.put("long", long.class);
        primClasses.put("float", float.class);
        primClasses.put("double", double.class);
        primClasses.put("void", void.class);
    }

}