package iaas.uni.stuttgart.de.sitme.logic;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import iaas.uni.stuttgart.de.sitme.model.BPELFactory;
import iaas.uni.stuttgart.de.sitme.model.TaskState;
import iaas.uni.stuttgart.de.sitme.util.Constants;
import iaas.uni.stuttgart.de.sitme.util.Util;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class SitMEScopeTransformer {

	public static void transformSitMEScopes(TaskState taskState) {
		Path bpelProcessPath = taskState.getProcessBpelPath();

		Document bpelProcessDocument = Util
				.parseFileToDOMDocument(bpelProcessPath);

		String xpathExpression = "//*[local-name()='SituationalScope' and namespace-uri()='"
				+ Constants.SitME_Namespace + "']";

		NodeList sitMeScopeNodes = Util.XpathQueryDocument(xpathExpression,
				bpelProcessDocument);

		// find global variable element
		xpathExpression = "/*[local-name()='process']/*[local-name()='variables']";

		NodeList variablesNodes = Util.XpathQueryDocument(xpathExpression,
				bpelProcessDocument);

		Node variablesNode = null;
		// FIXME works only if one global variables element is defined
		// Tip: Use recursive getParent and check for elements which can have a
		// 'variables' child element (scope,..)
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

		for (int i = 0; i < sitMeScopeNodes.getLength(); i++) {
			Node sitMeScopeNode = sitMeScopeNodes.item(i);
			// find sitEventNode
			NodeList sitMeScopeChildren = sitMeScopeNode.getChildNodes();
			// activities which are wrapped by the scope will be added here fpr
			// re-use
			List<Node> scopedActivities = new ArrayList<Node>();
			List<Node> sitMeEventNodes = new ArrayList<Node>();
			for (int j = 0; j < sitMeScopeChildren.getLength(); j++) {

				if (sitMeScopeChildren.item(j).getLocalName() == null) {
					continue;
				}

				if (sitMeScopeChildren.item(j).getLocalName()
						.equals("SituationEvent")) {
					sitMeEventNodes.add(sitMeScopeChildren.item(j));
				} else {
					scopedActivities.add(sitMeScopeChildren.item(j));
				}
			}

			if (sitMeEventNodes.isEmpty()) {
				System.out
						.println("Error. SituationalScope doesn't have a SituationEvent defined");
				return;
			}

			String entryMode = SitMEScopeTransformer
					.consolidateEntryMode(sitMeEventNodes);
			// simple check for wait logic
			if (entryMode.contains("Wait")) {
				// parse out the seconds (assuming only
				// "{non-negative-integer}s" is allowed)
				entryMode = entryMode.replace("Wait(", "");
				entryMode = entryMode.replace("s)", "");

				int seconds = Integer.parseInt(entryMode);

				SitMEScopeTransformer.addWaitControlFlow(bpelProcessDocument,
						sitMeScopeNode, scopedActivities,
						String.valueOf(System.currentTimeMillis()), seconds,
						sitMeEventNodes, Constants.SRSService_PartnerLinkName);
			} else if (entryMode.equals("Abort")) {
				// here we add abort logic
				SitMEScopeTransformer.addWaitControlFlow(bpelProcessDocument,
						sitMeScopeNode, scopedActivities,
						String.valueOf(System.currentTimeMillis()), 1,
						sitMeEventNodes, Constants.SRSService_PartnerLinkName);
			}

		}

		Util.writeDOMtoFile(bpelProcessDocument, taskState.getProcessBpelPath());
	}

	private static String consolidateEntryMode(List<Node> situationEventNodes) {

		List<String> entryModes = new ArrayList<String>();

		for (Node situationNodeEvent : situationEventNodes) {
			entryModes.add(SitMEScopeTransformer.getChildNodeValue(
					situationNodeEvent, "EntryMode"));
		}

		int entryModeSeconds = 0;
		for (String entryMode : entryModes) {
			if (entryMode.equals("Abort")) {
				// if one sit event says abort, we abort
				return entryMode;
			} else if (entryMode.contains("Wait")) {
				// if only waits are here we take the largest wait time
				String waitTime = entryMode.replace("Wait(", "");
				waitTime = waitTime.replace("s)", "");

				if (Integer.parseInt(waitTime) >= entryModeSeconds) {
					entryModeSeconds = Integer.parseInt(waitTime);
				}
			}
		}

		return "Wait(" + entryModeSeconds + "s)";
	}

	private static String getChildNodeValue(Node node, String childLocalName) {
		NodeList sitMEEventChildren = node.getChildNodes();

		for (int j = 0; j < sitMEEventChildren.getLength(); j++) {
			Node sitMEEventChild = sitMEEventChildren.item(j);

			if (sitMEEventChild.getLocalName() == null) {
				continue;
			}

			if (sitMEEventChild.getLocalName().equals(childLocalName)) {
				return sitMEEventChild.getTextContent();
			}
		}

		return null;
	}

	private static void addWaitControlFlow(Document bpelDocument,
			Node situationScopeNode, List<Node> scopedActivities, String id,
			int seconds, List<Node> situationEventNodes,
			String srsPartnerLinkName) {
		/*
		 * <!-- Fragment variability points: --> <!-- {id}, {seconds},
		 * {situationId}, {objectId}, {srsPartnerLink} --> <!-- Variables to
		 * declare for script: --> <!-- startTime{id}, situationCheck{id},
		 * SRSGetRequest{id}, SRSGetResponse{id} -->
		 */
		// load fragment to string
		URL scopeFragmentURL = SitMEScopeTransformer.class
				.getResource("/SitMEScopeFragment.xml");

		System.out.println("Loading SitMEScope fragment from "
				+ scopeFragmentURL);
		String scopeFragmentString = null;
		try {
			scopeFragmentString = FileUtils.readFileToString(new File(
					scopeFragmentURL.toURI()));
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		// replace variability points
		scopeFragmentString = scopeFragmentString.replace("{id}", id);
		scopeFragmentString = scopeFragmentString.replace("{seconds}",
				String.valueOf(seconds));
		scopeFragmentString = scopeFragmentString.replace("{srsPartnerLink}",
				srsPartnerLinkName);

		// checks whether we need an eventhandler (SituationViolation=Abort)
		boolean needsEventHandler = false;
		
		for (Node situationEventNode : situationEventNodes) {

			scopeFragmentString = scopeFragmentString.replace(
					"{SituationEvent}",
					SitMEScopeTransformer
							.generateSituationEventElement(situationEventNode)
							+ "{SituationEvent}");
			if(SitMEScopeTransformer.getChildNodeValue(situationEventNode, "SituationViolation").equals("Abort")){
				needsEventHandler = true;
			}
		}
		
		if(needsEventHandler){
			URL eventHandlerFragmentURL = SitMEScopeTransformer.class
					.getResource("/SitMEScopeEventHandlerFragment.xml");

			System.out.println("Loading SitMEScope eventHandler fragment from "
					+ eventHandlerFragmentURL);
			String eventHandlerFragmentString = null;
			try {
				eventHandlerFragmentString = FileUtils.readFileToString(new File(
						eventHandlerFragmentURL.toURI()));
			} catch (IOException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			// {srsServicePartnerLinkName},{srsServicePortType}, {id}
			eventHandlerFragmentString = eventHandlerFragmentString.replace("{id}", id);
			eventHandlerFragmentString = eventHandlerFragmentString.replace("{srsServicePortType}", Constants.SRSService_CallbackPortTypeName);
			eventHandlerFragmentString = eventHandlerFragmentString.replace("{srsServicePartnerLinkName}", Constants.SRSService_PartnerLinkName);
			
			scopeFragmentString = scopeFragmentString.replace("{eventHandler}",
					eventHandlerFragmentString);
		} else {
			scopeFragmentString = scopeFragmentString.replace("{eventHandler}",
					"");
		}

		// load to dom
		Element fragmentElement = null;
		try {
			DocumentBuilderFactory dbFac = DocumentBuilderFactory.newInstance();
			dbFac.setNamespaceAware(true);
			dbFac.setIgnoringComments(true);
			DocumentBuilder db = dbFac.newDocumentBuilder();

			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(scopeFragmentString));

			Document doc = db.parse(is);
			fragmentElement = (Element) doc.getFirstChild();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// get elements
		NodeList fragmentChildNodes = fragmentElement.getChildNodes();
		Node scopedActivitiesSequenceElement = null;

		// append to process
		for (int i = 0; i < fragmentChildNodes.getLength(); i++) {
			Node childNode = fragmentChildNodes.item(i);
			childNode = bpelDocument.importNode(childNode, true);
			if (childNode.getLocalName() != null
					&& childNode.getLocalName().equals("scope")) {

				for (int j = 0; j < childNode.getChildNodes().getLength(); j++) {
					Node fragmentScopeChild = childNode.getChildNodes().item(j);
					if (fragmentScopeChild.getLocalName() != null
							&& fragmentScopeChild.getLocalName().equals(
									"sequence")) {

						scopedActivitiesSequenceElement = fragmentScopeChild;
						break;

					}
				}

			}
			situationScopeNode.getParentNode().insertBefore(childNode,
					situationScopeNode);
		}

		for (int i = 0; i < scopedActivities.size(); i++) {
			Node scopedActivity = scopedActivities.get(i);
			scopedActivity = bpelDocument.importNode(scopedActivity, true);
			scopedActivitiesSequenceElement.appendChild(scopedActivity);
		}

		situationScopeNode.getParentNode().removeChild(situationScopeNode);
	}

	private static String generateSituationEventElement(Node situationEventNode) {
		return "<SituationEvent><Situation>"
				+ SitMEScopeTransformer.getChildNodeValue(situationEventNode,
						"Situation")
				+ "</Situation>"
				+ "<Object>"
				+ SitMEScopeTransformer.getChildNodeValue(situationEventNode,
						"Object") + "</Object></SituationEvent>";
	}
}
