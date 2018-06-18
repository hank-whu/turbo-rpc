package rpc.turbo.benchmark.client;

import org.springframework.stereotype.Component;

import rpc.turbo.boot.TurboClientAware;
import rpc.turbo.client.TurboClient;
import rpc.turbo.common.RemoteContext;
import rpc.turbo.filter.RpcClientFilter;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.trace.TracerContext;
import rpc.turbo.trace.Tracer;

@Component
public class TruboClientFilterTest implements TurboClientAware {

	@Override
	public void setTurboClient(TurboClient turboClient) {
		turboClient.addFirst(new RpcClientFilter() {

			@Override
			public boolean onSend(Request request) {
				try {
					Tracer tracer = TracerContext.nextTracer();

					if (tracer != null) {
						RemoteContext.getClientAddress().toString();
						RemoteContext.getServerAddress().toString();
						RemoteContext.getServiceMethodName();

						request.setTracer(tracer);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				System.out.println(request);

				return true;
			}

			@Override
			public void onRecive(Request request, Response response) {
				System.out.println(response);
			}

			@Override
			public void onError(Request request, Response response, Throwable throwable) {
				System.out.println(throwable);
			}
		});

	}

}
