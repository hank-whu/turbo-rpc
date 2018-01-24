package rpc.turbo.config.server;

import static rpc.turbo.config.ConfigUtils.getStringOrElse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.config.ConfigException;
import rpc.turbo.config.HostPort;
import rpc.turbo.serialization.JsonMapper;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.serialization.jackson.JacksonMapper;
import rpc.turbo.serialization.protostuff.ProtostuffSerializer;

public class ServerConfig {

	private String group;
	private String app;
	private String ownerName;
	private String ownerPhone;
	private Serializer serializer = new ProtostuffSerializer();
	private JsonMapper jsonMapper = new JacksonMapper();
	private List<RegisterConfig> registers;

	public ServerConfig() {
	}

	public ServerConfig(String group, String app) {
		this.group = group;
		this.app = app;
	}

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

	public String getOwnerName() {
		return ownerName;
	}

	public void setOwnerName(String ownerName) {
		this.ownerName = ownerName;
	}

	public String getOwnerPhone() {
		return ownerPhone;
	}

	public void setOwnerPhone(String ownerPhone) {
		this.ownerPhone = ownerPhone;
	}

	public Serializer getSerializer() {
		return serializer;
	}

	public void setSerializer(Serializer serializer) {
		this.serializer = serializer;
	}

	public JsonMapper getJsonMapper() {
		return jsonMapper;
	}

	public void setJsonMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public List<RegisterConfig> getRegisters() {
		return registers;
	}

	public void setRegisters(List<RegisterConfig> registers) {
		this.registers = registers;
	}

	@Override
	public String toString() {
		return "ServerConfig{" + //
				"group='" + group + '\'' + //
				", app='" + app + '\'' + //
				", ownerName='" + ownerName + '\'' + //
				", ownerPhone='" + ownerPhone + '\'' + //
				", serializer='" + serializer.getClass().getName() + '\'' + //
				", jsonMapper='" + jsonMapper.getClass().getName() + '\'' + //
				", registers=" + registers + //
				'}';
	}

	public static ServerConfig parse(String resourceName) {
		Config serverConfig = ConfigFactory.load(resourceName);
		return parse(serverConfig);
	}

	private static ServerConfig parse(Config config) {

		String group = getStringOrElse(config, "group", TurboService.DEFAULT_GROUP);
		String app = getStringOrElse(config, "app", TurboService.DEFAULT_GROUP);
		String ownerName = getStringOrElse(config, "owner.name", "");
		String ownerPhone = getStringOrElse(config, "owner.phone", "");

		String serializerClass = getStringOrElse(config, "serializer.class", ProtostuffSerializer.class.getName());

		Serializer serializer;
		try {
			serializer = (Serializer) Class//
					.forName(serializerClass)//
					.getDeclaredConstructor()//
					.newInstance();
		} catch (Throwable t) {
			throw new ConfigException(t);
		}

		String jsonMapperClass = getStringOrElse(config, "jsonMapper.class", JacksonMapper.class.getName());

		JsonMapper jsonMapper;
		try {
			jsonMapper = (JsonMapper) Class//
					.forName(jsonMapperClass)//
					.getDeclaredConstructor()//
					.newInstance();
		} catch (Throwable t) {
			throw new ConfigException(t);
		}

		List<? extends Config> registerConfigList = config.getConfigList("registers");

		List<RegisterConfig> registers = registerConfigList//
				.stream()//
				.map(registerConfig -> {
					try {
						return RegisterConfig.parse(registerConfig);
					} catch (Throwable e) {
						throw new ConfigException(e);
					}
				})//
				.collect(Collectors.toList());

		Map<Integer, RegisterConfig> portMap = new HashMap<>();
		for (RegisterConfig registerConfig : registers) {
			HostPort serverAddress = registerConfig.getServerAddress();
			RegisterConfig existed = portMap.get(serverAddress.port);

			if (existed == null) {
				portMap.put(serverAddress.port, registerConfig);
				continue;
			}

			if (existed.getProtocol() == registerConfig.getProtocol()
					&& existed.getServerAddress().equals(serverAddress)) {
				continue;
			}

			throw new ConfigException("存在端口冲突: " + registerConfig + " vs " + existed);
		}

		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setGroup(group);
		serverConfig.setApp(app);
		serverConfig.setOwnerName(ownerName);
		serverConfig.setOwnerPhone(ownerPhone);
		serverConfig.setSerializer(serializer);
		serverConfig.setJsonMapper(jsonMapper);
		serverConfig.setRegisters(registers);

		return serverConfig;
	}

}
