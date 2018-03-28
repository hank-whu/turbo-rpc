package rpc.turbo.recycle;

public class RecycleUtils {

	public static void release(Object obj) {
		if (obj instanceof Recycleable) {
			((Recycleable) obj).recycle();
		}
	}

}
