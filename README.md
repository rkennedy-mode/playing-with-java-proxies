# Playing With Java Proxies

This project plays with how Java manages TCP proxies (SOCKS5 in this case) to see how dynamic that 
support can be. While there are methods in Java's core libraries that let you specify a 
`java.net.Proxy`, there are situations where you don't make those connections yourself. For example, 
when using a JDBC driver. While JDBC drivers sometimes allow you to specify a proxy, support is 
not guaranteed and when it is available it's rarely (if ever) consistent.

This is not a perfect solution. There are issues involving threads that may make this approach 
inadvisable. But it's still useful in exploring the space to understand what's possible.

The provided `docker-compose.yml` file sets up two networks, each with an public SOCKS5 proxy and 
a private PostgreSQL database. `Main.java` uses those proxies to connect to the databases as a 
proof of concept.
