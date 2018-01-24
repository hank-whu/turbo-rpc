package rpc.turbo.discover;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import rpc.turbo.annotation.TurboService;
import rpc.turbo.config.HostPort;
import rpc.turbo.config.server.Protocol;

public class DirectConnectDiscover implements Discover {

	private List<HostPort> hostPorts;

	@Override
	public void init(List<HostPort> hostPorts) {
		this.hostPorts = hostPorts;
	}

	@Override
	public void addListener(String group, String app, Protocol protocol, DiscoverListener listener) {
		Map<HostPort, Integer> providerWithWeight = hostPorts.stream()
				.collect(Collectors.toMap(item -> item, item -> TurboService.DEFAULT_WEIGHT));

		listener.onChange(providerWithWeight);

		new Thread(() -> {
			while (true) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					break;
				}

				System.out.println("DirectConnectDiscover onChange");
				listener.onChange(providerWithWeight);
			}
		}).start();
	}

	@Override
	public void close() throws IOException {
		System.out.println("DirectConnectDiscover close");
	}

}
