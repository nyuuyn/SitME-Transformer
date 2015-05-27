package iaas.uni.stuttgart.de.sitme.data;

import iaas.uni.stuttgart.de.sitme.model.TaskState;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class RunningTasks {
	
	public List<TaskState> tasks = new ArrayList<TaskState>();
	
	private RunningTasks() {
	}

	private static class SingletonHolder {
		private static final RunningTasks INSTANCE = new RunningTasks();
	}

	public static RunningTasks getInstance() {
		return SingletonHolder.INSTANCE;
	}

}
