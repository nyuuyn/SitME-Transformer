package iaas.uni.stuttgart.de.sitme.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Kalman Kepes - kepeskn@studi.informatik.uni-stuttgart.de
 *
 */
public class Util {

	public static Document parseFileToDOMDocument(Path xmlFilePath) {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory
				.newInstance();

		builderFactory.setNamespaceAware(true);
		builderFactory.setIgnoringComments(true);

		DocumentBuilder builder = null;
		try {
			builder = builderFactory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		}

		Document document = null;
		try {
			document = builder.parse(new FileInputStream(xmlFilePath.toFile()));
			return document;
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public static void writeDOMtoFile(Document document, Path filePath) {
		try {
			TransformerFactory transformerFactory = TransformerFactory
					.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			DOMSource source = new DOMSource(document);
			StreamResult streamResult = new StreamResult(filePath.toFile());
			transformer.transform(source, streamResult);
		} catch (TransformerConfigurationException e) {
			e.printStackTrace();
		} catch (TransformerException e) {
			e.printStackTrace();
		}
	}

	public static NodeList XpathQueryDocument(String xpathExpression,
			Document xmlDocument) {
		XPath xPath = XPathFactory.newInstance().newXPath();

		NodeList nodeList = null;
		try {
			nodeList = (NodeList) xPath.compile(xpathExpression).evaluate(
					xmlDocument, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}

		return nodeList;

	}

	public static NodeList XpathQueryFile(String xpathExpression,
			Path xmlFilePath) {

		XPath xPath = XPathFactory.newInstance().newXPath();

		NodeList nodeList = null;
		try {
			nodeList = (NodeList) xPath.compile(xpathExpression).evaluate(
					Util.parseFileToDOMDocument(xmlFilePath),
					XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			e.printStackTrace();
		}

		return nodeList;
	}

	public static List<Path> findFilesRecursive(String suffix, Path directory) {
		List<Path> foundFiles = new ArrayList<Path>();

		Collection<File> files = FileUtils.listFiles(directory.toFile(),
				FileFilterUtils.suffixFileFilter(suffix),
				TrueFileFilter.INSTANCE);

		for (File file : files) {
			foundFiles.add(file.toPath());
		}
		return foundFiles;
	}

	public static List<Path> findFilesRecursive(Path directory) {
		List<Path> foundFiles = new ArrayList<Path>();

		Collection<File> files = FileUtils.listFiles(directory.toFile(),
				TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);

		for (File file : files) {
			foundFiles.add(file.toPath());
		}
		return foundFiles;
	}

	public static List<Path> findDirectoriesRecursive(Path directory) {
		List<Path> dirs = new ArrayList<Path>();

		Collection<File> filesAndDirs = FileUtils.listFilesAndDirs(
				directory.toFile(), TrueFileFilter.INSTANCE,
				TrueFileFilter.INSTANCE);

		for (File fileOrDir : filesAndDirs) {
			if (fileOrDir.isDirectory()) {
				dirs.add(fileOrDir.toPath());
			}
		}

		return dirs;
	}

	public static Path findWSDLForProcess(List<Path> wsdlPaths, Path bpelPath) {
		// TODO this is a pretty weak search for the wsdl, as this method should
		// look for a single receive with createInstance="yes". But in SitME
		// there may be none
		// fetch instance creating receive
		String xpathQuery = "//*[local-name()='process']";

		NodeList nodeList = Util.XpathQueryFile(xpathQuery, bpelPath);

		Node processNode = null;

		if (nodeList.getLength() != 1) {
			return null;
		}

		processNode = nodeList.item(0);

		String portTypeNS = processNode.getAttributes()
				.getNamedItem("targetNamespace").getTextContent();

		for (Path wsdlPath : wsdlPaths) {
			String defsXpathExpression = "//*[local-name()='definitions']";
			NodeList definitionsElements = Util.XpathQueryFile(
					defsXpathExpression, wsdlPath);
			if (definitionsElements.getLength() != 1) {
				System.out
						.println("WSDL files must contain only one definitions element");
				continue;
			}

			Node defsElement = definitionsElements.item(0);
			if (!defsElement.getAttributes().getNamedItem("targetNamespace")
					.getTextContent().equals(portTypeNS)) {
				continue;
			}

			return wsdlPath;
		}

		return null;
	}

	/*
	 * taken from http://www.java2s.com/Code/Java/XML/
	 * Startingfromanodefindthenamespacedeclarationforaprefix.htm
	 * 
	 * 22.05.2015
	 */
	private static final String XMLNAMESPACE = "xmlns";

	/**
	 * Starting from a node, find the namespace declaration for a prefix. for a
	 * matching namespace declaration.
	 * 
	 * @param node
	 *            search up from here to search for namespace definitions
	 * @param searchPrefix
	 *            the prefix we are searching for
	 * @return the namespace if found.
	 */
	public static String getNamespace(Node node, String searchPrefix) {

		Element el;
		while (!(node instanceof Element)) {
			node = node.getParentNode();
		}
		el = (Element) node;

		NamedNodeMap atts = el.getAttributes();
		for (int i = 0; i < atts.getLength(); i++) {
			Node currentAttribute = atts.item(i);
			String currentLocalName = currentAttribute.getLocalName();
			String currentPrefix = currentAttribute.getPrefix();
			if (searchPrefix.equals(currentLocalName)
					&& XMLNAMESPACE.equals(currentPrefix)) {
				return currentAttribute.getNodeValue();
			} else if (isEmpty(searchPrefix)
					&& XMLNAMESPACE.equals(currentLocalName)
					&& isEmpty(currentPrefix)) {
				return currentAttribute.getNodeValue();
			}
		}

		Node parent = el.getParentNode();
		if (parent instanceof Element) {
			return getNamespace((Element) parent, searchPrefix);
		}

		return null;
	}

	public static boolean isEmpty(String str) {
		return str == null || str.length() == 0;
	}
}
