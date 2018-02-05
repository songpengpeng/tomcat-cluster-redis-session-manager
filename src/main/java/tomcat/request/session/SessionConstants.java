package tomcat.request.session;

/**
 * Tomcat clustering with Redis data-cache implementation.
 * <p>
 * Session constants.
 *
 * @author Ranjith Manickam
 * @since 2.0
 */
public class SessionConstants {

	public static final byte[] NULL_SESSION = "null".getBytes();

	public static final String CATALINA_BASE = "CATALINA_BASE";

	public static final String CONF = "conf";
}