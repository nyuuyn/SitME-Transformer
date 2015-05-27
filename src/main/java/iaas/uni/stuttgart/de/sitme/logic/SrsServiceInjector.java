package iaas.uni.stuttgart.de.sitme.logic;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import iaas.uni.stuttgart.de.sitme.model.TaskState;
import iaas.uni.stuttgart.de.sitme.util.Constants;
import iaas.uni.stuttgart.de.sitme.util.Util;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class SrsServiceInjector {

	public static void injectSrsService(TaskState taskState) {
		// add wsdl to working dir
		Path workingDir = taskState.getWorkingDir();

		URL srsServiceUrl = taskState.getClass()
				.getResource("/srsService.wsdl");

		System.out.println("Adding srsService.wsdl to working dir from: "
				+ srsServiceUrl);

		Path srsServicePath = null;
		try {
			srsServicePath = Paths.get(srsServiceUrl.toURI());
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		try {
			FileUtils.copyFileToDirectory(srsServicePath.toFile(), taskState
					.getWorkingDir().toFile());
		} catch (IOException e) {
			e.printStackTrace();
		}

		// add import to bpel wsdl
		Document bpelWSDLDocument = Util.parseFileToDOMDocument(taskState
				.getProcessWSDLPath());

		NodeList bpelWSDLchildNodes = bpelWSDLDocument.getFirstChild()
				.getChildNodes();

		for (int i = 0; i < bpelWSDLchildNodes.getLength(); i++) {
			Node childNode = bpelWSDLchildNodes.item(i);

			if (childNode.getLocalName() == null) {
				continue;
			}

			if (childNode.getLocalName().equals("import")) {
				Element importElement = bpelWSDLDocument.createElementNS(
						"http://schemas.xmlsoap.org/wsdl/", "import");
				importElement.setAttribute("location", "srsService.wsdl");
				importElement.setAttribute("namespace",
						Constants.SRSService_Namespace);
				importElement = (Element) bpelWSDLDocument.importNode(
						importElement, true);
				bpelWSDLDocument.getFirstChild().insertBefore(importElement,
						childNode);
				break;
			}
		}

		// add partnerLinkType to bpel wsdl

		for (int i = 0; i < bpelWSDLchildNodes.getLength(); i++) {
			Node childNode = bpelWSDLchildNodes.item(i);
			if (childNode.getLocalName() == null) {
				continue;
			}

			if (childNode.getLocalName().equals("partnerLinkType")) {
				Element partnerLinkTypeElement = bpelWSDLDocument
						.createElementNS(
								"http://docs.oasis-open.org/wsbpel/2.0/plnktype",
								"partnerLinkType");
				partnerLinkTypeElement.setAttribute("name",
						Constants.SRSService_PartnerLinkTypeName);
				partnerLinkTypeElement.setAttribute("xmlns:srsService",
						Constants.SRSService_Namespace);

				Element myRoleElement = bpelWSDLDocument.createElementNS(
						"http://docs.oasis-open.org/wsbpel/2.0/plnktype",
						"role");
				myRoleElement.setAttribute("name", "Requester");
				myRoleElement.setAttribute("portType", "srsService:"
						+ Constants.SRSService_CallbackPortTypeName);

				Element partnerRoleElement = bpelWSDLDocument.createElementNS(
						"http://docs.oasis-open.org/wsbpel/2.0/plnktype",
						"role");
				partnerRoleElement.setAttribute("name", "Requestee");
				partnerRoleElement.setAttribute("portType", "srsService:"
						+ Constants.SRSService_PortTypeName);

				partnerLinkTypeElement.appendChild(myRoleElement);
				partnerLinkTypeElement.appendChild(partnerRoleElement);

				partnerLinkTypeElement = (Element) bpelWSDLDocument.importNode(
						partnerLinkTypeElement, true);
				bpelWSDLDocument.getFirstChild().insertBefore(
						partnerLinkTypeElement, childNode);
				break;
			}
		}

		Util.writeDOMtoFile(bpelWSDLDocument, taskState.getProcessWSDLPath());

		// add import to bpel
		Document bpelDocument = Util.parseFileToDOMDocument(taskState
				.getProcessBpelPath());

		NodeList bpelChildNodes = bpelDocument.getFirstChild().getChildNodes();

		for (int i = 0; i < bpelChildNodes.getLength(); i++) {
			Node bpelChildNode = bpelChildNodes.item(i);

			if (bpelChildNode.getLocalName() == null) {
				continue;
			}

			if (bpelChildNode.getLocalName().equals("import")) {
				Element importElement = bpelDocument
						.createElementNS(
								"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
								"import");
				importElement.setAttribute("namespace",
						Constants.SRSService_Namespace);
				importElement.setAttribute("location", "srsService.wsdl");
				importElement.setAttribute("importType",
						"http://schemas.xmlsoap.org/wsdl/");

				importElement = (Element) bpelDocument.importNode(
						importElement, true);
				bpelDocument.getFirstChild().insertBefore(importElement,
						bpelChildNode);
				break;
			}
		}

		// add partnerLink to bpel

		for (int i = 0; i < bpelChildNodes.getLength(); i++) {
			Node bpelChildNode = bpelChildNodes.item(i);

			if (bpelChildNode.getLocalName() == null) {
				continue;
			}

			if (bpelChildNode.getLocalName().equals("partnerLinks")) {
				Element partnerLinkElement = bpelDocument
						.createElementNS(
								"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
								"partnerLink");
				partnerLinkElement.setAttribute("name",
						Constants.SRSService_PartnerLinkName);
				partnerLinkElement.setAttribute("myRole", "Requester");
				partnerLinkElement.setAttribute("partnerRole", "Requestee");

				partnerLinkElement.setAttribute("partnerLinkType",
						"blubPrefix:"
								+ Constants.SRSService_PartnerLinkTypeName);
				// append namespace def to partnerLinksElement
				((Element) bpelChildNode).setAttribute("xmlns:blubPrefix",
						bpelWSDLDocument.getFirstChild().getAttributes()
								.getNamedItem("targetNamespace")
								.getTextContent());

				partnerLinkElement = (Element) bpelDocument.importNode(
						partnerLinkElement, true);

				bpelChildNode.appendChild(partnerLinkElement);
				break;
			}
		}

		// write notifyOperation correlationSets

		Node bpelCorrelationSets = null;

		for (int i = 0; i < bpelChildNodes.getLength(); i++) {
			Node bpelChildNode = bpelChildNodes.item(i);

			if (bpelChildNode.getLocalName() == null) {
				continue;
			}

			if (bpelChildNode.getLocalName().equals("correlationSets")) {
				// found global correlation sets
				bpelCorrelationSets = bpelChildNode;
			}
		}

		if (bpelCorrelationSets == null) {
			// have to generate correlationSets element

			Element newBpelCorrelationSetsElement = bpelDocument
					.createElementNS(
							"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
							"correlationSets");
			bpelCorrelationSets = newBpelCorrelationSetsElement = (Element) bpelDocument
					.importNode(newBpelCorrelationSetsElement, true);

			for (int i = 0; i < bpelChildNodes.getLength(); i++) {
				if (bpelChildNodes.item(i).getLocalName() != null
						&& bpelChildNodes.item(i).getLocalName()
								.equals("variables")) {
					bpelChildNodes
							.item(i)
							.getParentNode()
							.insertBefore(newBpelCorrelationSetsElement,
									bpelChildNodes.item(i));
				}
			}
		}
		((Element) bpelCorrelationSets).setAttribute("xmlns:srsService",
				Constants.SRSService_Namespace);

		Element newSrsCorrelationSet = bpelDocument.createElementNS(
				"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
				"correlationSet");

		newSrsCorrelationSet.setAttribute("name",
				Constants.SRSService_correlationSetName);
		newSrsCorrelationSet.setAttribute("properties", "srsService:"
				+ Constants.SRSService_notifyVarPropertyName);

		newSrsCorrelationSet = (Element) bpelDocument.importNode(
				newSrsCorrelationSet, true);

		bpelCorrelationSets.appendChild(newSrsCorrelationSet);

		/*
		 * <correlationSets> <correlationSet
		 * name="InvokePortTypeCorrelationSet6"
		 * properties="tns:InvokePortTypeProperty5"/> <correlationSet
		 * name="InvokePortTypeCorrelationSet12"
		 * properties="tns:InvokePortTypeProperty11"/> </correlationSets>
		 */

		// write bpel process file
		Util.writeDOMtoFile(bpelDocument, taskState.getProcessBpelPath());

		// add invoker provide on deploy.xml

		Path deployXmlPath = taskState.getDeployXmlPath();

		Document deployXmlDocument = Util.parseFileToDOMDocument(deployXmlPath);

		String xpathExpression = "/*[local-name()='deploy']/*[local-name()='process']";

		NodeList processNodes = Util.XpathQueryDocument(xpathExpression,
				deployXmlDocument);

		Node processNode = null;
		switch (processNodes.getLength()) {
		case 1:
			processNode = processNodes.item(0);
			break;
		default:
			System.out
					.println("Error. Found not only one process element in deploy.xml");
			break;
		}

		((Element) processNode).setAttribute("xmlns:srsService",
				Constants.SRSService_Namespace);

		Element invokeNode = deployXmlDocument.createElementNS(
				Constants.ApacheOde_Namespace, "invoke");

		invokeNode.setAttribute("partnerLink",
				Constants.SRSService_PartnerLinkName);

		Element invokerServiceNode = deployXmlDocument.createElementNS(
				Constants.ApacheOde_Namespace, "service");

		invokerServiceNode.setAttribute("name", "srsService:"
				+ Constants.SRSService_serviceName);
		invokerServiceNode.setAttribute("port", Constants.SRSService_portName);

		invokeNode.appendChild(invokerServiceNode);

		processNode.appendChild(invokeNode);

		Element provideNode = deployXmlDocument.createElementNS(
				Constants.ApacheOde_Namespace, "provide");

		provideNode.setAttribute("partnerLink",
				Constants.SRSService_PartnerLinkName);

		Element provideServiceNode = deployXmlDocument.createElementNS(
				Constants.ApacheOde_Namespace, "service");

		provideServiceNode.setAttribute("name", "srsService:"
				+ Constants.SRSService_serviceName);

		provideServiceNode.setAttribute("port",
				Constants.SRSService_callbackPortName);

		provideNode.appendChild(provideServiceNode);

		processNode.appendChild(provideNode);

		Util.writeDOMtoFile(deployXmlDocument, deployXmlPath);
	}

}
