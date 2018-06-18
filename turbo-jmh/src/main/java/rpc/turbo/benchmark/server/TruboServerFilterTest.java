package rpc.turbo.benchmark.server;

import org.springframework.stereotype.Component;

import rpc.turbo.boot.TurboServerAware;
import rpc.turbo.common.RemoteContext;
import rpc.turbo.filter.RpcServerFilter;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.server.TurboServer;
import rpc.turbo.trace.TracerContext;
import rpc.turbo.trace.Tracer;

//@Component
public class TruboServerFilterTest implements TurboServerAware {

	@Override
	public void setTurboServer(TurboServer turboServer) {
		turboServer.addFirst(new RpcServerFilter() {

			@Override
			public void onSend(Request request, Response response) {
				try {
					Tracer tracer = request.getTracer();

					if (tracer != null) {
						RemoteContext.getClientAddress().toString();
						RemoteContext.getServerAddress().toString();
						RemoteContext.getServiceMethodName();

						TracerContext.setTracer(tracer);
						response.setTracer(tracer);
					}

					System.out.println(response);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public boolean onRecive(Request request) {
				System.out.println(request);
				return true;
			}

			@Override
			public void onError(Request request, Response response, Throwable throwable) {
				System.out.println(throwable);
			}
		});

	}

}
