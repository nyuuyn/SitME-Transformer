package iaas.uni.stuttgart.de.sitme.logic;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.opentosca.bpsconnector.BpsConnector;
import org.opentosca.util.fileaccess.service.impl.zip.ZipManager;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.uni_stuttgart.iaas.srsservice.SrsService;
import de.uni_stuttgart.iaas.srsservice.SrsService_Service;
import de.uni_stuttgart.iaas.srsservice.SrsService_SrsServiceSOAP_Client;
import de.uni_stuttgart.iaas.srsservice.SubscribeRequest;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;
import net.lingala.zip4j.core.ZipFile;
import iaas.uni.stuttgart.de.sitme.data.Configuration;
import iaas.uni.stuttgart.de.sitme.model.TaskState;
import iaas.uni.stuttgart.de.sitme.util.Constants;
import iaas.uni.stuttgart.de.sitme.util.Util;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class TaskWorker implements Runnable {

	private TaskState currentState;

	public TaskWorker(TaskState state) {
		this.currentState = state;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {

		/* create temp dir */
		this.currentState.setCurrentState(TaskState.State.UNPACKING);
		this.currentState.printState();
		try {
			Path downloadTempDir = Files.createTempDirectory("sitme");
			this.currentState.setWorkingDir(downloadTempDir);
		} catch (IOException e) {
			e.printStackTrace();
		}

		/* unpack workflow zip into temp dir */

		ZipManager.getInstance().unzip(
				this.currentState.getWorkflowPath().toFile(),
				this.currentState.getWorkingDir().toFile());
		System.out.println("Extracted Zip to working dir: "
				+ this.currentState.getWorkingDir().toString());

		this.currentState.setCurrentState(TaskState.State.SITMEXFORMING);
		this.currentState.printState();

		/* fetch deploy.xml */
		Path deployXmlPath = Paths.get(this.currentState.getWorkingDir()
				.toString(), "deploy.xml");
		if (!deployXmlPath.toFile().exists()) {
			this.currentState.setCurrentState(TaskState.State.ERROR);
			this.currentState
					.setCurrentMessage("Could'nt find deploy.xml process archive");
			this.currentState.printState();
			return;
		}
		this.currentState.setDeployXmlPath(deployXmlPath);

		/* fetch .bpel */
		// fetch process name from deploy.xml
		String xpathExpr = "/*/*[namespace-uri()='http://www.apache.org/ode/schemas/dd/2007/03' and local-name()='process']";

		NodeList deployXmlProcessNameQueryResult = Util.XpathQueryFile(
				xpathExpr, deployXmlPath);

		if (deployXmlProcessNameQueryResult.getLength() != 1) {
			this.currentState.setCurrentState(TaskState.State.ERROR);
			this.currentState
					.setCurrentMessage("Couldn't find proper process element. Deploy.xml must contain exactly one process element");
			this.currentState.printState();
			return;
		}

		Node deployXmlProcessNode = deployXmlProcessNameQueryResult.item(0);
		String processNameWithNamespace = deployXmlProcessNode.getAttributes()
				.getNamedItem("name").getTextContent();

		String processName = processNameWithNamespace.split(":")[1];

		System.out.println("Found processName: " + processName);

		Path processBpelPath = Paths.get(this.currentState.getWorkingDir()
				.toString(), processName + ".bpel");

		if (!processBpelPath.toFile().exists()) {
			this.currentState.setCurrentState(TaskState.State.ERROR);
			this.currentState
					.setCurrentMessage("Couldn't find process .bpel file. Deploy.xml doesn't reference proper file name.");
			this.currentState.printState();
			return;
		}

		this.currentState.setProcessBpelPath(processBpelPath);
		
		List<Path> wsdlPaths = Util.findFilesRecursive("wsdl",
				this.currentState.getWorkingDir());

		Path processWSDLFile = Util.findWSDLForProcess(wsdlPaths,
				this.currentState.getProcessBpelPath());

		this.currentState.setProcessWSDLPath(processWSDLFile);
		
		// inject wsdl, partnerLinkType, partnerLink, invoke and provide
		SrsServiceInjector.injectSrsService(this.currentState);		
		
		// transform sitme event activities
		SitMEEventTransformer.transformSitMEEvents(this.currentState);
		
		// TODO transform rest of sitme activities
		
		// package process in temp dir
		try {
			Path repackagedProcessArchivePath = Paths.get(Files
					.createTempDirectory("sitme").toString(), this.currentState
					.getWorkflowPath().getFileName().toString());

			this.currentState
					.setRepackagedProcessPath(repackagedProcessArchivePath);

			ZipManager.getInstance().zip(
					this.currentState.getWorkingDir().toFile(),
					repackagedProcessArchivePath.toFile());

			System.out.println("Repackaged process into zip file at: "
					+ this.currentState.getRepackagedProcessPath().toString());
		} catch (IOException e) {
			e.printStackTrace();
		}

		/* deploy process on bps */
		this.currentState.setCurrentState(TaskState.State.DEPLOYING);
		this.currentState.setCurrentMessage("Deploying to WSO2 BPS");
		BpsConnector bpsConnector = new BpsConnector();

		// "https://192.168.2.108:9443"

		String processId = bpsConnector.deploy(this.currentState
				.getRepackagedProcessPath().toFile(), Configuration
				.getInstance().getWso2BpsAddress(), Configuration.getInstance()
				.getWso2BpsUserLogin(), Configuration.getInstance()
				.getWso2BpsPwLogin());
		Map<String, URI> endpoints = bpsConnector.getEndpointsForPID(processId,
				Configuration.getInstance().getWso2BpsAddress(), Configuration
						.getInstance().getWso2BpsUserLogin(), Configuration
						.getInstance().getWso2BpsPwLogin());

		URI srsCallbackEndpoint = null;
		
		System.out.println("Found following endpoints: ");
		for(String serviceName : endpoints.keySet()){
			System.out.println("ServiceName: " + serviceName);
			System.out.println("Endpoint: " +  endpoints.get(serviceName));
			
			if(serviceName.equals(Constants.SRSService_PartnerLinkName)){
				srsCallbackEndpoint = endpoints.get(serviceName);
				if(srsCallbackEndpoint.toString().endsWith("?wsdl")){
					try {
						srsCallbackEndpoint = new URI(srsCallbackEndpoint.toString().substring(0, srsCallbackEndpoint.toString().length() - "?wsdl".length()));
					} catch (URISyntaxException e) {
						e.printStackTrace();
					}
				}
			}
		}
		
		/* subscribe process at srs */
		this.currentState.setCurrentState(TaskState.State.SUBSCRIBING);
		this.currentState.setCurrentMessage("Subscribing SitME Events");

		URL serviceUrl = null;
		try {
			serviceUrl = new URL(Configuration.getInstance()
					.getSrsServiceAddress() + "?wsdl");
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}

		SrsService_Service service = new SrsService_Service(serviceUrl);
		SrsService serviceClient = service.getSrsServiceSOAP();

		SubscribeRequest subReq = new SubscribeRequest();

		// TODO set proper data
		subReq.setSituation("someSit123");
		subReq.setObject("someObj123");
		subReq.setEndpoint(srsCallbackEndpoint.toString());
		subReq.setCorrelation("someCorrelation123");

		serviceClient.subscribe(subReq);
	}

}
