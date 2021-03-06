package tomcat.request.session;

import java.io.*;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.util.CustomObjectInputStream;

/**
 * Tomcat clustering with Redis data-cache implementation.
 * <p>
 * Session serialization utility.
 *
 * @author Ranjith Manickam
 * @since 2.0
 */
public class SerializationUtil {

	private ClassLoader loader;

	/**
	 * To set class loader
	 *
	 * @param loader
	 */
	public void setClassLoader(ClassLoader loader) {
		this.loader = loader;
	}

	/**
	 * To get session attributes hash code
	 *
	 * @param session
	 * @return
	 * @throws IOException
	 */
	public byte[] getSessionAttributesHashCode(Session session) throws IOException {
		byte[] serialized = null;
		Map<String, Object> attributes = new HashMap<String, Object>();

		for (Enumeration<String> enumerator = session.getAttributeNames(); enumerator.hasMoreElements();) {
			String key = enumerator.nextElement();
			attributes.put(key, session.getAttribute(key));
		}

		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));) {
			oos.writeUnshared(attributes);
			oos.flush();
			serialized = bos.toByteArray();
		}

		MessageDigest digester = null;
		try {
			digester = MessageDigest.getInstance("MD5");
		} catch (Exception ex) {
			System.out.println("Unable to get MessageDigest instance for MD5");
		}
		return digester.digest(serialized);
	}

	/**
	 * To serialize session object
	 *
	 * @param session
	 * @param metadata
	 * @return
	 * @throws IOException
	 */
	public byte[] serializeSessionData(Session session, SessionMetadata metadata) throws IOException {
		byte[] serialized = null;
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(bos));) {
			oos.writeObject(metadata);
			session.writeObjectData(oos);
			oos.flush();
			serialized = bos.toByteArray();
		}
		return serialized;
	}

	/**
	 * To de-serialize session object
	 *
	 * @param data
	 * @param session
	 * @param metadata
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public void deserializeSessionData(byte[] data, Session session, SessionMetadata metadata)
			throws IOException, ClassNotFoundException {
		try (BufferedInputStream bis = new BufferedInputStream(new ByteArrayInputStream(data));
				ObjectInputStream ois = new CustomObjectInputStream(bis, this.loader);) {
			SessionMetadata serializedMetadata = (SessionMetadata) ois.readObject();
			metadata.copyFieldsFrom(serializedMetadata);
			session.readObjectData(ois);
		}
	}

}