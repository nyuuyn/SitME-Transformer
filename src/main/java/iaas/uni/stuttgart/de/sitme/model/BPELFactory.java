package iaas.uni.stuttgart.de.sitme.model;

import iaas.uni.stuttgart.de.sitme.util.Constants;

import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 * 
 */
public class BPELFactory {

	public static Element createAssignCurrentTime(Document xmlDocument,
			Element variableElement) {

		Element assignElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "assign");
		assignElement.setAttribute("name",
				"SitMEAssignCurrentTime" + System.currentTimeMillis());

		Element copyElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "copy");
		Element fromElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "from");
		Element toElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "to");
		Element queryElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "query");
		CDATASection cdataSection = xmlDocument
				.createCDATASection("current-dateTime()");

		queryElement.appendChild(cdataSection);
		fromElement.appendChild(queryElement);
		copyElement.appendChild(fromElement);

		toElement
				.setAttribute("variable", variableElement.getAttribute("name"));
		copyElement.appendChild(toElement);

		assignElement.appendChild(copyElement);

		return assignElement;
	}

	public static Element createAssignBooleanValue(Document xmlDocument,
			Element variableElement, boolean value) {
		return BPELFactory.createAssignFromQueryToVar(xmlDocument,
				value ? "true()" : "false()",
				variableElement.getAttribute("name"));
	}

	public static Element createSyncInvoke(Document xmlDocument,
			String inputVarName, String outputVarName) {
		/*
		 * <invoke name="Write_DBWrite" partnerLink="WriteDBRecord"
		 * portType="ns2:WriteDBRecord_ptt" operation="insert"
		 * inputVariable="Invoke_DBWrite_merge_InputVariable"/>
		 */
		Element invokeElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "invoke");

		invokeElement.setAttribute("xmlns:srsService",
				Constants.SRSService_Namespace);
		invokeElement.setAttribute("name",
				"SitMEInvokeSrsGet" + System.currentTimeMillis());
		invokeElement.setAttribute("partnerLink",
				Constants.SRSService_PartnerLinkName);
		invokeElement.setAttribute("portType", "srsService:"
				+ Constants.SRSService_PortTypeName);
		invokeElement.setAttribute("operation", "Get");
		invokeElement.setAttribute("inputVariable", inputVarName);
		invokeElement.setAttribute("outputVariable", outputVarName);

		return invokeElement;
	}

	public static Element createWhile(Document xmlDocument, String condition) {
		Element whileElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "while");

		whileElement.setAttribute("name",
				"SitMEWhile" + System.currentTimeMillis());
		whileElement.setAttribute("condition", condition);

		return whileElement;
	}

	public static Element createWait(Document xmlDocument, int seconds) {
		Element waitElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "wait");

		waitElement.setAttribute("name",
				"SitMEWait" + System.currentTimeMillis());
		waitElement.setAttribute("for", "PT0H0M" + seconds + "S");

		return waitElement;
	}

	public static Element createAssignFromQueryToVar(Document xmlDocument,
			String query, String assignedVarName) {
		Element assignElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "assign");
		assignElement.setAttribute("name",
				"SitMEAssignWithQuery" + System.currentTimeMillis());

		Element copyElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "copy");
		Element fromElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "from");
		Element toElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "to");
		Element queryElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "query");
		CDATASection cdataSection = xmlDocument.createCDATASection(query);

		queryElement.appendChild(cdataSection);
		fromElement.appendChild(queryElement);
		copyElement.appendChild(fromElement);

		toElement.setAttribute("variable", assignedVarName);
		copyElement.appendChild(toElement);

		assignElement.appendChild(copyElement);

		return assignElement;
	}

	public static Element createAssignSrsServiceGetRequestVar(
			Document xmlDocument, Element variableElement,
			String situationName, String objectId) {
		Element assignElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "assign");
		assignElement.setAttribute("name",
				"SitMEAssignSrsGetRequest" + System.currentTimeMillis());

		Element copyElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "copy");
		Element fromElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "from");
		Element toElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "to");
		Element literalElement = xmlDocument.createElementNS(
				Constants.BPEL_Namespace, "literal");

		// create GetRequest Elements
		/*
		 * <xsd:element name="GetRequest"> <xsd:complexType> <xsd:sequence>
		 * <xsd:element name="Situation" type="xsd:string" /> <xsd:element
		 * name="Object" type="xsd:string" /> </xsd:sequence> </xsd:complexType>
		 * </xsd:element>
		 */

		Element getRequestElement = xmlDocument.createElementNS(
				Constants.SRSService_Namespace, "GetReqtest");
		Element situationElement = xmlDocument.createElementNS(
				Constants.SRSService_Namespace, "Situation");
		Element objectElement = xmlDocument.createElementNS(
				Constants.SRSService_Namespace, "Object");

		situationElement.setTextContent(situationName);
		objectElement.setTextContent(objectId);

		getRequestElement.appendChild(situationElement);
		getRequestElement.appendChild(objectElement);

		literalElement.appendChild(getRequestElement);

		fromElement.appendChild(literalElement);
		copyElement.appendChild(fromElement);

		toElement
				.setAttribute("variable", variableElement.getAttribute("name"));
		copyElement.appendChild(toElement);

		assignElement.appendChild(copyElement);

		return assignElement;
	}
}
