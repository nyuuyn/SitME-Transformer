package iaas.uni.stuttgart.de.sitme.logic;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import iaas.uni.stuttgart.de.sitme.data.Configuration;
import iaas.uni.stuttgart.de.sitme.model.BPELFactory;
import iaas.uni.stuttgart.de.sitme.model.TaskState;
import iaas.uni.stuttgart.de.sitme.util.Constants;
import iaas.uni.stuttgart.de.sitme.util.Util;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class SitMEScopeTransformer {
	
	private static final Logger LOG = Logger.getLogger(SitMEScopeTransformer.class
			.getName());

	public static void transformSitMEScopes(TaskState taskState) {
		Path bpelProcessPath = taskState.getProcessBpelPath();

		Document bpelProcessDocument = Util.parseFileToDOMDocument(bpelProcessPath);

		String xpathExpression = "//*[local-name()='SituationalScope' and namespace-uri()='" + Constants.SitME_Namespace
				+ "']";

		NodeList sitMeScopeNodes = Util.XpathQueryDocument(xpathExpression, bpelProcessDocument);

		// find global variable element
		xpathExpression = "/*[local-name()='process']/*[local-name()='variables']";

		NodeList variablesNodes = Util.XpathQueryDocument(xpathExpression, bpelProcessDocument);

		Node variablesNode = null;
		// FIXME works only if one global variables element is defined
		// Tip: Use recursive getParent and check for elements which can have a
		// 'variables' child element (scope,..)
		switch (variablesNodes.getLength()) {
		case 0:
			LOG.log(Level.FINEST,"No global variables element defined in process");
			break;
		case 1:
			LOG.log(Level.FINEST,"Found single variables element");
			variablesNode = variablesNodes.item(0);
			break;
		default:
			LOG.log(Level.FINEST,"Error. Found multiple global variables element");
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

				if (sitMeScopeChildren.item(j).getLocalName().equals("SituationEvent")) {
					sitMeEventNodes.add(sitMeScopeChildren.item(j));
				} else {
					scopedActivities.add(sitMeScopeChildren.item(j));
				}
			}

			if (sitMeEventNodes.isEmpty()) {
				LOG.log(Level.FINEST,"Error. SituationalScope doesn't have a SituationEvent defined");
				return;
			}

			String entryMode = SitMEScopeTransformer.consolidateEntryMode(sitMeEventNodes);
			// simple check for wait logic
			if (entryMode.contains("Wait")) {
				// parse out the seconds (assuming only
				// "{non-negative-integer}s" is allowed)
				entryMode = entryMode.replace("Wait(", "");
				entryMode = entryMode.replace("s)", "");

				int seconds = Integer.parseInt(entryMode);

				SitMEScopeTransformer.addWaitControlFlow(bpelProcessDocument, sitMeScopeNode, scopedActivities,
						String.valueOf(System.currentTimeMillis()), seconds, sitMeEventNodes,
						Constants.SRSService_PartnerLinkName);
			} else if (entryMode.equals("Abort")) {
				// here we add abort logic
				SitMEScopeTransformer.addWaitControlFlow(bpelProcessDocument, sitMeScopeNode, scopedActivities,
						String.valueOf(System.currentTimeMillis()), 1, sitMeEventNodes,
						Constants.SRSService_PartnerLinkName);
			}

		}

		Util.writeDOMtoFile(bpelProcessDocument, taskState.getProcessBpelPath());
	}

	private static String consolidateEntryMode(List<Node> situationEventNodes) {

		List<String> entryModes = new ArrayList<String>();

		for (Node situationNodeEvent : situationEventNodes) {
			entryModes.add(SitMEScopeTransformer.getChildNodeValue(situationNodeEvent, "EntryMode"));
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

		LOG.log(Level.FINEST,"Loading SitMEScope fragment from "
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
		/* replace variability points */
		Configuration config = new Configuration();

		scopeFragmentString = scopeFragmentString.replace("{id}", id);
		scopeFragmentString = scopeFragmentString.replace("{seconds}",
				String.valueOf(seconds));
		scopeFragmentString = scopeFragmentString.replace("{srsPartnerLink}",
				srsPartnerLinkName);

		// {srsServiceCallbackEndpoint}, {srsServiceEndpoint}
		scopeFragmentString = scopeFragmentString.replace(
				"{srsServiceEndpoint}", config.getSrsServiceAddress());
		scopeFragmentString = scopeFragmentString.replace(
				"{srsServiceCallbackEndpoint}",
				config.getSrsServiceCallbackAddress());

		// checks whether we need an eventhandler (SituationViolation=Abort)
		boolean needsEventHandler = false;
		long subscriptionCorrelation = System.currentTimeMillis();
		int index = 1;
		for (Node situationEventNode : situationEventNodes) {

			scopeFragmentString = scopeFragmentString.replace(
					"{SituationEvent}",
					SitMEScopeTransformer
							.generateSituationEventElement(situationEventNode)
							+ "{SituationEvent}");
			if (SitMEScopeTransformer.getChildNodeValue(situationEventNode,
					"SituationViolation").equals("Abort")) {
				needsEventHandler = true;
			}

			// append SituationSubscription to SubscribeReq
			// xmlns:tns{id}="http://www.iaas.uni-stuttgart.de/srsService/"
			String subscriptionXMLString = "<Subscription><Situation>"
					+ SitMEScopeTransformer.getChildNodeValue(
							situationEventNode, "Situation")
					+ "</Situation><Object>"
					+ SitMEScopeTransformer.getChildNodeValue(
							situationEventNode, "Object")
					+ "</Object></Subscription>";

			scopeFragmentString = scopeFragmentString.replace("{Subscription}",
					subscriptionXMLString + "{Subscription}");

		}
		// remove finished tags
		scopeFragmentString = scopeFragmentString.replace("{SituationEvent}",
				"");

		scopeFragmentString = scopeFragmentString.replace("{Subscription}", "");

		scopeFragmentString = scopeFragmentString.replace("{Correlation}",
				String.valueOf(subscriptionCorrelation));

		// check for eventhandler (SitME Abort Handling)
		if (needsEventHandler) {
			// activate subscribe activities (remove comment brackets..)
			scopeFragmentString = scopeFragmentString.replace("<!--subscribe", "").replace("subscribe-->", "");
			
			URL eventHandlerFragmentURL = SitMEScopeTransformer.class
					.getResource("/SitMEScopeEventHandlerFragment.xml");

			LOG.log(Level.FINEST,"Loading SitMEScope eventHandler fragment from "
					+ eventHandlerFragmentURL);
			String eventHandlerFragmentString = null;
			try {
				eventHandlerFragmentString = FileUtils
						.readFileToString(new File(eventHandlerFragmentURL
								.toURI()));
			} catch (IOException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			// {srsServicePartnerLinkName},{srsServicePortType}, {id}
			eventHandlerFragmentString = eventHandlerFragmentString.replace(
					"{id}", id);
			eventHandlerFragmentString = eventHandlerFragmentString.replace(
					"{srsServicePortType}",
					Constants.SRSService_CallbackPortTypeName);
			eventHandlerFragmentString = eventHandlerFragmentString.replace(
					"{srsServicePartnerLinkName}",
					Constants.SRSService_PartnerLinkName);

			scopeFragmentString = scopeFragmentString.replace("{eventHandler}",
					eventHandlerFragmentString);

			
		} else {
			scopeFragmentString = scopeFragmentString.replace("{eventHandler}",
					"");
		}

		LOG.log(Level.FINEST,"Created following skeleton: ");
		LOG.log(Level.FINEST,scopeFragmentString);

		// load the fragment string to dom
		Element fragmentElement = null;
		Document fragmentDoc = null;
		try {
			DocumentBuilderFactory dbFac = DocumentBuilderFactory.newInstance();
			dbFac.setNamespaceAware(true);
			dbFac.setIgnoringComments(true);
			DocumentBuilder db = dbFac.newDocumentBuilder();

			InputSource is = new InputSource();
			is.setCharacterStream(new StringReader(scopeFragmentString));

			fragmentDoc = db.parse(is);
			fragmentElement = (Element) fragmentDoc.getFirstChild();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		// find the sequence element in the fragment
		// //</bpel:sequence>
		// </bpel:scope>
		// </bpel:scope>
		// </bpel:sequence>
		// </bpel:scope>
		// </fragment>
		Node scopedActivitiesSequenceElement = null;
		// TODO maybe reduce expr (local-name()='.. ignores NS,..)
		String xpathExpr = "/*[local-name()='fragment']/*[local-name()='scope']/*[local-name()='sequence']/*[local-name()='scope']/*[local-name()='sequence']/*[local-name()='scope']/*[local-name()='sequence' and @name='SitMESequence"
				+ id + "']";
		XPath xPath = XPathFactory.newInstance().newXPath();
		NodeList nodes = null;
		try {
			nodes = (NodeList) xPath.evaluate(xpathExpr,
					fragmentDoc.getDocumentElement(), XPathConstants.NODESET);
			if (nodes.getLength() == 1) {
				scopedActivitiesSequenceElement = nodes.item(0);
			} else {
				LOG.log(Level.FINEST,"Internal fragment is malformed. Only one sequence with element with attribute name=\"SitMESequence"
								+ id + "\" is allowed");
				return;
			}
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}

		// add activities in the defined SitME Scope to the fragment
		for (int i = 0; i < scopedActivities.size(); i++) {
			Node scopedActivity = scopedActivities.get(i);
			scopedActivity = fragmentDoc.importNode(scopedActivity, true);
			scopedActivitiesSequenceElement.appendChild(scopedActivity);
		}
		// append the fragment to the process, by finding the scope where the sitme while is and
		// append it
		// get elements
		NodeList fragmentChildNodes = fragmentElement.getChildNodes();
		for (int i = 0; i < fragmentChildNodes.getLength(); i++) {
			Node childNode = fragmentChildNodes.item(i);
			childNode = bpelDocument.importNode(childNode, true);
			
			situationScopeNode.getParentNode().insertBefore(childNode,
					situationScopeNode);
		}

		// remove the defined sitme scope from doc
		situationScopeNode.getParentNode().removeChild(situationScopeNode);
	}

	private static String generateSituationEventElement(Node situationEventNode) {
		return "<SituationEvent><Situation>" + SitMEScopeTransformer.getChildNodeValue(situationEventNode, "Situation")
				+ "</Situation>" + "<Object>" + SitMEScopeTransformer.getChildNodeValue(situationEventNode, "Object")
				+ "</Object></SituationEvent>";
	}
}
