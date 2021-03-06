package tomcat.request.session.data.cache.impl;

import java.io.*;
import java.util.*;

import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisClusterMaxRedirectionsException;
import redis.clients.jedis.exceptions.JedisConnectionException;
import tomcat.request.session.SessionConstants;
import tomcat.request.session.data.cache.DataCache;

/**
 * Tomcat clustering with Redis data-cache implementation.
 * 
 * Redis data-cache implementation to store/retrieve session objects.
 * 
 * @author Ranjith Manickam
 * @since 2.0
 */
public class RedisDataCache implements DataCache {

	private static DataCache dataCache;

	public RedisDataCache() {
		initialize();
	}

	/** {@inheritDoc} */
	@Override
	public byte[] set(String key, byte[] value) {
		return dataCache.set(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public Long setnx(String key, byte[] value) {
		return dataCache.setnx(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public Long expire(String key, int seconds) {
		return dataCache.expire(key, seconds);
	}

	/** {@inheritDoc} */
	@Override
	public byte[] get(String key) {
		return (key != null) ? dataCache.get(key) : null;
	}

	/** {@inheritDoc} */
	@Override
	public Long delete(String key) {
		return dataCache.delete(key);
	}

	/**
	 * To parse data-cache key
	 * 
	 * @param key
	 * @return
	 */
	public static String parseDataCacheKey(String key) {
		return key.replaceAll("\\s", "_");
	}

	/**
	 * To initialize the data-cache
	 *
	 */
	@SuppressWarnings("unchecked")
	private void initialize() {
		if (dataCache != null) {
			return;
		}
		Properties properties = loadProperties();

		boolean clusterEnabled = Boolean.valueOf(
				properties.getProperty(RedisConstants.CLUSTER_ENABLED, RedisConstants.DEFAULT_CLUSTER_ENABLED));

		String hosts = properties.getProperty(RedisConstants.HOSTS,
				Protocol.DEFAULT_HOST.concat(":").concat(String.valueOf(Protocol.DEFAULT_PORT)));
		Collection<? extends Serializable> nodes = getJedisNodes(hosts, clusterEnabled);

		String password = properties.getProperty(RedisConstants.PASSWORD);
		password = (password != null && !password.isEmpty()) ? password : null;

		int database = Integer
				.parseInt(properties.getProperty(RedisConstants.DATABASE, String.valueOf(Protocol.DEFAULT_DATABASE)));

		int timeout = Integer
				.parseInt(properties.getProperty(RedisConstants.TIMEOUT, String.valueOf(Protocol.DEFAULT_TIMEOUT)));
		timeout = (timeout < Protocol.DEFAULT_TIMEOUT) ? Protocol.DEFAULT_TIMEOUT : timeout;

		if (clusterEnabled) {
			dataCache = new RedisClusterCacheUtil((Set<HostAndPort>) nodes, timeout, getPoolConfig(properties));
		} else {
			dataCache = new RedisCacheUtil(((List<String>) nodes).get(0),
					Integer.parseInt(((List<String>) nodes).get(1)), password, database, timeout,
					getPoolConfig(properties));
		}
	}

	/**
	 * To get jedis pool configuration
	 * 
	 * @param properties
	 * @return
	 */
	private JedisPoolConfig getPoolConfig(Properties properties) {
		JedisPoolConfig poolConfig = new JedisPoolConfig();
		int maxActive = Integer
				.parseInt(properties.getProperty(RedisConstants.MAX_ACTIVE, RedisConstants.DEFAULT_MAX_ACTIVE_VALUE));
		poolConfig.setMaxTotal(maxActive);

		boolean testOnBorrow = Boolean.parseBoolean(
				properties.getProperty(RedisConstants.TEST_ONBORROW, RedisConstants.DEFAULT_TEST_ONBORROW_VALUE));
		poolConfig.setTestOnBorrow(testOnBorrow);

		boolean testOnReturn = Boolean.parseBoolean(
				properties.getProperty(RedisConstants.TEST_ONRETURN, RedisConstants.DEFAULT_TEST_ONRETURN_VALUE));
		poolConfig.setTestOnReturn(testOnReturn);

		int maxIdle = Integer
				.parseInt(properties.getProperty(RedisConstants.MAX_ACTIVE, RedisConstants.DEFAULT_MAX_ACTIVE_VALUE));
		poolConfig.setMaxIdle(maxIdle);

		int minIdle = Integer
				.parseInt(properties.getProperty(RedisConstants.MIN_IDLE, RedisConstants.DEFAULT_MIN_IDLE_VALUE));
		poolConfig.setMinIdle(minIdle);

		boolean testWhileIdle = Boolean.parseBoolean(
				properties.getProperty(RedisConstants.TEST_WHILEIDLE, RedisConstants.DEFAULT_TEST_WHILEIDLE_VALUE));
		poolConfig.setTestWhileIdle(testWhileIdle);

		int testNumPerEviction = Integer.parseInt(properties.getProperty(RedisConstants.TEST_NUMPEREVICTION,
				RedisConstants.DEFAULT_TEST_NUMPEREVICTION_VALUE));
		poolConfig.setNumTestsPerEvictionRun(testNumPerEviction);

		long timeBetweenEviction = Long.parseLong(properties.getProperty(RedisConstants.TIME_BETWEENEVICTION,
				RedisConstants.DEFAULT_TIME_BETWEENEVICTION_VALUE));
		poolConfig.setTimeBetweenEvictionRunsMillis(timeBetweenEviction);
		return poolConfig;
	}

	/**
	 * To get jedis nodes
	 * 
	 * @param hosts
	 * @param clusterEnabled
	 * @return
	 */
	private Collection<? extends Serializable> getJedisNodes(String hosts, boolean clusterEnabled) {
		hosts = hosts.replaceAll("\\s", "");
		String[] hostPorts = hosts.split(",");

		List<String> node = null;
		Set<HostAndPort> nodes = null;

		for (String hostPort : hostPorts) {
			String[] hostPortArr = hostPort.split(":");

			if (clusterEnabled) {
				nodes = (nodes == null) ? new HashSet<HostAndPort>() : nodes;
				nodes.add(new HostAndPort(hostPortArr[0], Integer.valueOf(hostPortArr[1])));
			} else {
				int port = Integer.valueOf(hostPortArr[1]);
				if (!hostPortArr[0].isEmpty() && port > 0) {
					node = (node == null) ? new ArrayList<String>() : node;
					node.add(hostPortArr[0]);
					node.add(String.valueOf(port));
					break;
				}
			}
		}
		return clusterEnabled ? nodes : node;
	}

	/**
	 * To load data-cache properties
	 * 
	 * @param filePath
	 * @return
	 */
	private Properties loadProperties() {
		Properties properties = new Properties();
		try {
			String filePath = System.getenv(SessionConstants.CATALINA_BASE)
					.concat(File.separator).concat(SessionConstants.CONF)
					.concat(File.separator).concat(RedisConstants.PROPERTIES_FILE);
			InputStream resourceStream = null;
			try {
				resourceStream = (filePath != null && !filePath.isEmpty() && new File(filePath).exists())
						? new FileInputStream(filePath)
						: null;
				if (resourceStream == null) {
					ClassLoader loader = Thread.currentThread().getContextClassLoader();
					resourceStream = loader.getResourceAsStream(RedisConstants.PROPERTIES_FILE);
				}
				properties.load(resourceStream);
			} finally {
				resourceStream.close();
			}
		} catch (IOException ex) {
			System.out.println("Error while loading task scheduler properties");
		}
		return properties;
	}

	/**
	 * Tomcat clustering with Redis data-cache implementation.
	 * 
	 * Redis stand-alone mode data-cache implementation.
	 * 
	 * @author Ranjith Manickam
	 * @since 2.0
	 */
	private class RedisCacheUtil implements DataCache {

		private JedisPool pool;

		private final int numRetries = 3;

		public RedisCacheUtil(String host, int port, String password, int database, int timeout,
				JedisPoolConfig poolConfig) {
			pool = new JedisPool(poolConfig, host, port, timeout, password, database);
		}

		/** {@inheritDoc} */
		@Override
		public byte[] set(String key, byte[] value) {
			int tries = 0;
			boolean success = false;
			String retVal = null;
			do {
				tries++;
				try {
					Jedis jedis = pool.getResource();
					retVal = jedis.set(key.getBytes(), value);
					jedis.close();
					success = true;
				} catch (JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries)
						throw ex;
				}
			} while (!success && tries <= numRetries);
			return (retVal != null) ? retVal.getBytes() : null;
		}

		/** {@inheritDoc} */
		@Override
		public Long setnx(String key, byte[] value) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					Jedis jedis = pool.getResource();
					retVal = jedis.setnx(key.getBytes(), value);
					jedis.close();
					sucess = true;
				} catch (JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries)
						throw ex;
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public Long expire(String key, int seconds) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					Jedis jedis = pool.getResource();
					retVal = jedis.expire(key, seconds);
					jedis.close();
					sucess = true;
				} catch (JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries)
						throw ex;
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public byte[] get(String key) {
			int tries = 0;
			boolean sucess = false;
			byte[] retVal = null;
			do {
				tries++;
				try {
					Jedis jedis = pool.getResource();
					retVal = jedis.get(key.getBytes());
					jedis.close();
					sucess = true;
				} catch (JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries)
						throw ex;
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public Long delete(String key) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					Jedis jedis = pool.getResource();
					retVal = jedis.del(key);
					jedis.close();
					sucess = true;
				} catch (JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries)
						throw ex;
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}
	}

	/**
	 * Tomcat clustering with Redis data-cache implementation.
	 * 
	 * Redis multiple node cluster data-cache implementation.
	 * 
	 * @author Ranjith Manickam
	 * @since 2.0
	 */
	private class RedisClusterCacheUtil implements DataCache {

		private JedisCluster cluster;

		private final int numRetries = 30;

		public RedisClusterCacheUtil(Set<HostAndPort> nodes, int timeout, JedisPoolConfig poolConfig) {
			cluster = new JedisCluster(nodes, timeout, poolConfig);
		}

		/** {@inheritDoc} */
		@Override
		public byte[] set(String key, byte[] value) {
			int tries = 0;
			boolean sucess = false;
			String retVal = null;
			do {
				tries++;
				try {
					retVal = cluster.set(key.getBytes(), value);
					sucess = true;
				} catch (JedisClusterMaxRedirectionsException | JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries) {
						throw ex;
					}
					waitforFailover();
				}
			} while (!sucess && tries <= numRetries);
			return (retVal != null) ? retVal.getBytes() : null;
		}

		/** {@inheritDoc} */
		@Override
		public Long setnx(String key, byte[] value) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					retVal = cluster.setnx(key.getBytes(), value);
					sucess = true;
				} catch (JedisClusterMaxRedirectionsException | JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries) {
						throw ex;
					}
					waitforFailover();
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public Long expire(String key, int seconds) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					retVal = cluster.expire(key, seconds);
					sucess = true;
				} catch (JedisClusterMaxRedirectionsException | JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries) {
						throw ex;
					}
					waitforFailover();
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public byte[] get(String key) {
			int tries = 0;
			boolean sucess = false;
			byte[] retVal = null;
			do {
				tries++;
				try {
					retVal = cluster.get(key.getBytes());
					sucess = true;
				} catch (JedisClusterMaxRedirectionsException | JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries) {
						throw ex;
					}
					waitforFailover();
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/** {@inheritDoc} */
		@Override
		public Long delete(String key) {
			int tries = 0;
			boolean sucess = false;
			Long retVal = null;
			do {
				tries++;
				try {
					retVal = cluster.del(key);
					sucess = true;
				} catch (JedisClusterMaxRedirectionsException | JedisConnectionException ex) {
					System.out.println(RedisConstants.CONN_FAILED_RETRY_MSG + tries);
					if (tries == numRetries) {
						throw ex;
					}
					waitforFailover();
				}
			} while (!sucess && tries <= numRetries);
			return retVal;
		}

		/**
		 * To wait for handling redis fail-over
		 */
		private void waitforFailover() {
			try {
				Thread.sleep(4000);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public static void main(String[] args) {
		RedisDataCache cache = new RedisDataCache();
		cache.loadProperties();
	}

}