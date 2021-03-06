package iaas.uni.stuttgart.de.sitme.model;

import iaas.uni.stuttgart.de.sitme.model.TaskState.State;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class TaskState {
	
	private static final Logger LOG = Logger.getLogger(TaskState.class
			.getName());

	public enum State {
		DOWNLOADED, UNPACKING, SITMEXFORMING, DEPLOYING, SUBSCRIBING, FINISHED, ERROR
	}

	private State currentState = State.DOWNLOADED;

	private String currentMessage = "Upload of process was successful";

	private Path workflowPath;

	private Path workingDirPath;

	private Path deployXmlPath;

	private Path processBpelPath;

	private Path repackagedProcessArchivePath;

	private Path processWSDLPath;

	public TaskState(Path workflowPath) {
		this.workflowPath = workflowPath;
	}

	public State getCurrentState() {
		return this.currentState;
	}

	public void setCurrentState(State newState) {
		this.currentState = newState;
	}

	public String getCurrentMessage() {
		return this.currentMessage;
	}

	public void setCurrentMessage(String newMessage) {
		this.currentMessage = newMessage;
	}

	public Path getWorkflowPath() {
		return this.workflowPath;
	}

	public void setWorkingDir(Path workingDirPath) {
		this.workingDirPath = workingDirPath;
	}

	public Path getWorkingDir() {
		return this.workingDirPath;
	}

	public void setDeployXmlPath(Path deployXmlPath) {
		this.deployXmlPath = deployXmlPath;
	}

	public Path getDeployXmlPath() {
		return this.deployXmlPath;
	}

	public void setProcessBpelPath(Path processBpelPath) {
		this.processBpelPath = processBpelPath;
	}

	public Path getProcessBpelPath() {
		return this.processBpelPath;
	}

	public void setRepackagedProcessPath(Path repackagedProcessArchivePath) {
		this.repackagedProcessArchivePath = repackagedProcessArchivePath;
	}

	public Path getRepackagedProcessPath() {
		return this.repackagedProcessArchivePath;
	}

	public void printState() {
		LOG.log(Level.FINEST,"Task: " + this);
		LOG.log(Level.FINEST,"Current State: " + this.currentState);
		LOG.log(Level.FINEST,"Current Message: " + this.currentMessage);
	}

	public void setProcessWSDLPath(Path processWSDLFile) {
		this.processWSDLPath = processWSDLFile;
	}

	public Path getProcessWSDLPath() {
		return this.processWSDLPath;
	}
}
