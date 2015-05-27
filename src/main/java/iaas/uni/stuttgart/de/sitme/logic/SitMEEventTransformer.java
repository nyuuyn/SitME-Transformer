package iaas.uni.stuttgart.de.sitme.logic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import iaas.uni.stuttgart.de.sitme.model.Subscription;
import iaas.uni.stuttgart.de.sitme.model.TaskState;
import iaas.uni.stuttgart.de.sitme.util.Constants;
import iaas.uni.stuttgart.de.sitme.util.Util;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class SitMEEventTransformer {

	public static List<Subscription> transformSitMEEvents(TaskState taskState) {

		List<Subscription> subscriptions = new ArrayList<Subscription>();
		
		Path bpelProcessPath = taskState.getProcessBpelPath();

		Document bpelProcessDocument = Util
				.parseFileToDOMDocument(bpelProcessPath);

		Node bpelProcessNode = bpelProcessDocument.getFirstChild();

		String xpathExpression = "//*[local-name()='SituationEvent' and namespace-uri()='"
				+ Constants.SitME_Namespace + "']";

		NodeList sitMeEventNodes = Util.XpathQueryDocument(xpathExpression,
				bpelProcessDocument);

		// find global variable element
		xpathExpression = "/*[local-name()='process']/*[local-name()='variables']";

		NodeList variablesNodes = Util.XpathQueryDocument(xpathExpression,
				bpelProcessDocument);

		Node variablesNode = null;
		// FIXME works only if one global variables element is defined
		switch (variablesNodes.getLength()) {
		case 0:
			System.out
					.println("No global variables element defined in process");
			break;
		case 1:
			System.out.println("Found single variables element");
			variablesNode = variablesNodes.item(0);
			break;
		default:
			System.out
					.println("Error. Found multiple global variables element");
			break;
		}

		for (int i = 0; i < sitMeEventNodes.getLength(); i++) {
			Node sitMeEventNode = sitMeEventNodes.item(i);
			NodeList sitMeEventNodeChildren = sitMeEventNode.getChildNodes();

			Node situationNode = null;
			Node objectNode = null;

			for(int index = 0; index < sitMeEventNodeChildren.getLength(); index++){
				Node sitMeEventChildNode = sitMeEventNodeChildren.item(index);
				if(sitMeEventChildNode.getLocalName() != null && sitMeEventChildNode.getLocalName().equals("Situation")){
					situationNode = sitMeEventChildNode;
				}
				if(sitMeEventChildNode.getLocalName() != null && sitMeEventChildNode.getLocalName().equals("Object")){
					objectNode = sitMeEventChildNode;
				}
			}
			
			if(situationNode == null){
				System.out.println("SituationEvent must have Situation and Object elements.");
				return null;
			}
			
			if(objectNode == null){
				System.out.println("SituationEvent must have Situation and Object elements.");
				return null;
			}
			
			
			System.out.println("SituationNodeName: " + situationNode.getNodeName());
			System.out.println(situationNode.getTextContent());
			System.out.println(situationNode.getNodeValue());
			System.out.println(situationNode.getNodeType());
			System.out.println("ObjectNodeName: " + objectNode.getNodeName());
			System.out.println(objectNode.getTextContent());
			System.out.println(objectNode.getNodeValue());
			System.out.println(objectNode.getNodeType());
			
			subscriptions.add(new Subscription(situationNode.getTextContent(), objectNode.getTextContent()));

			Element bpelVariableElement = bpelProcessDocument.createElementNS(
					"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
					"variable");

			((Element)variablesNode).setAttribute("xmlns:srsNs",
					Constants.SRSService_Namespace);

			bpelVariableElement.setAttribute("messageType", "srsNs:"
					+ Constants.SitME_NotifyMessageType);

			String varName = "sitMeReqVar_" + System.currentTimeMillis();

			bpelVariableElement.setAttribute("name", varName);

			bpelVariableElement = (Element) bpelProcessDocument.importNode(
					bpelVariableElement, true);

			variablesNode.appendChild(bpelVariableElement);

			Element bpelReceiveElement = bpelProcessDocument.createElementNS(
					"http://docs.oasis-open.org/wsbpel/2.0/process/executable",
					"receive");

			bpelReceiveElement.setAttribute("name",
					"SitMeSituationEvent_" + System.currentTimeMillis());

			bpelReceiveElement.setAttribute("xmlns:srsNs",
					Constants.SRSService_Namespace);

			bpelReceiveElement.setAttribute("operation", "Notify");

			bpelReceiveElement.setAttribute("partnerLink",
					Constants.SRSService_PartnerLinkName);

			bpelReceiveElement.setAttribute("portType", "srsNs:"
					+ Constants.SRSService_CallbackPortTypeName);

			bpelReceiveElement.setAttribute("variable", varName);
			
			bpelReceiveElement.setAttribute("createInstance", "yes");
			
			// add correlation						
			Element correlationsElement = bpelProcessDocument.createElementNS("http://docs.oasis-open.org/wsbpel/2.0/process/executable", "correlations");
			Element correlationElement = bpelProcessDocument.createElementNS("http://docs.oasis-open.org/wsbpel/2.0/process/executable", "correlation");
			
			correlationElement.setAttribute("initiate", "yes");
			correlationElement.setAttribute("set", Constants.SRSService_correlationSetName);
			
			correlationsElement.appendChild(correlationElement);
			
			bpelReceiveElement.appendChild(correlationsElement);
			
			
			bpelReceiveElement = (Element) bpelProcessDocument.importNode(
					bpelReceiveElement, true);

			sitMeEventNode.getParentNode().insertBefore(bpelReceiveElement,
					sitMeEventNode);

			sitMeEventNode.getParentNode().removeChild(sitMeEventNode);

		}

		Util.writeDOMtoFile(bpelProcessDocument, taskState.getProcessBpelPath());
		
		return subscriptions;
	}

}
