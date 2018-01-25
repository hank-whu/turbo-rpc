package rpc.turbo.config.client;

import static rpc.turbo.config.ConfigUtils.getIntOrElse;
import static rpc.turbo.config.ConfigUtils.getStringOrElse;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Collectors;

import com.typesafe.config.Config;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.config.ConfigException;
import rpc.turbo.config.HostPort;
import rpc.turbo.discover.DirectConnectDiscover;
import rpc.turbo.discover.Discover;
import rpc.turbo.loadbalance.LoadBalanceFactory;
import rpc.turbo.loadbalance.RoundRobinLoadBalanceFactory;
import rpc.turbo.loadbalance.Weightable;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.serialization.protostuff.ProtostuffSerializer;

public class AppConfig {

	private static final Serializer DEFAULT_SERIALIZER = new ProtostuffSerializer();

	private String group = TurboService.DEFAULT_GROUP;
	private String app = TurboService.DEFAULT_APP;
	private Serializer serializer = DEFAULT_SERIALIZER;
	private int globalTimeout = 0;
	private int maxRequestWait = 10000;
	private int connectPerServer = 1;
	private int serverErrorThreshold = 16;
	private int connectErrorThreshold = 2 * serverErrorThreshold / connectPerServer;
	private LoadBalanceFactory<Weightable> loadBalanceFactory = new RoundRobinLoadBalanceFactory<>();
	private Discover discover;

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getApp() {
		return app;
	}

	public void setApp(String app) {
		this.app = app;
	}

	public Serializer getSerializer() {
		return serializer;
	}

	public void setSerializer(Serializer serializer) {
		this.serializer = serializer;
	}

	public int getGlobalTimeout() {
		return globalTimeout;
	}

	public void setGlobalTimeout(int globalTimeout) {
		this.globalTimeout = globalTimeout;
	}

	public int getMaxRequestWait() {
		return maxRequestWait;
	}

	public void setMaxRequestWait(int maxRequestWait) {
		this.maxRequestWait = maxRequestWait;
	}

	public int getConnectPerServer() {
		return connectPerServer;
	}

	public void setConnectPerServer(int connectPerServer) {
		this.connectPerServer = connectPerServer;
	}

	public int getServerErrorThreshold() {
		return serverErrorThreshold;
	}

	public void setServerErrorThreshold(int serverErrorThreshold) {
		this.serverErrorThreshold = serverErrorThreshold;
	}

	public int getConnectErrorThreshold() {
		return connectErrorThreshold;
	}

	public void setConnectErrorThreshold(int connectErrorThreshold) {
		this.connectErrorThreshold = connectErrorThreshold;
	}

	public LoadBalanceFactory<Weightable> getLoadBalanceFactory() {
		return loadBalanceFactory;
	}

	public void setLoadBalanceFactory(LoadBalanceFactory<Weightable> loadBalanceFactory) {
		this.loadBalanceFactory = loadBalanceFactory;
	}

	public Discover getDiscover() {
		return discover;
	}

	public void setDiscover(Discover discover) {
		this.discover = discover;
	}

	@Override
	public String toString() {
		return "AppConfig{" + //
				"group='" + group + '\'' + //
				", app='" + app + '\'' + //
				", globalTimeout=" + globalTimeout + //
				", maxRequestWait=" + maxRequestWait + //
				", connectPerServer=" + connectPerServer + //
				", serverErrorThreshold=" + serverErrorThreshold + //
				", connectErrorThreshold=" + connectErrorThreshold + //
				", loadBalanceFactory=" + loadBalanceFactory.getClass().getName() + //
				", discover=" + discover.getClass().getName() + //
				'}';
	}

	/**
	 * 从配置文件读取配置
	 * 
	 * @param appConfigList
	 *            typesafe config <a>https://github.com/lightbend/config</a>
	 * @return
	 */
	public static List<AppConfig> parse(List<? extends Config> appConfigList) {
		return appConfigList//
				.stream()//
				.map(appConfig -> {
					try {
						return parse(appConfig);
					} catch (Exception e) {
						throw new ConfigException(e);
					}
				})//
				.collect(Collectors.toList());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static AppConfig parse(Config config)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException,
			NoSuchMethodException, SecurityException, ClassNotFoundException {

		String group = getStringOrElse(config, "group", TurboService.DEFAULT_GROUP);
		String app = getStringOrElse(config, "app", TurboService.DEFAULT_GROUP);
		int globalTimeout = getIntOrElse(config, "globalTimeout", 0);
		int maxRequestWait = getIntOrElse(config, "maxRequestWait", 10000);
		int connectPerServer = getIntOrElse(config, "connectPerServer", 1);
		int serverErrorThreshold = getIntOrElse(config, "serverErrorThreshold", 16);
		int connectErrorThreshold = getIntOrElse(config, "connectErrorThreshold",
				2 * serverErrorThreshold / connectPerServer);

		String serializerClass = getStringOrElse(config, "serializer.class", ProtostuffSerializer.class.getName());

		Serializer serializer = (Serializer) Class//
				.forName(serializerClass)//
				.getDeclaredConstructor()//
				.newInstance();

		String loadBalanceFactoryClass = getStringOrElse(config, "loadBalanceFactory.class",
				RoundRobinLoadBalanceFactory.class.getName());

		LoadBalanceFactory loadBalanceFactory = (LoadBalanceFactory) Class//
				.forName(loadBalanceFactoryClass)//
				.getDeclaredConstructor()//
				.newInstance();

		String discoverClass = getStringOrElse(config, "discover.class", DirectConnectDiscover.class.getName());
		List<String> discoverAddressList = config.getStringList("discover.address");

		Discover discover = (Discover) Class//
				.forName(discoverClass)//
				.getDeclaredConstructor()//
				.newInstance();

		List<HostPort> hostPorts = discoverAddressList//
				.stream()//
				.map(str -> new HostPort(str))//
				.collect(Collectors.toList());

		discover.init(hostPorts);

		AppConfig appConfig = new AppConfig();
		appConfig.setGroup(group);
		appConfig.setApp(app);
		appConfig.setSerializer(serializer);
		appConfig.setGlobalTimeout(globalTimeout);
		appConfig.setMaxRequestWait(maxRequestWait);
		appConfig.setConnectPerServer(connectPerServer);
		appConfig.setServerErrorThreshold(serverErrorThreshold);
		appConfig.setConnectErrorThreshold(connectErrorThreshold);
		appConfig.setLoadBalanceFactory(loadBalanceFactory);
		appConfig.setDiscover(discover);

		return appConfig;
	}

}
