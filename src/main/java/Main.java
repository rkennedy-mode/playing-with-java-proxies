import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class Main {
    public static final int POSTGRESQL_DEFAULT_PORT = 5432;
    private static final Proxy PROXY_A = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1080));
    private static final Proxy PROXY_B = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("localhost", 1081));

    public static void main(String[] args) throws Exception {
        // Install our custom ProxySelector
        ProxySelector.setDefault(new PlayProxySelector());

        // Connect to "databasea" via the corresponding SOCKS5 proxy
        System.out.println(doViaProxy(PROXY_A, buildDatabaseVersionSupplier("databasea")));

        // Connect to "databaseb" via the corresponding SOCKS5 proxy
        System.out.println(doViaProxy(PROXY_B, buildDatabaseVersionSupplier("databaseb")));
    }

    /*
     * Execute the given supplier with all TCP connections connecting via the provided Proxy
     */
    private static <T> T doViaProxy(Proxy proxy, CheckedSupplier<T> toDo) throws Exception {
        try (ProxyContext ignored = new ProxyContext(proxy)) {
            return toDo.get();
        }
    }

    /*
     * Connect to the PostgreSQL database at the given hostname and return the server version
     */
    private static CheckedSupplier<String> buildDatabaseVersionSupplier(String hostname) {
        return () -> {
            final String connectionUrl = String.format("jdbc:postgresql://%s:%d/", hostname, POSTGRESQL_DEFAULT_PORT);

            final Properties connectionProperties = new Properties();
            connectionProperties.setProperty("user", "postgres");
            connectionProperties.setProperty("password", "password");
            connectionProperties.setProperty("ssl", "false");
            connectionProperties.setProperty("sslmode", "disable");

            try (Connection connection = DriverManager.getConnection(connectionUrl, connectionProperties)) {
                final DatabaseMetaData databaseMetaData = connection.getMetaData();
                return String.format("Connected to %s %s on %s:%d", databaseMetaData.getDatabaseProductName(),
                    databaseMetaData.getDatabaseProductVersion(),
                    hostname, Main.POSTGRESQL_DEFAULT_PORT);
            }
        };
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    /*
     * Object to be used in try-with-resources blocks to set the Proxy desired for all
     * TCP connections until this object is closed. This is implemented via a ThreadLocal,
     * which means other threads won't be affected. We could switch this to an
     * java.lang.InheritableThreadLocal, however that has some adverse side effects that
     * need to be fully understood.
     */
    private static class ProxyContext implements AutoCloseable {
        private static final ThreadLocal<Proxy> PROXY = new ThreadLocal<>();

        public ProxyContext(Proxy proxy) {
            PROXY.set(proxy);
        }

        static Optional<Proxy> get() {
            return Optional.ofNullable(PROXY.get());
        }

        @Override
        public void close() {
            PROXY.remove();
        }
    }

    /*
     * A ProxySelector implementation that returns the Proxy configured via ProxyContext or
     * Proxy.NO_PROXY if no proxy is currently configured.
     */
    private static class PlayProxySelector extends ProxySelector {
        public List<Proxy> select(URI uri) {
            return List.of(ProxyContext.get().orElse(Proxy.NO_PROXY));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            System.err.printf("Failed to connect to proxy server: uri = %s%n", uri);
        }
    }
}
